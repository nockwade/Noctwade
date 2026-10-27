package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView
import io.nekohasekai.sagernet.R

/** Reclaims decorative space first; normal portrait windows fit without any scroll range. */
class AdaptiveHomeScrollView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : NestedScrollView(context, attrs) {
    private fun px(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun height(id: Int, dp: Int, square: Boolean = false) {
        findViewById<View>(id)?.let {
            it.layoutParams.height = px(dp)
            if (square) it.layoutParams.width = px(dp)
            it.forceLayout()
        }
    }

    private fun dirty() {
        findViewById<View>(R.id.home_content)?.forceLayout()
        getChildAt(0)?.forceLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        height(R.id.home_hero_space, 48)
        height(R.id.home_power, 208, true)
        height(R.id.home_header, 80)
        dirty()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount == 0 || measuredHeight == 0) return
        var excess =
            ((getChildAt(0).measuredHeight - (measuredHeight - paddingTop - paddingBottom))
                    .coerceAtLeast(0) / resources.displayMetrics.density)
                .toInt() + 1
        if (excess <= 1) return
        val space = excess.coerceAtMost(48)
        excess -= space
        val power = excess.coerceAtMost(72)
        excess -= power
        val header = excess.coerceAtMost(16)
        height(R.id.home_hero_space, 48 - space)
        height(R.id.home_power, 208 - power, true)
        height(R.id.home_header, 80 - header)
        dirty()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
