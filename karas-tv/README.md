# Kara's TV 2.7 — an ad-free, animated Google TV home with live CCTV, weather, what's-on, and a full button remapper

**Package:** `com.moomoo.tvlauncherbase` (unchanged, so it installs over 1.x and keeps your settings)
**Version:** 2.8.1 (versionCode 18) · shown on the TV as **Kara's TV** (rename it in Customize home → Name) · **Min SDK:** 26 · **Target SDK:** 34 · **Dependencies:** none (framework APIs only)

Built for Google TV / Android TV boxes such as the **onn. Google TV 4K / 4K Pro**, Chromecast with Google TV, and TVs running Google TV.


## Screenshots

<table>
<tr>
<td><img src="screenshots/home-cameras.png" width="360" alt="Home screen with live cameras (camera feeds blurred)"></td>
<td><img src="screenshots/home-quick-menu.png" width="360" alt="Quick menu (camera feeds blurred)"></td>
</tr>
<tr>
<td><img src="screenshots/home-apps.png" width="360" alt="Your apps grid"></td>
<td><img src="screenshots/tv-guide-grid.png" width="360" alt="Grid TV guide"></td>
</tr>
</table>

## What's new in 2.9 (upcoming — not in the 2.8.1 release yet)

### IPTV: playlists, TV guide, and a grid guide
- Import M3U playlists by URL or file upload, from the web editor or by long-pressing 📺 → Add playlist. Multiple playlists are saved; re-importing one refreshes it instead of duplicating it.
- The TV guide shows the active playlist, or **All** — every playlist merged with your hand-added channels. Grid view (time across, channels down, programmes sized by length, red now-line) or list view; OK plays what's on now.
- Guide data (EPG): a playlist's own guide loads itself from its `url-tvg` header, or set a guide URL by hand. Cards show what's on now and next.
- Long-press a channel to favorite it; favorites sit at the top of the guide.
- A "My Playlists" row in the guide (under My channels): tap a playlist to set it active.
- No channels or playlists ship with the app — you add your own.

### Web editor (LAN only)
- Settings → System → Web editor, then open `http://<your-TV's-IP>:8080` in a browser on the same network. Edit quick-menu buttons and icons, the header logo, and IPTV channels and playlists — changes apply live on the TV, no restart, and save automatically. Nothing leaves your network.

## What's new in 2.8.1

- **Your wallpaper choice is kept when you change themes.** Picking a theme used to replace the wallpaper you'd chosen with the theme's own. Customize home → Wallpaper source shows what's actually on screen, and has an option to let themes set the wallpaper again.
- **Network trouble no longer writes a show off.** If AniList / TVmaze couldn't be reached when a show was first detected, it was marked "no match found" and skipped for a day. It now retries in 15 minutes.
- **Test connections** (Settings → Watching & upcoming, or Customize home → Wallpaper source): checks AniList, TVmaze, Apple, Open-Meteo and artwork from the TV itself, and says plainly which one is blocked and why.
- The Watching & upcoming screen shows the last lookup problem, if there was one.
- Includes everything from the combined 2.8: Upcoming lists every followed show, and the wallpaper shows exactly the mode you picked.

## What's new in 2.8

### Upcoming episodes are reliable again
- **One shared request pacer for AniList.** 2.7's Discover wallpaper and the episode lookups both used AniList, which allows about 30 requests a minute without an account. Together they could hit that limit, refreshes failed, and shows dropped out of Upcoming after their episode aired. Every AniList request now goes through one pacer (one every 2.2 s) with an automatic retry when AniList says "too busy".
- **Failed refreshes never erase a known date**, and they retry in 15 minutes instead of 3 hours.
- **Weekly estimate:** if a show's episode has aired and the database can't be reached, the next episode is estimated a week later, marked **≈**, until the real date comes in.
- **One entry per show:** the same show found on both AniList and TVmaze is merged into one entry, keeping AniList's dates for anime. (The Upcoming episodes panel still lists every followed show, including ones also under Airing soon, as of 2.7.5.)

### Redesigned header
- **One clean row, Google TV style:**
  `[Name]  🔍  Apps  📷  ⧉  ……  ☀ 61° / Mostly clear  |  1:16 PM / Wed, Nov 4  ✎  ⚙`
