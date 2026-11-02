package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeHomeBinding
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression: a pack picture used to stop short of the right edge (child size set after measure). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w392dp-h850dp-notnight-xhdpi")
class BackdropLayoutTest {
    private val app = RuntimeEnvironment.getApplication()

    @After
    fun clean() {
        ThemePacks.root(app).deleteRecursively()
    }

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun pack(): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { z ->
                    z.putNextEntry(ZipEntry("pack.json"))
                    z.write("""{"format":1,"id":"demo","backgrounds":[{"id":"room","day":"day.png","fit":"top"}]}""".toByteArray())
                    z.closeEntry()
                    z.putNextEntry(ZipEntry("day.png"))
                    z.write(png(0xFF88CCFF.toInt()))
                    z.closeEntry()
                }
            }
            .toByteArray()

    @Test
    fun pictureCoversTheWholeWidthPlusDriftMargin() {
        ThemePacks.import(app, ByteArrayInputStream(pack()))
        NoctwadeTheme.prefs(app).edit().clear().putString("background", "demo/room").putBoolean("animations", false).putBoolean("live", false).commit()
        val a = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup().get()
        val holder = a.main.fragmentHolder as ViewGroup
        val ui = LayoutNoctwadeHomeBinding.inflate(a.layoutInflater, holder, false)
        holder.addView(ui.root)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        }
        val backdrop = ui.homeBackdrop
        val picture = backdrop.getChildAt(0)
        val margin = (16 * a.resources.displayMetrics.density).toInt()
        assertEquals(backdrop.width + margin * 2, picture.width)
        assertEquals(-margin, picture.left)
        assertEquals(-margin, picture.left)
        assertEquals((picture.layoutParams as FrameLayout.LayoutParams).height, picture.height)
    }
}
