package com.moomoo.tvlauncherbase

import android.app.Activity
import android.content.Context

/**
 * WATCHING & UPCOMING: what feeds the Continue watching / Upcoming rows
 * and the Google TV-style wallpaper.
 */
class WatchActivity : RowListActivity() {

    override val screenTitle = "Watching & upcoming"
    override val screenDesc = "Shows you play are picked up automatically (“What's playing”). Next-episode " +
        "times come from AniList (anime / Crunchyroll) and TVmaze (TV series). Follow a show to track it " +
        "even if you watch it somewhere ${Brand.name} can't see."

    override fun onResume() {
        ServiceSetup.selfEnable(this)
        super.onResume()
    }

    override fun rows(): List<Row> {
        val c = this
        val out = mutableListOf<Row>()
        val on = MediaWatchService.instance != null || ServiceSetup.mediaEnabled(c)
        out += Row(
            if (on) "✓ “What's playing” access is on" else "⚠ Turn on “What's playing” access",
            if (on) (MediaWatchService.lastSeen?.let { "Last seen: $it" } ?: "Play a show in any app to test it.")
            else "Needed to see what you watch. Select for steps (Android hides this on some TVs)."
        ) { if (!on) showAccessFix(c) else Ui.toast(c, "All good — play something and come back") }
        out += Row("Test connections", ShowInfo.lastError?.let { "Last problem: $it — select to test" }
            ?: "Checks AniList, TVmaze and the other online services from this TV") { ConnTest.run(c) }
        out += Row("Track what's playing", if (BasePrefs.watchTracking(c)) "On" else "Off — Continue watching won't update") {
            BasePrefs.setWatchTracking(c, !BasePrefs.watchTracking(c)); refresh()
        }
        out += Row("Look up next episodes online", if (BasePrefs.showInfoOnline(c)) "On — AniList + TVmaze (only show names are sent)" else "Off") {
            BasePrefs.setShowInfoOnline(c, !BasePrefs.showInfoOnline(c)); refresh()
        }
        out += Row("+ Follow a show", "Search anime or TV and add it to Upcoming episodes") { followFlow(c) { refresh() } }
        out += Row("Refresh episode info now", "Re-checks every show") {
            ShowInfo.refreshStale(c, force = true); Ui.toast(c, "Refreshing in the background…")
        }
        val ignored = BasePrefs.ignoredWatchApps(c)
        out += Row("Apps never tracked", "${ignored.size} app(s) — music apps by default") {
            if (ignored.isEmpty()) return@Row
            Ui.choose(c, "Track again", ignored.map { AppRepository.label(c, it) }) {
                BasePrefs.setWatchIgnored(c, ignored[it], false); refresh()
            }
        }
        out += Row("Clear watch history", "Keeps followed shows") {
            Ui.confirm(c, "Clear watch history?", "Continue watching is emptied. Followed shows stay.") {
                WatchStore.clearHistory(c); refresh()
            }
        }
        MediaWatchService.lastRaw?.let { raw ->
            out += Row("What the playing app reported", raw.lines().firstOrNull() ?: "") {
                Ui.dialog(c).setTitle("Last reported by the app").setMessage(raw)
                    .setPositiveButton(R.string.ok, null).show()
            }
        }
        val items = WatchStore.snapshot(c).sortedWith(compareByDescending<WatchItem> { it.lastWatched }.thenBy { it.nextAt })
        if (items.isNotEmpty()) out += Row("Your shows", header = true)
        for (it in items) {
            val sub = listOfNotNull(
                it.pkg?.let { p -> AppRepository.label(c, p) },
                ShowInfo.nextLine(it) ?: when {
                    it.needsMatch -> "⚠ App only reported the episode name — select → Pick the show"
                    it.lookupFailed -> "No match found — select → Fix show match"
                    it.source == WatchStore.MANUAL -> "Added by you"
                    it.extId == 0 -> "Looking up…"
                    else -> "No upcoming episode announced" + (it.status?.let { s -> " (${s.lowercase().replace('_', ' ')})" } ?: "")
                }
            ).joinToString(" · ")
            out += Row((if (it.followed) "★ " else "") + it.title, sub) { itemMenu(c, it) { refresh() } }
        }
        return out
    }

