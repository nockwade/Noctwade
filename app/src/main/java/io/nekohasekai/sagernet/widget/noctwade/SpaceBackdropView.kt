package io.nekohasekai.sagernet.widget.noctwade

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme
import java.lang.ref.WeakReference
import java.util.concurrent.Future

/**
 * Packaged wallpapers plus a cached canvas atmosphere. No blur or bitmap work on animation frames.
 */
class SpaceBackdropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val scene =
        AtmosphereView(context) { dx, dy ->
            image.translationX = dx
            image.translationY = dy
        }
    /** Soft legibility veil: darker under the top bar and under the cards, clear in the middle. */
    private val scrim =
        android.view.View(context).apply {
            background =
                android.graphics.drawable.PaintDrawable().apply {
                    shape = android.graphics.drawable.shapes.RectShape()
                    shaderFactory =
                        object : android.graphics.drawable.ShapeDrawable.ShaderFactory() {
                            override fun resize(width: Int, height: Int): Shader {
                                val bg = NoctwadeTheme.background(context)
                                fun a(alpha: Float) =
                                    androidx.core.graphics.ColorUtils.setAlphaComponent(
                                        bg,
                                        (alpha * 255).toInt(),
                                    )
                                return LinearGradient(
                                    0f,
                                    0f,
                                    0f,
                                    height.toFloat(),
                                    intArrayOf(a(.62f), a(.18f), a(0f), a(.12f), a(.58f), a(.90f)),
                                    floatArrayOf(0f, .14f, .30f, .48f, .70f, 1f),
                                    Shader.TileMode.CLAMP,
                                )
                            }
                        }
                }
        }
    private var imageId = 0
    private var loading: Future<*>? = null
    private var imageToken = 0
    private var requestedImage: String? = null
    private var explicitlyVisible = false
    var previewMode = false
        set(value) {
            field = value
            scrim.visibility = if (value) GONE else VISIBLE
            image.alpha = if (value) 1f else PHOTO_ALPHA
            image.scaleType =
                if (value) ImageView.ScaleType.MATRIX else ImageView.ScaleType.CENTER_CROP
            requestedImage = null
            imageSize(height)
            loadImage()
        }

    var previewTheme: String? = null
        set(value) {
            field = value
            syncTheme()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        clipChildren = true
        addView(image, LayoutParams(-1, -1))
        addView(scene, LayoutParams(-1, -1))
        addView(scrim, LayoutParams(-1, -1))
        syncTheme()
    }

    private fun syncTheme() {
        val key = previewTheme ?: NoctwadeTheme.name(context)
        val resource = NoctwadeTheme.image(key)
        if (imageId != resource) {
            imageId = resource
            imageToken++
            loading?.cancel(true)
            requestedImage = null
            image.setImageDrawable(null)
        }
        image.visibility = if (resource == 0) GONE else VISIBLE
        // Photos are slightly muted so they set the mood without competing with the interface.
        image.alpha = if (previewMode) 1f else PHOTO_ALPHA
        scene.variant = key
        imageSize(height)
    }

    private fun imageSize(h: Int) {
        if (h <= 0) return
        // the wallpapers are all composed for a phone screen; no theme needs special cropping.
        val original = false
        val margin = (16 * resources.displayMetrics.density).toInt()
        image.layoutParams =
            (image.layoutParams as LayoutParams).apply {
                width = this@SpaceBackdropView.width + margin * 2
                leftMargin = -margin
                height =
                    if (original) (h * 1.30f).toInt() else h + maxOf((h * .08f).toInt(), margin * 2)
                topMargin =
                    if (original) (-h * .23f).toInt() else -maxOf((h * .03f).toInt(), margin)
            }
        loadImage()
    }

    private fun loadImage() {
        if (!isAttachedToWindow || imageId == 0 || width <= 0 || height <= 0) return
        val params = image.layoutParams
        val signature = "$imageId:${params.width}:${params.height}:$previewMode"
        if (requestedImage == signature) return
        requestedImage = signature
        loading?.cancel(true)
        val token = ++imageToken
        val weak = WeakReference(this)
        loading =
            WallpaperImages.load(resources, imageId, params.width, params.height, previewMode) {
                bitmap ->
                val view = weak.get()
                if (view != null && view.imageToken == token && view.isAttachedToWindow) {
                    view.loading = null
                    if (bitmap != null) {
                        view.image.setImageBitmap(bitmap)
                        view.positionPreview(bitmap)
                    }
                }
            }
    }

    private fun positionPreview(bitmap: Bitmap) {
        if (!previewMode) return
        val w = image.layoutParams.width.toFloat()
        val h = image.layoutParams.height.toFloat()
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        // The supplied compositions keep the subject in the upper half; previews retain it.
        image.imageMatrix =
            Matrix().apply {
                setScale(scale, scale)
                postTranslate((w - bitmap.width * scale) / 2f, (h - bitmap.height * scale) * .15f)
            }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        imageSize(h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadImage()
    }

    override fun onDetachedFromWindow() {
        imageToken++
        loading?.cancel(true)
        loading = null
        requestedImage = null
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

    private companion object {
        const val PHOTO_ALPHA = .86f
    }
}
