package io.nekohasekai.sagernet.ui.noctwade

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.nekohasekai.sagernet.R

/**
 * A full colour picker: the saturation / brightness plane of the chosen hue, a hue bar, red / green / blue sliders and
 * a hex field. Every control edits the same colour; [onChange] gets it (opaque) on each change.
 */
internal class ColorEditor(context: Context, color: Int, private val onChange: (Int) -> Unit) : LinearLayout(context) {
    private val hsv = FloatArray(3)
    private var rgb = Color.BLACK
    private var updating = false
    private val density = context.resources.displayMetrics.density

    private val plane = Plane(context)
    private val hueBar = HueBar(context)
    private val sliders = ArrayList<SeekBar>()
    private val values = ArrayList<TextView>()
    private val hex: EditText

    /** The colour being edited, opaque. */
    val color: Int
        get() = rgb

    init {
        orientation = VERTICAL
        addView(plane, LayoutParams(-1, dp(150)))
        addView(hueBar, LayoutParams(-1, dp(30)).apply { topMargin = dp(10) })
        for ((index, label) in listOf("R", "G", "B").withIndex()) {
            val tint = intArrayOf(0xFFE5484D.toInt(), 0xFF30A46C.toInt(), 0xFF3E63DD.toInt())[index]
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(
                TextView(context).apply {
                    text = label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(NoctwadeTheme.muted(context))
                },
                LayoutParams(dp(18), -2),
            )
            val slider =
                SeekBar(context).apply {
                    max = 255
                    progressTintList = ColorStateList.valueOf(tint)
                    thumbTintList = ColorStateList.valueOf(tint)
                    setPadding(dp(14), 0, dp(14), 0)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                                if (updating || !fromUser) return
                                val r = if (index == 0) progress else Color.red(rgb)
                                val g = if (index == 1) progress else Color.green(rgb)
                                val b = if (index == 2) progress else Color.blue(rgb)
                                applyRgb(Color.rgb(r, g, b), false)
                            }

                            override fun onStartTrackingTouch(bar: SeekBar) {
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }

                            override fun onStopTrackingTouch(bar: SeekBar) = Unit
                        }
                    )
                }
            sliders += slider
            row.addView(slider, LayoutParams(0, dp(40), 1f))
            val value =
                TextView(context).apply {
                    textSize = 13f
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    setTextColor(NoctwadeTheme.text(context))
                }
            values += value
            row.addView(value, LayoutParams(dp(34), -2))
            addView(row, LayoutParams(-1, -2).apply { topMargin = if (index == 0) dp(6) else 0 })
        }
        hex =
            EditText(context).apply {
                hint = "#RRGGBB"
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                filters = arrayOf(InputFilter.LengthFilter(7))
                textSize = 15f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(NoctwadeTheme.text(context))
                setHintTextColor(NoctwadeTheme.muted(context))
                setBackgroundResource(R.drawable.nw_input_field)
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                        override fun afterTextChanged(s: Editable?) {
                            if (updating) return
                            parseHex(s?.toString().orEmpty())?.let { applyRgb(it, true) }
                        }
                    }
                )
            }
        addView(hex, LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setColor(color)
    }

    private fun dp(n: Int) = (n * density).toInt()

    /** Moves the editor to [color] without telling [onChange]. */
    fun setColor(color: Int) {
        rgb = color or Color.BLACK
        Color.colorToHSV(rgb, hsv)
        sync(false)
    }

    private fun applyHsv() {
        rgb = Color.HSVToColor(hsv)
        sync(false)
        onChange(rgb)
    }

    private fun applyRgb(value: Int, fromHex: Boolean) {
        val hue = hsv[0]
        rgb = value or Color.BLACK
        Color.colorToHSV(rgb, hsv)
        // Grey and black have no hue: keep the one the plane showed instead of snapping to red.
        if (hsv[1] == 0f || hsv[2] == 0f) hsv[0] = hue
        sync(fromHex)
        onChange(rgb)
    }

    private fun sync(keepHexText: Boolean) {
        updating = true
        sliders[0].progress = Color.red(rgb)
        sliders[1].progress = Color.green(rgb)
        sliders[2].progress = Color.blue(rgb)
        values[0].text = Color.red(rgb).toString()
        values[1].text = Color.green(rgb).toString()
        values[2].text = Color.blue(rgb).toString()
        if (!keepHexText) hex.setText(String.format("#%06X", rgb and 0xFFFFFF))
        plane.invalidate()
        hueBar.invalidate()
        updating = false
    }

    /** "#RRGGBB" or "RRGGBB"; null while the text is not a whole colour yet. */
    private fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 6 || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return Color.rgb(digits.substring(0, 2).toInt(16), digits.substring(2, 4).toInt(16), digits.substring(4, 6).toInt(16))
    }

    /** Saturation to the right, brightness upwards, for the current hue. */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class Plane(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val clip = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            clip.reset()
            clip.addRoundRect(RectF(0f, 0f, w, h), 14 * density, 14 * density, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clip)
            paint.shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, Color.BLACK, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            canvas.restoreToCount(save)
            val x = hsv[1] * w
            val y = (1f - hsv[2]) * h
            ring.strokeWidth = 3f * density
            ring.color = Color.WHITE
            canvas.drawCircle(x, y, 10 * density, ring)
            ring.strokeWidth = 1f * density
            ring.color = 0x66000000
            canvas.drawCircle(x, y, 12 * density, ring)
            paint.color = rgb
            canvas.drawCircle(x, y, 7.5f * density, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                hsv[1] = (event.x / width).coerceIn(0f, 1f)
                hsv[2] = 1f - (event.y / height).coerceIn(0f, 1f)
                applyHsv()
            }
            return true
        }
    }

    /** The rainbow of hues with a round thumb. */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class HueBar(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val inset = h / 2f
            val track = RectF(inset, h * .28f, w - inset, h * .72f)
            paint.shader =
                LinearGradient(
                    track.left,
                    0f,
                    track.right,
                    0f,
                    intArrayOf(0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRoundRect(track, track.height() / 2f, track.height() / 2f, paint)
            paint.shader = null
            val x = track.left + hsv[0] / 360f * track.width()
            paint.color = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
            canvas.drawCircle(x, h / 2f, inset - 3 * density, paint)
            thumb.strokeWidth = 3f * density
            thumb.color = Color.WHITE
            canvas.drawCircle(x, h / 2f, inset - 3 * density, thumb)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                val inset = height / 2f
                hsv[0] = ((event.x - inset) / (width - 2 * inset)).coerceIn(0f, 1f) * 360f
                if (hsv[1] == 0f) hsv[1] = 1f
                if (hsv[2] == 0f) hsv[2] = 1f
                applyHsv()
            }
            return true
        }
    }
}
