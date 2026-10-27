package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme

/**
 * The connect button's glass in miniature: a faint tinted disc you can see through, a pool of soft
 * accent light inside and a light rim that is brighter at the top.
 */
internal class GlassCircleDrawable(context: Context) : Drawable() {
    private val accent = NoctwadeTheme.accent(context)
    private val secondary = NoctwadeTheme.secondaryAccent(context)
    private val surface = NoctwadeTheme.surface(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stroke = context.resources.displayMetrics.density * 1.1f

    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        fill.shader =
            LinearGradient(
                bounds.left + w * .25f,
                bounds.top.toFloat(),
                bounds.left + w * .75f,
                bounds.bottom.toFloat(),
                intArrayOf(
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(surface, accent, .30f), 96),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(surface, accent, .08f), 78),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(surface, secondary, .20f), 96),
                ),
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP,
            )
        glow.shader =
            RadialGradient(
                bounds.exactCenterX(),
                bounds.top + h * .42f,
                w * .62f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(accent, 70),
                    ColorUtils.setAlphaComponent(accent, 18),
                    ColorUtils.setAlphaComponent(accent, 0),
                ),
                floatArrayOf(0f, .65f, 1f),
                Shader.TileMode.CLAMP,
            )
        rim.strokeWidth = stroke
        rim.shader =
            LinearGradient(
                bounds.exactCenterX(),
                bounds.top.toFloat(),
                bounds.exactCenterX(),
                bounds.bottom.toFloat(),
                intArrayOf(0x66FFFFFF, 0x14FFFFFF, 0x05FFFFFF),
                floatArrayOf(0f, .5f, 1f),
                Shader.TileMode.CLAMP,
            )
    }

    override fun draw(canvas: Canvas) {
        val r = minOf(bounds.width(), bounds.height()) / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, glow)
        canvas.drawCircle(cx, cy, r - stroke / 2f, rim)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Round icon button with the transparent glass look of the connect button. */
class GlassIconButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageButton(context, attrs) {
    init {
        val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        background =
            RippleDrawable(
                ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(NoctwadeTheme.accent(context), 60)
                ),
                GlassCircleDrawable(context),
                mask,
            )
    }
}
