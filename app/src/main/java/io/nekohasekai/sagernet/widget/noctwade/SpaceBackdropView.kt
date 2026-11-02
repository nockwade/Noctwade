package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import io.nekohasekai.sagernet.ui.noctwade.ThemePacks
import java.lang.ref.WeakReference
import java.util.concurrent.Future

/**
 * The Home / Servers background: the theme gradient (or the user's own gradient) and, when chosen,
 * a picture from a theme pack or the gallery, under a cached canvas atmosphere. No blur or bitmap
 * work on animation frames.
 */
class SpaceBackdropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val picture = PictureView(context)
    private val scene =
        AtmosphereView(context) { dx, dy ->
            picture.translationX = dx
            picture.translationY = dy
        }
    /** Soft legibility veil: darker under the top bar and under the cards, clear in the middle. */
    private val scrim =
        View(context).apply {
            background =
                android.graphics.drawable.PaintDrawable().apply {
                    shape = android.graphics.drawable.shapes.RectShape()
                    shaderFactory =
                        object : android.graphics.drawable.ShapeDrawable.ShaderFactory() {
                            override fun resize(width: Int, height: Int): Shader {
                                val bg = NoctwadeTheme.background(context)
                                fun a(alpha: Float) =
                                    androidx.core.graphics.ColorUtils.setAlphaComponent(bg, (alpha * 255).toInt())
                                return LinearGradient(
                                    0f,
                                    0f,
                                    0f,
                                    height.toFloat(),
                                    intArrayOf(a(.55f), a(.14f), a(0f), a(.10f), a(.45f), a(.75f)),
                                    floatArrayOf(0f, .14f, .30f, .48f, .70f, 1f),
                                    Shader.TileMode.CLAMP,
                                )
                            }
                        }
                }
        }
    private var backdrop: NoctwadeTheme.Backdrop? = null
    private var loading: Future<*>? = null
    private var token = 0
    private var requested: String? = null
    private var explicitlyVisible = false

    var previewMode = false
        set(value) {
            field = value
            scrim.visibility = if (value || backdrop !is NoctwadeTheme.Backdrop.Picture) GONE else VISIBLE
            requested = null
            load()
        }

    /** Shows this background key instead of the chosen one (previews in the settings). */
    var previewBackground: String? = null
        set(value) {
            field = value
            sync()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        clipChildren = true
        addView(picture, LayoutParams(-1, -1))
        addView(scene, LayoutParams(-1, -1))
        addView(scrim, LayoutParams(-1, -1))
        sync()
    }

    private fun sync() {
        val b = NoctwadeTheme.backdrop(context, previewBackground ?: NoctwadeTheme.backgroundKey(context))
        backdrop = b
        // The theme gradient is always underneath: it shows below "top" pictures and while loading.
        val gradient = if (b is NoctwadeTheme.Backdrop.Gradient) b else NoctwadeTheme.defaultGradient(context)
        background =
            GradientDrawable(NoctwadeTheme.orientation(gradient.angle), gradient.colors).apply { setDither(true) }
        token++
        loading?.cancel(true)
        requested = null
        picture.set(null, ThemePacks.Fit.COVER, 0f)
        picture.visibility = if (b is NoctwadeTheme.Backdrop.Picture) VISIBLE else GONE
        scrim.visibility = if (!previewMode && b is NoctwadeTheme.Backdrop.Picture) VISIBLE else GONE
        load()
    }

    /**
     * The picture is a little larger than the view (room for the drift). Its size must be known
     * before the children are measured: setting it from onSizeChanged came after the measure pass,
     * so the picture was positioned for the larger size but kept the old width and left a strip.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            val margin = (16 * resources.displayMetrics.density).toInt()
            (picture.layoutParams as LayoutParams).apply {
                width = w + margin * 2
                leftMargin = -margin
                height = h + maxOf((h * .08f).toInt(), margin * 2)
                topMargin = -maxOf((h * .03f).toInt(), margin)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun load() {
        val b = backdrop as? NoctwadeTheme.Backdrop.Picture ?: return
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val params = picture.layoutParams
        val signature = "${b.file.path}:${params.width}:${params.height}:$previewMode"
        if (requested == signature) return
        requested = signature
        loading?.cancel(true)
        val current = ++token
        val weak = WeakReference(this)
        loading =
            WallpaperImages.loadFile(b.file.path, params.width, params.height, previewMode) { bitmap ->
                val view = weak.get()
                if (view != null && view.token == current && view.isAttachedToWindow) {
                    view.loading = null
                    view.picture.set(bitmap, b.fit, b.dim)
                }
            }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        load()
    }

    override fun onDetachedFromWindow() {
        token++
        loading?.cancel(true)
        loading = null
        requested = null
        super.onDetachedFromWindow()
    }

    fun setEffectsVisible(visible: Boolean) {
        explicitlyVisible = visible
        scene.setActive(visible)
    }

    override fun onVisibilityAggregated(visible: Boolean) {
        super.onVisibilityAggregated(visible)
        scene.setActive(explicitlyVisible && visible)
    }

    /**
     * Draws the picture: [ThemePacks.Fit.COVER] fills the view; [ThemePacks.Fit.TOP] fills the width
     * at the top and fades out (alpha mask) into the gradient below. [dim] darkens it at night.
     */
    private class PictureView(context: Context) : View(context) {
        private var bitmap: Bitmap? = null
        private var fit = ThemePacks.Fit.COVER
        private var dim = 0f
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val mask = Paint(Paint.DITHER_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        private val shade = Paint()
        private val matrix = Matrix()

        fun set(bitmap: Bitmap?, fit: ThemePacks.Fit, dim: Float) {
            this.bitmap = bitmap
            this.fit = fit
            this.dim = dim
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val b = bitmap ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val scale = if (fit == ThemePacks.Fit.TOP) w / b.width else maxOf(w / b.width, h / b.height)
            val shownH = b.height * scale
            val dx = (w - b.width * scale) / 2f
            val dy = if (fit == ThemePacks.Fit.TOP) 0f else (h - shownH) * .3f
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            if (fit == ThemePacks.Fit.TOP) {
                val bottom = minOf(shownH, h)
                // The mask covers the whole layer, not just up to the picture's edge: the layer is rounded out to
                // whole pixels, and the row past a fractional edge used to keep the picture unmasked. As the
                // background drifted, that row came and went as a thin flickering line across the screen.
                val layer = canvas.saveLayer(0f, 0f, w, h, null)
                canvas.drawBitmap(b, matrix, paint)
                if (dim > 0f) {
                    shade.color = Color.argb((dim * 255).toInt(), 0, 0, 0)
                    canvas.drawRect(0f, 0f, w, bottom, shade)
                }
                mask.shader =
                    LinearGradient(0f, bottom * .55f, 0f, bottom * .985f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, mask)
                canvas.restoreToCount(layer)
            } else {
                canvas.drawBitmap(b, matrix, paint)
                if (dim > 0f) canvas.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 0))
            }
        }
    }
}
