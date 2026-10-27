package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity

/** One touch target: clockwise reload -> spinner -> clickable latency. */
class PingButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private val arrow =
        ImageView(context).apply {
            setImageResource(R.drawable.nw_refresh_clockwise)
            imageTintList =
                android.content.res.ColorStateList.valueOf(
                    io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.accent(context)
                )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private val label =
        TextView(context).apply {
            // Number on top, small unit below: always fits the square 44dp target,
            // so the text never runs into the signal bars next to it.
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            setLineSpacing(0f, 0.9f)
            maxLines = 2
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private var loading = false
    private var numeric = false
    private var spinner: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        background = null
        isClickable = true
        isFocusable = true
        addView(arrow, LayoutParams(dp(26), dp(26), Gravity.CENTER))
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        label.visibility = View.INVISIBLE
    }

    fun render(latency: Int, running: Boolean) {
        val showNumber = !running && latency != 0
        val value =
            when {
                latency > 0 -> context.getString(R.string.nw_ping, latency)
                latency == ProxyEntity.LATENCY_CONNECT_ONLY -> "OK"
                else -> "—"
            }
        label.text = if (latency > 0) stackedLatency(latency) else value
        label.setTextColor(
            ContextCompat.getColor(
                context,
                if (latency > 0 || latency == ProxyEntity.LATENCY_CONNECT_ONLY) R.color.nw_green
                else R.color.nw_error,
            )
        )
        contentDescription =
            context.getString(
                if (running) R.string.nw_ping_preparing else R.string.nw_test_server
            ) + if (showNumber) ", $value" else ""
        isEnabled = !running
        val changed = numeric != showNumber
        numeric = showNumber
        if (changed) {
            arrow.animate().cancel()
            label.animate().cancel()
            arrow.visibility = if (showNumber) View.INVISIBLE else View.VISIBLE
            label.visibility = if (showNumber) View.VISIBLE else View.INVISIBLE
            if (NoctwadeMotion.enabled(context) && isShown) {
                val incoming = if (showNumber) label else arrow
                incoming.alpha = 0f
                incoming.scaleX = .85f
                incoming.scaleY = .85f
                incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
            } else {
                arrow.alpha = 1f
                label.alpha = 1f
            }
        }
        if (loading != running) {
            loading = running
            updateSpinner()
        }
    }

    private fun stackedLatency(latency: Int): CharSequence {
        val number = latency.toString()
        val unit = context.getString(R.string.nw_ping_unit)
        return android.text.SpannableString("$number\n$unit").apply {
            setSpan(
                android.text.style.RelativeSizeSpan(0.72f),
                number.length + 1,
                length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun updateSpinner() {
        spinner?.cancel()
        spinner = null
        arrow.rotation = 0f
        if (
            !loading ||
                !isAttachedToWindow ||
                windowVisibility != View.VISIBLE ||
                !NoctwadeMotion.enabled(context)
        )
            return
        spinner =
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { arrow.rotation = it.animatedValue as Float }
                start()
            }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSpinner()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateSpinner()
    }

    override fun onDetachedFromWindow() {
        spinner?.cancel()
        spinner = null
        arrow.animate().cancel()
        label.animate().cancel()
        arrow.alpha = 1f
        label.alpha = 1f
        arrow.scaleX = 1f
        arrow.scaleY = 1f
        label.scaleX = 1f
        label.scaleY = 1f
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
    }
}
