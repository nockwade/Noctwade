package io.nekohasekai.sagernet.widget.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme

internal object NoctwadeMotion {
    fun enabled(context: Context): Boolean =
        NoctwadeTheme.animations(context) &&
            if (Build.VERSION.SDK_INT >= 26) {
                ValueAnimator.areAnimatorsEnabled()
            } else {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) > 0f
            }

    /** Long, gentle ease-out with no overshoot. */
    val soft = PathInterpolator(.22f, .61f, .36f, 1f)

    /** Screen transition from the theme. */
    fun anim(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true) && value.resourceId != 0)
            value.resourceId
        else fallback
    }

    fun slideInRight(context: Context) =
        anim(context, R.attr.nwAnimSlideInRight, R.anim.nw_slide_in_right)

    fun slideOutLeft(context: Context) =
        anim(context, R.attr.nwAnimSlideOutLeft, R.anim.nw_slide_out_left)

    fun slideInLeft(context: Context) =
        anim(context, R.attr.nwAnimSlideInLeft, R.anim.nw_slide_in_left)

    fun slideOutRight(context: Context) =
        anim(context, R.attr.nwAnimSlideOutRight, R.anim.nw_slide_out_right)

    /** Transform-only feedback: no layout passes, blur, or permanent hardware layers. */
    fun press(view: View) {
        view.setOnTouchListener { target, event ->
            if (enabled(target.context)) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        target
                            .animate()
                            .scaleX(.975f)
                            .scaleY(.975f)
                            .setStartDelay(0)
                            .setDuration(160)
                            .setInterpolator(soft)
                            .start()
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL ->
                        target
                            .animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setStartDelay(0)
                            .setDuration(360)
                            .setInterpolator(soft)
                            .start()
                }
            }
            false // Keep framework click, keyboard, accessibility and ripple handling.
        }
    }
}
