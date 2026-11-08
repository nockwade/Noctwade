package io.nekohasekai.sagernet.ui.noctwade

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeEco
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import io.nekohasekai.sagernet.widget.noctwade.ShimmerSurface
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The theme's character on Home: she stands at the right edge behind the cards and talks through
 * a visual-novel dialogue box that also carries the connection status. She breathes, sways and
 * floats, blinks at random, moves her mouth while her line types out, smiles and hops when the
 * connection comes up, and answers taps.
 */
internal class CharacterHome(
    private val character: ThemePacks.PackCharacter,
    private val scene: FrameLayout,
    private val content: LinearLayout,
    private val scroll: View,
    private val dialogIndex: Int,
) {
    private enum class Face {
        IDLE,
        BLINK,
        HAPPY,
        TALK,
    }

    private val context: Context = scene.context
    private val density = context.resources.displayMetrics.density
    private val accent = NoctwadeTheme.accent(context)
    private val secondary = NoctwadeTheme.secondaryAccent(context)
    private val main = Handler(Looper.getMainLooper())
    private val withLines = NoctwadeTheme.lines(context)

    private val layer =
        FrameLayout(context).apply {
            clipChildren = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    private val mascot =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_END
            alpha = 0f
        }
    private val frames = HashMap<Face, Bitmap>()
    private var shown: Face? = null
    private var halo: FloatingHalo? = null
    private var connectedMix = 0f
    private var connectingMix = 0f

    // Dialogue box.
    val dialog = FrameLayout(context).apply { clipChildren = false }
    private val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val club = TextView(context)
    private val status = TextView(context)
    private val body = TextView(context)
    private val next = TextView(context)
    private val nameTag = TextView(context)

    private var ticker: TimeAnimator? = null
    private var time = 0f
    private var floatY = 0f
    private var hopY = 0f
    private var enterY = 0f
    private var happyUntil = 0L
    private var blinking = false
    private var talking = false
    private var mouthOpen = false
    private var line = ""
    private var lastLine = ""
    private var typed = 0
    private var lineKey: String? = null
    private var timer: String? = null
    private var state = BaseService.State.Idle
    private var hasServer = true
    private var resumed = false
    private var entered = false
    private var animateEntrance = false
    private var destroyed = false

    private fun dp(n: Int) = (n * density).toInt()

    private fun motion() = NoctwadeMotion.enabled(context) && !NoctwadeEco.active(context)

    /** A tap on "hide the interface" is a short one-off transition: it runs even when the idle animations are saved. */
    private fun slide() = NoctwadeMotion.enabled(context)

    /** False when the dialogues are switched off: there is no box then, and Home writes the status under the button. */
    val hasDialogue: Boolean
        get() = withLines

    /** The interface is hidden: she grows to fill the screen, her box is docked at the bottom, the connect button above it. */
    private var zoomed = false
    private var faceMargin = 0
    private var shownOrb: View? = null

    /** How much the character is magnified now (1 = her place beside the cards). */
    internal val zoomScale: Float
        get() = layer.scaleX

    /**
     * Fills the screen with her (hiding the interface) or puts her back. The layer grows about her feet, so she
     * stays put at the bottom, and slides to the middle. Her box is docked along the bottom edge as a solid panel
     * (floating mid-screen over her it looked cut off), and the connect button, made smaller, sits right above it.
     */
    fun zoom(on: Boolean, animate: Boolean, orb: View? = null) {
        zoomed = on
        if (orb != null) shownOrb = orb
        dock(on)
        // A child is clipped to its own bounds while its parent clips children: with the scene clipping, the
        // layer's enlarged picture would be cut where the layer used to end, and the box and the button moved
        // down inside the scrolling column would be cut at its edge. Let everything draw freely while zoomed.
        if (on) {
            scroll.scrollTo(0, 0)
            freeClip(true)
        }
        if (dialog.parent != null && !dialogWatched) {
            dialogWatched = true
            dialog.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
                // The box got taller or shorter (its margin or text changed): keep it and the button docked.
                if (zoomed && b - t != ob - ot) scene.post { if (zoomed) applyZoom(slide()) }
            }
        }
        applyZoom(animate && slide())
    }

    private var dialogWatched = false

    private fun freeClip(free: Boolean) {
        scene.clipChildren = !free
        (scroll as? ViewGroup)?.let {
            it.clipChildren = !free
            it.clipToPadding = !free
            (it.getChildAt(0) as? ViewGroup)?.clipChildren = !free
        }
        content.clipChildren = !free
    }

    /** Where [v] ends in the scene's coordinates by layout alone, ignoring its own and its parents' transforms. */
    private fun layoutBottom(v: View): Float {
        var y = v.bottom.toFloat()
        var p = v.parent as? View
        while (p != null && p !== scene) {
            y += p.top - p.scrollY
            p = p.parent as? View
        }
        return y
    }

    private fun applyBoxMargin() {
        val end = if (zoomed) 0 else faceMargin
        (dialog.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.marginEnd != end) {
                it.marginEnd = end
                dialog.layoutParams = it
            }
        }
    }

    /** The dark fade along the bottom of the screen that the docked text sits on, like a visual novel's. */
    private val scrim =
        View(context).apply {
            visibility = View.GONE
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    // Eased stops: the black melts into the picture instead of ending in a visible edge.
                    intArrayOf(0x00000000, 0x14000000, 0x38000000, 0x68000000, 0x98000000.toInt(), 0xBC000000.toInt(), 0xD0000000.toInt()),
                )
        }
    private var boxEdge: android.graphics.drawable.Drawable? = null

    /**
     * Docked: no card at all, only the fade along the bottom with the name, the line and the status on it in light
     * text; undocked: the glass card again.
     */
    private fun dock(on: Boolean) {
        if (scrim.parent == null) scene.addView(scrim, 2, FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM))
        scrim.animate().cancel()
        if (on) {
            scrim.visibility = View.VISIBLE
            if (slide()) scrim.animate().alpha(1f).setDuration(ZOOM_MS).setInterpolator(NoctwadeMotion.soft).start() else scrim.alpha = 1f
        } else if (slide()) {
            scrim.animate().alpha(0f).setDuration(ZOOM_MS).setInterpolator(NoctwadeMotion.soft)
                .withEndAction { if (!zoomed) scrim.visibility = View.GONE }.start()
        } else {
            scrim.alpha = 0f
            scrim.visibility = View.GONE
        }
        if (!withLines || dialog.parent == null) return
        applyBoxMargin()
        // Above the tiles that fade in and out behind it, so it never slides out from under them.
        dialog.translationZ = if (on) dp(6).toFloat() else 0f
        restyle?.cancel()
        val fromT = dockT
        if (!on) {
            box.background = ShimmerSurface.background(context, 22)
            box.foreground = boxEdge
        } else if (boxEdge == null) boxEdge = box.foreground
        val toT = if (on) 1f else 0f
        val paint = { t: Float ->
            dockT = t
            val a = ((1f - t) * 255).toInt()
            box.background?.alpha = a
            box.foreground?.alpha = a
            club.setTextColor(ColorUtils.blendARGB(NoctwadeTheme.muted(context), 0xFFC9CBD2.toInt(), t))
            body.setTextColor(ColorUtils.blendARGB(NoctwadeTheme.text(context), Color.WHITE, t))
        }
        val finish = {
            if (toT == 1f) {
                box.background = null
                box.foreground = null
            }
        }
        if (!slide()) {
            paint(toT)
            finish()
        } else {
            restyle =
                android.animation.ValueAnimator.ofFloat(fromT, toT).apply {
                    duration = ZOOM_MS
                    interpolator = NoctwadeMotion.soft
                    addUpdateListener { paint(it.animatedValue as Float) }
                    addListener(
                        object : android.animation.AnimatorListenerAdapter() {
                            var cancelled = false

                            override fun onAnimationCancel(animation: android.animation.Animator) {
                                cancelled = true
                            }

                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                if (!cancelled) finish()
                            }
                        },
                    )
                    start()
                }
        }
    }

    private var restyle: android.animation.ValueAnimator? = null
    private var dockT = 0f

    private fun applyZoom(animate: Boolean) {
        val width = scene.width.toFloat()
        val height = scene.height.toFloat()
        val lp = mascot.layoutParams as? FrameLayout.LayoutParams
        val mw = lp?.width?.toFloat() ?: 0f
        val mh = lp?.height?.toFloat() ?: 0f
        if (width <= 0f || height <= 0f || mw <= 0f || mh <= 0f) return
        (scrim.layoutParams as? FrameLayout.LayoutParams)?.let {
            val stack = if (dialog.parent != null) (dialog.height + dp(12)).toFloat() else 0f
            val wanted = maxOf(height * .46f, stack + dp(90)).coerceAtMost(height * .8f).toInt()
            if (it.height != wanted) {
                it.height = wanted
                scrim.layoutParams = it
            }
        }
        // Her feet sit a little below the screen (place() pushes her down by 5% of her height).
        val feet = height + mh * .05f
        val centre = width - mw / 2f + mw * .22f
        layer.pivotX = centre
        layer.pivotY = feet
        val scale = if (zoomed) minOf(height * .98f / mh, width * 1.2f / mw).coerceAtLeast(1f) else 1f
        val shift = if (zoomed) width / 2f - centre else 0f
        // The box ends just above the bottom padding of the scrolling content; the connect button stays where it is.
        val bottomLimit = height - maxOf(scroll.paddingBottom, content.paddingBottom) - dp(4)
        // Layout positions (no transforms): the content column starts at the scene's top-left.
        val boxShift = if (zoomed && dialog.parent != null) (bottomLimit - layoutBottom(dialog)).coerceAtLeast(0f) else 0f
        if (!animate) {
            layer.animate().cancel()
            dialog.animate().cancel()
            layer.scaleX = scale
            layer.scaleY = scale
            layer.translationX = shift
            dialog.translationY = boxShift
            if (!zoomed) freeClip(false)
            return
        }
        if (dialog.parent != null)
            dialog.animate().translationY(boxShift).setDuration(ZOOM_MS).setInterpolator(NoctwadeMotion.soft).start()
        layer
            .animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationX(shift)
            .setDuration(ZOOM_MS)
            .setInterpolator(NoctwadeMotion.soft)
            .withEndAction { if (!zoomed) freeClip(false) }
            .start()
    }

    fun attach(animate: Boolean) {
        animateEntrance = animate
        if (withLines) {
            buildDialog()
            content.addView(
                dialog,
                dialogIndex.coerceIn(0, content.childCount),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) },
            )
        }
        layer.addView(mascot, FrameLayout.LayoutParams(0, 0, Gravity.BOTTOM or Gravity.END))
        character.halo?.takeIf { NoctwadeTheme.characterHalo(context) }?.let {
            halo = FloatingHalo(context, it, character.colors.getOrNull(0) ?: accent, character.colors.getOrNull(1) ?: secondary)
            halo?.visibility = View.GONE
            layer.addView(halo, FrameLayout.LayoutParams(0, 0, Gravity.BOTTOM or Gravity.END))
        }
        scene.addView(layer, 1, FrameLayout.LayoutParams(-1, -1))
        scene.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            // Re-place after this layout pass, not inside it.
            if (r - l != or - ol || b - t != ob - ot) scene.post { place(r - l, b - t) }
        }
        listenForTaps()
        load()
    }

    private fun buildDialog() {
        val text = NoctwadeTheme.text(context)
        val muted = NoctwadeTheme.muted(context)
        box.background = ShimmerSurface.background(context, 22)
        // A fine accent edge so the box reads as the character's speech bubble.
        box.foreground =
            GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 90))
            }
        box.setPadding(dp(18), dp(20), dp(18), dp(13))
        box.isClickable = true
        box.isFocusable = true
        box.setOnClickListener { tap() }
        NoctwadeMotion.press(box)

        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        club.apply {
            this.text = character.subtitle
            visibility = if (character.subtitle.isBlank()) View.GONE else View.VISIBLE
            textSize = 11.5f
            setTextColor(muted)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        status.apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(9), dp(3), dp(10), dp(3))
            compoundDrawablePadding = dp(6)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        header.addView(club, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(header, LinearLayout.LayoutParams(-1, -2))

        body.apply {
            textSize = BODY_SP
            setTextColor(text)
            setLineSpacing(0f, 1.2f)
            // Always three lines tall, whatever the line: the box (and the button under it) never moves. A longer line
            // gets a slightly smaller font instead (fit).
            height = lineHeight * BODY_LINES + paddingTop + paddingBottom
            maxLines = BODY_LINES
            ellipsize = android.text.TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol && line.isNotEmpty()) post { fit(line) } }
        }
        box.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        next.apply {
            this.text = "▼"
            textSize = 9f
            setTextColor(accent)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Footer: connection status on the left, the "next" mark on the right.
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(status, LinearLayout.LayoutParams(-2, -2))
        footer.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        footer.addView(next, LinearLayout.LayoutParams(-2, -2))
        // The status pill changes size with its text; a fixed footer keeps the box (and what is docked to it) still.
        footer.minimumHeight = dp(28)
        box.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        dialog.addView(box, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })

        nameTag.apply {
            this.text = character.name
            textSize = 13.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            letterSpacing = .03f
            elevation = dp(3).toFloat()
            setPadding(dp(15), dp(4), dp(17), dp(5))
            background =
                GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(accent, ColorUtils.blendARGB(accent, secondary, .65f)),
                    )
                    .apply { cornerRadius = dp(100).toFloat() }
        }
        dialog.addView(nameTag, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { marginStart = dp(14) })
        renderStatus()
    }

    /** Decodes the expression sprites off the main thread; she appears once they are ready. */
    private fun load() {
        val s = character.sprites
        val files =
            listOf(Face.IDLE to s.idle, Face.BLINK to s.blink, Face.HAPPY to s.happy, Face.TALK to s.talk)
                .mapNotNull { (face, file) -> file?.let { face to it } }
        // About the height she is drawn at; larger pack sprites are sampled down.
        val target = (context.resources.displayMetrics.heightPixels * .7f).toInt().coerceAtLeast(600)
        val weak = WeakReference(this)
        decoder.execute {
            val decoded =
                files.mapNotNull { (face, file) ->
                    runCatching {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.path, bounds)
                            var sample = 1
                            while (bounds.outHeight / (sample * 2) >= target) sample *= 2
                            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                        .getOrNull()
                        ?.let { face to it }
                }
            val haloImage =
                character.halo?.image?.let { file ->
                    runCatching {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.path, bounds)
                            var sample = 1
                            while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
                            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                        .getOrNull()
                }
            main.post {
                val home = weak.get() ?: return@post
                if (home.destroyed) return@post
                home.halo?.let {
                    if (haloImage != null) it.setBitmap(haloImage) else it.loadShader()
                    it.visibility = if (it.hasContent()) View.VISIBLE else View.GONE
                }
                home.frames.putAll(decoded)
                home.showFace()
                if (home.scene.width > 0) home.place(home.scene.width, home.scene.height)
                home.enter()
            }
        }
    }

    private fun place(width: Int, height: Int) {
        val idle = frames[Face.IDLE] ?: return
        if (width <= 0 || height <= 0) return
        val ratio = idle.width.toFloat() / idle.height
        // A wide sprite (a whole weapon, long hair) is made smaller so it still fits the screen width.
        val h = (height * .58f).toInt().coerceAtMost((width / ratio * .9f).toInt())
        val w = (h * ratio).toInt()
        mascot.layoutParams =
            (mascot.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = w
                this.height = h
                bottomMargin = -(h * .05f).toInt()
            }
        mascot.translationX = w * .22f
        mascot.pivotX = w / 2f
        mascot.pivotY = h.toFloat()
        halo?.let {
            it.layoutParams =
                (it.layoutParams as FrameLayout.LayoutParams).apply {
                    this.width = w
                    this.height = h
                    bottomMargin = -(h * .05f).toInt()
                }
            it.translationX = w * .22f
            it.pivotX = w / 2f
            it.pivotY = h.toFloat()
        }
        // The dialogue ends before her face so she stays visible beside it.
        faceMargin = (w * .40f - 20 * density).toInt().coerceIn(dp(48), dp(120))
        applyBoxMargin()
        layer.requestLayout()
        if (zoomed) applyZoom(false)
    }

    private fun enter() {
        if (entered) return
        entered = true
        if (!animateEntrance || !motion()) {
            mascot.alpha = 1f
            updateTicker()
            return
        }
        enterY = dp(28).toFloat()
        applyTransform()
        mascot.animate().alpha(1f).setStartDelay(160).setDuration(900).setInterpolator(NoctwadeMotion.soft).start()
        ValueAnimator.ofFloat(enterY, 0f).apply {
            startDelay = 160
            duration = 1000
            interpolator = NoctwadeMotion.soft
            addUpdateListener {
                enterY = it.animatedValue as Float
                applyTransform()
            }
            start()
        }
        updateTicker()
    }

    private fun applyTransform() {
        mascot.translationY = floatY + hopY + enterY
        // The halo follows her exactly: same size, pivot and transforms.
        halo?.let {
            it.translationY = mascot.translationY
            it.scaleX = mascot.scaleX
            it.scaleY = mascot.scaleY
            it.rotation = mascot.rotation
            it.alpha = mascot.alpha
            it.time = time
            it.connected = connectedMix
            it.connecting = connectingMix
            it.invalidate()
        }
    }

    /** One clock for breathing, sway and floating; it only runs while Home is on screen. */
    private fun updateTicker() {
        val run = resumed && entered && mascot.parent != null && motion() && !destroyed
        if (!run) {
            ticker?.cancel()
            ticker = null
            main.removeCallbacks(blink)
            return
        }
        if (ticker != null) return
        ticker =
            TimeAnimator().apply {
                setTimeListener { _, _, delta ->
                    val dt = delta.coerceAtMost(64L) / 1000f
                    time += dt
                    val ease = (dt * 2.5f).coerceAtMost(1f)
                    connectedMix += ((if (state == BaseService.State.Connected) 1f else 0f) - connectedMix) * ease
                    connectingMix += ((if (state == BaseService.State.Connecting) 1f else 0f) - connectingMix) * ease
                    val breathe = .5f + .5f * sin(time * 2f * PI.toFloat() / 4.2f)
                    mascot.scaleY = 1f + .011f * breathe
                    mascot.scaleX = 1f + .004f * breathe
                    mascot.rotation = .65f * sin(time * 2f * PI.toFloat() / 7f)
                    floatY = -dp(5) * (.5f + .5f * sin(time * 2f * PI.toFloat() / 3.6f))
                    applyTransform()
                }
                start()
            }
        scheduleBlink()
    }

    private val blink =
        object : Runnable {
            override fun run() {
                if (frames[Face.BLINK] == null || ticker == null) return
                blinking = true
                showFace()
                main.postDelayed(
                    {
                        blinking = false
                        showFace()
                        // Now and then a quick double blink.
                        if (Random.nextFloat() < .22f)
                            main.postDelayed(
                                {
                                    blinking = true
                                    showFace()
                                    main.postDelayed({ blinking = false; showFace() }, 110)
                                },
                                170,
                            )
                    },
                    130,
                )
                scheduleBlink()
            }
        }

    private fun scheduleBlink() {
        main.removeCallbacks(blink)
        if (frames[Face.BLINK] != null) main.postDelayed(blink, 2400L + Random.nextLong(3600L))
    }

    private fun showFace() {
        val face =
            when {
                SystemClock.uptimeMillis() < happyUntil -> Face.HAPPY
                talking && mouthOpen -> Face.TALK
                blinking -> Face.BLINK
                else -> Face.IDLE
            }
        val bitmap = frames[face] ?: frames[Face.IDLE] ?: return
        if (shown == face && mascot.drawable != null) return
        shown = face
        mascot.setImageBitmap(bitmap)
    }

    fun resume() {
        resumed = true
        updateTicker()
        scheduleChatter()
    }

    fun pause() {
        resumed = false
        main.removeCallbacks(chatter)
        updateTicker()
        finishTyping()
    }

    fun destroy() {
        destroyed = true
        pause()
        main.removeCallbacksAndMessages(null)
        mascot.animate().cancel()
        next.animate().cancel()
        mascot.setImageDrawable(null)
        halo?.setBitmap(null)
        halo = null
        frames.clear()
    }

    fun setTimer(value: String?) {
        if (timer == value) return
        timer = value
        renderStatus()
    }

    private fun renderStatus() {
        if (!withLines) return
        val (label, color) =
            when {
                state == BaseService.State.Connected ->
                    R.string.nw_connected to ContextCompat.getColor(context, R.color.nw_green)
                state == BaseService.State.Connecting -> R.string.nw_connecting to accent
                state == BaseService.State.Stopping -> R.string.nw_stopping to NoctwadeTheme.muted(context)
                !hasServer -> R.string.nw_ch_no_profile to NoctwadeTheme.muted(context)
                else -> R.string.nw_offline to NoctwadeTheme.muted(context)
            }
        val text = context.getString(label)
        status.text = if (state == BaseService.State.Connected && timer != null) "$text · $timer" else text
        status.setTextColor(
            if (NoctwadeTheme.light(context) && state == BaseService.State.Connected)
                ColorUtils.blendARGB(color, Color.BLACK, .25f)
            else color
        )
        status.background =
            GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                setColor(ColorUtils.setAlphaComponent(color, 34))
            }
        val dot =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setSize(dp(7), dp(7))
            }
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
    }

    /** New state: a fresh line for it; the same state keeps its line so it doesn't flicker. */
    fun render(state: BaseService.State, hasServer: Boolean) {
        val wasConnected = this.state == BaseService.State.Connected
        this.state = state
        this.hasServer = hasServer
        renderStatus()
        val key =
            when {
                state == BaseService.State.Connected -> "connected"
                state == BaseService.State.Connecting -> "connecting"
                state == BaseService.State.Stopping -> "stopping"
                !hasServer -> "empty"
                else -> "idle"
            }
        if (key == lineKey) return
        val first = lineKey == null
        lineKey = key
        if (key == "connected" && !wasConnected && !first) celebrate()
        // Without dialogues she only reacts with her face; the status is Home's own text under the button.
        if (withLines) say(lineFor(key), if (first) 700L else 0L)
    }

    private fun lineFor(key: String): String {
        val r = context.resources
        if (!withLines)
            return r.getString(
                when (key) {
                    "connected" -> R.string.nw_secure
                    "connecting" -> R.string.nw_wait
                    "stopping" -> R.string.nw_wait_stop
                    "empty" -> R.string.nw_add_hint
                    else -> R.string.nw_tap_connect
                }
            )
        val lines = character.lines
        val all =
            when (key) {
                "connected" -> lines.connected
                "connecting" -> lines.connecting
                "stopping" -> lines.stopping
                "tap" -> lines.tap
                "empty" -> lines.empty
                else -> lines.idle
            }
        // A pack without lines for this moment falls back to the plain hint.
        if (all.isEmpty())
            return if (key == "tap") "♪"
            else r.getString(
                when (key) {
                    "connected" -> R.string.nw_secure
                    "connecting" -> R.string.nw_wait
                    "stopping" -> R.string.nw_wait_stop
                    "empty" -> R.string.nw_add_hint
                    else -> R.string.nw_tap_connect
                }
            )
        // Never the same line twice in a row.
        val options = all.filter { it != lastLine }.ifEmpty { all }
        return options[Random.nextInt(options.size)].also { lastLine = it }
    }

    /** Now and then she says something new about the same situation. */
    private val chatter =
        Runnable {
            if (resumed && withLines && !talking && lineKey in setOf("idle", "connected")) {
                say(lineFor(lineKey!!), 0L)
            }
            scheduleChatter()
        }

    private fun scheduleChatter() {
        main.removeCallbacks(chatter)
        if (resumed && withLines && !destroyed) main.postDelayed(chatter, 30_000L + Random.nextLong(20_000L))
    }

    private fun tap() {
        if (!withLines) {
            celebrate()
            return
        }
        lineKey = "tap"
        say(lineFor("tap"), 0L)
        celebrate()
        // After a while she goes back to talking about the connection.
        main.removeCallbacks(backToState)
        main.postDelayed(backToState, 6000L)
    }

    private val backToState = Runnable {
        if (lineKey == "tap") {
            lineKey = null
            render(state, hasServer)
        }
    }

    /** Happy face and a little hop. */
    private fun celebrate() {
        happyUntil = SystemClock.uptimeMillis() + 2200L
        showFace()
        main.postDelayed({ showFace() }, 2250L)
        if (!motion() || mascot.parent == null) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 820
            addUpdateListener {
                val t = it.animatedFraction
                // Up quickly and softly, down with a tiny settle.
                hopY = -dp(16) * sin(PI.toFloat() * t) * (1f - .35f * t)
                applyTransform()
            }
            start()
        }
    }

    private val type =
        object : Runnable {
            override fun run() {
                if (typed >= line.length) {
                    finishTyping()
                    return
                }
                typed = (typed + 1).coerceAtMost(line.length)
                // Mouth flaps every other letter, closed on spaces and punctuation.
                val c = line[typed - 1]
                mouthOpen = c.isLetterOrDigit() && typed % 3 != 0
                showFace()
                renderTyped()
                main.postDelayed(this, if (c in ".,!?…~") 120L else 34L)
            }
        }

    private fun say(text: String, delay: Long) {
        main.removeCallbacks(type)
        line = text
        fit(text)
        body.contentDescription = text
        next.animate().cancel()
        next.alpha = 0f
        if (!motion()) {
            typed = line.length
            renderTyped()
            finishTyping()
            return
        }
        typed = 0
        talking = true
        renderTyped()
        main.postDelayed(type, delay + 120L)
    }

    /** The largest font, from [BODY_SP] down to [BODY_MIN_SP], at which [text] fits the box's three lines. */
    private fun fit(text: String) {
        val width = body.width - body.totalPaddingLeft - body.totalPaddingRight
        if (width <= 0) return
        val paint = android.text.TextPaint(body.paint)
        val px = context.resources.displayMetrics.scaledDensity
        var size = BODY_SP
        while (size > BODY_MIN_SP) {
            paint.textSize = size * px
            val layout =
                android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(body.includeFontPadding)
                    .build()
            if (layout.lineCount <= BODY_LINES) break
            size -= .5f
        }
        if (body.textSize != size * px) body.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size)
    }

    /** The whole line is laid out from the start (unrevealed letters are transparent), so the box never jumps. */
    private fun renderTyped() {
        val span = SpannableString(line)
        if (typed < line.length)
            span.setSpan(ForegroundColorSpan(Color.TRANSPARENT), typed, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        body.text = span
    }

    private fun finishTyping() {
        main.removeCallbacks(type)
        if (!withLines) {
            talking = false
            mouthOpen = false
            return
        }
        if (typed < line.length) {
            typed = line.length
            renderTyped()
        }
        talking = false
        mouthOpen = false
        showFace()
        if (destroyed) return
        next.alpha = 1f
        if (motion() && resumed)
            next.animate().translationY(dp(3).toFloat()).setDuration(700).withEndAction(::bob).start()
    }

    private fun bob() {
        if (destroyed || talking || !resumed) return
        next.animate()
            .translationY(if (next.translationY > 0f) 0f else dp(3).toFloat())
            .setDuration(700)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction(::bob)
            .start()
    }

    /** Taps on her (the scroll view is above her) get a reaction; scrolling still works. */
    private fun listenForTaps() {
        val detector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        // Back through the zoom of the layer to where she is laid out.
                        val x = (e.x - layer.translationX - layer.pivotX) / layer.scaleX + layer.pivotX
                        val y = (e.y - layer.translationY - layer.pivotY) / layer.scaleY + layer.pivotY
                        val left = mascot.left + mascot.translationX
                        val top = mascot.top + mascot.translationY
                        // Upper two thirds: the lower part runs behind the cards.
                        if (x >= left + mascot.width * .15f && x <= left + mascot.width * .85f &&
                            y >= top && y <= top + mascot.height * .62f
                        )
                            tap()
                        return false
                    }
                },
            )
        scroll.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    companion object {
        private const val ZOOM_MS = 520L
        private const val BODY_SP = 16f
        private const val BODY_MIN_SP = 13f
        private const val BODY_LINES = 3
        private val decoder =
            Executors.newSingleThreadExecutor { job ->
                Thread(job, "noctwade-character").apply { isDaemon = true }
            }

        /** Header icons follow the palette (bright skies need dark icons). */
        fun styleHeader(menu: ImageView?, add: ImageView) {
            val context = add.context
            menu?.imageTintList = ColorStateList.valueOf(NoctwadeTheme.text(context))
            add.imageTintList = ColorStateList.valueOf(NoctwadeTheme.accent(context))
        }

        /** Shimmering glass card, keeping the view's padding. */
        fun styleCard(view: View, radiusDp: Int = 24) {
            val padding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            view.background = ShimmerSurface.background(view.context, radiusDp)
            view.setPadding(padding[0], padding[1], padding[2], padding[3])
        }
    }
}
