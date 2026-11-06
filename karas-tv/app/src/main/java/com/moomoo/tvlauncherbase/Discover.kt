package com.moomoo.tvlauncherbase

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * "Discover" wallpaper: backdrops of what's new and coming — rotated behind
 * the home screen with a small caption. Free sources, no account needed:
 *  - AniList: trending & upcoming anime (series and movies), big banners
 *  - TVmaze: shows airing today on TV and streaming, with backdrops
 *  - Apple's top movies chart: new movie releases (posters)
 * Optional: a free TMDB key (themoviedb.org) adds "Now in theaters" and
 * "Coming soon" with full movie backdrops. Refreshed every 6 hours.
 */
object Discover {

    class Item(val title: String, val caption: String, val image: String, val portrait: Boolean, val source: String) {
        fun toJson() = JSONObject().put("t", title).put("c", caption).put("i", image).put("p", portrait).put("s", source)
        companion object {
            fun from(o: JSONObject) = Item(o.getString("t"), o.optString("c"), o.getString("i"), o.optBoolean("p"), o.optString("s"))
        }
    }

    val SOURCES = listOf(
        "anime" to "Anime — trending & upcoming (AniList)",
        "tv" to "TV & streaming — airing today (TVmaze)",
        "movies" to "Movies — top new releases (Apple)",
        "theaters" to "In theaters & coming soon (needs a free TMDB key)"
    )

    private val pool = Executors.newSingleThreadExecutor()
    @Volatile private var items: List<Item>? = null
    @Volatile private var loading = false
    /** Why the last refresh came back empty (shown in Test connections / Customize). */
    @Volatile var lastError: String? = null

    private fun file(c: Context) = File(c.filesDir, "discover.json")

