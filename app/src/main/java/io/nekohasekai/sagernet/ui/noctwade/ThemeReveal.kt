package io.nekohasekai.sagernet.ui.noctwade

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Region
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import kotlin.math.hypot
import kotlin.math.max

/**
 * A new colour theme or light/dark spreads out from the control that was tapped: before the screen is recreated a
 * picture of the old look is kept; the recreated screen shows it on top and opens a growing circle in it, so the new
 * look appears from that point in all directions.
 */
internal object ThemeReveal {
    private var snapshot: Bitmap? = null
    private var centreX = 0f
    private var centreY = 0f

    /**
     * Remembers the current look, then runs [then] (the recreate); [origin] is where the change starts (the screen
     * centre when null). The picture is copied from the screen by the GPU (PixelCopy, about one frame): drawing the
     * whole window again in software used to hold the switch up noticeably.
     */
    fun capture(activity: Activity, origin: View?, then: () -> Unit) {
        snapshot?.recycle()
        snapshot = null
        val root = activity.window?.decorView
        if (!NoctwadeMotion.enabled(activity) || root == null || root.width <= 0 || root.height <= 0) return then()
        centre(root, origin)
        val bitmap = runCatching { Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return then()
        fun software() {
            snapshot = runCatching { bitmap.also { root.draw(Canvas(it)) } }.getOrNull()
            then()
        }
        if (Build.VERSION.SDK_INT < 26) return software()
        var done = false
        val result =
            runCatching {
                android.view.PixelCopy.request(
                    activity.window,
                    bitmap,
                    { code ->
                        if (done) return@request
                        done = true
                        if (code == android.view.PixelCopy.SUCCESS) {
                            snapshot = bitmap
                            then()
                        } else software()
                    },
                    android.os.Handler(android.os.Looper.getMainLooper()),
                )
            }
        if (result.isFailure && !done) {
            done = true
            software()
            return
        }
        // Never wait on the copy for long: the switch itself matters more than the animation.
        root.postDelayed(
            {
                if (!done) {
                    done = true
                    software()
                }
            },
            250,
        )
    }

    private fun centre(root: View, origin: View?) {
        val rootAt = IntArray(2).also { root.getLocationInWindow(it) }
        if (origin != null && origin.isAttachedToWindow) {
            val at = IntArray(2).also { origin.getLocationInWindow(it) }
            centreX = (at[0] - rootAt[0] + origin.width / 2f)
            centreY = (at[1] - rootAt[1] + origin.height / 2f)
        } else {
            centreX = root.width / 2f
            centreY = root.height / 2f
        }
    }

    /** Called by the recreated activity: plays the reveal over its new content, once. */
    fun play(activity: Activity) {
        val old = snapshot ?: return
        snapshot = null
        val decor = activity.window?.decorView as? ViewGroup ?: return old.recycle()
        val cx = centreX
        val cy = centreY
        val overlay =
            object : View(activity) {
                var radius = 0f
                private val hole = Path()

                override fun onDraw(canvas: Canvas) {
                    hole.reset()
                    hole.addCircle(cx, cy, radius, Path.Direction.CW)
                    val save = canvas.save()
                    if (Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(hole)
                    else @Suppress("DEPRECATION") canvas.clipPath(hole, Region.Op.DIFFERENCE)
                    canvas.drawBitmap(old, 0f, 0f, null)
                    canvas.restoreToCount(save)
                }
            }
        overlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        decor.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        overlay.post {
            val far = max(hypot(cx, cy), max(hypot(decor.width - cx, cy), max(hypot(cx, decor.height - cy), hypot(decor.width - cx, decor.height - cy))))
            ValueAnimator.ofFloat(0f, far).apply {
                duration = 560
                interpolator = NoctwadeMotion.soft
                addUpdateListener {
                    overlay.radius = it.animatedValue as Float
                    overlay.invalidate()
                }
                doOnEnd {
                    decor.removeView(overlay)
                    old.recycle()
                }
                start()
            }
        }
    }
}
