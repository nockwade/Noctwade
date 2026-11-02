package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.widget.noctwade.WallpaperImages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Themes117Test {
    @Test
    fun presetsAndSwatchesAreConsistent() {
        assertEquals(12, NoctwadeTheme.palettes.size)
        assertEquals(NoctwadeTheme.keys.size, NoctwadeTheme.keys.toSet().size)
        assertEquals(Swatches.HUES * Swatches.TONES, Swatches.colors.size)
        for (list in listOf(Swatches.light, Swatches.dark, Swatches.secondLight, Swatches.secondDark))
            assertEquals(Swatches.colors.size, list.size)
        for (p in NoctwadeTheme.presets) {
            assertTrue(Swatches.valid(p.accent) && Swatches.valid(p.second))
            assertEquals(p.accent, Swatches.nearest(Swatches.colors[p.accent]))
        }
    }

    @Test
    fun everyGradientKeepsLabelsReadableAcrossBothEnds() {
        val controller = Robolectric.buildActivity(RoutingUiTest.Host::class.java).setup()
        val a = controller.get()
        // Every main colour with every second colour would be 1296 themes; the second colour only
        // touches the far gradient end, so check each main colour with its worst second colour.
        for (accent in Swatches.colors.indices) for (second in listOf(0, 13, 28, 35)) {
            NoctwadeTheme.prefs(a)
                .edit()
                .clear()
                .putString("palette", NoctwadeTheme.CUSTOM)
                .putInt("accent_swatch", accent)
                .putInt("second_swatch", second)
                .commit()
            NoctwadeTheme.apply(a)
            val start = NoctwadeTheme.gradientStart(a)
            val end = NoctwadeTheme.gradientEnd(a)
            for (background in listOf(start, end)) {
                assertEquals(255, Color.alpha(background))
                assertTrue("$accent/$second: title", ColorUtils.calculateContrast(NoctwadeTheme.text(a), background) >= 4.5)
                assertTrue("$accent/$second: secondary", ColorUtils.calculateContrast(NoctwadeTheme.muted(a), background) >= 4.0)
            }
        }
        controller.pause().stop().destroy()
    }

    @Test
    fun previewSamplingReducesMemoryWithoutDownsamplingFullScreenArtwork() {
        assertEquals(4, WallpaperImages.sampleSize(856, 1836, 192, 192))
        assertEquals(4, WallpaperImages.sampleSize(932, 2070, 192, 192))
        assertEquals(1, WallpaperImages.sampleSize(856, 1836, 1080, 2400))
        assertEquals(2, WallpaperImages.sampleSize(1920, 4000, 900, 1800))
        assertEquals(1, WallpaperImages.sampleSize(100, 200, 192, 192))
    }
}