    companion object {
        val CRUNCHYROLL = listOf("com.crunchyroll.crunchyroid", "com.crunchyroll.crunchyroid.tv")

        fun showAccessFix(c: Context) {
            if (ServiceSetup.selfEnable(c) && ServiceSetup.mediaEnabled(c)) { Ui.toast(c, "Switched on"); return }
            Ui.dialog(c).setTitle("Turn on “What's playing”")
                .setMessage(
                    "${Brand.name} reads the title and artwork of what's playing (it never reads your notifications). " +
                    "Android only allows that through “Notification access”.\n\n" +
                    "1. Try: Settings → Apps → Special app access → Notification access → Kara's TV → On.\n\n" +
                    "2. Google TV often hides that page or greys it out for sideloaded apps. Run once from a computer (adb):\n\n" +
                    ServiceSetup.adbCommands(c) + "\n\n" +
                    "With the second command ${Brand.name} switches this (and the remapper) on by itself and keeps them on.\n\n" +
                    "3. Or directly:\nadb shell cmd notification allow_listener ${c.packageName}/${MediaWatchService::class.java.name}"
                )
                .setPositiveButton("Open settings") { _, _ -> ServiceSetup.openNotificationAccess(c) }
                .setNegativeButton(R.string.cancel, null).show()
        }

        /**
         * Search AniList + TVmaze and let the user pick; returns the full match.
         * The list always ends with "Add it manually", so any show can be tracked.
         * [onManual] handles the manual route (default: follow it by hand).
         */
        fun searchAndPick(c: Context, initial: String, onManual: ((String) -> Unit)? = null, onPick: (ShowInfo.Match) -> Unit) {
            Ui.prompt(c, "Search anime or TV shows", initial, "e.g. Frieren, Severance") { q ->
                if (q.isBlank()) return@prompt
                Ui.toast(c, "Searching…")
                ShowInfo.searchAsync(q) { results, err ->
                    if (c is Activity && c.isFinishing) return@searchAsync
                    if (err != null) Ui.toast(c, err + if (results.isEmpty()) " — check the internet connection" else " — showing what was found")
                    val manual = "✎  Not listed? Add “$q” yourself (pick the day & time)"
                    val labels = results.map {
                        val kind = if (it.source == WatchStore.ANILIST) "Anime" else "TV"
                        "${it.title}  —  $kind" + (it.extra?.takeIf { e -> e.isNotEmpty() }?.let { e -> " · $e" } ?: "")
                    } + manual
                    Ui.choose(c, if (results.isEmpty()) "Nothing found for “$q”" else "Pick the show", labels) { i ->
                        if (i == results.size) (onManual ?: { name -> manualFlow(c, name) {} })(q)
                        else {
                            val m = results[i]
                            ShowInfo.byIdAsync(m.source, m.id) { full -> onPick(full ?: m) }
                        }
                    }
                }
            }
        }

        /** Add a show by hand: app → weekday → time → next episode number. */
        fun manualFlow(c: Context, name: String, done: () -> Unit) {
            val apps = AppRepository.load(c, true)
            val cr = apps.firstOrNull { it.packageName in CRUNCHYROLL }
            val appOpts = listOfNotNull(cr) + apps.filter { it != cr }
            Ui.choose(c, "Which app do you watch “$name” on?", listOf("No particular app") + appOpts.map { it.label }) { ai ->
                val pkg = if (ai == 0) null else appOpts[ai - 1].packageName
                val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                Ui.choose(c, "New episodes come out on…", days) { d ->
                    val times = (0 until 48).map { it * 30 }
                    fun fmt(m: Int): String { val h = m / 60; val mm = m % 60
                        return "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(mm)} ${if (h < 12) "AM" else "PM"}" }
                    Ui.choose(c, "At what time? (your time)", times.map { fmt(it) }) { t ->
                        Ui.prompt(c, "Next episode number (optional)", "", "e.g. 8", numeric = true) { ep ->
                            WatchStore.addManual(c, name, pkg, d + 1, times[t], ep.toIntOrNull() ?: 0)
                            Ui.toast(c, "Added $name — every ${days[d]} at ${fmt(times[t])}")
                            done()
                        }
                    }
                }
            }
        }

        fun followFlow(c: Context, done: () -> Unit) {
            searchAndPick(c, "", onManual = { name -> manualFlow(c, name, done) }) { m ->
                val anime = m.source == WatchStore.ANILIST
                val defaultPkg = if (anime) CRUNCHYROLL.firstOrNull { AppRepository.find(c, it) != null } else null
                WatchStore.follow(c, m, defaultPkg)
                Ui.toast(c, "Following ${m.title}" + (ShowInfo.nextLine(WatchItem("", m.title, nextAt = m.nextAt, nextEp = m.nextEp, nextSeason = m.nextSeason))
                    ?.let { " — next: $it" } ?: ""))
                done()
            }
        }

        /** Options for one show (from the home rows, the hero, or this screen). */
        fun itemMenu(c: Context, item: WatchItem, changed: () -> Unit) {
            val app = item.pkg?.let { AppRepository.label(c, it) }
            val opts = mutableListOf<Pair<String, () -> Unit>>()
            app?.let { a -> opts += "▶ Open $a" to { if (!AppRepository.launchPackage(c, item.pkg!!)) Ui.toast(c, "Can't open $a") } }
            val fix: () -> Unit = {
                searchAndPick(c, if (item.needsMatch) "" else (item.matchedTitle ?: item.show)) { m ->
                    WatchStore.update(c, item.key) {
                        if (it.needsMatch && it.episode == null) it.episode = it.show
                        WatchStore.apply(it, m); it.manualMatch = true
                    }
                    Ui.toast(c, "Matched to ${m.title}. Next episodes from this app will join it automatically.")
                    changed()
                }
            }
            if (item.needsMatch) opts.add(0, "⚠ Pick the show (the app only reported the episode name)" to fix)
            opts += "Details" to { details(c, item) }
            if (!item.needsMatch) opts += "Fix show match (wrong show / no dates)" to fix
            opts += (if (item.followed) "Unfollow" else "Follow (keep in Upcoming)") to {
                WatchStore.update(c, item.key) { it.followed = !it.followed }; changed()
            }
            opts += "Open with which app…" to {
                val apps = AppRepository.load(c, true)
                Ui.choose(c, "Watch ${item.title} on", apps.map { it.label }) { i ->
                    WatchStore.update(c, item.key) { it.pkg = apps[i].packageName }; changed()
                }
            }
            opts += "Refresh episode info" to { ShowInfo.resolveAsync(c, item.key); Ui.toast(c, "Refreshing…") }
            opts += "Remove from list" to { WatchStore.remove(c, item.key); changed() }
            if (item.pkg != null && !item.followed) opts += "Never track ${app ?: item.pkg}" to {
                BasePrefs.setWatchIgnored(c, item.pkg!!, true)
                WatchStore.snapshot(c).filter { it.pkg == item.pkg && !it.followed }.forEach { WatchStore.remove(c, it.key) }
                changed()
            }
            Ui.choose(c, item.title, opts.map { it.first }) { opts[it].second() }
        }

        fun details(c: Context, it: WatchItem) {
            val lines = mutableListOf<String>()
            it.matchedTitle?.takeIf { t -> t != it.show }?.let { _ -> lines += "Detected as: ${it.show}" }
            it.episode?.let { e -> lines += "Last episode: $e" }
            if (it.lastWatched > 0) lines += "Last watched: ${ShowInfo.whenText(it.lastWatched)}"
            it.pkg?.let { p -> lines += "App: ${AppRepository.label(c, p)}" }
            lines += ShowInfo.nextLine(it)?.let { n -> "Next: $n" } ?: "Next episode: not announced"
            it.nextName?.let { n -> lines += "Next title: $n" }
            it.status?.let { s -> lines += "Status: ${s.lowercase().replace('_', ' ')}" }
            lines += "Info from: " + when (it.source) {
                WatchStore.ANILIST -> "AniList (#${it.extId}) — Japanese broadcast time; Crunchyroll simulcasts usually land at or within ~1 hour of it"
                WatchStore.TVMAZE -> "TVmaze (#${it.extId})"
                WatchStore.MANUAL -> "Added by you — repeats weekly; use Fix show match to change it"
                else -> "not matched yet"
            }
            Ui.dialog(c).setTitle(it.title).setMessage(lines.joinToString("\n\n"))
                .setPositiveButton(R.string.ok, null).show()
        }
    }
}
