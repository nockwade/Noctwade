package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import io.nekohasekai.sagernet.widget.noctwade.SpaceBackdropView

/**
 * The picture tiles of the personalization pages (backgrounds, characters, halos): their look, the mark on the chosen
 * one and the grid they sit in. One instance per screen, [reset] each time the pages are rebuilt.
 */
internal class PersonalizationTiles(private val context: Context) {
    private val resources
        get() = context.resources

    private fun requireContext() = context

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    /** Forgets the tiles of the previous build. */
    fun reset() = selection.clear()

    fun label(context: android.content.Context, title: String, subtitle: String) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(20), dp(10), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x8C000000.toInt()))
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 12.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(Color.WHITE)
                    setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            )
            if (subtitle.isNotBlank())
                addView(
                    TextView(context).apply {
                        text = subtitle
                        textSize = 10f
                        setTextColor(0xD9FFFFFF.toInt())
                        setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                )
        }

    /**
     * Head-and-shoulders crop of a sprite: [zoom] is its height relative to the view, [centre] where it sits across.
     * The picture is decoded once the view has its real size: a page that was hidden (size 0) used to get a 1-pixel
     * decode, stretched into a bright smear.
     */
    fun sprite(file: java.io.File, zoom: Float, centre: Float) =
        android.widget.ImageView(requireContext()).apply {
            scaleType = android.widget.ImageView.ScaleType.MATRIX
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            var bitmap: android.graphics.Bitmap? = null
            var requested = false
            fun fit() {
                val b = bitmap ?: return
                if (width == 0 || height == 0) return
                val scale = height * zoom / b.height
                imageMatrix =
                    android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(width * centre - b.width * scale / 2f, height * .04f)
                    }
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                fit()
                if (!requested && width > 0 && height > 0) {
                    requested = true
                    io.nekohasekai.sagernet.widget.noctwade.WallpaperImages.loadFile(file.path, (width * .8f).toInt(), (height * zoom).toInt(), false) {
                        if (it != null && isAttachedToWindow) {
                            bitmap = it
                            setImageBitmap(it)
                            fit()
                            if (NoctwadeMotion.enabled(context)) {
                                alpha = 0f
                                animate().alpha(1f).setDuration(260).setInterpolator(NoctwadeMotion.soft).start()
                            }
                        }
                    }
                }
            }
        }

    /** Tiles that show which item is chosen, by group ("bg", "ch", "halo"): a new choice only moves the mark. */
    private val selection = HashMap<String, MutableList<Pair<String, FrameLayout>>>()

    fun select(group: String, key: String) {
        for ((k, tile) in selection[group].orEmpty()) mark(tile, k == key, true)
    }

    /** A frame as tall as [ratio] times its width, so tiles keep their shape in any column width. */
    private class AspectFrame(context: android.content.Context, private val ratio: Float) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((width * ratio).toInt(), MeasureSpec.EXACTLY),
            )
        }
    }

    /** The chosen tile: an accent edge and a check that pops in; the others a faint edge. */
    private fun mark(tile: FrameLayout, selected: Boolean, animate: Boolean) {
        val context = tile.context
        val density = resources.displayMetrics.density
        tile.foreground =
            GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(
                    ((if (selected) 2.5f else 1f) * density).toInt().coerceAtLeast(1),
                    if (selected) NoctwadeTheme.accent(context) else ColorUtils.setAlphaComponent(NoctwadeTheme.text(context), 34),
                )
            }
        val check = tile.findViewWithTag<View>("check") ?: return
        check.animate().cancel()
        if (animate && NoctwadeMotion.enabled(context)) {
            if (selected) {
                check.visibility = View.VISIBLE
                check.alpha = 0f
                check.scaleX = .4f
                check.scaleY = .4f
                check.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(NoctwadeMotion.soft).start()
            } else if (check.visibility == View.VISIBLE)
                check.animate().alpha(0f).scaleX(.6f).scaleY(.6f).setDuration(160).withEndAction { check.visibility = View.GONE }.start()
        } else {
            check.visibility = if (selected) View.VISIBLE else View.GONE
            check.alpha = 1f
            check.scaleX = 1f
            check.scaleY = 1f
        }
    }

    /**
     * A tile: a picture (a background, a gradient, a character on her background, or a halo) with its name, rounded,
     * with a check on the chosen one. [glyph] marks a tile that adds something.
     */
    fun tile(
        group: String,
        key: String,
        title: String,
        subtitle: String,
        selected: Boolean,
        backdrop: String? = null,
        gradient: NoctwadeTheme.Backdrop.Gradient? = null,
        sprite: java.io.File? = null,
        halo: String? = null,
        glyph: TileGlyph.Kind? = null,
        longClick: (() -> Unit)? = null,
        click: (View) -> Unit,
    ): View {
        val context = requireContext()
        val tile =
            AspectFrame(context, 1.3f).apply {
                background = NoctwadeTheme.surfaceGradient(context, dp(18).toFloat())
                clipToOutline = true
                isClickable = true
                isFocusable = true
                contentDescription = title
                setOnClickListener { click(it) }
                if (longClick != null)
                    setOnLongClickListener {
                        longClick()
                        true
                    }
            }
        when {
            gradient != null ->
                tile.addView(
                    View(context).apply { background = GradientDrawable(NoctwadeTheme.orientation(gradient.angle), gradient.colors).apply { setDither(true) } },
                    FrameLayout.LayoutParams(-1, -1),
                )
            halo != null || glyph != null && backdrop == null -> {}
            else ->
                tile.addView(
                    SpaceBackdropView(context).apply {
                        previewMode = true
                        previewBackground = backdrop ?: NoctwadeTheme.BG_GRADIENT
                    },
                    FrameLayout.LayoutParams(-1, -1),
                )
        }
        if (sprite != null) tile.addView(sprite(sprite, 2.2f, .55f), FrameLayout.LayoutParams(-1, -1))
        if (halo != null)
            tile.addView(
                io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView(context).apply {
                    haloKey = halo
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener(null)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setEffectsVisible(true)
                },
                FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(6), dp(6), dp(6), dp(26)) },
            )
        if (glyph != null)
            tile.addView(
                View(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    // White over a picture or gradient, the accent over the plain tile.
                    val color = if (backdrop != null || gradient != null) Color.WHITE else NoctwadeTheme.accent(context)
                    background = TileGlyph(glyph, color, resources.displayMetrics.density)
                },
                // Centred in the picture, above the name.
                FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(if (subtitle.isBlank()) 34 else 46) },
            )
        tile.addView(label(context, title, subtitle), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        tile.addView(
            TextView(context).apply {
                tag = "check"
                text = "✓"
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(NoctwadeTheme.accent(context))
                        setStroke(dp(2), Color.WHITE)
                    }
            },
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(7), dp(7), 0) },
        )
        mark(tile, selected, false)
        NoctwadeMotion.press(tile)
        selection.getOrPut(group) { ArrayList() } += key to tile
        return tile
    }

    /** Tiles three to a row: everything in view, nothing to scroll sideways or to expand. */
    fun grid(body: LinearLayout, tiles: List<View>, columns: Int = 3) {
        val context = body.context
        for (chunk in tiles.chunked(columns)) {
            val line = LinearLayout(context)
            for (t in chunk) line.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            repeat(columns - chunk.size) {
                line.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            body.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = -dp(4)
                marginEnd = -dp(4)
            })
        }
    }
}
