package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Shows you're watching (recorded from apps' media sessions) and shows
 * you follow, with next-episode info from AniList / TVmaze. Stored as
 * JSON in the app's private files. Nothing leaves the TV except the
 * show-name lookups (which can be switched off).
 */
class WatchItem(
    val key: String,
    var show: String,
    var episode: String? = null,
    var pkg: String? = null,
    var lastWatched: Long = 0L,
    /** 0..1, or -1 when unknown. */
    var progress: Float = -1f,
    var artPath: String? = null,
    var artUrl: String? = null,
    var followed: Boolean = false,
    var source: String? = null,
    var extId: Int = 0,
    var matchedTitle: String? = null,
    var posterUrl: String? = null,
    var backdropUrl: String? = null,
    var nextEp: Int = 0,
    var nextSeason: Int = 0,
    var nextName: String? = null,
    /** Epoch ms of the next episode, 0 = none known. */
    var nextAt: Long = 0L,
    var status: String? = null,
    var lastLookup: Long = 0L,
    var lookupFailed: Boolean = false,
    var manualMatch: Boolean = false,
    /** The app only reported an episode title — the show must be picked once (Fix show match). */
    var needsMatch: Boolean = false,
    /** nextAt is our weekly estimate (the database couldn't be reached). */
    var estimated: Boolean = false
) {
    val title get() = matchedTitle ?: show
    /** Best landscape image for cards and the wallpaper. */
    val landscape get() = backdropUrl ?: artPath ?: artUrl ?: posterUrl
    val isAnime get() = source == WatchStore.ANILIST

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key); put("show", show); putOpt("episode", episode); putOpt("pkg", pkg)
        put("lastWatched", lastWatched); put("progress", progress.toDouble()); putOpt("artPath", artPath)
        putOpt("artUrl", artUrl); put("followed", followed); putOpt("source", source); put("extId", extId)
        putOpt("matchedTitle", matchedTitle); putOpt("posterUrl", posterUrl); putOpt("backdropUrl", backdropUrl)
        put("nextEp", nextEp); put("nextSeason", nextSeason); putOpt("nextName", nextName); put("nextAt", nextAt)
        putOpt("status", status); put("lastLookup", lastLookup); put("lookupFailed", lookupFailed)
        put("manualMatch", manualMatch); put("needsMatch", needsMatch); put("estimated", estimated)
    }

    companion object {
        private fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
        fun fromJson(o: JSONObject) = WatchItem(
            o.getString("key"), o.optString("show"), o.str("episode"), o.str("pkg"),
            o.optLong("lastWatched"), o.optDouble("progress", -1.0).toFloat(), o.str("artPath"), o.str("artUrl"),
            o.optBoolean("followed"), o.str("source"), o.optInt("extId"), o.str("matchedTitle"),
            o.str("posterUrl"), o.str("backdropUrl"), o.optInt("nextEp"), o.optInt("nextSeason"),
            o.str("nextName"), o.optLong("nextAt"), o.str("status"), o.optLong("lastLookup"),
            o.optBoolean("lookupFailed"), o.optBoolean("manualMatch"), o.optBoolean("needsMatch"),
            o.optBoolean("estimated")
        )
    }
}

object WatchStore {
    const val ANILIST = "anilist"
    const val TVMAZE = "tvmaze"
    /** Shows you added by hand with a weekly day & time. */
    const val MANUAL = "manual"

    /** Bumps on every change; the home screen redraws when it moves. */
    @Volatile var version = 0L
        private set

    private var items: MutableList<WatchItem>? = null
    private var lastSave = 0L

    private fun file(c: Context) = File(c.filesDir, "watch.json")

