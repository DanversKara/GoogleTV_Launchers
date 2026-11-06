package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Tiny image loader for artwork / wallpapers: memory LRU + disk cache,
 * downsampled to the target width. Sources: http(s) URLs or local file paths.
 */
object Img {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun memKey(src: String, w: Int) = "$src#${(w / 200 + 1) * 200}"

    fun evict(src: String) {
        val snap = mem.snapshot().keys.filter { it.startsWith("$src#") }
        snap.forEach { mem.remove(it) }
    }

    /** Loads [src] into [iv]; [done] gets true when an image was set. */
    fun load(iv: ImageView, src: String?, targetW: Int, done: ((Boolean) -> Unit)? = null) {
        iv.tag = src
        if (src.isNullOrEmpty()) { done?.invoke(false); return }
        mem.get(memKey(src, targetW))?.let { iv.setImageBitmap(it); done?.invoke(true); return }
        val ctx = iv.context.applicationContext
        pool.execute {
            val b = try { fetch(ctx, src, targetW) } catch (e: Throwable) { null }
            main.post {
                if (iv.tag == src) {
                    if (b != null) iv.setImageBitmap(b)
                    done?.invoke(b != null)
                }
            }
        }
    }

    /** Blocking fetch + decode (call off the main thread). */
    fun fetch(c: Context, src: String, targetW: Int): Bitmap? {
        val k = memKey(src, targetW)
        mem.get(k)?.let { return it }
        val file = if (src.startsWith("/")) File(src) else download(c, src) ?: return null
        if (!file.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        var s = 1
        while (o.outWidth / (s * 2) >= targetW) s *= 2
        val b = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        mem.put(k, b)
        return b
    }

    private fun download(c: Context, url: String): File? {
        val dir = File(c.cacheDir, "img").apply { mkdirs() }
        val f = File(dir, WatchStore.md5(url))
        if (f.exists() && f.length() > 0) return f
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000; conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "TVLauncher/2.1 (Android TV)")
            if (conn.responseCode !in 200..299) return null
            val tmp = File(dir, f.name + ".part")
            conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            tmp.renameTo(f)
            trimDisk(dir)
            f
        } catch (e: Exception) { null } finally { conn.disconnect() }
    }

    /** Keeps the disk cache under ~80 MB. */
    private fun trimDisk(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total < 80L * 1024 * 1024) break
            total -= f.length(); f.delete()
        }
    }
}
