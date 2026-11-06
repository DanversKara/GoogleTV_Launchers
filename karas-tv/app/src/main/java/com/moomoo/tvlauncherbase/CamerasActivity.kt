package com.moomoo.tvlauncherbase

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * CAMERAS: add, edit, test, reorder and remove camera streams.
 * MotionEye: open the camera's settings in MotionEye → "Video Streaming",
 * and copy the "Streaming URL" (MJPEG) — or "Snapshot URL" for polling.
 */
class CamerasActivity : RowListActivity() {

    override val screenTitle = "Cameras"
    override val screenDesc = "MotionEye / CCTV streams shown at the top of the home screen and in " +
        "floating windows. Select a camera to edit it, long-press for more."

    override fun rows(): List<Row> {
        val cams = BasePrefs.cameras(this)
        val out = mutableListOf<Row>()
        out += Row("+ Add camera", "Paste a MotionEye streaming URL, e.g. http://192.168.1.20:8081/") {
            editCamera(this, null) { refresh() }
        }
        out += Row("Watch all cameras full screen", "${cams.size} camera(s)") {
            startActivity(Intent(this, CameraWallActivity::class.java))
        }
        out += Row("Floating windows over other apps", OverlayManager.statusLine(this)) {
            startActivity(Intent(this, OverlaySettingsActivity::class.java))
        }
        out += Row("Home strip size", stripLabel(this)) { pickStripSize(this) { refresh() } }
        out += Row("Show camera names", if (BasePrefs.camShowLabels(this)) "On" else "Off") {
            BasePrefs.setCamShowLabels(this, !BasePrefs.camShowLabels(this)); refresh()
        }
        if (cams.isNotEmpty()) out += Row("Your cameras", header = true)
        cams.forEachIndexed { i, cam ->
            out += Row(
                (if (cam.onHome) "" else "◌ ") + cam.name,
                Camera.typeLabel(cam.type) + " · " + cam.url,
                onLong = { cameraMenu(i) }
            ) { editCamera(this, i) { refresh() } }
        }
        return out
    }

    private fun cameraMenu(i: Int) {
        val cams = BasePrefs.cameras(this)
        val cam = cams.getOrNull(i) ?: return
        Ui.choose(this, cam.name, listOf(
            "Edit", "Watch full screen", "Show as floating window",
            if (cam.onHome) "Hide from home strip" else "Show on home strip",
            "Move up", "Move down", "Duplicate", "Delete"
        )) { w ->
            when (w) {
                0 -> editCamera(this, i) { refresh() }
                1 -> startActivity(Intent(this, CameraWallActivity::class.java).putExtra("cam", i))
                2 -> OverlayManager.show(this, i)
                3 -> { cams[i] = cam.copy(onHome = !cam.onHome); BasePrefs.setCameras(this, cams) }
                4 -> if (i > 0) { cams.add(i - 1, cams.removeAt(i)); BasePrefs.setCameras(this, cams) }
                5 -> if (i < cams.size - 1) { cams.add(i + 1, cams.removeAt(i)); BasePrefs.setCameras(this, cams) }
                6 -> { cams.add(i + 1, cam.copy(name = cam.name + " copy")); BasePrefs.setCameras(this, cams) }
                7 -> Ui.confirm(this, "Delete ${cam.name}?", "This only removes it from the launcher.") {
                    cams.removeAt(i); BasePrefs.setCameras(this, cams); refresh()
                }
            }
            refresh()
        }
    }

    companion object {
        val STRIP_SIZES = listOf(0 to "Hidden", 140 to "Small", 220 to "Medium", 320 to "Large", 440 to "Extra large")

        fun stripLabel(c: Context): String {
            val h = BasePrefs.camStripHeightDp(c)
            val name = STRIP_SIZES.firstOrNull { it.first == h }?.second ?: "${h}dp"
            return "$name · ${BasePrefs.camStripColumns(c)} per row (on the home screen, press Menu on a camera → Resize for fine control)"
        }

        fun pickStripSize(c: Context, done: () -> Unit) {
            Ui.choose(c, "Camera strip height", STRIP_SIZES.map { it.second }) { w ->
                BasePrefs.setCamStripHeightDp(c, STRIP_SIZES[w].first)
                Ui.choose(c, "Cameras per row", listOf("1", "2", "3", "4")) { n ->
                    BasePrefs.setCamStripColumns(c, n + 1); done()
                }
                done()
            }
        }

        /** Add (index == null) or edit a camera. */
        fun editCamera(c: Context, index: Int?, done: () -> Unit) {
            val cams = BasePrefs.cameras(c)
            val cam = index?.let { cams.getOrNull(it) } ?: Camera("Camera ${cams.size + 1}", "http://")
            val pad = Ui.dp(c, 20)
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
            fun field(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
                col.addView(Ui.text(c, label, 14f, LauncherTheme.dimText))
                return EditText(c).apply { setText(value); isSingleLine = true; inputType = type }.also { col.addView(it) }
            }
            val name = field("Name", cam.name)
            val url = field("Stream URL", cam.url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            val user = field("Username (optional)", cam.user)
            val pass = field("Password (optional)", cam.pass,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val refresh = field("Snapshot refresh (ms, snapshot mode only)", cam.refreshMs.toString(), InputType.TYPE_CLASS_NUMBER)
            val onHome = CheckBox(c).apply { text = "Show on the home strip"; isChecked = cam.onHome }
            col.addView(onHome)
            var type = cam.type
            var rotation = cam.rotation
            col.addView(Ui.button(c, "Type: ${Camera.typeLabel(type)}") {}.also { b ->
                b.setOnClickListener {
                    Ui.choose(c, "Stream type", Camera.TYPES.map { Camera.typeLabel(it) }) { w ->
                        type = Camera.TYPES[w]; b.text = "Type: ${Camera.typeLabel(type)}"
                    }
                }
                (b.layoutParams as? LinearLayout.LayoutParams)?.topMargin = pad / 2
            })
            col.addView(Ui.button(c, "Rotation: $rotation°") {}.also { b ->
                b.setOnClickListener { rotation = (rotation + 90) % 360; b.text = "Rotation: $rotation°" }
            })
            val scroll = ScrollView(c).apply { addView(col) }
            val dlg = Ui.dialog(c)
                .setTitle(if (index == null) "Add camera" else "Edit camera")
                .setView(scroll)
                .setPositiveButton("Save") { _, _ ->
                    val u = url.text.toString().trim()
                    if (u.length < 8) { Ui.toast(c, "Enter a stream URL"); return@setPositiveButton }
                    val nc = Camera(
                        name.text.toString().trim().ifEmpty { "Camera" },
                        if (u.contains("://")) u else "http://$u",
                        type, user.text.toString().trim(), pass.text.toString(),
                        onHome.isChecked, refresh.text.toString().toIntOrNull() ?: 1000, rotation
                    )
                    val list = BasePrefs.cameras(c)
                    if (index != null && index in list.indices) list[index] = nc else list.add(nc)
                    BasePrefs.setCameras(c, list)
                    Ui.toast(c, "Saved ${nc.name}")
                    done()
                }
                .setNegativeButton(R.string.cancel, null)
            if (index != null) dlg.setNeutralButton("Delete") { _, _ ->
                val list = BasePrefs.cameras(c); if (index in list.indices) list.removeAt(index)
                BasePrefs.setCameras(c, list); done()
            }
            dlg.show()
        }
    }
}
