package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import java.io.File

/**
 * Appearance: a colour theme (a preset or two custom swatches), light / dark / system, a background
 * (the theme gradient, a custom gradient, a gallery picture or a theme-pack picture) and an
 * optional theme-pack character. The four choices are independent.
 */
internal object NoctwadeTheme {
    data class Palette(val key: String, val title: Int, val overlay: Int, val image: Int = 0)

    data class Preset(val key: String, val title: Int, val accent: Int, val second: Int)

    private fun swatch(hue: Int, tone: Int) = hue * Swatches.TONES + tone

    val presets =
        listOf(
            Preset("sky", R.string.nw_theme_p_sky, swatch(7, 1), swatch(0, 0)),
            Preset("sakura", R.string.nw_theme_p_sakura, swatch(0, 1), swatch(10, 0)),
            Preset("lavender", R.string.nw_theme_p_lavender, swatch(10, 1), swatch(7, 0)),
            Preset("mint", R.string.nw_theme_p_mint, swatch(5, 1), swatch(7, 0)),
            Preset("sunset", R.string.nw_theme_p_sunset, swatch(1, 1), swatch(3, 0)),
            Preset("ocean", R.string.nw_theme_p_ocean, swatch(8, 1), swatch(6, 0)),
            Preset("amethyst", R.string.nw_theme_p_amethyst, swatch(9, 1), swatch(11, 0)),
            Preset("gold", R.string.nw_theme_p_gold, swatch(3, 1), swatch(1, 0)),
            Preset("ruby", R.string.nw_theme_p_ruby, swatch(0, 2), swatch(3, 0)),
            Preset("forest", R.string.nw_theme_p_forest, swatch(5, 2), swatch(4, 0)),
            Preset("midnight", R.string.nw_theme_p_midnight, swatch(9, 2), swatch(8, 0)),
            Preset("candy", R.string.nw_theme_p_candy, swatch(11, 1), swatch(7, 0)),
        )

    val palettes = presets.map { Palette(it.key, it.title, Swatches.light[it.accent]) }

    val keys
        get() = palettes.map { it.key }

    const val CUSTOM = "custom"
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val BG_GRADIENT = "gradient"
    const val BG_CUSTOM = "custom_gradient"
    const val BG_GALLERY = "gallery"

    /** Gradient directions offered for custom gradients, in degrees. */
    val angles = intArrayOf(90, 135, 0, 45)

    fun prefs(context: Context) =
        context.getSharedPreferences("noctwade_appearance", Context.MODE_PRIVATE)

    /** The colour theme: a preset key or [CUSTOM]. */
    fun palette(context: Context): String {
        val key = prefs(context).getString("palette", null)
        return if (key == CUSTOM || presets.any { it.key == key }) key!! else presets.first().key
    }

    private fun preset(context: Context) = presets.firstOrNull { it.key == palette(context) }

    fun accentSwatch(context: Context): Int =
        preset(context)?.accent
            ?: prefs(context).getInt("accent_swatch", presets.first().accent).takeIf(Swatches::valid)
            ?: presets.first().accent

    fun secondSwatch(context: Context): Int =
        preset(context)?.second
            ?: prefs(context).getInt("second_swatch", presets.first().second).takeIf(Swatches::valid)
            ?: presets.first().second

    fun mode(context: Context) = prefs(context).getString("mode", MODE_SYSTEM) ?: MODE_SYSTEM

    /** Light/dark forced by the user, or null to follow the system. */
    fun forcedNight(context: Context): Boolean? =
        when (mode(context)) {
            MODE_LIGHT -> false
            MODE_DARK -> true
            else -> null
        }

    /**
     * Dark or light as shown. "System" reads the device's own setting (not an activity's configuration, which AppCompat
     * may still have overridden from a forced mode while the screen is being recreated: switching quickly could then
     * leave a light app on a dark phone).
     */
    fun night(context: Context): Boolean = forcedNight(context) ?: systemNight()