    @Synchronized private fun all(c: Context): MutableList<WatchItem> {
        items?.let { return it }
        val list = try {
            val a = JSONArray(file(c).readText())
            MutableList(a.length()) { WatchItem.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { mutableListOf() }
        // Items saved by 2.2 from an app that only reported a long episode title.
        for (it in list) if (it.extId == 0 && !it.manualMatch && ShowInfo.looksAnimeApp(it.pkg) &&
            it.show.split(" ").size >= 7) it.needsMatch = true
        // Videos (YouTube, SmartTube…) that earlier versions wrongly matched to TV shows.
        for (it in list) if (isVideoApp(it.pkg) && !it.manualMatch && it.extId != 0) {
            it.source = null; it.extId = 0; it.matchedTitle = null; it.nextAt = 0L; it.nextEp = 0; it.nextSeason = 0
            it.nextName = null; it.posterUrl = null; it.backdropUrl = null; it.status = null; it.followed = false
        }
        items = list
        mergeDuplicates(c)
        return list
    }

    /**
     * Merges entries that turned out to be the same show (same AniList/TVmaze id),
     * e.g. a show you follow (Upcoming, has artwork) and the same show detected
     * while playing (Continue watching). The merged entry keeps the artwork,
     * dates and follow flag, plus the latest episode / progress / app.
     */
    @Synchronized private fun mergeDuplicates(c: Context) {
        val list = all(c)
        // Same app, same (or swapped) show/episode name → one entry, newest wins.
        run {
            val sorted = list.sortedByDescending { it.lastWatched }
            val gone = HashSet<WatchItem>()
            for ((i, a) in sorted.withIndex()) {
                if (a in gone || a.pkg == null) continue
                val an = setOfNotNull(a.show, a.episode, a.matchedTitle).map { normalize(cleanShowName(it)) }.filter { it.length >= 3 }.toSet()
                for (b in sorted.drop(i + 1)) {
                    if (b in gone || b.pkg != a.pkg) continue
                    // Two different ids only merge when it's clearly the same show
                    // (same title — e.g. one entry from AniList and one from TVmaze).
                    val sameTitleExact = normalize(a.matchedTitle ?: a.show) == normalize(b.matchedTitle ?: b.show)
                    if (b.extId != 0 && a.extId != 0 && b.extId != a.extId && !sameTitleExact) continue
                    val bn = setOfNotNull(b.show, b.episode, b.matchedTitle).map { normalize(cleanShowName(it)) }
                    val sameTitle = normalize(a.matchedTitle ?: a.show) == normalize(b.matchedTitle ?: b.show)
                    if (!sameTitle && bn.none { x -> an.any { y -> sameName(x, y) } }) continue
                    // keep a (newer); inherit what b knows
                    a.followed = a.followed || b.followed
                    a.manualMatch = a.manualMatch || b.manualMatch
                    if (a.extId == 0 && b.extId != 0) {
                        a.source = b.source; a.extId = b.extId; a.matchedTitle = b.matchedTitle
                        a.nextEp = b.nextEp; a.nextSeason = b.nextSeason; a.nextName = b.nextName
                        a.nextAt = b.nextAt; a.status = b.status; a.needsMatch = false
                    }
                    // For anime apps AniList's dates are the reliable ones — keep them.
                    if (b.source == ANILIST && a.source == TVMAZE && looksAnime(a.pkg ?: "")) {
                        a.source = b.source; a.extId = b.extId; a.matchedTitle = b.matchedTitle
                        a.nextAt = b.nextAt; a.nextEp = b.nextEp; a.nextSeason = b.nextSeason; a.nextName = b.nextName
                        a.status = b.status; a.estimated = b.estimated
                        if (b.posterUrl != null) a.posterUrl = b.posterUrl
                        if (b.backdropUrl != null) a.backdropUrl = b.backdropUrl
                    }
                    if (a.nextAt == 0L && b.nextAt > 0L) {
                        if (a.source == null) a.source = b.source
                        a.nextAt = b.nextAt; a.nextEp = b.nextEp; a.nextSeason = b.nextSeason; a.nextName = b.nextName
                    }
                    if (a.matchedTitle == null) a.matchedTitle = b.matchedTitle
                    if (a.artPath == null) a.artPath = b.artPath
                    if (a.artUrl == null) a.artUrl = b.artUrl
                    if (a.posterUrl == null) a.posterUrl = b.posterUrl
                    if (a.backdropUrl == null) a.backdropUrl = b.backdropUrl
                    gone += b
                }
            }
            list.removeAll(gone)
        }
        val groups = list.filter { it.extId != 0 && it.source != null }.groupBy { it.source + ":" + it.extId }
        for ((_, g) in groups) {
            if (g.size < 2) continue
            val keep = g.maxWith(compareBy<WatchItem>({ it.followed }, { it.manualMatch }, { it.lastWatched }))
            for (o in g) {
                if (o === keep) continue
                if (o.lastWatched > keep.lastWatched) {
                    keep.lastWatched = o.lastWatched; keep.episode = o.episode ?: keep.episode
                    keep.progress = o.progress; if (o.pkg != null) keep.pkg = o.pkg
                }
                keep.followed = keep.followed || o.followed
                keep.manualMatch = keep.manualMatch || o.manualMatch
                if (keep.artPath == null) keep.artPath = o.artPath
                if (keep.artUrl == null) keep.artUrl = o.artUrl
                if (keep.posterUrl == null) keep.posterUrl = o.posterUrl
                if (keep.backdropUrl == null) keep.backdropUrl = o.backdropUrl
                list.remove(o)
            }
        }
    }

    @Synchronized fun save(c: Context, force: Boolean = true) {
        mergeDuplicates(c)
        version++
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 10_000) return
        lastSave = now
        if (force) Backup.schedule(c)
        try {
            val tmp = File(c.filesDir, "watch.json.tmp")
            tmp.writeText(JSONArray(all(c).map { it.toJson() }).toString())
            tmp.renameTo(file(c))
        } catch (e: Exception) {}
    }

    @Synchronized fun get(c: Context, key: String) = all(c).firstOrNull { it.key == key }
    @Synchronized fun snapshot(c: Context): List<WatchItem> = all(c).toList()

    /** Display key: two entries that look like the same thing from the same app count once. */
    private fun displayKey(it: WatchItem) = (it.pkg ?: "") + "|" + normalize(it.matchedTitle ?: it.show)

    fun continueWatching(c: Context): List<WatchItem> = continueWatchingRaw(c).distinctBy { displayKey(it) }

    private fun continueWatchingRaw(c: Context): List<WatchItem> =
        snapshot(c).filter { it.lastWatched > 0 }.sortedByDescending { it.lastWatched }.take(20)

    /**
     * Shows with a known next episode (or one that aired in the last 12 h), soonest
     * first — then followed shows with no announced date ("date TBA") at the end.
     */
    fun upcoming(c: Context): List<WatchItem> {
        val cutoff = System.currentTimeMillis() - 12 * 3600_000L
        return snapshot(c).filter { it.nextAt > cutoff || (it.followed && it.nextAt == 0L && !isVideoApp(it.pkg)) }
            .sortedBy { if (it.nextAt == 0L) Long.MAX_VALUE else it.nextAt }
            // one entry per show, even if it's known from two databases
            .distinctBy { normalize(it.matchedTitle ?: it.show) }
            .take(25)
    }

    /** Next time [weekday] (Calendar.SUNDAY..SATURDAY) at [minutes] after midnight, from now. */
    fun nextWeekly(weekday: Int, minutes: Int, from: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = from
            set(java.util.Calendar.HOUR_OF_DAY, minutes / 60); set(java.util.Calendar.MINUTE, minutes % 60)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.DAY_OF_WEEK, weekday)
        }
        while (cal.timeInMillis <= from) cal.add(java.util.Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }

