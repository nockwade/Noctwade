package io.nekohasekai.sagernet.ui.noctwade

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The line icons of the "add" tiles, drawn at the exact centre of their bounds: a photo for the gallery, a plus for a
 * new gradient, an empty dashed halo with a plus for a new halo. Rounded strokes with a soft shadow, so they read over
 * any picture.
 */
internal class TileGlyph(private val kind: Kind, private val color: Int, private val density: Float) : Drawable() {
    enum class Kind {
        PHOTO,
        PLUS,
        HALO,
    }

    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.2f * density
            color = this@TileGlyph.color
            setShadowLayer(4f * density, 0f, 1f * density, 0x66000000)
        }
    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = this@TileGlyph.color
            setShadowLayer(4f * density, 0f, 1f * density, 0x66000000)
        }
    private val path = Path()
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val d = density
        when (kind) {
            Kind.PLUS -> plus(canvas, cx, cy, 11f * d)
            Kind.PHOTO -> {
                // A landscape frame: hills and a sun, with a small plus at the corner.
                box.set(cx - 17f * d, cy - 13f * d, cx + 17f * d, cy + 13f * d)
                canvas.drawRoundRect(box, 5f * d, 5f * d, stroke)
                path.reset()
                path.moveTo(box.left + 4f * d, box.bottom - 5f * d)
                path.lineTo(cx - 5f * d, cy - 1f * d)
                path.lineTo(cx + 2f * d, cy + 5f * d)
                path.lineTo(cx + 7f * d, cy + 1f * d)
                path.lineTo(box.right - 4f * d, box.bottom - 5f * d)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(cx + 8f * d, cy - 5.5f * d, 2.6f * d, fill)
                plus(canvas, box.right + 1f * d, box.bottom + 1f * d, 5.5f * d)
            }
            Kind.HALO -> {
                val dashed =
                    Paint(stroke).apply {
                        pathEffect = DashPathEffect(floatArrayOf(4.5f * d, 4f * d), 0f)
                        alpha = 200
                    }
                canvas.drawCircle(cx, cy, 21f * d, dashed)
                plus(canvas, cx, cy, 8f * d)
            }
        }
    }

    private fun plus(canvas: Canvas, cx: Float, cy: Float, arm: Float) {
        canvas.drawLine(cx - arm, cy, cx + arm, cy, stroke)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, stroke)
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
