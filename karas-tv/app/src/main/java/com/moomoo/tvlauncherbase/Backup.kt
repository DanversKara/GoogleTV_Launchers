package com.moomoo.tvlauncherbase

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.io.File

/**
 * Backups that survive "Clear storage" and uninstalling.
 *
 * The file lives in the TV's shared Downloads folder:
 *   Download/TV Launcher/tv-launcher-backup.json   (latest)
 *   Download/TV Launcher/tv-launcher-backup-previous.json (the one before, kept daily)
 * It holds every setting, camera, button mapping, theme, favorite, followed
 * show and Continue-watching entry. With auto-backup on, it is rewritten
 * ~20 s after any change.
 *
 * Writing: Android 10+ through MediaStore (no permission needed); Android 8–9
 * with the storage permission. Reading after a wipe: from MediaStore when
 * Android still counts the file as ours, else directly (Android 8–10, or
 * Android 11+ with "All files access"), else the user picks the file.
 */
object Backup {
    const val NAME = "tv-launcher-backup.json"
    const val PREV = "tv-launcher-backup-previous.json"
    private const val FOLDER = "Kara's TV"
    /** Where 2.6 saved backups — still checked when restoring. */
    private const val OLD_FOLDER = "TV Launcher"
    const val REQ_PICK = 7311
    const val REQ_PERM = 7312

    /** True while restoring, so the restore itself doesn't trigger a backup. */
    @Volatile var suspended = false

    private val main = Handler(Looper.getMainLooper())
    private var pending: Context? = null
    private val runner = Runnable {
        val c = pending ?: return@Runnable
        pending = null
        Thread { writeNow(c) }.start()
    }

    val location get() = "Download/$FOLDER/$NAME"

    /** Called after every settings change; writes ~20 s later (changes are batched). */
    fun schedule(c: Context) {
        if (suspended) return
        val app = c.applicationContext
        // Never overwrite a good backup with a freshly wiped, empty setup.
        if (!BasePrefs.setupDone(app) || !BasePrefs.autoBackup(app)) return
        pending = app
        main.removeCallbacks(runner)
        main.postDelayed(runner, 20_000)
    }

