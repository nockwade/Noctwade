package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme

/** A translucent solid capsule moves independently of touch; no stroke or pressed overlay. */
class NoctwadeNavigation @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private val selectionFill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color =
                androidx.core.graphics.ColorUtils.setAlphaComponent(
                    NoctwadeTheme.accent(context),
                    46,
                )
        }
    private val capsule = RectF()
    private var position = 0f
    private var selectionAnimator: ValueAnimator? = null
    private val labels = ArrayList<TextView>()
    private val icons = ArrayList<ImageView>()
    var selectedTab = 0
        private set

    var onTabSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = NoctwadeTheme.glassSurface(context, dp(30).toFloat(), .92f)
        elevation = 0f
        val names = intArrayOf(R.string.nw_home, R.string.nw_servers, R.string.nw_menu)
        val drawables =
            intArrayOf(R.drawable.nw_home, R.drawable.nw_servers, R.drawable.nw_menu_grid)
        for (index in names.indices) {
            val tab =
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    background = null
                    contentDescription = context.getString(names[index])
                    setOnClickListener { onTabSelected?.invoke(index) }
                }
            ViewCompat.setAccessibilityDelegate(
                tab,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Button::class.java.name
                    }
                },
            )
            val icon =
                ImageView(context).apply {
                    setImageResource(drawables[index])
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            val label =
                TextView(context).apply {
                    text = context.getString(names[index])
                    textSize = 11f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    typeface = Typeface.create("sans-serif-medium", 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            tab.addView(icon, LayoutParams(dp(24), dp(24)))
            tab.addView(label, LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addView(tab, LayoutParams(0, -1, 1f))
            icons.add(icon)
            labels.add(label)
        }
        select(0, false)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (childCount > 0) {
            val first = getChildAt(0)
            val last = getChildAt(2)
            val left = first.left + (last.left - first.left) * (position / 2f)
            capsule.set(
                left + dp(2),
                paddingTop + dp(1).toFloat(),
                left + first.width - dp(2),
                height - paddingBottom - dp(1).toFloat(),
            )
            canvas.drawRoundRect(capsule, dp(26).toFloat(), dp(26).toFloat(), selectionFill)
        }
        super.dispatchDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        selectionAnimator?.cancel()
        selectionAnimator = null
        position = selectedTab.toFloat()
        super.onDetachedFromWindow()
    }

    fun select(index: Int, animate: Boolean = true) {
        if (index !in 0..2) return
        val changed = selectedTab != index
        selectedTab = index
        if (changed) {
            selectionAnimator?.cancel()
            if (animate && NoctwadeMotion.enabled(context) && isLaidOut) {
                selectionAnimator =
                    ValueAnimator.ofFloat(position, index.toFloat()).apply {
                        duration = 280L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            position = it.animatedValue as Float
                            invalidate()
                        }
                        start()
                    }
            } else {
                position = index.toFloat()
                invalidate()
            }
        }
        for (i in labels.indices) {
            val color =
                if (i == index) NoctwadeTheme.accent(context) else NoctwadeTheme.muted(context)
            labels[i].setTextColor(color)
            icons[i].imageTintList = ColorStateList.valueOf(color)
            getChildAt(i).isSelected = i == index
        }
    }
}
