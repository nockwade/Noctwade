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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.ui.noctwade.HaloShaders
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import io.nekohasekai.sagernet.ui.noctwade.ThemePacks
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Connect button: a soft disc under a slowly turning gradient halo with small triangles, as in the
 * game's UI. Connecting splits the halo into arcs and speeds it up; connected fills the disc with
 * the accent and sends gentle ripples out. One TimeAnimator drives everything and stops when
 * hidden, with animations off or in economy mode.
 */
class OrbitConnectView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = NoctwadeTheme.accent(context)
    private val secondary = NoctwadeTheme.secondaryAccent(context)
    private val surface = NoctwadeTheme.surface(context)
    private val text = NoctwadeTheme.text(context)
    private val baLight = NoctwadeTheme.light(context)

    private val power =
        Path().apply {
            moveTo(120f, 98f)
            lineTo(120f, 118f)
            addArc(RectF(99f, 101f, 141f, 143f), -55f, 290f)
        }

    private fun withAlpha(color: Int, a: Int) =
        ColorUtils.setAlphaComponent(color, a.coerceIn(0, 255))

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: HaloShaders.Halo? = null
    private var shaderKey: String? = null
    private var shaderFailed = false

    /** Shows this halo instead of the chosen one (previews in the settings); null = the chosen halo. */
    var haloKey: String? = null
        set(value) {
            field = value
            resolvedHalo = null
            invalidate()
        }
    private var resolvedHalo: String? = null

    private var state = BaseService.State.Idle
    private var mix = 0f // 0 idle → 1 connected
    private var connecting = 0f // eased 0..1 while connecting
    private var time = 0f // seconds of motion
    private var spin = 0f // arc rotation in degrees, integrated so speed changes stay smooth
    private var effectsVisible = false
    private var transition: ValueAnimator? = null
    private var ticker: TimeAnimator? = null

    /** On Home the halo can be turned by hand, like a spinner; the previews in the settings stay still. */
    var spinnable = false

    private var manual = 0f // degrees the halo was turned by hand, on top of its own motion
    private var spinSpeed = 0f // deg/s of the last drag; fades away after the finger leaves
    private var dragging = false
    private var ringTouch = false
    private var downX = 0f
    private var downY = 0f
    private var lastAngle = 0f
    private var lastMoveAt = 0L
    private var clickAngle = 0f
    private var coast: TimeAnimator? = null
    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    /** How far the halo was turned by hand, in degrees. */
    internal val handTurn: Float
        get() = manual

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
                    duration = 900L
                    interpolator = NoctwadeMotion.soft
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

    /** False while the view or a parent is hidden (say a halo tile on another tab): no frames are drawn for it. */
    private var aggregated = true

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregated = isVisible
        updateTicker()
    }

    private fun animating() =
        effectsVisible &&
            aggregated &&
            isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            NoctwadeTheme.live(context) &&
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
                    connecting += (target - connecting) * (dt * 2.5f).coerceAtMost(1f)
                    // degrees per second: idle 9, connected 20, connecting 130
                    val speed = 9f + 11f * mix + 121f * connecting
                    spin = (spin + speed * dt) % 360f
                    invalidate()
                }
                start()
            }
    }

    private fun angleOf(x: Float, y: Float) = Math.toDegrees(atan2(y - height / 2f, x - width / 2f).toDouble()).toFloat()

    /** The ring around the disc, where a finger turns the halo; the disc itself still just taps. */
    private fun inRing(x: Float, y: Float): Boolean {
        val half = minOf(width, height) / 2f
        return hypot(x - width / 2f, y - height / 2f) >= half * .66f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!spinnable || !isEnabled) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopCoast()
                ringTouch = inRing(event.x, event.y)
                // At the sides the finger moves up or down, which a scrolling parent would take at once.
                if (ringTouch) parent?.requestDisallowInterceptTouchEvent(true)
                dragging = false
                downX = event.x
                downY = event.y
                lastAngle = angleOf(event.x, event.y)
                lastMoveAt = event.eventTime
                spinSpeed = 0f
            }
            MotionEvent.ACTION_MOVE ->
                if (ringTouch) {
                    if (!dragging && hypot(event.x - downX, event.y - downY) > slop) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        // The tap that began here is over: no click and no pressed state.
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        lastAngle = angleOf(event.x, event.y)
                        lastMoveAt = event.eventTime
                    }
                    if (dragging) {
                        turnBy(event)
                        return true
                    }
                }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL ->
                if (dragging) {
                    dragging = false
                    release(event.eventTime)
                    return true
                }
        }
        return super.onTouchEvent(event)
    }

    /** Follows the finger round the centre; the speed is smoothed so a flick carries on after release. */
    private fun turnBy(event: MotionEvent) {
        val angle = angleOf(event.x, event.y)
        var delta = angle - lastAngle
        if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
        manual = (manual + delta) % 360f
        val seconds = (event.eventTime - lastMoveAt).coerceAtLeast(1L) / 1000f
        spinSpeed = spinSpeed * .55f + (delta / seconds).coerceIn(-1800f, 1800f) * .45f
        lastAngle = angle
        lastMoveAt = event.eventTime
        tick(delta)
        invalidate()
    }

    /** A soft click for every 30 degrees, like the notches of a dial. */
    private fun tick(delta: Float) {
        clickAngle += abs(delta)
        if (clickAngle >= 30f) {
            clickAngle %= 30f
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun release(at: Long) {
        // A finger that rested before lifting leaves no speed behind.
        if (at - lastMoveAt > 90L || abs(spinSpeed) < 60f || !NoctwadeMotion.enabled(context)) {
            spinSpeed = 0f
            return
        }
        stopCoast()
        coast =
            TimeAnimator().apply {
                setTimeListener { _, _, delta ->
                    val dt = delta.coerceAtMost(64L) / 1000f
                    manual = (manual + spinSpeed * dt) % 360f
                    tick(spinSpeed * dt)
                    spinSpeed *= exp(-1.7f * dt) // friction: about a second and a half to a stop
                    invalidate()
                    if (abs(spinSpeed) < 8f) stopCoast()
                }
                start()
            }
    }

    private fun stopCoast() {
        coast?.cancel()
        coast = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resolvedHalo = null
        pictureKey = null
        updateTicker()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        stopCoast()
        ticker?.cancel()
        transition?.cancel()
        ticker = null
        transition = null
        picture = null
        pictureKey = null
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
        drawHalo(canvas)
        canvas.restoreToCount(save)
    }

    // A white (light) or deep (dark) disc under a slowly turning halo, a soft glow and small triangles.
    private val baDisc =
        LinearGradient(
            120f,
            42f,
            120f,
            198f,
            if (baLight) intArrayOf(Color.WHITE, ColorUtils.blendARGB(Color.WHITE, accent, .10f))
            else
                intArrayOf(
                    ColorUtils.blendARGB(surface, accent, .16f),
                    ColorUtils.blendARGB(surface, Color.BLACK, .15f),
                ),
            null,
            Shader.TileMode.CLAMP,
        )
    private val baActive =
        LinearGradient(
            120f,
            42f,
            120f,
            198f,
            intArrayOf(ColorUtils.blendARGB(accent, Color.WHITE, .38f), accent),
            null,
            Shader.TileMode.CLAMP,
        )
    private val baGlow =
        RadialGradient(
            120f,
            120f,
            120f,
            intArrayOf(withAlpha(accent, 0), withAlpha(accent, 120), withAlpha(accent, 0)),
            floatArrayOf(.48f, .70f, 1f),
            Shader.TileMode.CLAMP,
        )
    private val baHalo =
        SweepGradient(
            120f,
            120f,
            intArrayOf(accent, secondary, accent),
            null,
        )
    private val baTriangle =
        Path().apply {
            moveTo(0f, -4.5f)
            lineTo(4f, 2.6f)
            lineTo(-4f, 2.6f)
            close()
        }
    private val baHaloBounds = RectF(28f, 28f, 212f, 212f)

    private fun drawHalo(canvas: Canvas) {
        val breathe = .5f + .5f * sin(time * 1.05f)
        val lift = maxOf(mix, connecting)

        // Soft aura: always a little, fuller once connected.
        paint.style = Paint.Style.FILL
        paint.shader = baGlow
        paint.alpha = (40 + 70 * mix * (.6f + .4f * breathe) + 40 * connecting).toInt()
        canvas.drawCircle(120f, 120f, 120f, paint)
        paint.shader = null

        // Gentle ripples while connected.
        if (mix > .01f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            for (k in 0..1) {
                val t = ((time / 4.2f) + k * .5f) % 1f
                paint.color = withAlpha(accent, (mix * 110 * (1 - t) * (1 - t)).toInt())
                canvas.drawCircle(120f, 120f, 82f + 34f * t, paint)
            }
        }

        // The halo (not the disc) turns by the angle the finger gave it.
        val turned = canvas.save()
        if (manual != 0f) canvas.rotate(manual, 120f, 120f)
        when (val style = haloStyle()) {
            HaloShaders.NONE -> {}
            HaloShaders.RINGS -> drawRings(canvas, lift, breathe)
            HaloShaders.CLASSIC -> drawClassic(canvas, lift, breathe)
            else ->
                if (style.startsWith(HaloShaders.PACK)) {
                    // A pack ring: its shader where shaders run, else its picture.
                    if (!drawShader(canvas, style) && !drawPicture(canvas, style, lift, breathe)) drawClassic(canvas, lift, breathe)
                } else if (!drawShader(canvas, style)) drawClassic(canvas, lift, breathe)
        }
        canvas.restoreToCount(turned)
        drawDisc(canvas)
    }

    /** The halo picture of a pack ring (decoded once per key, at most 512 px). */
    private fun loadPicture(key: String): android.graphics.Bitmap? {
        if (pictureKey != key) {
            pictureKey = key
            picture =
                runCatching {
                        val file = ThemePacks.halo(context, key.removePrefix(HaloShaders.PACK))?.image?.takeIf { it.isFile } ?: error("missing")
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
                        android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                    .getOrNull()
        }
        return picture
    }

    private val pictureMatrixRect = RectF()
    private val picturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var picture: android.graphics.Bitmap? = null
    private var pictureKey: String? = null

    /**
     * A halo picture from a theme pack, drawn behind the button disc in its own shape and swaying gently.
     * False when the picture cannot be read.
     */
    private fun drawPicture(canvas: Canvas, key: String, lift: Float, breathe: Float): Boolean {
        val bitmap = loadPicture(key) ?: return false
        // A flat halo picture (seen from the front) turns slowly round the button, like the shader halos.
        val fit = 228f * (1f + .012f * breathe * (1f + lift)) / maxOf(bitmap.width, bitmap.height)
        val w = bitmap.width * fit
        val h = bitmap.height * fit
        pictureMatrixRect.set(120f - w / 2f, 120f - h / 2f, 120f + w / 2f, 120f + h / 2f)
        val save = canvas.save()
        canvas.rotate(spin * .8f, 120f, 120f)
        picturePaint.alpha = (200 + 55 * lift).toInt()
        canvas.drawBitmap(bitmap, null, pictureMatrixRect, picturePaint)
        picturePaint.alpha = 255
        canvas.restoreToCount(save)
        return true
    }

    /** The halo to draw. Resolved once per attach (it reads assets), not on every frame. */
    private fun haloStyle(): String =
        resolvedHalo ?: HaloShaders.usable(context, haloKey ?: NoctwadeTheme.halo(context)).also { resolvedHalo = it }

    /** An imported or built-in AGSL halo; false when it cannot run (the caller draws the classic ring). */
    private fun drawShader(canvas: Canvas, key: String): Boolean {
        if (!HaloShaders.supported()) return false
        if (shaderKey != key) {
            shaderKey = key
            shader = null
            shaderFailed = false
        }
        if (shaderFailed) return false
        if (shader == null)
            shader =
                runCatching {
                        HaloShaders.Halo.create(HaloShaders.read(context, key) ?: error("missing")).also { halo ->
                            // A pack halo's shader may draw the pack's own halo picture (uniform shader iImage).
                            if (halo.wantsImage) halo.setImage(loadPicture(key) ?: error("no picture"))
                        }
                    }
                    .getOrElse {
                        shaderFailed = true
                        null
                    }
        val halo = shader ?: return false
        return runCatching {
                halo.apply(shaderPaint, 240f, 240f, time, accent, secondary, mix, connecting)
                canvas.drawRect(0f, 0f, 240f, 240f, shaderPaint)
                true
            }
            .getOrElse {
                shaderFailed = true
                false
            }
    }

    /** Two thin rings and twelve dots turning slowly: the canvas halo that needs no GPU shader. */
    private fun drawRings(canvas: Canvas, lift: Float, breathe: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = withAlpha(accent, (120 + 100 * lift).toInt())
        canvas.drawCircle(120f, 120f, 99f, paint)
        paint.strokeWidth = 1f
        paint.color = withAlpha(secondary, (70 + 70 * lift).toInt())
        canvas.drawCircle(120f, 120f, 108f, paint)
        paint.style = Paint.Style.FILL
        for (k in 0 until 12) {
            val deg = spin * .6f + k * 30f
            val rad = Math.toRadians(deg.toDouble())
            val big = k % 3 == 0
            paint.color = withAlpha(if (big) accent else secondary, (110 + 120 * lift * (.7f + .3f * breathe)).toInt())
            canvas.drawCircle(120f + 108f * cos(rad).toFloat(), 120f + 108f * sin(rad).toFloat(), if (big) 3.4f else 2f, paint)
        }
    }

    private fun drawClassic(canvas: Canvas, lift: Float, breathe: Float) {
        // Thin guide ring and four small triangles drifting the other way.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = withAlpha(accent, (46 + 40 * lift).toInt())
        canvas.drawCircle(120f, 120f, 108f, paint)
        paint.style = Paint.Style.FILL
        for (k in 0..3) {
            val deg = -spin * .5f + k * 90f
            val rad = Math.toRadians(deg.toDouble() - 90.0)
            val tri = canvas.save()
            canvas.translate(120f + 108f * cos(rad).toFloat(), 120f + 108f * sin(rad).toFloat())
            canvas.rotate(deg + 180f)
            paint.color = withAlpha(if (k % 2 == 0) accent else secondary, (130 + 90 * lift).toInt())
            canvas.drawPath(baTriangle, paint)
            canvas.restoreToCount(tri)
        }

        // The halo: a glowing band under a crisp gradient ring, split into arcs while connecting.
        val haloSave = canvas.save()
        canvas.rotate(spin, 120f, 120f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.shader = baHalo
        val gap = 28f * connecting
        paint.strokeWidth = 11f
        paint.alpha = (34 + 50 * lift * (.7f + .3f * breathe)).toInt()
        for (k in 0..2) canvas.drawArc(baHaloBounds, k * 120f + gap / 2f, 120f - gap, false, paint)
        paint.strokeWidth = 3.4f
        paint.alpha = (150 + 105 * lift).toInt()
        for (k in 0..2) canvas.drawArc(baHaloBounds, k * 120f + gap / 2f, 120f - gap, false, paint)
        paint.shader = null
        canvas.restoreToCount(haloSave)
    }

    private fun drawDisc(canvas: Canvas) {
        // Disc: white in the light theme, deep navy in the dark one; fills with sky blue once connected.
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(accent, if (baLight) 34 else 60)
        canvas.drawCircle(120f, 124f, 76f, paint) // soft drop shadow
        paint.shader = baDisc
        paint.alpha = 255
        canvas.drawCircle(120f, 120f, 74f, paint)
        paint.shader = baActive
        paint.alpha = (mix * 235).toInt()
        canvas.drawCircle(120f, 120f, 74f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = withAlpha(ColorUtils.blendARGB(accent, Color.WHITE, .55f), 200)
        canvas.drawCircle(120f, 120f, 74f, paint)

        // Power glyph: accent on white, white on blue.
        paint.strokeWidth = 4.6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color =
            ColorUtils.blendARGB(
                if (baLight) accent else ColorUtils.blendARGB(accent, Color.WHITE, .55f),
                Color.WHITE,
                mix,
            )
        canvas.drawPath(power, paint)

        if (isFocused) {
            paint.color = accent
            paint.strokeWidth = 2f
            canvas.drawCircle(120f, 120f, 117f, paint)
        }
        paint.alpha = 255
    }
}