    /** Adds a show by hand: weekly day/time, optional episode number and app. */
    fun addManual(c: Context, name: String, pkg: String?, weekday: Int, minutes: Int, nextEp: Int) {
        synchronized(this) {
            val list = all(c)
            val key = "manual|" + normalize(name)
            val item = list.firstOrNull { it.key == key } ?: WatchItem(key, name).also { list += it }
            item.followed = true; item.manualMatch = true; item.needsMatch = false
            item.source = MANUAL; item.matchedTitle = name; item.pkg = pkg ?: item.pkg
            item.nextAt = nextWeekly(weekday, minutes); item.nextEp = nextEp
            item.lastLookup = System.currentTimeMillis()
        }
        save(c)
    }

    /** Hand-added shows move to next week once an episode has aired (12 h grace). */
    fun rollManual(c: Context) {
        var changed = false
        synchronized(this) {
            val now = System.currentTimeMillis()
            for (it in all(c)) if (it.source == MANUAL && it.nextAt in 1 until now - 12 * 3600_000L) {
                while (it.nextAt < now - 12 * 3600_000L) {
                    it.nextAt += 7 * 24 * 3600_000L
                    if (it.nextEp > 0) it.nextEp++
                }
                changed = true
            }
        }
        if (changed) save(c)
    }

    /** Same name, or one contains the other ("Lofi beats" vs "Lofi beats (Live)"). */
    fun sameName(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= 8 && (a.contains(b) || b.contains(a)))

    fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private val EP_PATTERN = Regex("(?i)\\b(episode|ep\\.?|e)\\s*\\d+|\\bs\\d+\\s*e\\d+|\\bseason\\s*\\d+")