- **Crisp drawn icons:** Search, Cameras, Float, Customize and Settings are now line icons in Google's Material style, instead of emoji.
- **Apps tab:** shows an accent underline while open. The 📷 and ⧉ buttons still follow their show/hide settings from 2.7.5.
- **Weather:** a single short line under the temperature. Wind, rain chance and the 4-day forecast are one OK press away.
- **Clock:** time, with "Wed, Nov 4" underneath.
- **Always fits:** if the row is ever wider than the screen, everything shrinks together until it fits. Nothing is hidden or pushed off.
- **New settings** (Settings → More home options, and Customize home → Design):
  - **Text size** for the whole app (90–130%),
  - **Header size** (85–130%).

## What's new in 2.7.5

- **Floating cameras remember everything.** The ⧉ button now reopens every window that was open, each with its own camera, spot and size, after toggling off/on or a reboot (it used to reopen just one camera). The first time, all cameras (up to 4) open at the smallest size in a row along the bottom-right corner. Arrange windows has a new "Small, in a row in the bottom-right corner" layout, and "Float all cameras" keeps your positions.
- **Upcoming episodes in the compact strip and the featured cards** now list every followed show, same as the Upcoming episodes row. Before, a show airing inside the "Airing soon" window was left out, so the panel said "Follow a show".
- **Wallpaper shows what you picked.** Discover, Slideshow and the other modes were being replaced by the first Continue watching show's art. Only "From what you're watching" follows the highlighted show now.
- **TV inputs row shows HDMI only** by default. App inputs such as Xumo Play can be turned back on in Settings → App inputs.
- **Settings → More home options:** show or hide the 📷 Cameras and ⧉ Floating cameras buttons in the top bar.
- **Discover is wallpaper only.** Tall movie posters were drawn as a separate panel floating above the cameras and rows. That panel is gone: every Discover image fills the background behind the content, and the title caption sits behind the content too.

## What's new in 2.7.4

- **Floating cameras are fully opaque at 100%.** Android 12+ automatically caps overlay windows that let touches pass through at 80% opacity, so they looked see-through over bright video. Floating windows no longer pass touches through (TVs have no touchscreen; the remote still controls the movie), so the cap no longer applies and the Opacity setting is exact.

## What's new in 2.7.3

- **Settings → More home options → Search button**: show or hide the 🔍 Search button in the top bar.

## What's new in 2.7.2

- Top bar tidied up:
  - The **Home** button is gone; Back or the remote's Home button already take you home.
  - **Cameras** is now a 📷 icon, and **Float** is just the ⧉ icon.
  - **Apps** shows "✕ Apps" while open; press it again to go back.

## What's new in 2.7.1

- **✎ Customize and ⚙ Settings are always visible.** With the name, buttons, weather and clock all in the top bar, they could be pushed off the right edge of the screen. The right side (weather, clock, ✎, ⚙) now always gets its space first. The left side (name, Search, Home, Apps, Cameras, Float) uses the rest and scrolls sideways if it ever doesn't fit.

## What's new in 2.7

- **Now called Kara's TV.** The name is a setting (Customize home → Name), and every in-app screen uses it. It can show:
  - in the header (far left, or between Float and the weather),
  - under the top bar,
  - in the footer,
  - or not at all,
  
  as a glowing title with an animated shimmer. Android's own screens (Accessibility list, Home picker) show the built-in label "Kara's TV". The package ID is unchanged, so it upgrades in place and keeps everything.
