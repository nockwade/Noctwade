package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class SlowMarqueeTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs) {
    private var x = 0f
    private var direction = 1
    private var last = 0L
    private var waitUntil = 0L
    private val frame =
        object : Runnable {
            override fun run() {
                if (!isAttachedToWindow || !isShown || !NoctwadeMotion.enabled(context)) return
                val extra =
                    ((layout?.getLineWidth(0) ?: 0f) -
                            (width - compoundPaddingLeft - compoundPaddingRight))
                        .coerceAtLeast(0f)
                if (extra < 1f) {
                    scrollTo(0, 0)
                    return
                }
                val now = SystemClock.uptimeMillis()
                val delta = (now - last).coerceIn(0, 100)
                last = now
                if (now >= waitUntil) {
                    x =
                        (x + direction * delta * .012f * resources.displayMetrics.density).coerceIn(
                            0f,
                            extra,
                        )
                    scrollTo(x.toInt(), 0)
                    if ((direction > 0 && x >= extra) || (direction < 0 && x <= 0)) {
                        direction = -direction
                        waitUntil = now + 1600
                    }
                }
                postDelayed(this, 40L)
            }
        }

    init {
        setSingleLine(true)
        setHorizontallyScrolling(true)
        ellipsize = null
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
    }

    fun setTitle(value: CharSequence) {
        if (text.toString() != value.toString()) {
            text = value
            restart()
        }
    }

    private fun restart() {
        removeCallbacks(frame)
        x = 0f
        direction = 1
        scrollTo(0, 0)
        last = SystemClock.uptimeMillis()
        waitUntil = last + 1800
        if (isAttachedToWindow) postDelayed(frame, 100L)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        restart()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) restart() else removeCallbacks(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
}
