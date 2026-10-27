package io.nekohasekai.sagernet.widget.noctwade

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Connect button: a glass disc with a thin power glyph, three orbiting accent arcs and a dotted
 * ring with a travelling highlight.
 *
 * Idle: everything drifts slowly. Connecting: the arcs speed up and breathe, a bright dot orbits.
 * Connected: the disc fills with the accent, soft ripples spread out and the halo breathes.
 *
 * Drawn on a 240-unit canvas; every shader and path is allocated once. One TimeAnimator drives all
 * motion and stops when hidden, with animations off or in economy mode.
 */
class OrbitConnectView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = NoctwadeTheme.accent(context)
    private val secondary = NoctwadeTheme.secondaryAccent(context)
    private val surface = NoctwadeTheme.surface(context)
    private val text = NoctwadeTheme.text(context)

    private val power =
        Path().apply {
            moveTo(120f, 98f)
            lineTo(120f, 118f)
            addArc(RectF(99f, 101f, 141f, 143f), -55f, 290f)
        }
    private val arcBounds = RectF(20f, 20f, 220f, 220f)

    private fun withAlpha(color: Int, a: Int) =
        ColorUtils.setAlphaComponent(color, a.coerceIn(0, 255))

    private val idleFill =
        LinearGradient(
            70f,
            42f,
            170f,
            198f,
            intArrayOf(
                ColorUtils.blendARGB(surface, accent, .22f),
                ColorUtils.blendARGB(surface, accent, .06f),
                ColorUtils.blendARGB(surface, secondary, .14f),
            ),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP,
        )
    private val activeFill =
        LinearGradient(
            60f,
            50f,
            180f,
            190f,
            intArrayOf(
                ColorUtils.blendARGB(accent, Color.WHITE, .12f),
                accent,
                ColorUtils.blendARGB(secondary, surface, .25f),
            ),
            floatArrayOf(0f, .5f, 1f),
            Shader.TileMode.CLAMP,
        )
    private val sheen =
        RadialGradient(
            98f,
            82f,
            70f,
            intArrayOf(0x30FFFFFF, 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP,
        )
    private val halo =
        RadialGradient(
            120f,
            120f,
            120f,
            intArrayOf(withAlpha(accent, 0), withAlpha(accent, 110), withAlpha(accent, 0)),
            floatArrayOf(.5f, .72f, 1f),
            Shader.TileMode.CLAMP,
        )
    /** Soft light pooled inside the transparent disc. */
    private val innerGlow =
        RadialGradient(
            120f,
            112f,
            82f,
            intArrayOf(withAlpha(accent, 70), withAlpha(accent, 18), withAlpha(accent, 0)),
            floatArrayOf(0f, .65f, 1f),
            Shader.TileMode.CLAMP,
        )

    /** Gentle inner rim light, brighter at the top: reads as a soft glass edge. */
    private val rimLight =
        LinearGradient(
            120f,
            42f,
            120f,
            198f,
            intArrayOf(0x66FFFFFF, 0x14FFFFFF, 0x05FFFFFF),
            floatArrayOf(0f, .5f, 1f),
            Shader.TileMode.CLAMP,
        )

    private val arcShader = SweepGradient(120f, 120f, intArrayOf(accent, secondary, accent), null)

    private var state = BaseService.State.Idle
    private var mix = 0f // 0 idle → 1 connected
    private var connecting = 0f // eased 0..1 while connecting
    private var time = 0f // seconds of motion
    private var spin = 0f // arc rotation in degrees, integrated so speed changes stay smooth
    private var effectsVisible = false
    private var transition: ValueAnimator? = null
    private var ticker: TimeAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        NoctwadeMotion.press(this)
    }

    fun setEffectsVisible(visible: Boolean) {
        effectsVisible = visible
        updateTicker()
        if (!visible) {
            transition?.cancel()
            mix = if (state == BaseService.State.Connected) 1f else 0f
        }
        invalidate()
    }

    fun setState(value: BaseService.State) {
        if (state == value) return
        state = value
        contentDescription =
            context.getString(
                if (value.canStop) R.string.nw_disconnect_action else R.string.nw_connect_action
            )
        // Stopping is not actionable; this also prevents rapid repeated starts while the service
        // closes.
        isEnabled = value != BaseService.State.Stopping
        val target = if (value == BaseService.State.Connected) 1f else 0f
        transition?.cancel()
        if (effectsVisible && NoctwadeMotion.enabled(context)) {
            transition =
                ValueAnimator.ofFloat(mix, target).apply {
                    duration = 520L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        mix = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        } else mix = target
        if (!animating()) connecting = if (value == BaseService.State.Connecting) 1f else 0f
        updateTicker()
        invalidate()
    }

    private fun animating() =
        effectsVisible &&
            isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            NoctwadeMotion.enabled(context) &&
            !NoctwadeEco.active(context)

    private fun updateTicker() {
        if (!animating()) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker != null) return
        ticker =
            TimeAnimator().apply {
                setTimeListener { _, _, delta ->
                    val dt = (delta.coerceAtMost(64L)) / 1000f
                    time += dt
                    val target = if (state == BaseService.State.Connecting) 1f else 0f
                    connecting += (target - connecting) * (dt * 5f).coerceAtMost(1f)
                    // degrees per second: idle 15, connected 36, connecting 300
                    val speed = 15f + 21f * mix + 285f * connecting
                    spin = (spin + speed * dt) % 360f
                    invalidate()
                }
                start()
            }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTicker()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        ticker?.cancel()
        transition?.cancel()
        ticker = null
        transition = null
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        val size = minOf(width, height).toFloat()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 240f, size / 240f)
        val breathe = .5f + .5f * sin(time * 1.6f)

        // Halo: faint when idle, breathing once connected.
        paint.style = Paint.Style.FILL
        paint.shader = halo
        paint.alpha = (35 + mix * (120 + 70 * breathe) + connecting * 50).toInt().coerceAtMost(255)
        canvas.drawCircle(120f, 120f, 120f, paint)

        // Ripples spreading out while connected.
        paint.shader = null
        if (mix > .01f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            for (k in 0..1) {
                val t = ((time / 2.8f) + k * .5f) % 1f
                paint.color = withAlpha(accent, (mix * 120 * (1 - t)).toInt())
                canvas.drawCircle(120f, 120f, 80f + 38f * t, paint)
            }
        }

        // Dotted ring with a highlight travelling the other way.
        paint.style = Paint.Style.FILL
        val dots = 60
        val head = (-spin * .6f + 360f) % 360f
        for (i in 0 until dots) {
            val deg = i * 360f / dots
            val rad = Math.toRadians(deg.toDouble() - 90.0)
            var distance = kotlin.math.abs(deg - head)
            if (distance > 180f) distance = 360f - distance
            val lit = (1f - distance / 70f).coerceAtLeast(0f)
            paint.color =
                withAlpha(
                    ColorUtils.blendARGB(text, accent, .5f),
                    (34 + lit * (110 + 90 * mix)).toInt(),
                )
            canvas.drawCircle(
                120f + 112f * cos(rad).toFloat(),
                120f + 112f * sin(rad).toFloat(),
                1.1f + lit * .9f,
                paint,
            )
        }

        // Thin track and three orbiting arcs.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1f
        paint.color = withAlpha(text, 26)
        canvas.drawCircle(120f, 120f, 100f, paint)
        paint.shader = arcShader
        paint.strokeWidth = 3f
        paint.alpha = (110 + 120 * maxOf(mix, connecting)).toInt()
        val arcSave = canvas.save()
        canvas.rotate(spin, 120f, 120f)
        val sweep = 54f + 30f * connecting * sin(time * 5f) + 10f * mix
        for (k in 0..2) canvas.drawArc(arcBounds, k * 120f, sweep, false, paint)
        canvas.restoreToCount(arcSave)
        paint.shader = null

        // Orbiting comet while connecting.
        if (connecting > .02f) {
            val rad = Math.toRadians((spin * 1.6f - 90f).toDouble())
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(Color.WHITE, (connecting * 230).toInt())
            canvas.drawCircle(
                120f + 100f * cos(rad).toFloat(),
                120f + 100f * sin(rad).toFloat(),
                3.4f,
                paint,
            )
        }

        // Glass disc.
        paint.style = Paint.Style.FILL
        // Transparent glass: a faint tint, a pool of soft light and a light edge; the wallpaper
        // stays visible through it. Connected adds a translucent accent fill.
        paint.shader = idleFill
        paint.alpha = 92
        canvas.drawCircle(120f, 120f, 78f, paint)
        paint.shader = innerGlow
        paint.alpha = (200 + 55 * breathe * mix).toInt()
        canvas.drawCircle(120f, 120f, 78f, paint)
        paint.shader = activeFill
        paint.alpha = (mix * 150).toInt()
        canvas.drawCircle(120f, 120f, 78f, paint)
        paint.shader = sheen
        paint.alpha = 255
        canvas.drawCircle(120f, 120f, 78f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.shader = rimLight
        paint.alpha = (190 + 65 * mix).toInt()
        canvas.drawCircle(120f, 120f, 78f, paint)
        paint.shader = null
        paint.alpha = 255

        // Power glyph.
        paint.strokeWidth = 4.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color =
            ColorUtils.blendARGB(ColorUtils.blendARGB(accent, Color.WHITE, .72f), Color.WHITE, mix)
        canvas.drawPath(power, paint)

        if (isFocused) {
            paint.color = ColorUtils.blendARGB(accent, Color.WHITE, .8f)
            paint.strokeWidth = 2f
            canvas.drawCircle(120f, 120f, 118f, paint)
        }
        canvas.restoreToCount(save)
    }
}
