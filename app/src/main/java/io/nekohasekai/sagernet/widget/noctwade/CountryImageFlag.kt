package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import java.util.Locale

/** Local square image resources, independent of the device's emoji font. */
class CountryFlagView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val image = ImageView(context)
    private var current: String? = null

    private val plate = GradientDrawable().apply { setColor(NoctwadeTheme.surface(context)) }

    /** Same shape at every size: a fixed radius looked round on Home and square in the list. */
    private fun radius() = minOf(width, height) * CORNER_FRACTION

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = plate
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius())
                }
            }
        clipToOutline = true
        addView(image, LayoutParams(-1, -1))
        setCountry("")
    }

    fun setCountry(code: String) {
        val key = code.trim().uppercase(Locale.ROOT)
        if (key == current) return
        current = key
        val resource = CountryFlagImages.images[key]
        val padding = if (resource == null) (10 * resources.displayMetrics.density).toInt() else 0
        image.setPadding(padding, padding, padding, padding)
        image.scaleType =
            if (resource == null) ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.FIT_XY
        image.imageTintList =
            if (resource == null) ColorStateList.valueOf(NoctwadeTheme.muted(context)) else null
        image.setImageResource(resource ?: R.drawable.nw_location)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        plate.cornerRadius = radius()
        invalidateOutline()
    }

    private companion object {
        const val CORNER_FRACTION = .32f
    }
}
