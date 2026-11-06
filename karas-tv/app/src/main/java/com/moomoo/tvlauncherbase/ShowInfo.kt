package com.moomoo.tvlauncherbase

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Next-episode dates and artwork from two free, key-less databases:
 *  - AniList (graphql.anilist.co) — anime, i.e. what you watch on Crunchyroll,
 *    HIDIVE etc. Gives the next episode number and its exact airing time.
 *  - TVmaze (api.tvmaze.com) — regular TV series.
 * Only show names are sent. Results are cached in [WatchStore]; each show is
 * re-checked at most every 3 hours (or right after its episode airs).
 */
object ShowInfo {

    class Match(
        val source: String, val id: Int, val title: String,
        val posterUrl: String?, val backdropUrl: String?,
        val nextEp: Int, val nextSeason: Int, val nextName: String?, val nextAt: Long,
        val status: String?, val extra: String?
    )

    private val pool = Executors.newSingleThreadExecutor()
    /** Last lookup problem, e.g. "AniList: HTTP 403" — shown in Watching & upcoming. */
    @Volatile var lastError: String? = null
    @Volatile var lastOkAt = 0L
    /** Searches you start get their own lane, so they never wait behind background lookups. */
    private val searchPool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private val inFlight = HashSet<String>()

    private val ANIME_APPS = listOf("crunchyroll", "funimation", "hidive", "animelab", "wakanim", "aniplus", "retrocrush")

    fun looksAnimeApp(pkg: String?) = pkg != null && ANIME_APPS.any { pkg.contains(it, true) }

