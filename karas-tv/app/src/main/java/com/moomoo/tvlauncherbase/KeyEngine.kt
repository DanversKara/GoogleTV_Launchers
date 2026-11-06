package com.moomoo.tvlauncherbase

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.moomoo.tvlauncherbase.BasePrefs.Press

/**
 * The single / long / double press engine, shared by:
 *  - HomeGuardService (system-wide, every app), and
 *  - MainActivity (fallback while the home screen is in front and the
 *    accessibility service is off — so mappings still work on home).
 *
 * Mappings pause only while the Remote buttons editor is open
 * ([KeyMapActivity.editing]) so a bad mapping can always be undone.
 * In tester mode ([KeyTestActivity.active]) nothing runs — keys are
 * only logged with what *would* have happened.
 */
class KeyEngine(private val ctx: Context, private val source: String) {

    private val h = Handler(Looper.getMainLooper())
    private val consumed = HashSet<Int>()
    private var downCode = -1
    private var longFired = false
    private var pendingSingle = -1
    private val longRunnable = Runnable {
        longFired = true
        fire(downCode, Press.LONG)
    }
    private val singleRunnable = Runnable {
        val code = pendingSingle
        pendingSingle = -1
        if (code != -1) singleOrReplay(code)
    }

    private fun action(code: Int, p: Press): String? =
        BasePrefs.keyAction(ctx, code, p)?.takeIf { it != RemoteKeys.NONE }

    /** Returns true when the key was consumed by a mapping. */
    fun onKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (KeyTestActivity.active) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                val parts = Press.values().mapNotNull { p ->
                    action(code, p)?.let { "${p.label.substringBefore(' ')} → ${RemoteKeys.describe(ctx, it, BasePrefs.keyArg(ctx, code, p))}" }
                }
                KeyLog.add(code, source, if (parts.isEmpty()) "not mapped" else parts.joinToString(" · "))
            }
            return false
        }
        if (!BasePrefs.remapEnabled(ctx) || KeyMapActivity.editing) return false
        val short = action(code, Press.SHORT)
        val long = action(code, Press.LONG)
        val dbl = action(code, Press.DOUBLE)

        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return code in consumed
                if (short == null && long == null && dbl == null) return false
                consumed += code
                if (long == null && dbl == null) {
                    fire(code, Press.SHORT)
                    return true
                }
                downCode = code
                longFired = false
                h.removeCallbacks(longRunnable)
                if (long != null) h.postDelayed(longRunnable, BasePrefs.longPressMs(ctx).toLong())
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (!consumed.remove(code)) return false
                if (code == downCode) h.removeCallbacks(longRunnable)
                if (long == null && dbl == null) return true
                if (code == downCode && longFired) { longFired = false; return true }
                if (dbl != null) {
                    if (pendingSingle == code) {
                        h.removeCallbacks(singleRunnable)
                        pendingSingle = -1
                        fire(code, Press.DOUBLE)
                    } else {
                        if (pendingSingle != -1) { h.removeCallbacks(singleRunnable); singleOrReplay(pendingSingle) }
                        pendingSingle = code
                        h.postDelayed(singleRunnable, BasePrefs.doublePressMs(ctx).toLong())
                    }
                } else {
                    singleOrReplay(code)
                }
                return true
            }
        }
        return code in consumed
    }

    private fun fire(code: Int, p: Press) {
        val a = action(code, p) ?: return
        val ok = try { RemoteKeys.run(ctx, a, BasePrefs.keyArg(ctx, code, p)) } catch (e: Exception) { false }
        KeyLog.add(code, source, "${p.label}: ${RemoteKeys.label(a)}" + if (ok) " ✓" else " ✗ failed")
        if (!ok) Ui.toast(ctx, "Couldn't run: ${RemoteKeys.label(a)}")
    }

    private fun singleOrReplay(code: Int) {
        if (action(code, Press.SHORT) != null) fire(code, Press.SHORT)
        else {
            val ok = RemoteKeys.replay(ctx, code)
            KeyLog.add(code, source, if (ok) "normal press passed on" else "normal press lost (can't re-send this key)")
        }
    }

    fun reset() {
        h.removeCallbacksAndMessages(null)
        consumed.clear(); pendingSingle = -1; downCode = -1
    }
}

/** Ring buffer of recent key activity, shown in the button tester. */
object KeyLog {
    data class Entry(val at: Long, val code: Int, val source: String, val what: String)
    private val entries = ArrayDeque<Entry>()
    @Volatile var version = 0L
        private set

    @Synchronized fun add(code: Int, source: String, what: String) {
        entries.addFirst(Entry(System.currentTimeMillis(), code, source, what))
        while (entries.size > 30) entries.removeLast()
        version++
    }

    @Synchronized fun list(): List<Entry> = entries.toList()
    @Synchronized fun clear() { entries.clear(); version++ }
}
