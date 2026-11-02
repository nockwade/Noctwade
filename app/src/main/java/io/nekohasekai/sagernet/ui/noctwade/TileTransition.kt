package io.nekohasekai.sagernet.ui.noctwade

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Outline
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import java.util.WeakHashMap

/**
 * A shared move between screens: the screen opened from a menu tile grows out of that tile (its rounded rectangle
 * widens to the whole screen while the content fades in), instead of sliding in from the side.
 *
 * The list remembers the tile under the finger ([track]); the screen opened right after takes it ([take]) and plays
 * [expand] once it is laid out.
 */
internal object TileTransition {
    private val origin = Rect()
    private var at = 0L
    private val pending = WeakHashMap<Any, Rect>()

    /** Remembers which row of [list] is tapped. */
    @SuppressLint("ClickableViewAccessibility")
    fun track(list: RecyclerView) {
        list.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (e.actionMasked == MotionEvent.ACTION_UP) rv.findChildViewUnder(e.x, e.y)?.let(::remember)
                    return false
                }
            }
        )
    }

    fun remember(view: View) {
        val at = IntArray(2)
        view.getLocationInWindow(at)
        origin.set(at[0], at[1], at[0] + view.width, at[1] + view.height)
        this.at = SystemClock.uptimeMillis()
    }

    /** The tile tapped just now, if any; [owner] (the new screen) plays [expand] from it. */
    fun take(owner: Any): Boolean {
        val fresh = SystemClock.uptimeMillis() - at < 700 && !origin.isEmpty
        at = 0
        if (fresh) pending[owner] = Rect(origin)
        return fresh
    }

    fun expand(owner: Any, view: View) {
        val from = pending.remove(owner) ?: return
        if (!NoctwadeMotion.enabled(view.context)) return
        view.alpha = 0f
        view.post { play(view, from) }
    }

    private fun play(view: View, from: Rect) {
        val at = IntArray(2)
        view.getLocationInWindow(at)
        val start = Rect(from).apply { offset(-at[0], -at[1]) }
        val radius = 18 * view.resources.displayMetrics.density
        val now = Rect()
        var fraction = 0f
        val provider =
            object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    now.set(
                        lerp(start.left, 0, fraction),
                        lerp(start.top, 0, fraction),
                        lerp(start.right, v.width, fraction),
                        lerp(start.bottom, v.height, fraction),
                    )
                    outline.setRoundRect(now, radius * (1 - fraction))
                }
            }
        val previous = view.outlineProvider
        val clipped = view.clipToOutline
        view.outlineProvider = provider
        view.clipToOutline = true
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = NoctwadeMotion.soft
            addUpdateListener {
                fraction = it.animatedValue as Float
                view.alpha = (fraction / .35f).coerceAtMost(1f)
                view.invalidateOutline()
            }
            doOnEnd {
                view.alpha = 1f
                view.outlineProvider = previous
                view.clipToOutline = clipped
            }
            start()
        }
    }

    private fun ValueAnimator.doOnEnd(action: () -> Unit) =
        addListener(
            object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = action()

                override fun onAnimationCancel(animation: android.animation.Animator) = action()
            }
        )

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
}
