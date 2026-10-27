package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R

internal object NoctwadeTheme {
    data class Palette(val key: String, val title: Int, val overlay: Int, val image: Int = 0)

    val palettes =
        listOf(
            Palette(
                "noctwade",
                R.string.app_name,
                R.style.ThemeOverlay_Noctwade_Default,
                R.drawable.nw_wallpaper_noctwade,
            ),
            Palette(
                "sakura",
                R.string.nw_theme_sakura,
                R.style.ThemeOverlay_Noctwade_Sakura,
                R.drawable.nw_wallpaper_sakura,
            ),
            Palette(
                "china",
                R.string.nw_theme_china,
                R.style.ThemeOverlay_Noctwade_China,
                R.drawable.nw_wallpaper_china,
            ),
            Palette(
                "max",
                R.string.nw_theme_max,
                R.style.ThemeOverlay_Noctwade_Max,
                R.drawable.nw_wallpaper_max,
            ),
            Palette(
                "aurora",
                R.string.nw_theme_aurora,
                R.style.ThemeOverlay_Noctwade_Aurora,
                R.drawable.nw_wallpaper_aurora,
            ),
            Palette(
                "ocean",
                R.string.nw_theme_ocean,
                R.style.ThemeOverlay_Noctwade_Ocean,
                R.drawable.nw_wallpaper_ocean,
            ),
            Palette(
                "sunset",
                R.string.nw_theme_sunset,
                R.style.ThemeOverlay_Noctwade_Sunset,
                R.drawable.nw_wallpaper_sunset,
            ),
            Palette(
                "prism",
                R.string.nw_theme_prism,
                R.style.ThemeOverlay_Noctwade_Prism,
                R.drawable.nw_wallpaper_prism,
            ),
            Palette(
                "cyber",
                R.string.nw_theme_cyber,
                R.style.ThemeOverlay_Noctwade_Cyber,
                R.drawable.nw_wallpaper_cyber,
            ),
            Palette(
                "panther",
                R.string.nw_theme_panther,
                R.style.ThemeOverlay_Noctwade_Panther,
                R.drawable.nw_wallpaper_panther,
            ),
            Palette(
                "dubai",
                R.string.nw_theme_dubai,
                R.style.ThemeOverlay_Noctwade_Dubai,
                R.drawable.nw_wallpaper_dubai,
            ),
            Palette(
                "orbit",
                R.string.nw_theme_orbit,
                R.style.ThemeOverlay_Noctwade_Orbit,
                R.drawable.nw_wallpaper_orbit,
            ),
            Palette(
                "planet",
                R.string.nw_theme_planet,
                R.style.ThemeOverlay_Noctwade_Planet,
                R.drawable.nw_wallpaper_planet,
            ),
            Palette(
                "alpine",
                R.string.nw_theme_alpine,
                R.style.ThemeOverlay_Noctwade_Alpine,
                R.drawable.nw_wallpaper_alpine,
            ),
        )
    val keys
        get() = palettes.map { it.key }

    fun image(key: String): Int = palettes.firstOrNull { it.key == key }?.image ?: 0

    fun prefs(context: Context) =
        context.getSharedPreferences("noctwade_appearance", Context.MODE_PRIVATE)

    fun name(context: Context) = prefs(context).getString("theme", "noctwade") ?: "noctwade"

    fun animations(context: Context) = prefs(context).getBoolean("animations", true)

    fun live(context: Context) = prefs(context).getBoolean("live", true)

    fun speed(context: Context) = prefs(context).getFloat("speed", 1f).coerceIn(.5f, 1.5f)

    fun intensity(context: Context) = prefs(context).getFloat("intensity", .65f).coerceIn(.2f, 1f)

    fun apply(context: Context) {
        context.theme.applyStyle(R.style.ThemeOverlay_Noctwade, true)
        context.theme.applyStyle(
            (palettes.firstOrNull { it.key == name(context) } ?: palettes.first()).overlay,
            true,
        )
        val accents =
            intArrayOf(
                R.style.NwAccent_Violet,
                R.style.NwAccent_Rose,
                R.style.NwAccent_Blue,
                R.style.NwAccent_Mint,
                R.style.NwAccent_Gold,
                R.style.NwAccent_Coral,
            )
        val index = prefs(context).getInt("accent", -1)
        if (index in accents.indices) context.theme.applyStyle(accents[index], true)
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

    fun stroke(context: Context) = color(context, R.attr.nwStroke, 0xFF493259.toInt())

    fun text(context: Context) = color(context, R.attr.nwText, 0xFFF8F3FF.toInt())

    fun muted(context: Context) = color(context, R.attr.nwMuted, 0xFFB2A4C9.toInt())

    fun background(context: Context) = color(context, R.attr.nwBackground, 0xFF08070D.toInt())

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
