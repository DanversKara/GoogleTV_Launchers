package com.moomoo.tvlauncherbase

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import com.moomoo.tvlauncherbase.BasePrefs.Press

/**
 * REMOTE BUTTONS: the button remapper. Each button can have a different
 * action for a single, long and double press. "Learn a button" captures
 * any key the remote sends (including ones not in the list).
 */
class KeyMapActivity : RowListActivity() {

    override val screenTitle get() = getString(R.string.keymap_title)
    override val screenDesc get() = getString(R.string.keymap_desc)

    override fun onResume() {
        editing = true
        if (!ServiceSetup.a11yEnabled(this)) ServiceSetup.selfEnable(this)
        super.onResume()
    }

    override fun onPause() {
        editing = false
        super.onPause()
    }

    override fun rows(): List<Row> {
        val out = mutableListOf<Row>()
        val svcOn = HomeGuardService.instance != null
        out += Row(
            if (svcOn) "✓ Remapper service is running" else "⚠ Remapper service is OFF — buttons won't be remapped in apps",
            if (svcOn) "Mappings work in every app and on the home screen (they pause only while this screen is open)."
            else "Status: ${ServiceSetup.a11yStatus(this)}. Select for the fix."
        ) { if (svcOn) Ui.toast(this, "All good") else showServiceFix(this) }
        out += Row("Button tester", "Press buttons and see live whether the remapper receives them, and what they're mapped to") {
            startActivity(Intent(this, KeyTestActivity::class.java))
        }
        out += Row("Remapper", if (BasePrefs.remapEnabled(this)) "On" else "Off — all buttons behave normally") {
            BasePrefs.setRemapEnabled(this, !BasePrefs.remapEnabled(this)); refresh()
        }
        out += Row("+ Learn a button", "Press any remote button to add it — finds keys not listed below") { learn() }
        out += Row("Long-press time", "${BasePrefs.longPressMs(this)} ms") {
            val o = listOf(350, 450, 550, 700, 900, 1200)
            Ui.choose(this, "Hold for", o.map { "$it ms" }) { BasePrefs.setLongPressMs(this, o[it]); refresh() }
        }
        out += Row("Double-press window", "${BasePrefs.doublePressMs(this)} ms") {
            val o = listOf(200, 250, 300, 400, 500)
            Ui.choose(this, "Second press within", o.map { "$it ms" }) { BasePrefs.setDoublePressMs(this, o[it]); refresh() }
        }
        out += Row("Reset all buttons", "Every button back to default") {
            Ui.confirm(this, "Reset all buttons?", "All single, long and double press mappings are cleared.") {
                BasePrefs.resetAllKeys(this); refresh()
            }
        }
        out += Row("Buttons", header = true)
        val learned = BasePrefs.learnedKeys(this)
        for (k in RemoteKeys.allKeys(this)) {
            val mapped = RemoteKeys.isMapped(this, k.code)
            out += Row(
                (if (mapped) "● " else "") + k.name,
                RemoteKeys.summary(this, k.code),
                onLong = if (k.code in learned) ({
                    Ui.confirm(this, "Remove ${k.name}?", "Removes the button and its mappings.") {
                        BasePrefs.removeLearnedKey(this, k.code); refresh()
                    }
                }) else null
            ) { editKey(k) }
        }
        return out
    }

    private fun learn() {
        val handler = Handler(Looper.getMainLooper())
        var dlg: AlertDialog? = null
        val done: (Int) -> Unit = { code ->
            HomeGuardService.learnListener = null
            handler.removeCallbacksAndMessages(null)
            dlg?.dismiss()
            if (code in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                Ui.toast(this, "OK and the D-pad can't be remapped (you'd lose navigation)")
            } else {
                BasePrefs.addLearnedKey(this, code)
                refresh()
                editKey(RemoteKeys.MappedKey(code, RemoteKeys.keyName(code)))
            }
        }
        dlg = Ui.dialog(this)
            .setTitle("Learn a button")
            .setMessage("Press the remote button you want to remap…\n\n(Waiting 10 seconds.)" +
                if (HomeGuardService.instance == null)
                    "\n\nThe Home guard service is off, so only buttons that reach apps can be learned." else "")
            .setOnKeyListener { _, code, ev ->
                // Fallback path when the accessibility service is off.
                if (ev.action == KeyEvent.ACTION_UP && HomeGuardService.instance == null) { done(code); true } else false
            }
            .create()
        HomeGuardService.learnListener = { code -> runOnUiThread { done(code) } }
        handler.postDelayed({
            HomeGuardService.learnListener = null
            dlg.dismiss()
            Ui.toast(this, "No button pressed")
        }, 10_000)
        dlg.setOnDismissListener { HomeGuardService.learnListener = null; handler.removeCallbacksAndMessages(null) }
        dlg.show()
    }

