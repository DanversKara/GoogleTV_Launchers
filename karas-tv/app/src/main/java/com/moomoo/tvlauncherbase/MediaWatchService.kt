package com.moomoo.tvlauncherbase

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * "What's playing" watcher for Continue watching / Upcoming.
 *
 * Android only lets an app see other apps' media sessions if it holds
 * notification access, so this is a NotificationListenerService. It only
 * looks at the media session's title / subtitle / artwork, and at the
 * playing app's own media-controls card (to find the series name) — never
 * at messages or any other notification — while something plays (Crunchyroll, Netflix,
 * Prime Video, YouTube, Plex, ...) and records it in [WatchStore].
 *
 * Third-party launchers can't read Google TV's own "Watch Next" list
 * (that needs a system-only permission), so this is how TV Launcher knows
 * what you're watching. Apps that don't publish a media session can't be
 * seen — add those shows with "Follow a show" instead.
 */
class MediaWatchService : NotificationListenerService() {

    companion object {
        @Volatile var instance: MediaWatchService? = null
            private set
        @Volatile var lastSeen: String? = null
            private set
        /** Everything the last playing app reported (for diagnosing detection). */
        @Volatile var lastRaw: String? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private val tracked = HashMap<MediaSession_Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        main.post { track(list ?: emptyList()) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        msm = m
        val me = ComponentName(this, MediaWatchService::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, me, main)
            track(m.getActiveSessions(me))
        } catch (e: SecurityException) { /* access revoked */ }
    }

    override fun onListenerDisconnected() {
        cleanup()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance === this) instance = null
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) {}
        for ((_, p) in tracked) try { p.first.unregisterCallback(p.second) } catch (e: Exception) {}
        tracked.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { /* notifications are not read */ }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun track(list: List<MediaController>) {
        val live = list.map { it.sessionToken }.toSet()
        val gone = tracked.keys.filter { it !in live }
        for (t in gone) tracked.remove(t)?.let { (mc, cb) -> try { mc.unregisterCallback(cb) } catch (e: Exception) {} }
        for (mc in list) {
            if (mc.sessionToken in tracked) continue
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { capture(mc) }
                override fun onPlaybackStateChanged(state: PlaybackState?) { capture(mc) }
            }
            try { mc.registerCallback(cb, main) } catch (e: Exception) { continue }
            tracked[mc.sessionToken] = mc to cb
            capture(mc)
        }
    }

    private fun capture(mc: MediaController) {
        if (!BasePrefs.watchTracking(this)) return
        val state = mc.playbackState?.state ?: return
        if (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_PAUSED) return
        val md = mc.metadata ?: return
        val pkg = mc.packageName
        val title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_TITLE)

        // Every place an app might put the SERIES name, best first.
        val cands = mutableListOf<String?>(
            md.getString(MediaMetadata.METADATA_KEY_ALBUM),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            md.getString(MediaMetadata.METADATA_KEY_ARTIST),
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            md.getString(MediaMetadata.METADATA_KEY_AUTHOR),
            md.getString(MediaMetadata.METADATA_KEY_WRITER),
            mc.queueTitle?.toString(),
            md.description?.subtitle?.toString()
        )
        // The app's media-controls card (only notifications attached to a media
        // session are looked at — never messages or anything else).
        val notifTexts = mediaNotificationTexts(pkg)
        cands += notifTexts
        md.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)?.takeIf { it.length <= 80 }?.let { cands += it }

        // Raw dump for Settings → Watching & upcoming → "What the app reported".
        lastRaw = buildString {
            append("App: ${AppRepository.label(this@MediaWatchService, pkg)} ($pkg)\n")
            for (k in md.keySet().sorted()) {
                val v = try { md.getString(k) } catch (e: Exception) { null }
                    ?: if (md.getBitmap(k) != null) "[picture]" else try { md.getLong(k).toString() } catch (e: Exception) { "?" }
                append("${k.removePrefix("android.media.metadata.")}: $v\n")
            }
            mc.queueTitle?.let { append("QUEUE_TITLE: $it\n") }
            if (notifTexts.isNotEmpty()) append("MEDIA CARD: ${notifTexts.joinToString(" | ")}\n")
        }

        val art: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.description?.iconBitmap
        val artUri = md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.description?.iconUri?.toString()
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val pos = mc.playbackState?.position ?: -1L
        if (title.isNullOrBlank() && cands.all { it.isNullOrBlank() }) return
        lastSeen = "${AppRepository.label(this, pkg)}: ${listOfNotNull(cands.firstOrNull { !it.isNullOrBlank() }, title).joinToString(" — ")}"
        val app = applicationContext
        val candList = cands.filterNotNull()
        // Bitmap work + disk writes off the main thread.
        Thread {
            try {
                val bmp = art ?: loadLocalArt(artUri)
                WatchStore.recordPlayback(app, pkg, title, candList, bmp, artUri, pos, duration)
            } catch (e: Exception) {}
        }.start()
    }

    /** Text of the playing app's media-controls notification (title / text / subtext). */
    private fun mediaNotificationTexts(pkg: String): List<String> = try {
        activeNotifications.orEmpty()
            .filter { it.packageName == pkg && it.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) }
            .flatMap { sbn ->
                val e = sbn.notification.extras
                listOf(android.app.Notification.EXTRA_TEXT, android.app.Notification.EXTRA_SUB_TEXT,
                    android.app.Notification.EXTRA_TITLE, android.app.Notification.EXTRA_INFO_TEXT)
                    .mapNotNull { e.getCharSequence(it)?.toString()?.takeIf { t -> t.isNotBlank() } }
            }
    } catch (e: Exception) { emptyList() }

    /** Artwork given as content:// or android.resource:// instead of a picture. */
    private fun loadLocalArt(uri: String?): Bitmap? {
        if (uri == null || !(uri.startsWith("content:") || uri.startsWith("android.resource:") || uri.startsWith("file:"))) return null
        return try {
            contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }
    }
}

private typealias MediaSession_Token = android.media.session.MediaSession.Token
