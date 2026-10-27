package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View

internal object NoctwadeMotion {
    fun enabled(context: Context): Boolean =
        io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.animations(context) &&
            if (Build.VERSION.SDK_INT >= 26) {
                ValueAnimator.areAnimatorsEnabled()
            } else {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) > 0f
            }

    /** Transform-only feedback: no layout passes, blur, or permanent hardware layers. */
    fun press(view: View) {
        view.setOnTouchListener { target, event ->
            if (enabled(target.context)) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        target
                            .animate()
                            .scaleX(.97f)
                            .scaleY(.97f)
                            .setStartDelay(0)
                            .setDuration(100)
                            .start()
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL ->
                        target
                            .animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setStartDelay(0)
                            .setDuration(180)
                            .start()
                }
            }
            false // Keep framework click, keyboard, accessibility and ripple handling.
        }
    }
}
