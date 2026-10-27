package io.nekohasekai.sagernet.widget.noctwade

import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import kotlin.math.*
import kotlin.random.Random

/**
 * Animated canvas layer over the wallpaper: fog, mist, halo, ribbons, stars, petals and meteors.
 * Shaders and paths are prepared on size/theme change; frames only move them (~30 updates/s).
 * [onDrift] receives the slow parallax offset for the wallpaper image (0,0 when stopped).
 */
internal class AtmosphereView(context: Context, private val onDrift: (Float, Float) -> Unit) :
    View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbons = Array(3) { Path() }
    private val shaders = arrayOfNulls<Shader>(3)
    private val petal =
        Path().apply {
            moveTo(0f, -5f)
            cubicTo(7f, -6f, 8f, 3f, 0f, 7f)
            cubicTo(-4f, 2f, -4f, -2f, 0f, -5f)
        }
    private val random = Random(73)
    private val points = FloatArray(90) { random.nextFloat() }
    private var sky: Shader? = null
    private var fog: Shader? = null
    private var mist: Shader? = null
    private var halo: Shader? = null
    private val fogBounds = RectF()
    private val mistBounds = RectF()
    private val haloBounds = RectF()
    private val matrix = Matrix()
    private val meteor = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var starsColor = Color.WHITE
    private var requested = false
    private var running = false
    private var phase = 0f
    private var startedAt = 0L
    private var speed = 1f
    private var amount = .65f
    private var raster = true
    var variant = "noctwade"
        set(value) {
            field = value
            raster = NoctwadeTheme.image(value) != 0
            prepare(width, height)
            invalidate()
        }

    private val frame =
        object : Runnable {
            override fun run() {
                if (!running) return
                val now = SystemClock.elapsedRealtime()
                if (startedAt == 0L) startedAt = now
                phase = (now - startedAt) / 1000f * speed
                if (raster) {
                    val d = resources.displayMetrics.density
                    onDrift(
                        sin(phase * .10f) * d * 8f * amount,
                        cos(phase * .08f) * d * 10f * amount,
                    )
                }
                invalidate()
                postOnAnimationDelayed(this, 33L)
            }
        }

    fun setActive(active: Boolean) {
        requested = active
        speed = NoctwadeTheme.speed(context)
        amount = NoctwadeTheme.intensity(context)
        update()
    }

    private fun update() {
        removeCallbacks(frame)
        running =
            requested &&
                isAttachedToWindow &&
                isShown &&
                windowVisibility == VISIBLE &&
                NoctwadeTheme.live(context) &&
                NoctwadeMotion.enabled(context) &&
                !NoctwadeEco.active(context)
        if (running) postOnAnimation(frame)
        else {
            onDrift(0f, 0f)
            invalidate()
        }
    }

    private var ecoReceiver: BroadcastReceiver? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ecoReceiver = NoctwadeEco.observe(context) { update() }
        update()
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        update()
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        NoctwadeEco.stopObserving(context, ecoReceiver)
        ecoReceiver = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        prepare(w, h)
    }

    private fun prepare(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val palette = AtmospherePalette.of(variant)
        // Photographic wallpapers already contain shading; don't cover Aurora with the
        // opaque procedural sky used by its former canvas-only implementation.
        sky =
            LinearGradient(
                0f,
                0f,
                0f,
                h.toFloat(),
                if (raster) intArrayOf(Color.TRANSPARENT, 0x60000000)
                else intArrayOf(palette.skyTop, palette.skyBottom),
                null,
                Shader.TileMode.CLAMP,
            )
        fog =
            RadialGradient(
                w * .55f,
                h * .3f,
                w * .9f,
                intArrayOf(palette.fog, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
        mist =
            RadialGradient(
                w * .5f,
                h * .5f,
                w * .7f,
                intArrayOf(palette.mist, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
        halo =
            RadialGradient(
                w * .5f,
                h * .22f,
                w * .65f,
                intArrayOf(
                    0x007F63FF,
                    if (variant == "noctwade" || variant == "max") 0x496E49D8 else 0x1AFFFFFF,
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .62f, 1f),
                Shader.TileMode.CLAMP,
            )
        fogBounds.set(-w * .2f, h * .08f, w * 1.2f, h * .78f)
        mistBounds.set(-w * .25f, h * .28f, w * 1.25f, h * .82f)
        haloBounds.set(-w * .16f, h * .22f - w * .65f, w * 1.16f, h * .22f + w * .65f)
        starsColor = palette.particle
        if (!raster)
            ribbons.forEachIndexed { i, path ->
                path.reset()
                val y = h * (.08f + i * .13f)
                path.moveTo(-w * .2f, y + w * .2f)
                path.cubicTo(w * .2f, y - w * .28f, w * .52f, y + w * .4f, w * 1.18f, y - w * .12f)
                path.lineTo(w * 1.2f, y + w * .18f)
                path.cubicTo(w * .5f, y + w * .62f, w * .18f, y - w * .02f, -w * .2f, y + w * .45f)
                path.close()
                shaders[i] =
                    LinearGradient(
                        0f,
                        y,
                        w.toFloat(),
                        y + w * .3f,
                        intArrayOf(
                            ColorUtils.setAlphaComponent(palette.ribbons[i], 8),
                            ColorUtils.setAlphaComponent(palette.ribbons[i], 110),
                            ColorUtils.setAlphaComponent(palette.ribbons[(i + 1) % 3], 20),
                        ),
                        floatArrayOf(0f, .5f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = sky
        c.drawRect(0f, 0f, w, h, paint)
        if (!raster)
            ribbons.forEachIndexed { i, path ->
                val save = c.save()
                c.translate(
                    sin(phase * .17f + i) * w * .08f * amount,
                    cos(phase * .13f + i) * h * .035f * amount,
                )
                paint.shader = shaders[i]
                matrix.setRotate(sin(phase * .14f + i) * 5f * amount, w * .5f, h * .3f)
                shaders[i]?.setLocalMatrix(matrix)
                c.drawPath(path, paint)
                c.restoreToCount(save)
            }
        var save = c.save()
        c.translate(sin(phase * .16f) * w * .18f * amount, cos(phase * .11f) * h * .05f * amount)
        paint.shader = fog
        paint.alpha = (100 + 80 * amount + sin(phase * .2f) * 30 * amount).toInt()
        c.drawOval(fogBounds, paint)
        c.restoreToCount(save)
        save = c.save()
        c.translate(cos(phase * .12f + 2) * w * .2f * amount, sin(phase * .14f) * h * .05f * amount)
        paint.shader = mist
        paint.alpha = (110 * amount).toInt()
        c.drawOval(mistBounds, paint)
        c.restoreToCount(save)
        if (variant == "noctwade" || variant == "max") {
            paint.shader = halo
            paint.alpha = (60 + sin(phase * .6f) * 45 * amount).toInt()
            c.drawOval(haloBounds, paint)
        }
        paint.shader = null
        val count = if (variant == "sakura") 28 else 30
        for (i in 0 until count) {
            val a = points[i * 3]
            val b = points[i * 3 + 1]
            val z = points[i * 3 + 2]
            val x =
                ((a + sin(phase * .19f + i) * .025f * amount + phase * .0015f * amount) % 1f) * w
            val y =
                when (variant) {
                    "sakura" -> ((b + phase * (.02f + z * .012f) * amount) % 1f) * h
                    "china" -> ((b - phase * .012f * amount) % 1f + 1f) % 1f * h
                    else -> ((b - phase * (.001f + z * .002f) * amount) % 1f + 1f) % 1f * h
                }
            paint.color = starsColor
            paint.alpha =
                ((55 + z * 70 + sin(phase * (.5f + z) + i) * 40) * amount).toInt().coerceIn(6, 165)
            if (variant == "sakura") {
                val k = c.save()
                c.translate(x, y)
                c.rotate(phase * 8f + i * 25f)
                val size = (.35f + z * .45f) * resources.displayMetrics.density
                c.scale(size, size)
                c.drawPath(petal, paint)
                c.restoreToCount(k)
            } else c.drawCircle(x, y, (.4f + z) * resources.displayMetrics.density, paint)
        }
        if (variant != "sakura" && variant != "china") {
            val t = (phase % 18f) / 1.4f
            if (t < 1f) {
                meteor.color = starsColor
                meteor.alpha = (sin(t * Math.PI).toFloat() * 115 * amount).toInt()
                meteor.strokeWidth = resources.displayMetrics.density
                val x = w * (.12f + t * .7f)
                val y = h * (.08f + t * .19f)
                c.drawLine(x, y, x - w * .09f, y - h * .025f, meteor)
            }
        }
        paint.alpha = 255
    }
}
