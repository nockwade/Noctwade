package io.nekohasekai.sagernet.widget.noctwade

import android.graphics.*
import android.graphics.drawable.Drawable

class NavigationFadeDrawable(private val baseColor: Int = Color.BLACK) : Drawable() {
    private val paint = Paint()

    override fun onBoundsChange(b: Rect) {
        paint.shader =
            LinearGradient(
                0f,
                b.top.toFloat(),
                0f,
                b.bottom.toFloat(),
                intArrayOf(0, 12, 60, 160, 255)
                    .map { (baseColor and 0x00FFFFFF) or (it shl 24) }
                    .toIntArray(),
                floatArrayOf(0f, .25f, .5f, .75f, 1f),
                Shader.TileMode.CLAMP,
            )
    }

    override fun draw(c: Canvas) {
        c.drawRect(bounds, paint)
    }

    override fun setAlpha(a: Int) {
        paint.alpha = a
        invalidateSelf()
    }

    override fun setColorFilter(f: ColorFilter?) {
        paint.colorFilter = f
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
