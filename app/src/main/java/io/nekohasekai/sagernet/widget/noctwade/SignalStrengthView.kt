package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme

/**
 * Compact iPhone-style quality indicator: three rising bars.
 *
 * Deliberately outside the ping button's touch target and not interactive.
 */
class SignalStrengthView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val active = NoctwadeTheme.accent(context)
    private val inactive = NoctwadeTheme.stroke(context)
    private var strength = 0

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun render(latency: Int, running: Boolean) {
        strength = levelFor(latency, running)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = BAR_WIDTH_DP * density
        val gap = BAR_GAP_DP * density
        val radius = barWidth / 2f
        val totalWidth = BARS * barWidth + (BARS - 1) * gap
        val tallest = BAR_HEIGHTS_DP.last() * density
        val left = (width - totalWidth) / 2f
        val bottom = (height + tallest) / 2f
        for (i in 0 until BARS) {
            paint.color = if (i < strength) active else inactive
            val x = left + i * (barWidth + gap)
            canvas.drawRoundRect(
                x,
                bottom - BAR_HEIGHTS_DP[i] * density,
                x + barWidth,
                bottom,
                radius,
                radius,
                paint,
            )
        }
    }

    companion object {
        const val BARS = 3
        private const val BAR_WIDTH_DP = 3f
        private const val BAR_GAP_DP = 2f
        private val BAR_HEIGHTS_DP = floatArrayOf(4.5f, 7.5f, 10.5f)

        /** 0 = unknown/error, 1..3 = bad..good. */
        fun levelFor(latency: Int, running: Boolean): Int =
            when {
                running || latency <= 0 -> 0
                latency < 150 -> 3
                latency < 300 -> 2
                else -> 1
            }
    }
}
