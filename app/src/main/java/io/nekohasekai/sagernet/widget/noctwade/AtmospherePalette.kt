package io.nekohasekai.sagernet.widget.noctwade

import androidx.core.graphics.ColorUtils

/**
 * Colors of the animated atmosphere for each theme key.
 *
 * [sky] is the top/bottom of the procedural sky (canvas-only themes); [ribbons] are the three
 * ribbon colors; [particle] colors stars, petals and meteors.
 */
internal data class AtmospherePalette(
    val skyTop: Int,
    val skyBottom: Int,
    val particle: Int,
    val ribbons: IntArray,
    val fog: Int,
    val mist: Int,
) {
    companion object {
        private const val DEFAULT_MIST = 0x506D51AF

        fun of(variant: String): AtmospherePalette =
            when (variant) {
                "aurora" -> canvas(0xFF0C272F, 0xFF071114, 0xFF75E4C9, 0xFF5B81EE, 0xFFB78FEA)
                "ocean" -> canvas(0xFF122B50, 0xFF07111E, 0xFF52C8EA, 0xFF4076D0, 0xFF8A92E6)
                "sunset" -> canvas(0xFF382131, 0xFF140C12, 0xFFF49B79, 0xFFCE648A, 0xFFCA92DB)
                "prism" -> canvas(0xFF242043, 0xFF0D0916, 0xFFEF8FBF, 0xFF65CFDE, 0xFFB590F3)
                "cyber" -> photo(0x66040712, 0xFF91BFFF)
                "panther" -> photo(0x66060615, 0xFFA696F5)
                "dubai" -> photo(0x660A0819, 0xFFE2BAFF)
                "orbit" -> photo(0x660C0718, 0xFFF0C2FF)
                "planet" -> photo(0x66060619, 0xFFBBAAFF)
                "alpine" -> photo(0x66040B1B, 0xFFAAC5FF)
                "sakura" -> photo(0x77000000, 0xFFFFB2D0, fog = 0x38F7B0CC, mist = 0x45EC9DBE)
                "china" -> photo(0x77000000, 0xFFF0BF74, fog = 0x2FF4BE78, mist = 0x40D29A6F)
                "max" -> photo(0x77000000, 0xFFB9ACFF, fog = 0x556759E4, mist = 0x5C8259D2)
                "noctwade" -> photo(0x77000000, 0xFFCFACF2, fog = 0x288341CA)
                else -> photo(0x77000000, 0xFFCFACF2)
            }

        /** Canvas-only theme: own sky gradient and three colored ribbons. */
        private fun canvas(top: Long, bottom: Long, a: Long, b: Long, c: Long): AtmospherePalette {
            val particle = a.toInt()
            return AtmospherePalette(
                skyTop = top.toInt(),
                skyBottom = bottom.toInt(),
                particle = particle,
                ribbons = intArrayOf(a.toInt(), b.toInt(), c.toInt()),
                fog = ColorUtils.setAlphaComponent(particle, 32),
                mist = DEFAULT_MIST,
            )
        }

        /** Photo wallpaper theme: the picture already has the sky, so only light effects. */
        private fun photo(
            shade: Long,
            particle: Long,
            fog: Int = ColorUtils.setAlphaComponent(particle.toInt(), 32),
            mist: Int = DEFAULT_MIST,
        ) =
            AtmospherePalette(
                skyTop = 0,
                skyBottom = shade.toInt(),
                particle = particle.toInt(),
                ribbons = intArrayOf(particle.toInt(), 0, 0),
                fog = fog,
                mist = mist,
            )
    }
}