    /** Removes "Season 2", "(Dub)", "S2" etc. so the show name matches databases. */
    fun cleanShowName(s: String): String = s
        .replace(Regex("(?i)\\((english |japanese |spanish |portuguese |french |german |hindi )?(dub|sub|dubbed|subbed|uncut)\\)"), "")
        .replace(Regex("(?i)\\b(season|part|cour)\\s*\\d+\\b"), "")
        .replace(Regex("(?i)\\bs\\d+\\b"), "")
        .replace(Regex("\\s*[-–:|]\\s*$"), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private val GENERIC = setOf("crunchyroll", "netflix", "youtube", "prime video", "hulu", "max", "disney+",
        "paramount+", "peacock", "plex", "tubi", "pluto tv", "now playing", "playing", "episode", "watch", "live")

    /**
     * Decides which reported text is the SHOW and which is the EPISODE.
     * [candidates] are the other fields the app reported (album, subtitle,
     * artist, queue title, media-card text...), best first.
     * Returns (show, episode, episodeOnly) — episodeOnly = the app gave only
     * an episode title, so the show still has to be picked once.
     */
    fun pickShow(pkg: String, title: String?, candidates: List<String>, appLabel: String): Triple<String, String?, Boolean>? {
        val t = title?.trim()?.takeIf { it.isNotEmpty() }
        val cands = candidates.map { it.trim() }.filter {
            it.isNotEmpty() && it.length <= 120 && !it.equals(appLabel, true) &&
                normalize(it) !in GENERIC && (t == null || normalize(it) != normalize(t)) &&
                !Regex("^[0-9:./ -]+$").matches(it) // times, dates, counters
        }.distinctBy { normalize(it) }
        if (t == null) return cands.firstOrNull()?.let { Triple(it, null, false) }
        if (isVideoApp(pkg)) return Triple(t, cands.firstOrNull(), false)
        val s = cands.firstOrNull()
        if (s == null) {
            // Only one text: a long sentence-like title is almost certainly an episode name.
            val looksEpisode = EP_PATTERN.containsMatchIn(t) || t.split(" ").size >= 7
            return Triple(t, null, looksEpisode && looksAnime(pkg))
        }
        val tEp = EP_PATTERN.containsMatchIn(t)
        val sEp = EP_PATTERN.containsMatchIn(s)
        return when {
            sEp && !tEp -> Triple(t, s, false)
            else -> Triple(s, t, false)
        }
    }

    private fun looksAnime(pkg: String) = ShowInfo.looksAnimeApp(pkg)

    /** YouTube-style apps play videos, not series: Continue watching only, no episode lookups. */
    private val VIDEO_APPS = listOf("youtube", "smarttube", "liskovsoft", "newpipe", "tubio", "vanced", "revanced",
        "twitch", "vimeo", "dailymotion", "rumble", "kick.")
    fun isVideoApp(pkg: String?) = pkg != null && VIDEO_APPS.any { pkg.contains(it, true) }

    /** Kept for older callers/tests: two-field version of [pickShow]. */
    fun splitShow(pkg: String, title: String?, subtitle: String?, appLabel: String): Pair<String, String?>? =
        pickShow(pkg, title, listOfNotNull(subtitle), appLabel)?.let { it.first to it.second }

    /** Called by MediaWatchService for every playing / paused media session. */
    fun recordPlayback(
        c: Context, pkg: String, title: String?, candidates: List<String>,
        art: Bitmap?, artUri: String?, position: Long, duration: Long
    ) {
        if (pkg == c.packageName || pkg in BasePrefs.ignoredWatchApps(c)) return
        if (duration in 1..90_000) return // ads, trailers, previews
        val label = AppRepository.label(c, pkg)
        val (rawShow, ep0, episodeOnly) = pickShow(pkg, title, candidates, label) ?: return
        var show = cleanShowName(rawShow).ifEmpty { rawShow }
        var episode = ep0
        var isNew = false
        val item: WatchItem
        synchronized(this) {
            val list = all(c)
            var key = pkg + "|" + normalize(show)
            if (episodeOnly) {
                // Only an episode title: attach to the show you picked last time in
                // this app (binge-watching), if you watched it in the last 12 hours.
                val recent = list.filter { it.pkg == pkg && it.manualMatch && it.extId != 0 &&
                    System.currentTimeMillis() - it.lastWatched < 12 * 3600_000L }.maxByOrNull { it.lastWatched }
                // …or, if you follow exactly one show from this app (anime apps: any anime show), use it.
                val target = recent ?: list.filter {
                    it.followed && it.extId != 0 && (it.pkg == pkg || (it.pkg == null && it.isAnime && looksAnime(pkg)))
                }.singleOrNull()
                if (target != null) { key = target.key; episode = rawShow; show = target.show }
            }
            if (list.none { it.key == key }) {
                // Same thing reported a bit differently (metadata still loading, title and
                // subtitle swapped, channel added later…)? Reuse that entry, don't duplicate.
                val names = (listOfNotNull(title, rawShow, ep0) + candidates).map { normalize(cleanShowName(it)) }
                    .filter { it.length >= 3 }.toSet()
                list.filter { it.pkg == pkg && System.currentTimeMillis() - it.lastWatched < 48 * 3600_000L }
                    .firstOrNull { o -> listOfNotNull(o.show, o.episode, o.matchedTitle).any { n ->
                        val nn = normalize(cleanShowName(n)); names.any { sameName(it, nn) } } }
                    ?.let { key = it.key }
            }
            item = list.firstOrNull { it.key == key }
                ?: WatchItem(key, show, pkg = pkg, needsMatch = episodeOnly).also { list += it; isNew = true }
            val epChanged = item.episode != episode
            item.episode = episode
            item.pkg = pkg
            item.lastWatched = System.currentTimeMillis()
            if (duration > 0 && position >= 0) item.progress = (position.toFloat() / duration).coerceIn(0f, 1f)
            if (art != null && (item.artPath == null || epChanged)) item.artPath = saveArt(c, key, art)
            if (artUri != null && artUri.startsWith("http")) item.artUrl = artUri
        }
        save(c, force = isNew)
        if (!item.needsMatch && !isVideoApp(pkg) && (isNew || (item.extId == 0 && !item.lookupFailed))) ShowInfo.resolveAsync(c, item.key)
    }

    private fun saveArt(c: Context, key: String, b: Bitmap): String? = try {
        val dir = File(c.filesDir, "art").apply { mkdirs() }
        val f = File(dir, md5(key) + ".jpg")
        val scaled = if (b.width > 960) Bitmap.createScaledBitmap(b, 960, b.height * 960 / b.width, true) else b
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        Img.evict(f.absolutePath)
        f.absolutePath
    } catch (e: Exception) { null }

    fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Follow a show picked from search (it then shows in Upcoming even if never watched here). */
    fun follow(c: Context, m: ShowInfo.Match, pkg: String?) {
        synchronized(this) {
            val list = all(c)
            val key = "follow|${m.source}|${m.id}"
            val existing = list.firstOrNull { it.source == m.source && it.extId == m.id }
            val item = existing ?: WatchItem(key, m.title).also { list += it }
            item.followed = true
            if (pkg != null) item.pkg = pkg
            apply(item, m)
            item.manualMatch = true
        }
        save(c)
    }

    /** Applies looked-up info (title, art, next episode) to an item. */
    fun apply(item: WatchItem, m: ShowInfo.Match) {
        item.source = m.source; item.extId = m.id; item.matchedTitle = m.title
        item.posterUrl = m.posterUrl ?: item.posterUrl
        item.backdropUrl = m.backdropUrl ?: item.backdropUrl
        item.nextEp = m.nextEp; item.nextSeason = m.nextSeason; item.nextName = m.nextName
        item.nextAt = m.nextAt; item.status = m.status
        item.lastLookup = System.currentTimeMillis(); item.lookupFailed = false; item.needsMatch = false
    }

    fun update(c: Context, key: String, block: (WatchItem) -> Unit) {
        synchronized(this) { all(c).firstOrNull { it.key == key }?.let(block) }
        save(c)
    }

    fun remove(c: Context, key: String) {
        synchronized(this) {
            val list = all(c)
            list.firstOrNull { it.key == key }?.artPath?.let { File(it).delete() }
            list.removeAll { it.key == key }
        }
        save(c)
    }

    /** All shows as a JSON string (for backups). */
    @Synchronized fun exportJson(c: Context): String = JSONArray(all(c).map { it.toJson() }).toString()

    /** Replaces all shows with the ones from a backup. */
    fun importJson(c: Context, json: String) {
        val list = try {
            val a = JSONArray(json); MutableList(a.length()) { WatchItem.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { return }
        synchronized(this) { items = list }
        save(c)
    }

    fun clearHistory(c: Context) {
        synchronized(this) {
            val list = all(c)
            list.removeAll { !it.followed }
            list.forEach { it.lastWatched = 0L; it.progress = -1f }
        }
        save(c)
    }
}
