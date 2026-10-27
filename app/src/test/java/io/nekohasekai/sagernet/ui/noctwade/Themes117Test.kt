package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.widget.noctwade.WallpaperImages
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
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
    fun allTenSuppliedWallpapersAreSelectableAndKeepTheirDimensions() {
        val controller = Robolectric.buildActivity(RoutingUiTest.Host::class.java).setup()
        val a = controller.get()
        val catalog =
            JSONArray(
                a.assets.open("noctwade-wallpapers/sources.json").bufferedReader().use {
                    it.readText()
                }
            )
        assertEquals(NoctwadeTheme.palettes.size, catalog.length())
        assertEquals(NoctwadeTheme.keys.size, NoctwadeTheme.keys.toSet().size)
        val ids = mutableSetOf<Int>()
        for (i in 0 until catalog.length()) {
            val entry = catalog.getJSONObject(i)
            val key = entry.getString("key")
            val palette = NoctwadeTheme.palettes.single { it.key == key }
            assertTrue(a.getString(palette.title).isNotBlank())
            val id = NoctwadeTheme.image(key)
            assertTrue(id != 0)
            assertTrue(ids.add(id))
            // Robolectric's legacy ImageIO decoder substitutes 100x100 for WebP. Inspect the
            // actual opaque VP8 resource instead; Android decodes it using its native codec.
            val bytes = a.resources.openRawResource(id).use { it.readBytes() }
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBPVP8 ", String(bytes, 8, 8, Charsets.US_ASCII))
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(entry.getInt("width"), header.getShort(26).toInt() and 0x3fff)
            assertEquals(entry.getInt("height"), header.getShort(28).toInt() and 0x3fff)
        }
        assertEquals(14, NoctwadeTheme.palettes.size)
        controller.pause().stop().destroy()
    }

    @Test
    fun everyGradientKeepsLabelsReadableAcrossBothEnds() {
        val controller = Robolectric.buildActivity(RoutingUiTest.Host::class.java).setup()
        val a = controller.get()
        for (palette in NoctwadeTheme.palettes) {
            NoctwadeTheme.prefs(a).edit().clear().putString("theme", palette.key).commit()
            NoctwadeTheme.apply(a)
            val start = NoctwadeTheme.gradientStart(a)
            val end = NoctwadeTheme.gradientEnd(a)
            assertNotEquals(start, end)
            for (background in listOf(start, end)) {
                assertEquals(255, Color.alpha(background))
                assertTrue(
                    "${palette.key}: title contrast",
                    ColorUtils.calculateContrast(NoctwadeTheme.text(a), background) >= 4.5,
                )
                assertTrue(
                    "${palette.key}: secondary label contrast",
                    ColorUtils.calculateContrast(NoctwadeTheme.muted(a), background) >= 4.5,
                )
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
