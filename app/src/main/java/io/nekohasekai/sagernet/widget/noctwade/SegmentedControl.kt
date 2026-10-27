package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme

/** A solid segmented control with equal-width tabs; no glass or pressed overlays. */
class SegmentedControl @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NoctwadeTheme.color(context, R.attr.nwSurfaceHigh)
        }
    private val rect = RectF()
    private var position = 0f
    private var animator: ValueAnimator? = null
    var selectedIndex = 0
        private set

    var onSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        setPadding(
            (4 * density).toInt(),
            (4 * density).toInt(),
            (4 * density).toInt(),
            (4 * density).toInt(),
        )
        background =
            GradientDrawable().apply {
                setColor(NoctwadeTheme.surface(context))
                cornerRadius = 100 * density
            }
        for ((i, title) in
            TABS
                .withIndex()) addView(
            TextView(context).apply {
                setText(title)
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", 0)
                isClickable = true
                isFocusable = true
                background = null
                setOnClickListener {
                    if (selectedIndex != i) {
                        select(i)
                        onSelected?.invoke(i)
                    }
                }
            },
            LayoutParams(0, -1, 1f),
        )
        select(0, false)
    }

    fun select(index: Int, animate: Boolean = true) {
        if (index !in 0 until childCount) return
        selectedIndex = index
        animator?.cancel()
        for (i in 0 until childCount) (getChildAt(i) as TextView).apply {
            isSelected = i == index
            setTextColor(
                if (i == index) NoctwadeTheme.text(context) else NoctwadeTheme.muted(context)
            )
        }
        if (animate && isLaidOut && NoctwadeMotion.enabled(context))
            animator =
                ValueAnimator.ofFloat(position, index.toFloat()).apply {
                    duration = 260
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        position = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        else {
            position = index.toFloat()
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (childCount >= 2) {
            val a = getChildAt(0)
            val b = getChildAt(1)
            val left = a.left + (b.left - a.left) * position
            rect.set(
                left.toFloat(),
                paddingTop.toFloat(),
                left + a.width,
                height - paddingBottom.toFloat(),
            )
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
        }
        super.dispatchDraw(canvas)
    }

    private companion object {
        val TABS =
            listOf(R.string.nw_routing_tab_rules, R.string.nw_routing_tab_apps, R.string.nw_routing_tab_dns)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        position = selectedIndex.toFloat()
        super.onDetachedFromWindow()
    }
}
