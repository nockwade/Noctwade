package io.nekohasekai.sagernet.ui.noctwade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion

/** Colour pickers for the appearance settings: theme colours and custom gradients. */
internal object AppearancePickers {

    /** The swatch palette: one column per hue, one row per tone (pastel, vivid, deep). */
    @SuppressLint("ViewConstructor")
    class SwatchGrid(context: Context, private val selected: () -> List<Int>, private val tap: (Int) -> Unit) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * context.resources.displayMetrics.density
            }
        private val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
        private val text = NoctwadeTheme.text(context)

        init {
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        private fun cell() = width / Swatches.HUES.toFloat()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, (w / Swatches.HUES.toFloat() * Swatches.TONES).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val c = cell()
            val chosen = selected()
            label.textSize = c * .36f
            for (i in Swatches.colors.indices) {
                val cx = (i / Swatches.TONES + .5f) * c
                val cy = (i % Swatches.TONES + .5f) * c
                paint.color = Swatches.colors[i]
                canvas.drawCircle(cx, cy, c * .36f, paint)
                val order = chosen.indexOf(i)
                if (order >= 0) {
                    ring.color = text
                    canvas.drawCircle(cx, cy, c * .46f, ring)
                    if (chosen.size > 1) {
                        label.color = if (ColorUtils.calculateLuminance(Swatches.colors[i]) > .5) Color.BLACK else Color.WHITE
                        canvas.drawText("${order + 1}", cx, cy - (label.ascent() + label.descent()) / 2f, label)
                    }
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val c = cell()
                val col = (event.x / c).toInt().coerceIn(0, Swatches.HUES - 1)
                val row = (event.y / c).toInt().coerceIn(0, Swatches.TONES - 1)
                tap(col * Swatches.TONES + row)
                invalidate()
            }
            return true
        }
    }

    private fun dp(context: Context, n: Int) = (n * context.resources.displayMetrics.density).toInt()

    private fun card(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 12))
            background =
                GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(NoctwadeTheme.gradientStart(context), NoctwadeTheme.gradientEnd(context)),
                    )
                    .apply {
                        cornerRadius = dp(context, 28).toFloat()
                        setStroke(dp(context, 1), NoctwadeTheme.stroke(context))
                    }
        }

    private fun title(context: Context, text: Int) =
        TextView(context).apply {
            setText(text)
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(NoctwadeTheme.text(context))
            setPadding(0, 0, 0, dp(context, 8))
        }

    private fun caption(context: Context, text: Int) =
        TextView(context).apply {
            setText(text)
            textSize = 13f
            setTextColor(NoctwadeTheme.muted(context))
            setPadding(0, dp(context, 10), 0, dp(context, 6))
        }

    private fun preview(context: Context) =
        View(context).apply { minimumHeight = dp(context, 36) }

    private fun paintPreview(view: View, colors: IntArray, angle: Int) {
        view.background =
            GradientDrawable(NoctwadeTheme.orientation(angle), if (colors.size == 1) intArrayOf(colors[0], colors[0]) else colors).apply {
                cornerRadius = dp(view.context, 14).toFloat()
                setDither(true)
            }
    }

    private fun buttons(context: Context, cancel: () -> Unit, done: () -> Unit) =
        LinearLayout(context).apply {
            gravity = Gravity.END
            setPadding(0, dp(context, 12), 0, 0)
            fun button(text: Int, color: Int, action: () -> Unit) =
                TextView(context).apply {
                    setText(text)
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(color)
                    setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { action() }
                }
            addView(button(android.R.string.cancel, NoctwadeTheme.muted(context), cancel))
            addView(button(R.string.nw_done, NoctwadeTheme.accent(context), done))
        }

    /** A smaller card than the pickers' for questions and notes: tighter padding and corners. */
    internal fun compactCard(context: Context): LinearLayout =
        card(context).apply {
            setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 10))
            (background as GradientDrawable).cornerRadius = dp(context, 20).toFloat()
        }

    /** A short question with Cancel and an action; the card is at most 320 dp wide. */
    fun confirm(context: Context, message: String, yes: String, onYes: () -> Unit) {
        val card = compactCard(context)
        lateinit var dialog: AlertDialog
        card.addView(
            TextView(context).apply {
                text = message
                textSize = 15f
                setLineSpacing(0f, 1.15f)
                setTextColor(NoctwadeTheme.text(context))
            }
        )
        val row = LinearLayout(context).apply { gravity = Gravity.END; setPadding(0, dp(context, 14), 0, 0) }
        fun action(label: CharSequence, color: Int, click: () -> Unit) =
            row.addView(
                TextView(context).apply {
                    text = label
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(color)
                    setPadding(dp(context, 14), dp(context, 8), dp(context, 6), dp(context, 8))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { click() }
                }
            )
        action(context.getString(android.R.string.cancel), NoctwadeTheme.muted(context)) { dialog.dismiss() }
        action(yes, NoctwadeTheme.accent(context)) {
            dialog.dismiss()
            onYes()
        }
        card.addView(row)
        dialog = show(context, card, 320)
    }

    internal fun showCard(context: Context, card: LinearLayout, maxDp: Int = 440): AlertDialog = show(context, card, maxDp)

    private fun show(context: Context, card: View, maxDp: Int = 440): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Noctwade_Dialog_Soft).setView(card).create()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(.32f)
            setWindowAnimations(if (NoctwadeMotion.enabled(context)) R.style.NwSoftDialogAnimation else 0)
        }
        dialog.show()
        dialog.window?.setLayout(
            minOf(context.resources.displayMetrics.widthPixels - dp(context, 32), dp(context, maxDp)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }

    /** Main and second theme colour. */
    fun palette(context: Context, accent: Int, second: Int, done: (Int, Int) -> Unit) {
        var a = accent
        var s = second
        val card = card(context)
        val bar = preview(context)
        fun refresh() = paintPreview(bar, intArrayOf(Swatches.colors[a], Swatches.colors[s]), 0)
        card.addView(title(context, R.string.nw_colors_custom))
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(context, 36)))
        card.addView(caption(context, R.string.nw_colors_main))
        val main = SwatchGrid(context, { listOf(a) }) { a = it; refresh() }
        card.addView(main, LinearLayout.LayoutParams(-1, -2))
        card.addView(caption(context, R.string.nw_colors_second))
        val second = SwatchGrid(context, { listOf(s) }) { s = it; refresh() }
        card.addView(second, LinearLayout.LayoutParams(-1, -2))
        refresh()
        lateinit var dialog: AlertDialog
        card.addView(buttons(context, { dialog.dismiss() }) {
            dialog.dismiss()
            done(a, s)
        })
        dialog = show(context, card)
    }

    /**
     * Up to four colours of any RGB value, and a direction. The chosen colour is edited with the full picker (plane,
     * hue bar, R/G/B, hex) or taken from the ready swatches; "+" adds a colour, a long press on one removes it.
     */
    fun gradient(context: Context, colors: IntArray, angle: Int, done: (IntArray, Int) -> Unit) {
        val stops = colors.map { it or Color.BLACK }.take(4).toMutableList().ifEmpty { mutableListOf(Swatches.colors[0]) }
        var current = 0
        var direction = angle
        val card = card(context)
        val bar = preview(context)
        val directions = ArrayList<TextView>()
        val chips = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val remove =
            TextView(context).apply {
                setText(R.string.nw_gradient_remove_stop)
                textSize = 13f
                setTextColor(NoctwadeTheme.accent(context))
                setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 4))
                isClickable = true
                isFocusable = true
            }
        lateinit var editor: ColorEditor
        lateinit var swatches: SwatchGrid
        lateinit var refresh: () -> Unit

        fun chip(color: Int, on: Boolean, plus: Boolean, click: () -> Unit, longClick: (() -> Unit)?): View =
            View(context).apply {
                val ring = if (on) NoctwadeTheme.text(context) else ColorUtils.setAlphaComponent(NoctwadeTheme.text(context), 60)
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (plus) ColorUtils.setAlphaComponent(NoctwadeTheme.accent(context), 30) else color)
                        setStroke(dp(context, if (on) 3 else 1), if (plus) NoctwadeTheme.accent(context) else ring)
                    }
                contentDescription = if (plus) context.getString(R.string.nw_colors_add) else String.format("#%06X", color and 0xFFFFFF)
                isClickable = true
                isFocusable = true
                setOnClickListener { click() }
                longClick?.let {
                    setOnLongClickListener { _ ->
                        it()
                        true
                    }
                }
            }

        fun select(index: Int) {
            current = index.coerceIn(0, stops.lastIndex)
            editor.setColor(stops[current])
            refresh()
        }

        refresh = {
            paintPreview(bar, (if (stops.size == 1) listOf(stops[0], stops[0]) else stops).toIntArray(), direction)
            chips.removeAllViews()
            stops.forEachIndexed { i, c ->
                chips.addView(
                    chip(c, i == current, false, { select(i) }, { if (stops.size > 1) { stops.removeAt(i); select(minOf(current, stops.lastIndex)) } }),
                    LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply { marginEnd = dp(context, 10) },
                )
            }
            if (stops.size < 4)
                chips.addView(
                    chip(0, false, true, { stops.add(stops[current]); select(stops.lastIndex) }, null),
                    LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)),
                )
            remove.visibility = if (stops.size > 1) View.VISIBLE else View.GONE
            swatches.invalidate()
            directions.forEach { chip ->
                val on = chip.tag == direction
                chip.setTextColor(if (on) Color.WHITE else NoctwadeTheme.text(context))
                chip.background =
                    GradientDrawable().apply {
                        cornerRadius = dp(context, 100).toFloat()
                        setColor(if (on) NoctwadeTheme.accent(context) else ColorUtils.setAlphaComponent(NoctwadeTheme.muted(context), 40))
                    }
            }
        }
        editor = ColorEditor(context, stops[0]) { color ->
            stops[current] = color
            refresh()
        }
        swatches =
            SwatchGrid(context, { Swatches.colors.indices.filter { Swatches.colors[it] == stops[current] } }) { index ->
                stops[current] = Swatches.colors[index]
                editor.setColor(stops[current])
                refresh()
            }
        remove.setOnClickListener {
            if (stops.size > 1) {
                stops.removeAt(current)
                select(minOf(current, stops.lastIndex))
            }
        }

        card.addView(title(context, R.string.nw_bg_custom_gradient))
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(context, 56)))
        card.addView(caption(context, R.string.nw_bg_gradient_hint))
        card.addView(chips, LinearLayout.LayoutParams(-1, -2))
        card.addView(remove, LinearLayout.LayoutParams(-2, -2))
        card.addView(editor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) })
        card.addView(caption(context, R.string.nw_gradient_ready))
        card.addView(swatches, LinearLayout.LayoutParams(-1, -2))
        card.addView(caption(context, R.string.nw_bg_direction))
        val row = LinearLayout(context)
        // Arrows show where the gradient runs to.
        for ((value, arrow) in listOf(90 to "↓", 135 to "↘", 0 to "→", 45 to "↗")) {
            val chip =
                TextView(context).apply {
                    tag = value
                    text = arrow
                    textSize = 18f
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        direction = value
                        refresh()
                    }
                }
            directions.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(0, dp(context, 40), 1f).apply { marginEnd = dp(context, 6) })
        }
        card.addView(row, LinearLayout.LayoutParams(-1, -2))
        refresh()
        lateinit var dialog: AlertDialog
        card.addView(buttons(context, { dialog.dismiss() }) {
            dialog.dismiss()
            done((if (stops.size == 1) stops + stops else stops).toIntArray(), direction)
        })
        // The picker is tall: it scrolls inside the card on short screens.
        val scroll =
            android.widget.ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                // The card's gradient and rounded corners belong to the dialog, not to the scrolling content.
                background = card.background
                card.background = null
                clipToOutline = true
                outlineProvider =
                    object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, dp(context, 28).toFloat())
                        }
                    }
                addView(card)
            }
        dialog = show(context, scroll, 440)
    }
}