    private fun editKey(k: RemoteKeys.MappedKey) {
        val presses = Press.values()
        val items = presses.map { p ->
            "${p.label}: " + RemoteKeys.describe(this, BasePrefs.keyAction(this, k.code, p), BasePrefs.keyArg(this, k.code, p))
        }
        val b = Ui.dialog(this).setTitle(k.name)
            .setItems(items.toTypedArray()) { _, w -> pickAction(k, presses[w]) }
            .setNegativeButton(R.string.cancel, null)
        val warn = mutableListOf<String>()
        k.warn?.let { warn += it }
        val hasLongOrDouble = listOf(Press.LONG, Press.DOUBLE).any {
            BasePrefs.keyAction(this, k.code, it).let { a -> a != null && a != RemoteKeys.NONE }
        }
        val shortSet = BasePrefs.keyAction(this, k.code, Press.SHORT).let { a -> a != null && a != RemoteKeys.NONE }
        if (hasLongOrDouble && !shortSet && !RemoteKeys.canReplay(k.code))
            warn += "With a long/double press mapped, this button's normal single press can't be passed on — give it a single-press action too."
        // AlertDialog can't show a message and a list together, so warnings go in a toast.
        if (warn.isNotEmpty()) Toast.makeText(this, warn.joinToString("\n"), Toast.LENGTH_LONG).show()
        b.show()
    }

    private fun pickAction(k: RemoteKeys.MappedKey, press: Press) {
        Ui.choose(this, "${k.name} · ${press.label}", RemoteKeys.GROUPS.map { it.first }) { gi ->
            val group = RemoteKeys.GROUPS[gi].second
            Ui.choose(this, RemoteKeys.GROUPS[gi].first, group.map { RemoteKeys.label(it) }) { ai ->
                val action = group[ai]
                pickArg(this, action) { arg ->
                    if (action == RemoteKeys.NONE) BasePrefs.clearKey(this, k.code, press)
                    else BasePrefs.setKey(this, k.code, press, action, arg)
                    refresh()
                }
            }
        }
    }

    companion object {
        /** True while the editor is open: the remapper pauses so you can always fix mappings. */
        @Volatile var editing = false

        /** Explains how to switch the accessibility service on, incl. Android 13+ "Restricted setting". */
        fun showServiceFix(c: Context) {
            if (ServiceSetup.selfEnable(c) && ServiceSetup.a11yEnabled(c)) {
                Ui.toast(c, "Service switched on — give it a second"); return
            }
            Ui.dialog(c).setTitle("Turn on the remapper service")
                .setMessage(
                    "1. Settings → Accessibility → Kara's TV → turn it On.\n\n" +
                    "2. If the switch is greyed out or says \"Restricted setting\" (Android 13+ blocks sideloaded apps), " +
                    "run these once from a computer with adb, then try step 1 again — or skip step 1, " +
                    "because with the second command ${Brand.name} turns the service on by itself, and back on after updates:\n\n" +
                    ServiceSetup.adbCommands(c) + "\n\n" +
                    "3. Or enable it directly:\n" + ServiceSetup.adbDirect(c) + "\n\n" +
                    "No computer? Apps like \"Remote ADB Shell\" or TellyScope can run these on the TV itself " +
                    "(Developer options → Network/USB debugging)."
                )
                .setPositiveButton("Open Accessibility") { _, _ -> ServiceSetup.openAccessibility(c) }
                .setNegativeButton(R.string.cancel, null).show()
        }

        /** Asks for the action's argument (app, input, URL, camera); calls back with null if none needed. */
        fun pickArg(c: Context, action: String, done: (String?) -> Unit) {
            when (action) {
                RemoteKeys.APP -> {
                    val apps = AppRepository.load(c, true)
                    if (apps.isEmpty()) { Ui.toast(c, "No apps found"); return }
                    Ui.choose(c, "Open which app?", apps.map { it.label }) { done(apps[it].packageName) }
                }
                RemoteKeys.INPUT -> {
                    val ins = TvInputs.list(c, all = true)
                    if (ins.isEmpty()) { Ui.toast(c, "No TV inputs found on this device"); return }
                    Ui.choose(c, "Switch to which input?", ins.map { it.displayName(c) }) { done(ins[it].id) }
                }
                RemoteKeys.URL -> Ui.prompt(c, "Link to open", "https://", "https://… or app deep link") {
                    if (it.length > 3) done(it)
                }
                RemoteKeys.CAM_FULL -> {
                    val cams = BasePrefs.cameras(c)
                    if (cams.isEmpty()) { Ui.toast(c, "Add a camera first (Settings → Cameras)"); return }
                    Ui.choose(c, "Which camera?", cams.map { it.name }) { done(it.toString()) }
                }
                else -> done(null)
            }
        }
    }
}
