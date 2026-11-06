package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.VideoView
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One live camera tile. Works for MotionEye out of the box:
 *  - MJPEG stream  (MotionEye "Streaming URL", e.g. http://192.168.1.20:8081/)
 *  - JPEG snapshot (MotionEye "Snapshot URL", e.g. http://192.168.1.20:8765/picture/1/current/)
 *  - Video         (RTSP / HLS / MP4 through the system media player)
 *
 * Credentials: Basic and Digest auth (MotionEye supports both), given
 * either in the camera's user/password fields or as http://user:pass@host/.
 * Streams reconnect automatically with back-off. Call [start] / [stop]
 * from the host's lifecycle; [stop] also runs on detach.
 */
class CameraView(context: Context, var camera: Camera, private val compact: Boolean = false) :
    FrameLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val frameView = FrameView(context)
    private var videoView: VideoView? = null
    private val status = TextView(context)
    private val label = TextView(context)
    @Volatile private var running = false

    /** How the picture fills the tile: FIT (letterbox), FILL (crop to fill), STRETCH. */
    enum class Scale { FIT, FILL, STRETCH }
    var scale = Scale.FIT
        set(v) { field = v; frameView.scale = v; frameView.invalidate() }

    /** Called (main thread) with the picture's shape (after rotation) when it is first known or changes. */
    var onPictureSize: ((Int, Int) -> Unit)? = null
    private var lastW = 0
    private var lastH = 0

    private fun reportSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val swap = camera.rotation == 90 || camera.rotation == 270
        val rw = if (swap) h else w; val rh = if (swap) w else h
        if (rw == lastW && rh == lastH) return
        lastW = rw; lastH = rh
        onPictureSize?.invoke(rw, rh)
    }
    private var worker: Thread? = null
    private var lastFrameAt = 0L

    /**
     * Clips the whole camera picture to a rounded rectangle, so its square
     * video edges sit *behind* the tile / window frame instead of poking out.
     */
    fun setCornerRadius(px: Float) {
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, v.width, v.height, px)
            }
        }
        clipToOutline = px > 0f
        invalidateOutline()
    }

    init {
        setBackgroundColor(Color.BLACK)
        if (camera.type == Camera.TYPE_VIDEO) {
            videoView = VideoView(context)
            addView(videoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        } else {
            addView(frameView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        status.apply {
            setTextColor(LauncherTheme.dimText); textSize = if (compact) 12f else 15f
            gravity = Gravity.CENTER; text = "Connecting…"
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        label.apply {
            setTextColor(Color.WHITE); textSize = if (compact) 11f else 14f
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            background = Ui.rounded(context, 0x88000000.toInt(), 10)
            val p = Ui.dp(context, 4); setPadding(p * 2, p / 2, p * 2, p / 2)
            text = camera.name
            visibility = if (BasePrefs.camShowLabels(context)) View.VISIBLE else View.GONE
        }
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            val m = Ui.dp(context, 8); setMargins(m, m, m, m)
        })
    }

    val isRunning get() = running

    fun start() {
        if (running) return
        running = true
        if (camera.url.isBlank()) { setStatus("No URL set"); return }
        if (camera.type == Camera.TYPE_VIDEO) { startVideo(); return }
        val t = Thread({ loop() }, "cam-${camera.name}")
        t.isDaemon = true
        worker = t
        t.start()
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        videoView?.let { try { it.stopPlayback() } catch (e: Exception) {} }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun setStatus(s: String?) = main.post {
        status.text = s ?: ""
        status.visibility = if (s == null) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------ video (RTSP/HLS)
    private fun startVideo() {
        val vv = videoView ?: return
        setStatus("Connecting…")
        val headers = HashMap<String, String>()
        authHeaderBasic()?.let { headers["Authorization"] = it }
        vv.setOnPreparedListener { mp: MediaPlayer ->
            mp.setVolume(0f, 0f) // cameras never steal the movie's audio
            mp.isLooping = true
            reportSize(mp.videoWidth, mp.videoHeight)
            setStatus(null)
        }
        vv.setOnErrorListener { _, what, _ ->
            setStatus("Video error ($what) — retrying")
            main.postDelayed({ if (running) { try { vv.stopPlayback() } catch (e: Exception) {}; startVideo() } }, 4000)
            true
        }
        try {
            vv.setVideoURI(Uri.parse(stripUserInfo(camera.url)), headers)
            vv.start()
        } catch (e: Exception) {
            setStatus("Can't play: ${e.message}")
        }
    }

    // ------------------------------------------------------------ MJPEG / snapshot
    private fun loop() {
        var backoff = 1000L
        while (running) {
            try {
                if (camera.type == Camera.TYPE_SNAPSHOT) snapshotLoop() else mjpegLoop()
                backoff = 1000L
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                if (!running) return
                setStatus("${shortError(e)}\nretrying…")
            }
            try { Thread.sleep(backoff) } catch (e: InterruptedException) { return }
            backoff = (backoff * 2).coerceAtMost(15_000L)
        }
    }

    private fun shortError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Unknown host"
        is java.net.ConnectException -> "Can't connect"
        is java.net.SocketTimeoutException -> "Timed out"
        is AuthException -> "Wrong username/password"
        else -> e.message?.take(60) ?: e.javaClass.simpleName
    }

    private class AuthException : Exception("auth")

    private fun snapshotLoop() {
        while (running) {
            val conn = open(camera.url)
            try {
                val bytes = conn.inputStream.use { it.readBytes() }
                publish(bytes)
            } finally { conn.disconnect() }
            Thread.sleep(camera.refreshMs.coerceIn(200, 60_000).toLong())
        }
    }

    private fun mjpegLoop() {
        val conn = open(camera.url)
        try {
            val ct = conn.contentType ?: ""
            val ins = BufferedInputStream(conn.inputStream, 64 * 1024)
            if (ct.startsWith("image/jpeg", true)) {
                // The URL is actually a snapshot; treat it as one.
                publish(ins.readBytes())
                camera = camera.copy(type = Camera.TYPE_SNAPSHOT)
                return
            }
            while (running) {
                val frame = readFrame(ins) ?: throw java.io.EOFException("Stream ended")
                publish(frame)
            }
        } finally { conn.disconnect() }
    }

    /** Reads one JPEG from a multipart/x-mixed-replace stream. */
    private fun readFrame(ins: BufferedInputStream): ByteArray? {
        var contentLength = -1
        while (true) {
            ins.mark(2)
            val b0 = ins.read(); val b1 = ins.read()
            ins.reset()
            if (b0 == -1) return null
            if (b0 == 0xFF && b1 == 0xD8) break
            val line = readLine(ins) ?: return null
            if (line.isEmpty()) {
                if (contentLength > 0) break else continue
            }
            val l = line.lowercase()
            if (l.startsWith("content-length:"))
                contentLength = l.substringAfter(':').trim().toIntOrNull() ?: -1
        }
        if (contentLength in 1..20_000_000) {
            val buf = ByteArray(contentLength)
            var off = 0
            while (off < contentLength) {
                val n = ins.read(buf, off, contentLength - off)
                if (n < 0) return null
                off += n
            }
            return buf
        }
        // No length header: scan to the JPEG end marker.
        val out = ByteArrayOutputStream(128 * 1024)
        var prev = -1
        while (true) {
            val b = ins.read()
            if (b < 0) return null
            out.write(b)
            if (prev == 0xFF && b == 0xD9) return out.toByteArray()
            prev = b
            if (out.size() > 20_000_000) throw java.io.IOException("Frame too large")
        }
    }

    private fun readLine(ins: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = ins.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length < 1024) sb.append(b.toChar())
        }
    }

    private var sampleSize = 0

    private fun publish(bytes: ByteArray) {
        if (sampleSize == 0) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            val target = maxOf(width, 320)
            var s = 1
            while (o.outWidth / (s * 2) >= target) s *= 2
            sampleSize = s
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return
        lastFrameAt = System.currentTimeMillis()
        main.post {
            if (status.visibility == View.VISIBLE) setStatus(null)
            frameView.setFrame(bmp, camera.rotation)
            reportSize(bmp.width, bmp.height)
        }
    }

    // ------------------------------------------------------------ HTTP + auth
    private fun creds(): Pair<String, String>? {
        if (camera.user.isNotEmpty()) return camera.user to camera.pass
        val ui = try { URL(camera.url).userInfo } catch (e: Exception) { null } ?: return null
        val parts = Uri.decode(ui).split(":", limit = 2)
        return parts[0] to parts.getOrElse(1) { "" }
    }

    private fun authHeaderBasic(): String? {
        val (u, p) = creds() ?: return null
        return "Basic " + Base64.encodeToString("$u:$p".toByteArray(), Base64.NO_WRAP)
    }

    private fun stripUserInfo(u: String): String = try {
        val url = URL(u)
        if (url.userInfo == null) u else u.replaceFirst("${url.userInfo}@", "")
    } catch (e: Exception) { u }

    private fun open(urlStr: String): HttpURLConnection {
        val url = URL(stripUserInfo(urlStr))
        var conn = connect(url, authHeaderBasic())
        if (conn.responseCode == 401) {
            val challenge = conn.getHeaderField("WWW-Authenticate") ?: ""
            conn.disconnect()
            val creds = creds() ?: throw AuthException()
            if (challenge.startsWith("Digest", true)) {
                conn = connect(url, digest(challenge, url, creds.first, creds.second))
            }
            if (conn.responseCode == 401) { conn.disconnect(); throw AuthException() }
        }
        if (conn.responseCode !in 200..299) {
            val code = conn.responseCode; conn.disconnect()
            throw java.io.IOException("HTTP $code")
        }
        return conn
    }

    private fun connect(url: URL, auth: String?): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 12000
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TVLauncher-CameraView")
            if (auth != null) setRequestProperty("Authorization", auth)
        }

    private fun digest(challenge: String, url: URL, user: String, pass: String): String {
        val f = Regex("(\\w+)=\"?([^\",]*)\"?").findAll(challenge.substringAfter(' '))
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val realm = f["realm"] ?: ""; val nonce = f["nonce"] ?: ""
        val uri = url.file.ifEmpty { "/" }
        val ha1 = md5("$user:$realm:$pass"); val ha2 = md5("GET:$uri")
        val qop = f["qop"]?.split(",")?.map { it.trim() }?.firstOrNull { it == "auth" }
        val nc = "00000001"; val cnonce = md5(System.nanoTime().toString()).take(16)
        val resp = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        val sb = StringBuilder("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$resp\"")
        f["opaque"]?.let { sb.append(", opaque=\"$it\"") }
        if (qop != null) sb.append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
        return sb.toString()
    }

    /** Draws the latest frame letter-boxed (fit center), with rotation. */
    private class FrameView(c: Context) : View(c) {
        private var bmp: Bitmap? = null
        private var rot = 0
        var scale = Scale.FIT
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val m = Matrix()
        private val src = RectF(); private val dst = RectF()

        fun setFrame(b: Bitmap, rotation: Int) { bmp = b; rot = rotation; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val b = bmp ?: return
            val vw = width.toFloat(); val vh = height.toFloat()
            val swap = rot == 90 || rot == 270
            val bw = (if (swap) b.height else b.width).toFloat()
            val bh = (if (swap) b.width else b.height).toFloat()
            val sx: Float; val sy: Float
            when (scale) {
                Scale.FIT -> { val s = minOf(vw / bw, vh / bh); sx = s; sy = s }
                Scale.FILL -> { val s = maxOf(vw / bw, vh / bh); sx = s; sy = s }
                Scale.STRETCH -> { sx = vw / bw; sy = vh / bh }
            }
            m.reset()
            m.postTranslate(-b.width / 2f, -b.height / 2f)
            m.postRotate(rot.toFloat())
            m.postScale(sx, sy)
            m.postTranslate(vw / 2f, vh / 2f)
            canvas.drawBitmap(b, m, paint)
        }
    }
}
