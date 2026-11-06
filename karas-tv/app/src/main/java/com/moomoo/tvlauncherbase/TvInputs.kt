package com.moomoo.tvlauncherbase

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Build

/**
 * TV inputs (HDMI etc.) as launcher tiles, in basic form.
 * Opening an input fires the passthrough URI at the system TV app.
 * Display names are user-renamable (so an input can be called "PS5").
 */
object TvInputs {

    data class Input(val id: String, val label: String, val hardware: Boolean = true) {
        fun displayName(c: Context): String = BasePrefs.inputName(c, id, label)
    }

    /**
     * TV inputs. By default only real hardware inputs (HDMI and the like);
     * app-provided "inputs" such as Xumo Play are left out unless
     * Settings → Show app inputs is on, or [all] is true (button remapper).
     */
    fun list(c: Context, all: Boolean = false): List<Input> {
        if (Build.VERSION.SDK_INT < 21) return emptyList()
        val tim = c.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val withApps = all || BasePrefs.showAppInputs(c)
        return try {
            tim.tvInputList.map { info ->
                val hw = info.isPassthroughInput
                val loaded = try {
                    info.loadLabel(c)?.toString()?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }
                val label = loaded
                    ?: if (info.type == android.media.tv.TvInputInfo.TYPE_HDMI) "HDMI" else info.id.substringAfterLast('/')
                Input(info.id, label, hw)
            }.filter { withApps || it.hardware }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Open a TV input. Tries, in order:
     * 1. The passthrough URI at the system TV app (the right way for
     *    HDMI-style inputs — when the TV has an app that handles it).
     * 2. The input provider's own app. Virtual inputs like Xumo Play
     *    are really apps behind the scenes; opening the app is what
     *    the user wants, and the passthrough URI has no handler for
     *    them on most TVs, which is why the tile used to do nothing.
     * 3. The input's own setup intent, if it offers one.
     * Returns false only when nothing worked, so the caller can show
     * a message instead of failing silently.
     */
    fun open(c: Context, inputId: String): Boolean {
        try {
            val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
            c.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (e: Exception) {
            // Fall through to the provider app.
        }
        val providerPkg = inputId.substringBefore('/')
        if (providerPkg.isNotEmpty() && providerPkg != inputId) {
            val pm = c.packageManager
            val appIntent = pm.getLeanbackLaunchIntentForPackage(providerPkg)
                ?: pm.getLaunchIntentForPackage(providerPkg)
            if (appIntent != null) {
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    c.startActivity(appIntent)
                    return true
                } catch (e: Exception) {
                    // Fall through to the setup intent.
                }
            }
        }
        try {
            val tim = c.getSystemService(TvInputManager::class.java) ?: return false
            val setup = tim.tvInputList.firstOrNull { it.id == inputId }?.createSetupIntent()
                ?: return false
            setup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(setup)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Rename dialog shared by the home screen and Settings. */
    fun showRenameDialog(activity: android.app.Activity, input: Input, onDone: () -> Unit = {}) {
        val edit = android.widget.EditText(activity).apply {
            setText(input.displayName(activity))
            setSelection(text.length)
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.rename_input))
            .setMessage(activity.getString(R.string.input_name_hint))
            .setView(edit)
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    BasePrefs.setInputName(activity, input.id, name)
                    onDone()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}