    fun list(c: Context): List<Item> {
        items?.let { return it }
        val l = try {
            val a = JSONArray(file(c).readText()); List(a.length()) { Item.from(a.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
        items = l
        return l
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "KarasTV/2.7 (Android TV)")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun s(o: JSONObject?, k: String) = o?.optString(k)?.takeIf { it.isNotEmpty() && it != "null" }

    /** Refreshes in the background when older than 6 h (or [force]). */
    fun refresh(c: Context, force: Boolean = false, done: (() -> Unit)? = null) {
        val app = c.applicationContext
        val f = file(app)
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L && list(app).isNotEmpty()) return
        if (loading) return
        loading = true
        pool.execute {
            try {
                val out = mutableListOf<Item>()
                if (BasePrefs.discoverSource(app, "theaters") && BasePrefs.tmdbKey(app).isNotEmpty()) out += safe { tmdb(app) }
                if (BasePrefs.discoverSource(app, "anime")) out += safe { anime() }
                if (BasePrefs.discoverSource(app, "tv")) out += safe { tv() }
                if (BasePrefs.discoverSource(app, "movies")) out += safe { appleMovies() }
                // interleave sources so the slideshow mixes them
                val bySource = out.groupBy { it.source }.values.map { it.toMutableList() }
                val mixed = mutableListOf<Item>()
                while (bySource.any { it.isNotEmpty() }) for (l in bySource) if (l.isNotEmpty()) mixed += l.removeAt(0)
                if (mixed.isNotEmpty()) {
                    lastError = null
                    items = mixed
                    f.writeText(JSONArray(mixed.map { it.toJson() }).toString())
                    android.os.Handler(android.os.Looper.getMainLooper()).post { done?.invoke() }
                }
            } finally { loading = false }
        }
    }

    private fun safe(block: () -> List<Item>): List<Item> = try { block() } catch (e: Exception) {
        lastError = ShowInfo.describeError(e); emptyList()
    }

    private fun fmtDate(ms: Long) = SimpleDateFormat("EEE MMM d", Locale.getDefault()).format(Date(ms))

    // ---------------------------------------------------------------- AniList
    private fun anime(): List<Item> {
        val q = "query{Page(perPage:20){media(type:ANIME,status_in:[RELEASING,NOT_YET_RELEASED],sort:TRENDING_DESC,isAdult:false)" +
            "{title{english romaji} bannerImage format status nextAiringEpisode{airingAt episode} startDate{year month day}}}}"
        val o = JSONObject(ShowInfo.aniListPost(JSONObject().put("query", q).toString()))
        val arr = o.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val banner = s(m, "bannerImage") ?: return@mapNotNull null
            val t = m.optJSONObject("title")
            val title = s(t, "english") ?: s(t, "romaji") ?: return@mapNotNull null
            val next = m.optJSONObject("nextAiringEpisode")
            val movie = s(m, "format") == "MOVIE"
            val caption = when {
                next != null -> "Anime · Episode ${next.optInt("episode")} · ${fmtDate(next.optLong("airingAt") * 1000)}"
                s(m, "status") == "NOT_YET_RELEASED" -> (if (movie) "Anime movie" else "New anime") + " · coming soon"
                else -> if (movie) "Anime movie" else "Trending anime"
            }
            Item(title, caption, banner, false, "anime")
        }.take(10)
    }

    // ---------------------------------------------------------------- TVmaze
    private fun tv(): List<Item> {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val arr = JSONArray(get("https://api.tvmaze.com/schedule/web?date=$day&country=US")).let { web ->
            val all = JSONArray()
            for (i in 0 until web.length()) all.put(web.get(i))
            try {
                val tvArr = JSONArray(get("https://api.tvmaze.com/schedule?country=US&date=$day"))
                for (i in 0 until tvArr.length()) all.put(tvArr.get(i))
            } catch (e: Exception) {}
            all
        }
        // most popular distinct shows
        val shows = LinkedHashMap<Int, Pair<JSONObject, JSONObject>>()
        for (i in 0 until arr.length()) {
            val ep = arr.getJSONObject(i)
            val show = ep.optJSONObject("_embedded")?.optJSONObject("show") ?: ep.optJSONObject("show") ?: continue
            shows.putIfAbsent(show.getInt("id"), show to ep)
        }
        val top = shows.values.sortedByDescending { it.first.optInt("weight") }.take(8)
        return top.mapNotNull { (show, ep) ->
            Thread.sleep(250) // TVmaze rate limit friendly
            val bg = try {
                val imgs = JSONArray(get("https://api.tvmaze.com/shows/${show.getInt("id")}/images"))
                (0 until imgs.length()).map { imgs.getJSONObject(it) }
                    .firstOrNull { it.optString("type") == "background" }
                    ?.optJSONObject("resolutions")?.optJSONObject("original")?.optString("url")
            } catch (e: Exception) { null }
            val poster = s(show.optJSONObject("image"), "original")
            val img = bg ?: poster ?: return@mapNotNull null
            val net = s(show.optJSONObject("webChannel"), "name") ?: s(show.optJSONObject("network"), "name")
            val epTxt = if (ep.optInt("season") > 0) "S${ep.optInt("season")} E${ep.optInt("number")}" else "New episode"
            Item(show.optString("name"), listOfNotNull("New today", epTxt, net).joinToString(" · "), img, bg == null, "tv")
        }
    }

    // ---------------------------------------------------------------- Apple top movies
    private fun appleMovies(): List<Item> {
        val o = JSONObject(get("https://itunes.apple.com/us/rss/topmovies/limit=15/json"))
        val arr = o.optJSONObject("feed")?.optJSONArray("entry") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.getJSONObject(i)
            val title = e.optJSONObject("im:name")?.optString("label") ?: return@mapNotNull null
            val imgs = e.optJSONArray("im:image") ?: return@mapNotNull null
            val small = imgs.getJSONObject(imgs.length() - 1).optString("label")
            // Apple's image server resizes on request: ask for a big poster.
            val big = small.replace(Regex("/\\d+x\\d+[a-z]*\\.(png|jpg|jpeg)$"), "/1000x1500bb.jpg")
            val genre = e.optJSONObject("category")?.optJSONObject("attributes")?.optString("label")
            val rel = e.optJSONObject("im:releaseDate")?.optJSONObject("attributes")?.optString("label")
            Item(title, listOfNotNull("New movie", genre, rel).joinToString(" · "), big, true, "movies")
        }.take(10)
    }

    // ---------------------------------------------------------------- TMDB (optional key)
    private fun tmdb(c: Context): List<Item> {
        val key = BasePrefs.tmdbKey(c)
        fun list(path: String, label: String): List<Item> {
            // v3 key (32 hex chars) as a parameter, or a v4 read token as a Bearer header.
            val v3 = key.length <= 40
            val url = "https://api.themoviedb.org/3$path" + (if (path.contains("?")) "&" else "?") + "region=US" +
                if (v3) "&api_key=$key" else ""
            val o = JSONObject(get(url, if (v3) emptyMap() else mapOf("Authorization" to "Bearer $key")))
            val arr = o.optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val m = arr.getJSONObject(i)
                val bd = s(m, "backdrop_path") ?: return@mapNotNull null
                val title = s(m, "title") ?: s(m, "name") ?: return@mapNotNull null
                val date = s(m, "release_date")
                Item(title, listOfNotNull(label, date).joinToString(" · "), "https://image.tmdb.org/t/p/w1280$bd", false, "theaters")
            }.take(8)
        }
        return list("/movie/now_playing", "Now in theaters") + list("/movie/upcoming", "Coming soon to theaters")
    }
}