    // ------------------------------------------------------------ HTTP
    private fun http(url: String, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "TVLauncher/2.1 (Android TV)")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code == 404) return ""
            if (code == 429) throw RateLimited(conn.getHeaderField("Retry-After")?.toIntOrNull() ?: 60)
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ------------------------------------------------------------ AniList
    private const val AL_FIELDS = "id title{romaji english} coverImage{extraLarge large} bannerImage status " +
        "episodes seasonYear format nextAiringEpisode{airingAt episode}"

    private fun anilist(query: String, vars: JSONObject): JSONObject {
        val body = JSONObject().put("query", query).put("variables", vars).toString()
        return JSONObject(aniListPost(body))
    }

    private val aniLock = Object()
    private var lastAni = 0L

    /**
     * Every AniList request in the app (episode lookups AND the Discover
     * wallpaper) goes through here: at most one request every 2.2 s (AniList
     * allows ~30/min without an account), and one automatic retry when
     * AniList answers "too many requests".
     */
    fun aniListPost(body: String): String {
        repeat(2) { attempt ->
            synchronized(aniLock) {
                val wait = lastAni + 2200 - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                lastAni = System.currentTimeMillis()
            }
            try {
                return http("https://graphql.anilist.co", body)
            } catch (e: RateLimited) {
                if (attempt == 1) throw e
                Thread.sleep((e.retryAfterSec.coerceIn(5, 65)) * 1000L)
            }
        }
        throw java.io.IOException("AniList busy")
    }

    class RateLimited(val retryAfterSec: Int) : java.io.IOException("Rate limited")

    private fun alMatch(m: JSONObject): Match {
        val t = m.optJSONObject("title")
        val title = t?.optString("english").takeUnless { it.isNullOrEmpty() || it == "null" }
            ?: t?.optString("romaji") ?: "?"
        val cover = m.optJSONObject("coverImage")
        val poster = cover?.optString("extraLarge").takeUnless { it.isNullOrEmpty() || it == "null" }
            ?: cover?.optString("large")
        val banner = m.optString("bannerImage").takeUnless { it.isEmpty() || it == "null" }
        val next = m.optJSONObject("nextAiringEpisode")
        val extra = listOfNotNull(
            m.optString("format").takeUnless { it.isEmpty() || it == "null" },
            m.optInt("seasonYear").takeIf { it > 0 }?.toString(),
            m.optInt("episodes").takeIf { it > 0 }?.let { "$it eps" }
        ).joinToString(" · ")
        return Match(
            WatchStore.ANILIST, m.getInt("id"), title, poster, banner,
            next?.optInt("episode") ?: 0, 0, null,
            (next?.optLong("airingAt") ?: 0L) * 1000L,
            m.optString("status").takeUnless { it.isEmpty() || it == "null" }, extra
        )
    }

    fun searchAnime(q: String): List<Match> {
        val r = anilist("query(\$q:String){Page(perPage:10){media(search:\$q,type:ANIME,sort:SEARCH_MATCH){$AL_FIELDS}}}",
            JSONObject().put("q", q))
        val arr = r.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        return (0 until arr.length()).map { alMatch(arr.getJSONObject(it)) }
    }

    fun animeById(id: Int): Match? {
        val r = anilist("query(\$id:Int){Media(id:\$id,type:ANIME){$AL_FIELDS}}", JSONObject().put("id", id))
        return r.optJSONObject("data")?.optJSONObject("Media")?.let { alMatch(it) }
    }

    // ------------------------------------------------------------ TVmaze
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    fun parseAirstamp(s: String?): Long = try {
        if (s.isNullOrEmpty()) 0L else synchronized(iso) { iso.parse(s)?.time ?: 0L }
    } catch (e: Exception) {
        try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            f.parse(s!!.take(19))?.time ?: 0L
        } catch (e2: Exception) { 0L }
    }

    private fun tmShow(o: JSONObject, next: JSONObject?, backdrop: String?): Match {
        val img = o.optJSONObject("image")
        val net = o.optJSONObject("network")?.optString("name") ?: o.optJSONObject("webChannel")?.optString("name")
        val premiered = o.optString("premiered").takeIf { it.length >= 4 && it != "null" }?.take(4)
        return Match(
            WatchStore.TVMAZE, o.getInt("id"), o.optString("name", "?"),
            img?.optString("original")?.takeUnless { it.isEmpty() || it == "null" }, backdrop,
            next?.optInt("number") ?: 0, next?.optInt("season") ?: 0,
            next?.optString("name")?.takeUnless { it.isEmpty() || it == "null" },
            parseAirstamp(next?.optString("airstamp")),
            o.optString("status").takeUnless { it.isEmpty() || it == "null" },
            listOfNotNull(net, premiered).joinToString(" · ")
        )
    }

    fun searchTv(q: String): List<Match> {
        val txt = http("https://api.tvmaze.com/search/shows?q=${enc(q)}")
        if (txt.isEmpty()) return emptyList()
        val arr = JSONArray(txt)
        return (0 until minOf(arr.length(), 10)).map { tmShow(arr.getJSONObject(it).getJSONObject("show"), null, null) }
    }

    fun tvById(id: Int): Match? {
        val txt = http("https://api.tvmaze.com/shows/$id?embed=nextepisode")
        if (txt.isEmpty()) return null
        val o = JSONObject(txt)
        val next = o.optJSONObject("_embedded")?.optJSONObject("nextepisode")
        val backdrop = try { tvBackdrop(id) } catch (e: Exception) { null }
        return tmShow(o, next, backdrop)
    }

    private fun tvBackdrop(id: Int): String? {
        val txt = http("https://api.tvmaze.com/shows/$id/images")
        if (txt.isEmpty()) return null
        val arr = JSONArray(txt)
        var fallback: String? = null
        for (i in 0 until arr.length()) {
            val im = arr.getJSONObject(i)
            val url = im.optJSONObject("resolutions")?.optJSONObject("original")?.optString("url") ?: continue
            if (im.optString("type") == "background") return url
            if (im.optString("type") == "banner" && fallback == null) fallback = url
        }
        return fallback
    }

    // ------------------------------------------------------------ matching
    private fun words(s: String) = WatchStore.normalize(s).split(" ").filter { it.length > 1 }.toSet()

    /** 0..1 similarity between a detected name and a database title. */
    fun similarity(a: String, b: String): Double {
        val na = WatchStore.normalize(a); val nb = WatchStore.normalize(b)
        if (na == nb) return 1.0
        if (na.isNotEmpty() && nb.isNotEmpty() && (na.contains(nb) || nb.contains(na))) return 0.85
        val wa = words(a); val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return wa.intersect(wb).size.toDouble() / wa.union(wb).size
    }

    /** Best automatic match for a detected show name, or null. */
    fun autoMatch(name: String, pkg: String?): Match? {
        val anime = looksAnimeApp(pkg)
        val order = if (anime) listOf(WatchStore.ANILIST, WatchStore.TVMAZE) else listOf(WatchStore.TVMAZE, WatchStore.ANILIST)
        var errors = 0
        var lastErr: Exception? = null
        for (src in order) {
            val results = try { if (src == WatchStore.ANILIST) searchAnime(name) else searchTv(name) }
                catch (e: Exception) { errors++; lastErr = e; emptyList() }
            val best = results.maxByOrNull { similarity(name, it.title) } ?: continue
            if (similarity(name, best.title) >= (if (src == order[0]) 0.5 else 0.7)) {
                return (if (src == WatchStore.ANILIST) animeById(best.id) else tvById(best.id)) ?: best
            }
        }
        // Both databases unreachable = a network problem, not "no such show": let the caller retry soon.
        if (errors == order.size) throw lastErr ?: java.io.IOException("lookup failed")
        return null
    }

    fun byId(source: String, id: Int): Match? =
        if (source == WatchStore.ANILIST) animeById(id) else tvById(id)

    // ------------------------------------------------------------ background work
    /** Look up a newly seen show. */
    fun resolveAsync(c: Context, key: String) {
        val app = c.applicationContext
        if (!BasePrefs.showInfoOnline(app)) return
        synchronized(inFlight) { if (!inFlight.add(key)) return }
        pool.execute {
            try {
                val item = WatchStore.get(app, key) ?: return@execute
                if (item.needsMatch && item.extId == 0) return@execute
                if (item.source == WatchStore.MANUAL) return@execute
                if (WatchStore.isVideoApp(item.pkg) && !item.manualMatch) return@execute
                var netErr = false
                val m = try {
                    (if (item.extId != 0 && item.source != null) byId(item.source!!, item.extId)
                    else autoMatch(item.show, item.pkg)).also { lastOkAt = System.currentTimeMillis() }
                } catch (e: Exception) {
                    netErr = true
                    lastError = "${if (item.source == WatchStore.TVMAZE) "TVmaze" else "AniList"}: ${describeError(e)}"
                    null
                }
                WatchStore.update(app, key) {
                    val now = System.currentTimeMillis()
                    if (m != null && !(m.nextAt == 0L && it.nextAt > now && it.source == m.source)) {
                        WatchStore.apply(it, m)
                        it.estimated = false
                    } else if (m != null) {
                        // Database briefly lost the date we already know about: keep ours.
                        it.lastLookup = now; it.lookupFailed = false
                    } else {
                        // Lookup failed (offline / busy). Keep everything we know, try again in 15 min.
                        // Network trouble isn't "no such show": don't mark it failed, retry in 15 min.
                        // A real "not in either database" is marked and re-checked daily.
                        it.lookupFailed = it.extId == 0 && !netErr
                        it.lastLookup = if (netErr) now - 3 * 3600_000L + 15 * 60_000L else now
                        // Weekly shows: if the known episode already aired, estimate next week's.
                        if (it.extId != 0 && it.nextAt in 1 until now - 3600_000L) {
                            while (it.nextAt < now) { it.nextAt += 7 * 24 * 3600_000L; if (it.nextEp > 0) it.nextEp++ }
                            it.estimated = true
                        }
                    }
                }
            } finally {
                synchronized(inFlight) { inFlight.remove(key) }
            }
            Thread.sleep(700) // stay well under the free API rate limits
        }
    }

    /** Refreshes shows whose info is stale (called when the home screen opens). */
    fun refreshStale(c: Context, force: Boolean = false) {
        WatchStore.rollManual(c)
        if (!BasePrefs.showInfoOnline(c)) return
        val now = System.currentTimeMillis()
        for (it in WatchStore.snapshot(c)) {
            val stale = now - it.lastLookup > 3 * 3600_000L
            val aired = it.nextAt in 1 until now
            val retryFailed = it.lookupFailed && now - it.lastLookup > 24 * 3600_000L
            if (WatchStore.isVideoApp(it.pkg) && !it.manualMatch) continue
            if (it.source == WatchStore.MANUAL) continue
            val retryNet = it.extId == 0 && !it.lookupFailed && !it.needsMatch && stale
            if (force || (it.extId != 0 && (stale || aired)) || (it.extId == 0 && (retryFailed || retryNet || it.lastLookup == 0L)))
                resolveAsync(c, it.key)
        }
    }

    /** Search both databases (for "Follow a show" / "Fix match"); results on the main thread. */
    fun searchAsync(q: String, done: (List<Match>, String?) -> Unit) {
        searchPool.execute {
            val failed = mutableListOf<String>()
            fun run(query: String): List<Match> {
                val anime = try { searchAnime(query) } catch (e: Exception) { failed += "AniList (anime)"; emptyList() }
                val tv = try { searchTv(query) } catch (e: Exception) { failed += "TVmaze (TV)"; emptyList() }
                return (anime + tv).distinctBy { it.source + it.id }
            }
            var merged = run(q)
            // Nothing? Retry with a cleaned / shorter title ("Season 2", "(Dub)", long subtitles).
            if (merged.isEmpty()) {
                val cleaned = WatchStore.cleanShowName(q).substringBefore(":").trim()
                val short = cleaned.split(" ").take(3).joinToString(" ")
                for (alt in listOf(cleaned, short).distinct()) {
                    if (alt.length >= 2 && !alt.equals(q, true)) { merged = run(alt); if (merged.isNotEmpty()) break }
                }
            }
            merged = merged.sortedByDescending { similarity(q, it.title) }
            val err = failed.distinct().takeIf { it.isNotEmpty() }?.let { "${it.joinToString(" and ")} didn't respond" }
            main.post { done(merged, err) }
        }
    }

    fun byIdAsync(source: String, id: Int, done: (Match?) -> Unit) {
        pool.execute {
            val m = try { byId(source, id) } catch (e: Exception) { null }
            main.post { done(m) }
        }
    }

    fun describeError(e: Throwable): String = when (e) {
        is RateLimited -> "busy (too many requests) — will retry"
        is java.net.UnknownHostException -> "can't find the server (no internet / DNS blocked?)"
        is java.net.SocketTimeoutException -> "timed out"
        is javax.net.ssl.SSLException -> "secure connection failed (TV's date/time wrong?)"
        is java.net.ConnectException -> "connection refused (blocked by router / ad-blocker?)"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    // ------------------------------------------------------------ formatting
    /** "in 2d 4h", "in 35m", "aired 3h ago". */
    fun countdown(at: Long, now: Long = System.currentTimeMillis()): String {
        if (at <= 0L) return "date TBA"
        val d = at - now
        val past = d < 0
        val m = Math.abs(d) / 60_000L
        val txt = when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            m < 24 * 60 -> "${m / 60}h ${m % 60}m"
            else -> "${m / 1440}d ${(m % 1440) / 60}h"
        }
        return if (txt == "now") "airing now" else if (past) "aired $txt ago" else "in $txt"
    }

    fun whenText(at: Long): String {
        if (at <= 0L) return "date TBA"
        val f = SimpleDateFormat("EEE MMM d · h:mm a", Locale.getDefault())
        return f.format(java.util.Date(at))
    }

    fun nextLine(it: WatchItem): String? {
        if (it.nextAt <= 0) return null
        val ep = when {
            it.nextSeason > 0 && it.nextEp > 0 -> "S${it.nextSeason} E${it.nextEp}"
            it.nextEp > 0 -> "Episode ${it.nextEp}"
            else -> "Next episode"
        }
        return "$ep · ${if (it.estimated) "≈ " else ""}${whenText(it.nextAt)} (${countdown(it.nextAt)})"
    }
}