    fun systemNight(): Boolean =
        (android.content.res.Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun backgroundKey(context: Context) = prefs(context).getString("background", BG_GRADIENT) ?: BG_GRADIENT

    fun galleryFile(context: Context) = File(context.filesDir, "backgrounds/gallery.jpg")

    fun gradientColors(context: Context): IntArray =
        prefs(context)
            .getString("gradient_colors", null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.take(4)
            ?.takeIf { it.size >= 2 }
            ?.toIntArray()
            ?: intArrayOf(Swatches.colors[accentSwatch(context)], Swatches.colors[secondSwatch(context)])

    fun gradientAngle(context: Context) = prefs(context).getInt("gradient_angle", 135).takeIf { it in angles } ?: 135

    /** A custom gradient softened towards the palette so text stays readable in both modes. */
    fun softened(context: Context, colors: IntArray, angle: Int): Backdrop.Gradient {
        val bg = background(context)
        return Backdrop.Gradient(
            colors
                .map { c ->
                    if (light(context)) ColorUtils.blendARGB(c, Color.WHITE, .5f) else ColorUtils.blendARGB(c, bg, .6f)
                }
                .toIntArray(),
            angle,
        )
    }

    /** A colour theme the user made: two swatch indices. */
    data class SavedPalette(val accent: Int, val second: Int)

    /** A gradient the user made: up to four colours and a direction. */
    class SavedGradient(val colors: IntArray, val angle: Int) {
        fun sameAs(other: SavedGradient) = angle == other.angle && colors.contentEquals(other.colors)
    }

    const val MAX_SAVED = 24

    fun savedPalettes(context: Context): List<SavedPalette> = parsePalettes(prefs(context).getString("saved_palettes", null))

    fun addPalette(context: Context, accent: Int, second: Int) {
        val all = savedPalettes(context).filterNot { it.accent == accent && it.second == second } + SavedPalette(accent, second)
        prefs(context).edit().putString("saved_palettes", formatPalettes(all.takeLast(MAX_SAVED))).commit()
    }

    fun removePalette(context: Context, item: SavedPalette) {
        prefs(context).edit().putString("saved_palettes", formatPalettes(savedPalettes(context) - item)).commit()
    }

    fun savedGradients(context: Context): List<SavedGradient> = parseGradients(prefs(context).getString("saved_gradients", null))

    fun addGradient(context: Context, item: SavedGradient) {
        val all = savedGradients(context).filterNot { it.sameAs(item) } + item
        prefs(context).edit().putString("saved_gradients", formatGradients(all.takeLast(MAX_SAVED))).commit()
    }

    fun removeGradient(context: Context, item: SavedGradient) {
        prefs(context).edit().putString("saved_gradients", formatGradients(savedGradients(context).filterNot { it.sameAs(item) })).commit()
    }

    internal fun formatPalettes(list: List<SavedPalette>) = list.joinToString(";") { "${it.accent}:${it.second}" }

    internal fun parsePalettes(raw: String?): List<SavedPalette> =
        raw.orEmpty().split(';').mapNotNull {
            val (a, s) = it.split(':').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            val accent = a.toIntOrNull()?.takeIf(Swatches::valid) ?: return@mapNotNull null
            val second = s.toIntOrNull()?.takeIf(Swatches::valid) ?: return@mapNotNull null
            SavedPalette(accent, second)
        }.distinct().take(MAX_SAVED)

    internal fun formatGradients(list: List<SavedGradient>) =
        list.joinToString(";") { g -> g.colors.joinToString(",") + "|" + g.angle }

    internal fun parseGradients(raw: String?): List<SavedGradient> =
        raw.orEmpty().split(';').mapNotNull {
            val (c, a) = it.split('|').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            val colors = c.split(',').mapNotNull { v -> v.trim().toIntOrNull() }.take(4)
            val angle = a.toIntOrNull()?.takeIf { v -> v in angles } ?: return@mapNotNull null
            if (colors.size < 2) null else SavedGradient(colors.toIntArray(), angle)
        }.take(MAX_SAVED)

    sealed class Backdrop {
        class Gradient(val colors: IntArray, val angle: Int) : Backdrop()

        class Picture(val file: File, val fit: ThemePacks.Fit, val dim: Float) : Backdrop()
    }

    fun orientation(angle: Int) =
        when (angle) {
            90 -> GradientDrawable.Orientation.TOP_BOTTOM
            0 -> GradientDrawable.Orientation.LEFT_RIGHT
            45 -> GradientDrawable.Orientation.BL_TR
            else -> GradientDrawable.Orientation.TL_BR
        }

    /** The theme's own soft gradient: the default background. */
    fun defaultGradient(context: Context): Backdrop.Gradient {
        val bg = background(context)
        val strong = if (light(context)) .16f else .24f
        return Backdrop.Gradient(
            intArrayOf(
                ColorUtils.blendARGB(bg, accent(context), strong),
                bg,
                ColorUtils.blendARGB(bg, secondaryAccent(context), strong * .8f),
            ),
            135,
        )
    }

    /** Background for [key] (default: the chosen one) in the current light/dark mode. */
    fun backdrop(context: Context, key: String = backgroundKey(context)): Backdrop {
        val night = night(context)
        return when {
            key == BG_GALLERY ->
                galleryFile(context).takeIf { it.isFile }?.let {
                    Backdrop.Picture(it, ThemePacks.Fit.COVER, if (night) .38f else 0f)
                }
            key == BG_CUSTOM -> softened(context, gradientColors(context), gradientAngle(context))
            key.contains('/') ->
                ThemePacks.background(context, key)?.let { b ->
                    val file = if (night) b.night ?: b.day else b.day
                    Backdrop.Picture(file, b.fit, if (night && b.night == null) .38f else 0f)
                }
            else -> null
        } ?: defaultGradient(context)
    }

    fun characterRef(context: Context) = prefs(context).getString("character", "") ?: ""

    /** The chosen theme-pack character, or null (Home then shows the plain status). */
    fun character(context: Context): ThemePacks.PackCharacter? =
        characterRef(context).takeIf { it.isNotEmpty() }?.let { ThemePacks.character(context, it) }

    /** The halo around the connect button: a canvas style or a shader key (see [HaloShaders]). */
    fun halo(context: Context) = prefs(context).getString("halo", HaloShaders.CLASSIC) ?: HaloShaders.CLASSIC

    /** The halo floating above the chosen character's head, when the pack gives her one. */
    fun characterHalo(context: Context) = prefs(context).getBoolean("ch_halo", true)

    fun lines(context: Context) = prefs(context).getBoolean("ch_lines", true)

    /** The "hide the interface" button in the top bar of Home. */
    fun focusButton(context: Context) = prefs(context).getBoolean("focus_button", true)

    fun animations(context: Context) = prefs(context).getBoolean("animations", true)

    fun live(context: Context) = prefs(context).getBoolean("live", true)

    fun speed(context: Context) = prefs(context).getFloat("speed", 1f).coerceIn(.5f, 1.5f)

    fun intensity(context: Context) = prefs(context).getFloat("intensity", .65f).coerceIn(.2f, 1f)

    fun apply(context: Context) {
        context.theme.applyStyle(R.style.ThemeOverlay_Noctwade, true)
        val night = night(context)
        val accent = accentSwatch(context)
        val second = secondSwatch(context)
        context.theme.applyStyle(if (night) Swatches.dark[accent] else Swatches.light[accent], true)
        context.theme.applyStyle(if (night) Swatches.secondDark[second] else Swatches.secondLight[second], true)
        // Soft, unhurried motion and lighter dialogs.
        context.theme.applyStyle(R.style.ThemeOverlay_Noctwade_SoftStyle, true)
    }
    fun color(context: Context, attr: Int, fallback: Int = Color.WHITE): Int {
        val v = TypedValue()
        return if (
            context.theme.resolveAttribute(attr, v, true) &&
                v.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        )
            v.data
        else fallback
    }

    fun accent(context: Context) = color(context, R.attr.nwAccent, 0xFFCE9CFF.toInt())

    fun surface(context: Context) = color(context, R.attr.nwSurface, 0xFF14111D.toInt())

    fun surfaceHigh(context: Context) = color(context, R.attr.nwSurfaceHigh, surface(context))

    fun stroke(context: Context) = color(context, R.attr.nwStroke, 0xFF493259.toInt())

    fun text(context: Context) = color(context, R.attr.nwText, 0xFFF8F3FF.toInt())

    fun muted(context: Context) = color(context, R.attr.nwMuted, 0xFFB2A4C9.toInt())

    fun background(context: Context) = color(context, R.attr.nwBackground, 0xFF08070D.toInt())

    /** Light palette: dark system bar icons, light scrims. */
    fun light(context: Context) = ColorUtils.calculateLuminance(background(context)) > .5

    fun gradientStart(context: Context) = color(context, R.attr.nwGradientStart, surface(context))

    fun gradientEnd(context: Context) = color(context, R.attr.nwGradientEnd, surface(context))

    fun secondaryAccent(context: Context) =
        color(context, R.attr.nwAccentSecondary, accent(context))

    fun surfaceGradient(context: Context, radius: Float = 0f) =
        GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(gradientStart(context), gradientEnd(context)),
            )
            .apply { cornerRadius = radius }

    /** Translucent card surface with a faint light edge (same look as @drawable/nw_card). */
    fun glassSurface(context: Context, radius: Float, opacity: Float = .86f) =
        GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    ColorUtils.setAlphaComponent(gradientStart(context), (opacity * 255).toInt()),
                    ColorUtils.setAlphaComponent(gradientEnd(context), (opacity * 255).toInt()),
                ),
            )
            .apply {
                cornerRadius = radius
                setStroke(
                    (context.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                    ColorUtils.setAlphaComponent(text(context), 23),
                )
            }

    /** Opaque and image-free; background artwork stays on Home/Servers only. */
    fun menuGradient(context: Context) =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ColorUtils.blendARGB(background(context), gradientStart(context), .22f),
                background(context),
                ColorUtils.blendARGB(background(context), gradientEnd(context), .12f),
            ),
        )
}
