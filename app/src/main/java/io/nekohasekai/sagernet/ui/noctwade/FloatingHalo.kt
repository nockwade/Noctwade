package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

/**
 * A theme-pack character's halo floating above her head. The view has the size of her sprite and takes
 * the same transforms, so it breathes and sways with her; the halo itself bobs a little and either
 * turns slowly (a picture) or is drawn by the pack's AGSL shader.
 */
internal class FloatingHalo(
    context: Context,
    private val spec: ThemePacks.Halo,
    private val color: Int,
    private val color2: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private var bitmap: Bitmap? = null
    private var shader: HaloShaders.Halo? = null
    private val density = context.resources.displayMetrics.density

    var time = 0f
    var connected = 0f
    var connecting = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        invalidate()
    }

    /** Compiles the pack's shader; false when this device cannot run it (the halo is then not shown). */
    fun loadShader(): Boolean {
        val file = spec.shader ?: return false
        if (!HaloShaders.supported()) return false
        shader = runCatching { HaloShaders.Halo.create(file.readText()) }.getOrNull()
        return shader != null
    }

    fun hasContent() = bitmap != null || shader != null

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val size = spec.size * w
        val cx = spec.x * w
        val cy = spec.y * h + sin(time * 1.6f) * 3f * density
        val save = canvas.save()
        val glow = shader
        if (glow != null) {
            canvas.translate(cx - size / 2f, cy - size / 2f)
            canvas.scale(size / 240f, size / 240f)
            val ok =
                runCatching {
                        glow.apply(paint, 240f, 240f, time, color, color2, connected, connecting)
                        canvas.drawRect(0f, 0f, 240f, 240f, paint)
                    }
                    .isSuccess
            paint.shader = null
            if (!ok) shader = null
        } else {
            val b = bitmap
            if (b != null) {
                val height = size * b.height / b.width
                canvas.translate(cx, cy)
                canvas.rotate(sin(time * .5f) * 2.5f)
                target.set(-size / 2f, -height / 2f, size / 2f, height / 2f)
                paint.alpha = (215 + 40 * sin(time * 1.2f)).toInt().coerceIn(0, 255)
                canvas.drawBitmap(b, null, target, paint)
                paint.alpha = 255
            }
        }
        canvas.restoreToCount(save)
    }
}