    private fun legacyDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)

    /** Can we use plain file paths in Downloads? */
    fun directAccess(c: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == 29 -> Environment.isExternalStorageLegacy() &&
            c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun needsPermission(c: Context) = Build.VERSION.SDK_INT < 29 &&
        c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    fun requestPermission(a: Activity) {
        if (needsPermission(a)) a.requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERM)
    }

    @Synchronized fun writeNow(c: Context): Boolean {
        val json = try { BasePrefs.exportJson(c) } catch (e: Exception) { return false }
        val ok = try {
            if (directAccess(c)) {
                val dir = legacyDir().apply { mkdirs() }
                val f = File(dir, NAME)
                rotateFile(c, f, File(dir, PREV))
                val tmp = File(dir, "$NAME.tmp"); tmp.writeText(json)
                tmp.renameTo(f) || run { f.writeText(json); true }
            } else if (Build.VERSION.SDK_INT >= 29) {
                val old = readMediaStore(c, NAME)
                if (old != null && shouldRotate(c)) { writeMediaStore(c, PREV, old); markRotated(c) }
                writeMediaStore(c, NAME, json)
            } else false
        } catch (e: Exception) { false }
        if (ok) BasePrefs.markBackup(c, System.currentTimeMillis())
        return ok
    }

    private fun shouldRotate(c: Context) =
        System.currentTimeMillis() - c.getSharedPreferences("backup_meta", 0).getLong("rot", 0) > 24 * 3600_000L

    private fun markRotated(c: Context) =
        c.getSharedPreferences("backup_meta", 0).edit().putLong("rot", System.currentTimeMillis()).apply()

    private fun rotateFile(c: Context, f: File, prev: File) {
        if (f.exists() && shouldRotate(c)) { f.copyTo(prev, overwrite = true); markRotated(c) }
    }

    // ------------------------------------------------------------ MediaStore (Android 10+)
    private fun relPath() = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"

    private fun findUri(c: Context, name: String, folder: String = FOLDER): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        c.contentResolver.query(
            coll, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "%$folder%"), "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cur ->
            if (cur.moveToFirst()) return ContentUris.withAppendedId(coll, cur.getLong(0))
        }
        return null
    }

    private fun writeMediaStore(c: Context, name: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val r = c.contentResolver
        val uri = findUri(c, name) ?: r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath())
        }) ?: return false
        r.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: return false
        return true
    }

    private fun readMediaStore(c: Context, name: String, folder: String = FOLDER): String? = try {
        findUri(c, name, folder)?.let { u -> c.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }
    } catch (e: Exception) { null }

    // ------------------------------------------------------------ reading / restoring
    /** Finds the latest backup without asking the user, or null. */
    fun find(c: Context): String? {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (folder in listOf(FOLDER, OLD_FOLDER)) {
            try {
                val f = File(File(dl, folder), NAME)
                if (f.canRead()) return f.readText()
            } catch (e: Exception) {}
            readMediaStore(c, NAME, folder)?.let { return it }
        }
        return null
    }

    fun readUri(c: Context, uri: Uri): String? = try {
        c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) { null }

    /** "Saved Tue Nov 3 · 5:40 PM" for a backup's JSON, or null when it isn't one of ours. */
    fun describe(json: String): String? = try {
        val o = org.json.JSONObject(json)
        val at = o.optLong("__exported_at", 0L)
        val cams = try { org.json.JSONArray(o.optString("cameras", "[]")).length() } catch (e: Exception) { 0 }
        val shows = try { org.json.JSONArray(o.optString("__watch", "[]")).length() } catch (e: Exception) { 0 }
        val favs = try { org.json.JSONArray(o.optString("favorites", "[]")).length() } catch (e: Exception) { 0 }
        (if (at > 0) "Saved ${ShowInfo.whenText(at)}" else "Backup") +
            " · $cams camera(s), $favs favorite(s), $shows show(s)"
    } catch (e: Exception) { null }

    fun pickFile(a: Activity): Boolean = try {
        a.startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_PICK)
        true
    } catch (e: Exception) { false }

    val ADB_ALL_FILES = "adb shell appops set com.moomoo.tvlauncherbase MANAGE_EXTERNAL_STORAGE allow"

    /** Full restore flow: find automatically, else let the user pick, else explain. */
    fun restoreFlow(a: Activity, done: () -> Unit) {
        val json = find(a)
        if (json != null) { confirmRestore(a, json, done); return }
        Ui.dialog(a).setTitle("Find your backup")
            .setMessage("${Brand.name} couldn't open $location by itself (Android hides files from a reinstalled or wiped app).\n\n" +
                "• Choose “Pick the file” and open Download → $FOLDER → $NAME, or\n" +
                "• give ${Brand.name} file access once from a computer, then try again:\n$ADB_ALL_FILES")
            .setPositiveButton("Pick the file") { _, _ ->
                if (!pickFile(a)) Ui.toast(a, "This TV has no file picker — use the adb command")
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    fun confirmRestore(a: Activity, json: String, done: () -> Unit) {
        val d = describe(json)
        if (d == null) { Ui.toast(a, "That file isn't a ${Brand.name} backup"); return }
        Ui.dialog(a).setTitle("Restore your settings?")
            .setMessage("$d\n\nThis replaces the current settings, cameras, button mappings, theme and shows.")
            .setPositiveButton("Restore") { _, _ ->
                if (BasePrefs.importJson(a, json)) {
                    BasePrefs.setSetupDone(a)
                    AppRepository.invalidate()
                    Ui.toast(a, "Restored ✓")
                    done()
                } else Ui.toast(a, "Couldn't read that backup")
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
