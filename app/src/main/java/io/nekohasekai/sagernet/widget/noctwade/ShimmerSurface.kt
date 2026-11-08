package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Frosted glass with a slow, soft colour drift: an almost opaque glass gradient (the background
 * barely shows through), two large blurred glows in the theme's accent colours wandering across
 * it, a gentle top highlight and a hairline edge. Every gradient is dithered, so there is no
 * banding. Moves only while visible, with animations on and outside economy mode.
 */
class ShimmerSurface(context: Context, private val radius: Float) : Drawable() {
    private val app = context.applicationContext
    private val density = context.resources.displayMetrics.density
    private val light = NoctwadeTheme.light(context)
    private val accent = NoctwadeTheme.accent(context)
    private val secondary = NoctwadeTheme.secondaryAccent(context)
    private val start = NoctwadeTheme.gradientStart(context)
    private val end = NoctwadeTheme.gradientEnd(context)
    private val stroke = NoctwadeTheme.stroke(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val glowA = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val glowB = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val edge =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    private val rect = RectF()
    private val matrix = Matrix()
    private var phase = .2f
    private var animator: ValueAnimator? = null

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        rect.inset(edge.strokeWidth / 2f, edge.strokeWidth / 2f)
        val l = bounds.left.toFloat()
        val t = bounds.top.toFloat()
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        val glass = if (light) 238 else 226
        fill.shader =
            LinearGradient(
                l,
                t,
                l + w,
                t + h,
                intArrayOf(
                    ColorUtils.setAlphaComponent(start, glass),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(start, end, .5f), glass),
                    ColorUtils.setAlphaComponent(end, glass),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        val r = maxOf(w, h) * .75f
        fun glow(color: Int, alpha: Float) =
            RadialGradient(
                0f,
                0f,
                r,
                intArrayOf(
                    ColorUtils.setAlphaComponent(color, (alpha * 255).toInt()),
                    ColorUtils.setAlphaComponent(color, (alpha * .45f * 255).toInt()),
                    ColorUtils.setAlphaComponent(color, 0),
                ),
                floatArrayOf(0f, .45f, 1f),
                Shader.TileMode.CLAMP,
            )
        glowA.shader = glow(accent, if (light) .16f else .20f)
        glowB.shader = glow(secondary, if (light) .14f else .16f)
        highlight.shader =
            LinearGradient(
                l,
                t,
                l,
                t + h * .55f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(Color.WHITE, if (light) 110 else 18),
                    ColorUtils.setAlphaComponent(Color.WHITE, 0),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        edge.shader =
            LinearGradient(
                l,
                t,
                l + w,
                t + h,
                // Light: a visible tinted edge (a white one vanished on the light page); dark: a faint accent glint.
                if (light)
                    intArrayOf(
                        ColorUtils.blendARGB(stroke, Color.WHITE, .15f),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(stroke, accent, .35f), 220),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(stroke, secondary, .35f), 220),
                    )
                else
                    intArrayOf(
                        ColorUtils.setAlphaComponent(accent, 70),
                        ColorUtils.setAlphaComponent(accent, 40),
                        ColorUtils.setAlphaComponent(secondary, 46),
                    ),
                null,
                Shader.TileMode.CLAMP,
            )
        place()
    }

    /** The glows travel slow, different ellipses, so the colours keep shifting without a pattern. */
    private fun place() {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val a = phase * 2f * PI.toFloat()
        matrix.setTranslate(bounds.left + w * (.25f + .30f * cos(a)), bounds.top + h * (.15f + .35f * sin(a)))
        glowA.shader?.setLocalMatrix(matrix)
        matrix.setTranslate(bounds.left + w * (.75f - .30f * cos(a + 1.7f)), bounds.top + h * (.85f - .35f * sin(2f * a + .6f)))
        glowB.shader?.setLocalMatrix(matrix)
    }

    /** Whole-surface opacity (the card can fade out as one piece). */
    private var surfaceAlpha = 255

    override fun draw(canvas: Canvas) {
        if (surfaceAlpha <= 0) return
        val layer = if (surfaceAlpha < 255) canvas.saveLayerAlpha(rect, surfaceAlpha) else -1
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, glowA)
        canvas.drawRoundRect(rect, radius, radius, glowB)
        canvas.drawRoundRect(rect, radius, radius, highlight)
        canvas.drawRoundRect(rect, radius, radius, edge)
        if (layer >= 0) canvas.restoreToCount(layer)
    }

    override fun getAlpha() = surfaceAlpha

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) start() else stop()
        return changed
    }

    private fun start() {
        if (animator != null || !NoctwadeMotion.enabled(app) || NoctwadeEco.active(app)) return
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 16000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    place()
                    invalidateSelf()
                }
                start()
            }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun setAlpha(alpha: Int) {
        val a = alpha.coerceIn(0, 255)
        if (a == surfaceAlpha) return
        surfaceAlpha = a
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** Ripple over the frosted glass, as a View background. */
        fun background(context: Context, radiusDp: Int): Drawable {
            val radius = radiusDp * context.resources.displayMetrics.density
            val mask = GradientDrawable().apply {
                cornerRadius = radius
                setColor(Color.WHITE)
            }
            return RippleDrawable(
                ColorStateList.valueOf(NoctwadeTheme.color(context, R.attr.nwRipple)),
                ShimmerSurface(context, radius),
                mask,
            )
        }
    }
}
