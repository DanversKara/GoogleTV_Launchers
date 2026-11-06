package com.moomoo.tvlauncherbase

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Small UI helpers shared by every screen (no AndroidX, framework only). */
object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    /**
     * Focusable background in the theme's focus style:
     * border (accent outline), glow (wide soft accent outline + tint),
     * lift (lighter surface; tiles also zoom), underline (accent bar under it).
     */
    fun focusBg(c: Context, normal: Int = Color.TRANSPARENT, radiusDp: Int = LauncherTheme.CORNER_DP): StateListDrawable {
        val r = dpf(c, radiusDp.toFloat())
        val focused: android.graphics.drawable.Drawable = when (LauncherTheme.focusStyle) {
            "glow" -> android.graphics.drawable.LayerDrawable(arrayOf(
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT); cornerRadius = r
                    setStroke(dp(c, 8), (LauncherTheme.accent and 0x00FFFFFF) or 0x40000000)
                },
                GradientDrawable().apply {
                    setColor(LauncherTheme.focusFill); cornerRadius = r
                    setStroke(dp(c, 2), LauncherTheme.accent)
                }
            ))
            "lift" -> GradientDrawable().apply {
                setColor(LauncherTheme.focusFill); cornerRadius = r
                setStroke(dp(c, 2), (LauncherTheme.text and 0x00FFFFFF) or 0x66000000)
            }
            "underline" -> android.graphics.drawable.LayerDrawable(arrayOf(
                GradientDrawable().apply { setColor(LauncherTheme.focusFill); cornerRadius = r },
                GradientDrawable().apply { setColor(LauncherTheme.accent); cornerRadius = dpf(c, 2f) }
            )).apply {
                setLayerGravity(1, android.view.Gravity.BOTTOM or android.view.Gravity.FILL_HORIZONTAL)
                setLayerHeight(1, dp(c, 4))
            }
            else -> GradientDrawable().apply {
                setColor(LauncherTheme.focusFill)
                setStroke(dp(c, LauncherTheme.FOCUS_STROKE_DP), LauncherTheme.focusBorder)
                cornerRadius = r
            }
        }
        val pressed = GradientDrawable().apply { setColor(LauncherTheme.accent); cornerRadius = r }
        val n = GradientDrawable().apply { setColor(normal); cornerRadius = r }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_selected), focused)
            addState(intArrayOf(), n)
        }
    }

    /** Clips [v] (and everything inside it) to a rounded rectangle. */
    fun roundClip(v: View, radiusPx: Float) {
        v.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        v.clipToOutline = true
    }

    fun rounded(c: Context, color: Int, radiusDp: Int = LauncherTheme.CORNER_DP) =
        GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, radiusDp.toFloat()) }

    fun text(c: Context, s: CharSequence, sp: Float = LauncherTheme.BODY_SP,
             color: Int = LauncherTheme.text, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = sp * LauncherTheme.textScale; setTextColor(color)
        typeface = LauncherTheme.typeface(bold)
    }

    /** A focusable pill button that works well with a D-pad. */
    fun button(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = LauncherTheme.BODY_SP * LauncherTheme.textScale
        setTextColor(LauncherTheme.text)
        typeface = LauncherTheme.typeface(true)
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = focusBg(c, LauncherTheme.surface, 24)
        setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12))
        setOnClickListener { onClick() }
    }

    fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

    fun dialog(c: Context) = AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    fun choose(c: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        dialog(c).setTitle(title).setItems(items.toTypedArray()) { _, w -> onPick(w) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    fun confirm(c: Context, title: String, msg: String, onYes: () -> Unit) {
        dialog(c).setTitle(title).setMessage(msg)
            .setPositiveButton(R.string.ok) { _, _ -> onYes() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    fun prompt(c: Context, title: String, initial: String, hint: String = "",
               numeric: Boolean = false, onOk: (String) -> Unit) {
        val e = EditText(c).apply {
            setText(initial); setSelection(text.length); this.hint = hint
            isSingleLine = true
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
        }
        dialog(c).setTitle(title).setView(e)
            .setPositiveButton(R.string.ok) { _, _ -> onOk(e.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null).show()
        e.requestFocus()
    }
}

/**
 * Base for the list-style screens (Settings, Remote buttons, Cameras, ...):
 * a title, an optional description, and focusable rows with a title,
 * a subtitle and a click action. Subclasses implement [rows].
 */
abstract class RowListActivity : Activity() {

    class Row(
        val title: String,
        val sub: String = "",
        val header: Boolean = false,
        val onLong: (() -> Unit)? = null,
        val onClick: (() -> Unit)? = null
    )

    abstract val screenTitle: String
    open val screenDesc: String? = null
    abstract fun rows(): List<Row>

    protected lateinit var list: ListView
    private var current = listOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherTheme.load(this)
        val pad = Ui.dp(this, 40)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LauncherTheme.background)
            setPadding(pad, Ui.dp(this@RowListActivity, 28), pad, Ui.dp(this@RowListActivity, 20))
        }
        col.addView(Ui.text(this, screenTitle, LauncherTheme.TITLE_SP, bold = true).apply {
            setPadding(0, 0, 0, Ui.dp(this@RowListActivity, 6))
        })
        screenDesc?.let {
            col.addView(Ui.text(this, it, 15f, LauncherTheme.dimText).apply {
                setPadding(0, 0, 0, Ui.dp(this@RowListActivity, 14))
            })
        }
        list = ListView(this).apply {
            divider = null
            dividerHeight = Ui.dp(this@RowListActivity, 6)
            selector = Ui.rounded(this@RowListActivity, Color.TRANSPARENT)
            itemsCanFocus = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        col.addView(list)
        setContentView(col)
        list.setOnItemClickListener { _, _, pos, _ -> current.getOrNull(pos)?.onClick?.invoke() }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val r = current.getOrNull(pos)?.onLong ?: return@setOnItemLongClickListener false
            r(); true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        val pos = if (::list.isInitialized) list.selectedItemPosition else 0
        current = rows()
        list.adapter = object : BaseAdapter() {
            override fun getCount() = current.size
            override fun getItem(p: Int) = current[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun isEnabled(p: Int) = !current[p].header
            override fun areAllItemsEnabled() = false
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val row = current[p]
                val c = this@RowListActivity
                val item = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    if (row.header) {
                        setPadding(Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 2))
                    } else {
                        background = Ui.focusBg(c, LauncherTheme.surface)
                        setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 14))
                    }
                }
                if (row.header) {
                    item.addView(Ui.text(c, row.title.uppercase(), 14f, LauncherTheme.accent, true))
                } else {
                    item.addView(Ui.text(c, row.title, 19f, bold = true))
                    if (row.sub.isNotEmpty())
                        item.addView(Ui.text(c, row.sub, 15f, LauncherTheme.dimText))
                }
                return item
            }
        }
        if (pos in current.indices) list.setSelection(pos)
    }
}