- **Weather in the header**, left of the clock:
  - an animated icon (spinning sun, glowing moon with twinkling stars, drifting clouds, falling rain / drizzle / snow, lightning, fog, and wind streaks when it's windy),
  - the temperature, conditions and high/low, plus wind and rain chance.
  - OK opens a 4-day forecast.
  - Free Open-Meteo data: no account, no key, refreshed every 30 min.
  - Set your city / ZIP, or detect it automatically.
  - °F/°C.
- **Animated themes:** new live backgrounds in Customize home → Design → Animated background:
  - **Aurora** (flowing northern lights),
  - **Floating particles**,
  - **Starfield** (twinkling, with shooting stars),
  - **Rolling waves**,
  - **Bokeh lights**,
  - **Synthwave grid** (neon sun and moving grid).

  New presets built on them: **Aurora ✦, Galaxy ✦, Synthwave ✦, Cyberpunk ✦**. Neon, Glass, Ocean, Sunset and Retro are now animated too.
- **Hover glow:** a soft neon halo that **glides smoothly** from tile to tile and gently pulses. Its color is either the theme's or **each app's own color** (red for Netflix, orange for Crunchyroll…). Pulse can be on or off.
- **Discover wallpaper** (Customize home → Wallpaper → Discover):
  - rotating backdrops of what's new, with a title caption:
    - trending & upcoming **anime** (AniList),
    - **shows airing today** on TV and streaming (TVmaze),
    - **new movie releases** (Apple's top movies chart).
  - It's wallpaper only: every image stays behind your cameras, rows and apps.
  - No key needed. Add an optional free **TMDB key** for **Now in theaters** and **Coming soon**. IMDb has no free API, so TMDB is the free way to get theater listings.
  - Used by the new Cinema and Crunchyroll presets.
- Backups now save to **Download/Kara's TV/**. Older backups in Download/TV Launcher/ are still found when restoring.

## What's new in 2.6

### Themes and design (Customize home → Theme / Design)
- **10 theme presets:** Google TV, AMOLED Black, Glass, Neon, Crunchyroll, Cinema, Sunset, Ocean, Minimal Light and Retro. Each sets colors, panels, corners, focus effect, font and wallpaper in one go.
- **Mix and match on top of any preset:**
  - accent color (12),
  - focus effect: border, glow, lift & zoom, or underline,
  - corners: sharp, rounded or pill,
  - font: standard, condensed, rounded, monospace or serif,
  - density: comfortable or compact.
- **Wallpaper:**
  - **slideshow** of your shows' artwork,
  - **animated gradient** in the theme's colors,
  - **blur** (Light / Strong, Android 12+),
  - darkness 10–85%.
- **My themes:** save the current look under a name, re-apply it later, and it's included in backups.

### Clock
- **Styles:**
  - stacked (time, then *Tuesday · November 3*),
  - single line,
  - **big clock** at the top of home,
  - time only,
  - hidden.
- **Format:** 12 / 24 hour, optional seconds, and date order (November 3 / 3 November / 11/3).
- **Extras:** an optional greeting ("Good evening"), and a **💤 sleep-timer countdown** under the clock while the timer runs.

### Backups that survive "Clear storage"
- Saved to the shared **Download/TV Launcher/tv-launcher-backup.json**. Clearing the app's storage or uninstalling doesn't touch it.
- **Automatic:** rewritten about 20 s after any change, plus a daily "previous" copy.
- Includes settings, cameras, button mappings, theme, favorites, **followed shows and Continue watching**.
- **On a fresh start** (after clearing storage), setup finds the backup and offers to restore it in one tap. There's also **↺ Restore from a backup** in setup and in Settings → Backup.
- If Android hides the old file from a reinstalled app, you can pick it with the file picker, or grant file access once:
  ```
  adb shell appops set com.moomoo.tvlauncherbase MANAGE_EXTERNAL_STORAGE allow
  ```

### Your apps: hold to drag
**Hold OK** on a favorite to pick it up:
- **◀ ▶ ▲ ▼** move it through the grid live,
- **OK** drops it,
- **hold OK** again for its options,
- **Back** finishes.

### No wrap-around
Anywhere on the home screen, ◀ / ▶ at the end of a row stays put instead of jumping to another row or the top bar.

### Finding shows
- Searches have their own fast lane, so they no longer wait behind background lookups.
- If nothing is found, the search retries automatically with a cleaned or shorter title.
- When AniList or TVmaze doesn't answer, it says so instead of "nothing found".
- **"Not listed? Add it yourself":** pick the app, weekday, time and (optionally) the episode number. It shows in Upcoming every week and moves on to the next week after each episode.
- Followed shows with no announced date stay visible at the end of Upcoming as **"date TBA"**.

## What's new in 2.5

- **No more duplicates in Continue watching:**
  - When an app reports the same video or show slightly differently each time (metadata still loading, title and subtitle swapped, "(Live)" added…), it now updates the existing entry instead of adding another one.
  - Duplicates already saved are merged automatically, newest first.
  - The rows also never show the same thing twice.
- **SmartTube** (and NewPipe, ReVanced, Tubio, Twitch, Rumble…) are treated like YouTube: videos go in Continue watching only, with no TV-show lookups. Wrong show matches made by earlier versions are cleared.
- **"Your apps" is a compact grid:** icons stack in rows instead of one long line. Settings in Customize home → Your apps:
  - **3–12 per row** (bigger number = smaller tiles),
  - **rows shown** (1–4 or all),
  - or switch back to the single sideways row.
- "All apps" keeps its own per-row size setting.

## What's new in 2.4

- **Featured cards:** the single big banner is now **three compact cards side by side**: Continue watching, Airing soon and Upcoming episodes. Each shows the top show of its column with its own artwork, title, episode and countdown / % watched. They no longer copy whatever you highlighted last; only the wallpaper follows your focus. Each card can be switched off (Customize home → Featured cards). Turn the row on under Customize home → Rows.
- **"Airing soon" window:** choose 24 hours, 48 hours, 3 days, 1 week or 2 weeks (Customize home → Watching & upcoming strip). It applies to the strip and the featured cards.
- **One entry per show:** when a show you follow (with artwork) is also detected while playing, the two merge. Continue watching gets the proper show artwork, the dates and the follow flag, plus your latest episode and progress.
- **Smarter Crunchyroll guess:** if the app sends only an episode name and you follow exactly one Crunchyroll / anime show, it's assumed to be that show. No need to pick it.
- **YouTube** (and Twitch / Vimeo) videos appear in Continue watching only. They're never looked up as TV shows, so there are no bogus "next episode" dates.
- **Top bar:** ▶ on ⚙ Settings (and ◀ on Search) now stays put instead of jumping down to an app tile.

## What's new in 2.3

- **Better show detection (Crunchyroll):** some apps report only the *episode* title. That's why a long episode name showed up as the show, with no artwork or dates. TV Launcher now:
  - checks every field the player reports (album, subtitle, artist, queue title, and the app's own media-controls card) for the series name;
  - when the app really sends only the episode name, shows **"Which show is this? → Pick the show"**. Pick it once and the following episodes from that app join it automatically (within 12 hours, while binge-watching). After that you get the artwork, the wallpaper and the next-episode countdown;
  - can show you **What the playing app reported** (Settings → Watching & upcoming), so you can see exactly what Crunchyroll sends.
- **Artwork fallbacks:** artwork given as a local `content://` link is now loaded. When a show has no artwork at all, cards and thumbnails show the app's own TV banner instead of an empty box.
- **Compact featured banner:** a 16:9 thumbnail on the left with a one-line title, details, the next-episode line and two small buttons. It's about a third of the old height.
- Big card rows now default to Small.

## What's new in 2.2

- **Cameras look like they're behind a window.** No more square video corners poking past the rounded frame, and no thin black bars:
  - Home tiles, the camera wall and floating windows are full-bleed and clipped to rounded corners.
  - The border/focus frame is drawn over the video's edge.
  - The camera name is now a small pill in the corner.
  - Customize home → Cameras → *Camera picture* switches between Fill (default) and Whole picture.
- **New default layout: cameras on top**, then the new **Watching & upcoming strip**, then Your apps, Inputs and All apps. This applies once on upgrade; you can rearrange it any time.
- **Watching & upcoming strip:** a compact panel showing all three at once, side by side:
  - **Continue watching** (episode, app, % watched),
  - **Airing soon** (next 48 h, with countdown),
  - **Upcoming episodes** (later).
  - Text-only by default, with optional small thumbnails.
  - Adjustable lines per column (1–8) and text size (S/M/L). Each column can be switched off.
  - Focusing a line still changes the wallpaper. Menu / hold OK opens the show's options.
- **The big card rows are resizable:** Small / Medium / Large, or **text-only cards**. The featured banner and the big rows are hidden by default now (turn them back on under Customize home → Rows).

## What's new in 2.1.1

- **Floating camera windows have no black bars.** Each window snaps to its camera's real shape (4:3, 16:9, portrait or rotated), so the picture fills it without distortion. Moving, resizing and the layout presets keep that shape.
- Settings → Floating camera windows → **Picture fit** has four modes:
  - **Snap** (default),
  - **Fill** (crop the edges),
  - **Stretch**,
  - **Show whole picture** (the old letterbox look).
- The window border is thinner.

## What's new in 2.1

### Button remapping fixed
Remapping didn't work in 2.0 for two reasons:
- **Mappings were switched off on TV Launcher's own home screen**, so testing buttons from home did nothing. They now work everywhere. They pause only while the Remote buttons editor is open, so a bad mapping can always be undone.
- **Android 13+ greys out the accessibility switch for sideloaded apps** ("Restricted setting"), so the service often never started. Fixes, in order of preference:
  - Run these once from a computer (or a TV adb app):
    ```
    adb shell appops set com.moomoo.tvlauncherbase ACCESS_RESTRICTED_SETTINGS allow
    adb shell pm grant com.moomoo.tvlauncherbase android.permission.WRITE_SECURE_SETTINGS
    ```
    With the second line, TV Launcher **switches its services on by itself**, and back on after updates, reboots, or if the system turns them off.
  - Or enable them directly:
    ```
    adb shell settings put secure enabled_accessibility_services com.moomoo.tvlauncherbase/com.moomoo.tvlauncherbase.HomeGuardService
    adb shell settings put secure accessibility_enabled 1
    adb shell cmd notification allow_listener com.moomoo.tvlauncherbase/com.moomoo.tvlauncherbase.MediaWatchService
    ```
- **Remaps work on the home screen even with the service off.** Other apps still need the service.
- **New: Button tester** (Settings → Button tester, or Remote buttons → Button tester). Press buttons to see live:
  - whether the remapper service receives each one,
  - what it's mapped to,
  - and which buttons the TV keeps for itself (they never show up).
- The Remote buttons screen shows the service status, with a step-by-step fix when it's off.

### Google TV-style home
- **Full-screen wallpaper from what you're watching.** It uses the show's backdrop and changes as you move along the rows, like Google TV.
- **Featured banner:**
  - show title, last episode, % watched and the app it's on,
  - **"Next: S2 E8 · Sat Nov 8 · 9:30 AM (in 2d 4h)"**,
  - **▶ Watch on Crunchyroll** and **More** buttons.
- **Continue watching:** artwork cards with app badge and progress bar.
- **Upcoming episodes:** a countdown badge on every card, soonest first, plus a "+ Follow a show" card.
- Top bar: **Search** (Assistant), **Home / Apps** tabs, Cameras, Float, clock, **✎ Customize**, **⚙ Settings**.

### Where "watching" and "upcoming" come from
- **What's playing:** TV Launcher reads the title and artwork of whatever is playing from the app's media session (Crunchyroll, Netflix, Prime, YouTube, Plex…). It looks only at the playing app's media-controls card, to find the series name; it never reads messages or other notifications.
  - Android only allows this through "Notification access". On Google TV that page is often hidden, so use the adb commands above.
  - Third-party launchers **cannot** read Google TV's own "Watch Next" list: that needs a system-only permission. So TV Launcher builds its own list from what it sees playing.
- **Next-episode times:**
  - **AniList** for anime, i.e. Crunchyroll. Exact Japanese broadcast time; Crunchyroll simulcasts usually land at or within about an hour of it.
  - **TVmaze** for TV series.
  - Both are free and need no account. Only show names are sent, and lookups can be switched off.
- **Follow a show** to track it even if you watch it somewhere TV Launcher can't see. **Fix show match** if a show was detected wrong.
- Music apps are ignored by default, and you can stop tracking any app.

### Customize home (✎ on the top bar, or Menu on the home screen)
- **Style:** Google TV style (featured banner + wallpaper) or Classic.
- **Wallpaper:**
  - from what you're watching,
  - accent gradient,
  - solid color (7 presets),
  - your own image link,
  - or plain.
  - Darkness is adjustable from 20–85%.
- **Rows:** show or hide each row, and move them up, down or to the top. Rows: Featured, Continue watching, Upcoming, Your apps, Cameras, Inputs, All apps.
- **App tiles:**
  - TV banners, icon cards, or round icons,
  - **3–9 apps per row**,
  - names on or off,
  - zoom on focus on or off.
- Accent color, clock, hidden apps, phone-only apps, camera strip size, and reset.

### App tiles no longer cut off
The all-apps grid used fixed widths that didn't account for row padding, so the last column was pushed off the side and clipped. Tiles now share each row proportionally, so they always fit exactly.

Banners that aren't 16:9 are now shown whole instead of cropped. The focus zoom also no longer gets clipped at row edges.

## What's new in 2.0

### Ad-free home screen
- No ads, sponsored rows, "recommended for you" or tracking. Network use: your camera URLs and the optional AniList / TVmaze show-name lookups.
- Clock and date, then **Cameras → Favorites → Inputs → All apps**.
- App tiles use the app's real TV banner (or icon + name), with a focus zoom and an accent-colored border.
- **Menu** (or hold OK) on any app: add to / remove from favorites, move left or right, hide, map to a remote button, app info, uninstall.
- Hidden apps, phone-only apps, banners or icons, 12/24-hour clock and 7 accent colors are all in Settings.
- The home screen refreshes when apps are installed or removed. Home while already home jumps back to the top, and Back never "exits" the launcher.
- Settings can be backed up and restored.

### Live CCTV / MotionEye cameras on the home screen
- **Settings → Cameras → + Add camera.** Paste a MotionEye **Streaming URL**, e.g. `http://192.168.1.20:8081/`.
- Supported:
  - **MJPEG stream**: MotionEye's default. Uses its own decoder, which is fast and reconnects with back-off.
  - **JPEG snapshot**, polled: MotionEye's Snapshot URL, e.g. `http://192.168.1.20:8765/picture/1/current/`.
  - **Video**: RTSP / HLS / MP4 through the system player. Always muted.
- Logins: **Basic and Digest auth** (MotionEye supports both). Use the username/password fields or `http://user:pass@host:8081/`.
- Per-camera settings: name, rotation (0/90/180/270), whether it shows on the home strip, snapshot refresh rate.
- **Resizing:** press Menu on a camera tile → **Resize camera strip**. Then:
  - ▲▼ change the height, live.
  - ◀▶ change how many cameras fit per row (1–4).
  - OK or Back to finish.
  - There are also presets in Settings → Camera strip size (Hidden / Small / Medium / Large / Extra large).
- OK on a tile opens it full screen. The **camera wall** shows every camera in a grid. ◀▶ / CH± switch cameras, and Menu pops one out as a floating window.
- Streams pause when you leave the home screen, so they don't use bandwidth in the background.

### Floating camera windows over movies (picture-in-picture CCTV)
- Up to **4 floating camera windows** on top of any app: Netflix, YouTube, live TV, anything.
- The windows never take focus, so your remote keeps controlling the movie. Camera audio is always muted.
- Ways to open them:
  - the **⧉ Float** button on home,
  - Menu on a camera → "Show as floating window",
  - Settings → Floating camera windows,
  - or (best) **map a remote button** to *Floating cameras: show / hide*.
- **Moving and resizing with the remote:** map a button to *Floating cameras: move / resize*, or use Settings → Floating camera windows → *Move / resize with the remote*. Then:
  - **OK** cycles between Move → Resize → Pick window → Change camera.
  - The **D-pad** does the current job.
  - **Back** finishes; **Menu** closes the selected window.
  - Control mode also ends by itself after 20 s idle.
- One-tap layouts: stacked down the right side, one per corner, a row along the bottom, or all in the top-right.
- Opacity can be set from 40–100%. Window positions and sizes are remembered.
- **How it's allowed to draw over apps:**
  - With the Home guard accessibility service ON (recommended), it uses the service's own overlay layer. No extra permission is needed, which matters because Google TV often hides the "Display over other apps" page.
  - Otherwise it uses the overlay permission. If your TV has no page for that, grant it once from a computer:
    ```
    adb shell appops set com.moomoo.tvlauncherbase SYSTEM_ALERT_WINDOW allow
    ```

### Button remapper (overrides the stock onn / Google TV remote behaviour)
**Settings → Remote buttons.** This needs the Home guard service ON.

- Every button can have **3 separate actions: single press, long press, double press**. You can set the long-press time and the double-press window.
- **+ Learn a button:** press any button and it's added, including ones not in the list (keys from other remotes, game controllers, etc.).
- Built-in list:
  - Home, Back, Search/Mic, Assistant, Settings, Input, Guide/Live TV, TV, Menu, Info, Bookmark/Star, All apps, Profile, Captions
  - Mute, Volume ±, Channel ±, Last channel
  - Play/Pause, FF, RW
  - Red/Green/Yellow/Blue
- 46 actions in 7 groups:
  - **Apps & inputs:** open any app, switch to the previous app, jump straight to an HDMI input, open a link or deep link.
  - **Cameras:** floating show/hide, add another, show all, next camera, move/resize, close all, camera wall, one camera full screen.
  - **Media & volume:** play/pause, next/previous, FF/RW, stop, volume up/down, mute.
  - **System:** Back, stock Home, recents, notifications, quick settings, power menu, **sleep / screen off**, **sleep timer** (30/60/90/120 min), screenshot.
  - **Settings pages:** Wi-Fi, Bluetooth/remotes, display, sound, apps, accessibility, inputs.
  - **Launcher:** this launcher, its settings, Remote buttons, Cameras, Home guard, Permanent fix.
  - **Basic:** "Default (unchanged)" or "Disable button".
- Shortcut: Menu on any app → *Map to a remote button…*
- **Safety:**
  - OK and the D-pad can never be remapped.
  - Mappings pause while you're inside TV Launcher's own screens, so a bad mapping can always be undone.
  - There's a master on/off switch and a "Reset all buttons".
- **Map long/double press without losing the normal press:** Back, Home, volume, mute and media keys still do their normal job on a single press. Other buttons can't be re-injected without root, so give them a single-press action too (the editor warns you).

### Keep the Home button (kept from 1.x, improved)
- The guard now also catches the very old `com.google.android.leanbacklauncher`.
- "Make default" falls back to the system Home-app picker when Google TV ignores the role prompt.
- Everything else is as before: HOME + LEANBACK_LAUNCHER filters, Boot on Power, the 30-minute watchdog, and optional device-owner lockdown. See *Anti-takeover stack* below.

### Bugs fixed from 1.x
- **The manual `build.sh` never packed the Kotlin standard library into the APK.** Any APK built with it would crash the moment it opened. Fixed, and the script now finds your SDK automatically.
- The manifest's `package=` / version attributes conflicted with `build.gradle` (an error on AGP 8+, and a wrong versionCode in manual builds). The package now comes from Gradle, or is injected by `build.sh`.
- The home screen rebuilt everything on every resume, which lost focus position and restarted streams. It now rebuilds only when something actually changed, and restores focus.

## Install

```
adb install -r tv-launcher-debug.apk
```

Or copy the APK to a USB stick and install it with a file manager or TellyScope.

Open it once. Setup walks you through three steps:
1. **Make it your home screen.**
2. **Turn on the Home guard service:** Settings → Accessibility → TV Launcher → On. This powers the Home-button guard, the button remapper and the floating cameras. Android never lets an app turn this on by itself.
3. **Add your cameras** (optional).

On the onn. box, also consider disabling the stock launcher once TV Launcher is the default (see `disable-google-launcher.sh`).

## Honest limits

- **The Power button**, and on many Google TV builds the **branded app buttons** (YouTube / Netflix / Disney+ …) and sometimes the **mic button**, are handled by the system before any app or accessibility service sees them. No launcher can remap those without root. Use *Learn a button* to check yours: if the button gets learned, it can be remapped.
- **"Ad-free" means this home screen.** Ads inside YouTube, Tubi and other apps come from those apps and aren't touched.
- The **MotionEye defaults** assumed are: streaming port 8081 (MJPEG) and the snapshot URL on port 8765. If your stream needs a login, set *Streaming authentication* in MotionEye to Basic or Digest and enter the same credentials here.
- **RTSP/HLS playback** depends on the TV's built-in media player. If a video stream won't play, use MotionEye's MJPEG URL instead.
- **Blind build:** compiled and verified on a Linux build machine (manifest, signature, dex contents, API references), not yet run on a real TV.

## Anti-takeover stack (unchanged in spirit, do not strip)

1. **HOME + LEANBACK_LAUNCHER intent filters** on MainActivity.
2. **Home guard accessibility service.** It bounces `com.google.android.apps.tv.launcherx` (Google TV), `com.google.android.tvlauncher` and `com.google.android.leanbacklauncher` back to TV Launcher. Add OEM launchers in Settings → Extra launcher packages.
3. **Boot on Power:** opens TV Launcher when the TV powers on.
4. **30-minute watchdog:** posts a one-tap "restore" notification if another launcher took over.
5. **Optional device owner** (Settings → Permanent fix): `adb shell dpm set-device-owner com.moomoo.tvlauncherbase/.TvDeviceAdmin`. This usually only works before a Google account is added.

## Code map

| File | What it does |
|---|---|
| `MainActivity.kt` | Home screen: Google TV-style hero + wallpaper, Continue watching, Upcoming, apps, cameras, inputs |
| `CustomizeActivity.kt` | Customize home (style, wallpaper, row order, tile style/size) |
| `MediaWatchService.kt` | "What's playing" watcher (media sessions via notification access) |
| `WatchStore.kt` / `ShowInfo.kt` | Watched/followed shows; AniList + TVmaze next-episode lookups |
| `WatchActivity.kt` | Watching & upcoming manager: follow, fix match, choose app |
| `KeyEngine.kt` / `KeyTestActivity.kt` | Single/long/double press engine (shared by service and home), button tester |
| `ServiceSetup.kt` | Service status, self-enable via WRITE_SECURE_SETTINGS, adb fix commands |
| `Img.kt` | Artwork loader with memory + disk cache |
| `CameraView.kt` | MJPEG / snapshot / RTSP-HLS camera tile, Basic + Digest auth, auto-reconnect |
| `CameraWallActivity.kt` | Full-screen grid / single camera |
| `CamerasActivity.kt` | Add / edit / reorder cameras, strip size |
| `OverlayManager.kt` | Floating camera windows and remote control mode |
| `OverlaySettingsActivity.kt` | Floating window settings, plus the fallback key-catcher activity |
| `RemoteKeys.kt` | Remapper actions, key catalogue, action runner, key replay |
| `HomeGuardService.kt` | Accessibility service: launcher guard, single/long/double press engine, learn mode, previous-app history, sleep timer |
| `KeyMapActivity.kt` | Remote buttons editor |
| `BasePrefs.kt` | All settings (JSON-backed lists), migrates 1.x key mappings |
| `LauncherTheme.kt` / `Ui.kt` | Colors, accent, focus styling, shared list screen |
| `AppRepository.kt` | App list, banners, launch, app info and uninstall |
| `SettingsActivity.kt`, `SetupActivity.kt`, `HomeGuardActivity.kt`, `LockdownActivity.kt`, `BootReceiver.kt`, `DeviceOwnerHelper.kt`, `TvInputs.kt` | As named |

## Build

**Android Studio (JDK 17):** open the folder and press Run, or run `./gradlew assembleDebug`.

**No Gradle:** run `./build.sh`. It needs:
- an Android SDK with platform 34 and build-tools,
- `kotlinc` 2.x,
- JDK 17+.

Paths can be set with environment variables (`SDK`, `KOTLINC`, `KEYSTORE`, …). The script writes `tv-launcher-debug.apk`.

## Undo everything

1. Settings → Accessibility → TV Launcher → Off. This stops the guard, the remapper and the floating windows.
2. Re-enable the stock launcher if you disabled it:
   ```
   adb shell pm enable com.google.android.apps.tv.launcherx
   ```
3. If you used device owner: Settings → Permanent fix → Release device owner.
4. Press Home, choose the stock launcher, then uninstall TV Launcher.

## License

MIT — see LICENSE.
