package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import io.nekohasekai.sagernet.R

/**
 * Spins a refresh icon while a subscription updates. When the update ends the icon finishes its
 * current turn smoothly instead of snapping back. Without animations it only dims.
 */
internal object RefreshSpin {
    fun set(view: View, spinning: Boolean) {
        val current = view.getTag(R.id.nw_refresh_spin) as? ValueAnimator
        if (spinning) {
            view.isEnabled = false
            if (current?.isRunning == true && current.repeatCount == ValueAnimator.INFINITE) return
            current?.cancel()
            if (!NoctwadeMotion.enabled(view.context)) {
                view.alpha = .45f
                return
            }
            view.alpha = 1f
            val start = view.rotation % 360f
            val spin =
                ObjectAnimator.ofFloat(view, View.ROTATION, start, start + 360f).apply {
                    duration = 900L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            view.setTag(R.id.nw_refresh_spin, spin)
        } else {
            view.isEnabled = true
            view.alpha = 1f
            if (current == null || !current.isRunning) {
                view.rotation = 0f
                return
            }
            current.cancel()
            val from = view.rotation % 360f
            val settle =
                ObjectAnimator.ofFloat(view, View.ROTATION, from, 360f).apply {
                    duration = ((360f - from) / 360f * 600L).toLong().coerceAtLeast(120L)
                    interpolator = DecelerateInterpolator()
                    start()
                }
            view.setTag(R.id.nw_refresh_spin, settle)
        }
    }

    /** Stops immediately, e.g. when a recycled row is rebound to another group. */
    fun reset(view: View) {
        (view.getTag(R.id.nw_refresh_spin) as? ValueAnimator)?.cancel()
        view.setTag(R.id.nw_refresh_spin, null)
        view.rotation = 0f
        view.alpha = 1f
        view.isEnabled = true
    }
}
