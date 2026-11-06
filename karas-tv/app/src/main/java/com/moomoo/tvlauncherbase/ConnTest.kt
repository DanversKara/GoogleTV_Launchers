package com.moomoo.tvlauncherbase

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Test connections": checks, from the TV itself, every online service the
 * launcher uses and reports each one plainly — so when Upcoming or the
 * Discover wallpaper stay empty we know exactly which service is blocked.
 */
object ConnTest {

    private class Check(val name: String, val usedFor: String, val url: String, val body: String? = null)

    private val checks = listOf(
        Check("AniList", "anime episode dates, Discover anime",
            "https://graphql.anilist.co", """{"query":"{Media(id:21,type:ANIME){id}}"}"""),
        Check("TVmaze", "TV episode dates, Discover TV", "https://api.tvmaze.com/singlesearch/shows?q=one%20piece"),
        Check("Apple top movies", "Discover movies", "https://itunes.apple.com/us/rss/topmovies/limit=1/json"),
        Check("Open-Meteo", "weather", "https://api.open-meteo.com/v1/forecast?latitude=34&longitude=-118&current=temperature_2m"),
        Check("Artwork images", "posters & backdrops", "https://static.tvmaze.com/images/favico/favicon.ico")
    )

    private fun probe(c: Check): String {
        val t0 = System.currentTimeMillis()
        return try {
            val conn = URL(c.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "KarasTV/2.8 (Android TV)")
            conn.setRequestProperty("Accept", "application/json")
            if (c.body != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(c.body.toByteArray()) }
            }
            val code = conn.responseCode
            conn.disconnect()
            val ms = System.currentTimeMillis() - t0
            when {
                code in 200..299 -> "✓ OK (${ms} ms)"
                code == 429 -> "⚠ Busy — too many requests (wait a minute)"
                code == 403 -> "✗ Refused (HTTP 403) — the service is blocking this connection"
                else -> "✗ HTTP $code"
            }
        } catch (e: Exception) {
            "✗ " + ShowInfo.describeError(e)
        }
    }

    fun run(a: Activity) {
        Ui.toast(a, "Testing connections…")
        Thread {
            val lines = checks.map { c -> "${c.name}  (${c.usedFor})\n   ${probe(c)}" }
            val extra = buildString {
                ShowInfo.lastError?.let { append("\nLast lookup problem: $it\n") }
                if (ShowInfo.lastOkAt > 0) append("Last successful episode lookup: ${ShowInfo.whenText(ShowInfo.lastOkAt)}\n")
                Discover.lastError?.let { append("Discover: $it\n") }
                append("Discover images loaded: ${Discover.list(a).size}\n")
            }
            val tips = "\nIf AniList / TVmaze fail but weather works, something on your network " +
                "(router parental controls, Pi-hole/AdGuard, or a VPN) is blocking them — allow " +
                "graphql.anilist.co and api.tvmaze.com. \"Secure connection failed\" usually means the TV's date/time is wrong."
            Handler(Looper.getMainLooper()).post {
                if (a.isFinishing) return@post
                Ui.dialog(a).setTitle("Connection test")
                    .setMessage(lines.joinToString("\n\n") + "\n" + extra + tips)
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton("Retry lookups now") { _, _ ->
                        ShowInfo.refreshStale(a, force = true); Discover.refresh(a, force = true)
                        Ui.toast(a, "Refreshing shows and Discover…")
                    }.show()
            }
        }.start()
    }
}
