package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeHomeBinding
import io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w392dp-h850dp-notnight-xhdpi")
class AppearanceTest {
    private val app = RuntimeEnvironment.getApplication()
    private val out = System.getenv("NOCTWADE_SCREENS")?.let(::File)

    @After
    fun clean() {
        ThemePacks.root(app).deleteRecursively()
    }

    private fun png(color: Int, w: Int = 60, h: Int = 120): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { z ->
                    for ((name, data) in files) {
                        z.putNextEntry(ZipEntry(name))
                        z.write(data)
                        z.closeEntry()
                    }
                }
            }
            .toByteArray()

    private val packJson =
        """
        {"format":1,"id":"demo","name":{"en":"Demo","ru":"Демо"},"author":"Test",
         "backgrounds":[{"id":"room","name":{"en":"Room","ru":"Комната"},"day":"bg/day.png","night":"bg/night.png","fit":"top"}],
         "characters":[{"id":"neko","name":{"en":"Neko","ru":"Неко"},"subtitle":"Cat club","background":"room",
           "sprites":{"idle":"c/idle.png","blink":"c/blink.png"},
           "lines":{"ru":{"idle":["Привет!"],"connected":["Подключено, ня!"],"empty":"Добавь профиль, ня"},
                    "en":{"idle":["Hi!"],"connected":["Connected, nya!"],"empty":"Add a profile, nya"}}}]}
        """
            .trimIndent()

    private fun demo(extra: Map<String, ByteArray> = emptyMap()) =
        zip(
            mapOf(
                "pack.json" to packJson.toByteArray(),
                "bg/day.png" to png(0xFF88CCFF.toInt(), 120, 160),
                "bg/night.png" to png(0xFF223355.toInt(), 120, 160),
                "c/idle.png" to png(0xFFFFAACC.toInt()),
                "c/blink.png" to png(0xFFFFAACC.toInt()),
                "notes.txt" to "ignored".toByteArray(),
            ) + extra
        )

    @Test
    fun importsAPackAndReadsItInTheDeviceLanguage() {
        val pack = ThemePacks.import(app, ByteArrayInputStream(demo()))
        assertEquals("demo", pack.id)
        assertEquals("Демо", pack.name)
        val installed = ThemePacks.installed(app).single()
        assertEquals("Комната", installed.backgrounds.single().name)
        val c = installed.characters.single()
        assertEquals("Неко", c.name)
        assertEquals(listOf("Подключено, ня!"), c.lines.connected)
        assertEquals(listOf("Добавь профиль, ня"), c.lines.empty)
        assertNotNull(c.sprites.blink)
        assertNull(c.sprites.talk)
        assertEquals("room", c.background)
        assertFalse(File(installed.dir, "notes.txt").exists())
        assertEquals(1, ThemePacks.installed(app).size)
    }

    @Test
    fun rejectsUnsafeOrBrokenPacks() {
        fun refused(bytes: ByteArray) {
            try {
                ThemePacks.import(app, ByteArrayInputStream(bytes))
                fail("imported")
            } catch (_: ThemePacks.ImportException) {}
            assertTrue(ThemePacks.installed(app).isEmpty())
            assertFalse(File(app.filesDir, "evil.png").exists())
        }
        refused(demo(mapOf("../evil.png" to png(0))))
        refused(zip(mapOf("pack.json" to packJson.replace("\"demo\"", "\"../x\"").toByteArray())))
        refused(zip(mapOf("pack.json" to packJson.toByteArray()))) // referenced images missing
        refused(demo(mapOf("c/idle.png" to "not an image".toByteArray())))
        refused(zip(mapOf("readme.png" to png(0))))
    }

    @Test
    fun choicesAreIndependentAndFallBack() {
        ThemePacks.import(app, ByteArrayInputStream(demo()))
        val prefs = NoctwadeTheme.prefs(app)
        prefs.edit().clear().putString("palette", "ruby").putString("background", "demo/room").putString("character", "demo/neko").commit()
        val controller = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup()
        val a = controller.get()
        assertEquals("ruby", NoctwadeTheme.palette(a))
        assertEquals("Неко", NoctwadeTheme.character(a)?.name)
        val b = NoctwadeTheme.backdrop(a) as NoctwadeTheme.Backdrop.Picture
        assertEquals("day.png", b.file.name)
        assertEquals(ThemePacks.Fit.TOP, b.fit)
        controller.pause().stop().destroy()
        // A deleted pack falls back to the gradient and no character.
        ThemePacks.delete(app, "demo")
        assertTrue(NoctwadeTheme.backdrop(app) is NoctwadeTheme.Backdrop.Gradient)
        assertNull(NoctwadeTheme.character(app))
        prefs.edit().putString("palette", "nonsense").commit()
        assertEquals(NoctwadeTheme.presets.first().key, NoctwadeTheme.palette(app))
    }

    @Test
    fun homeDialogueUsesThePackCharacter() {
        ThemePacks.import(app, ByteArrayInputStream(demo()))
        NoctwadeTheme.prefs(app).edit().clear().putString("character", "demo/neko").putBoolean("animations", false).putBoolean("live", false).commit()
        val controller = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup()
        val a = controller.get()
        val ui = home(a, BaseService.State.Connected)
        settle()
        val texts = texts(ui.root)
        assertTrue("Неко" in texts)
        assertTrue("Подключено, ня!" in texts)
        assertTrue(a.getString(R.string.nw_connected) + " · 12:04" in texts)
        controller.pause().stop().destroy()
    }

    private var lastHome: CharacterHome? = null

    /** A line of three or four rows used to make the box taller and push the connect button down. */
    @Test
    fun longLinesDoNotMoveTheConnectButton() {
        ThemePacks.import(app, ByteArrayInputStream(demo()))
        NoctwadeTheme.prefs(app).edit().clear().putString("character", "demo/neko").putBoolean("animations", false).putBoolean("live", false).commit()
        val controller = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup()
        val a = controller.get()
        val ui = home(a, BaseService.State.Idle)
        settle()
        val say = CharacterHome::class.java.getDeclaredMethod("say", String::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
        fun buttonTop(text: String): Int {
            say.invoke(lastHome, text, 0L)
            settle()
            val at = IntArray(2)
            ui.homePower.getLocationInWindow(at)
            return at[1]
        }
        val short = buttonTop("Привет!")
        val two = buttonTop("Всё готово. Подключение уже ждёт тебя, нажми кнопку!")
        val long =
            buttonTop(
                "Сегодня столько дел: отчёты, подписки, маршруты, проверка серверов и ещё немного бумаг из библиотеки. " +
                    "Давай сначала подключимся, а потом всё разберём вместе, хорошо? Я подожду тебя здесь."
            )
        assertEquals(short, two)
        assertEquals(short, long)
        controller.pause().stop().destroy()
    }

    private fun texts(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun home(a: DesignScreensTest.Host, state: BaseService.State): LayoutNoctwadeHomeBinding {
        val holder = a.main.fragmentHolder as ViewGroup
        val ui = LayoutNoctwadeHomeBinding.inflate(a.layoutInflater, holder, false)
        holder.addView(ui.root)
        val d = a.resources.displayMetrics.density
        ui.homeContent.setPadding((20 * d).toInt(), (28 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        ui.homeServerName.setTitle("Netherlands · Amsterdam 2")
        ui.homeServerIcon.setCountry("NL")
        ui.homeServerDetail.text = "Noctwade Premium · VLESS · REALITY"
        ui.homePing.text = a.getString(R.string.nw_ping, 42)
        ui.homeQuota.apply {
            quotaName.text = "Noctwade Premium"
            quotaAmount.text = "440 ГБ"
            quotaAllowance.text = "/ ∞"
            quotaDetail.text = "Данные провайдера · обновлено 23.10.26, 01:12"
            quotaMeter.visibility = View.GONE
        }
        val character = NoctwadeTheme.character(a)
        if (character != null) {
            ui.homeStatus.visibility = View.GONE
            ui.homeSubtitle.visibility = View.GONE
            lastHome = CharacterHome(character, ui.root as FrameLayout, ui.homeContent, ui.homeScroll, ui.homeContent.indexOfChild(ui.homeStatus)).apply {
                attach(false)
                render(state, true)
                if (state == BaseService.State.Connected) setTimer("12:04")
            }
        } else {
            ui.homeStatus.setText(if (state == BaseService.State.Connected) R.string.nw_connected else R.string.nw_offline)
            ui.homeSubtitle.visibility = View.GONE
            if (state == BaseService.State.Connected) {
                ui.homeTimer.visibility = View.VISIBLE
                ui.homeTimer.text = "12:04"
            }
        }
        CharacterHome.styleCard(ui.homeServer)
        CharacterHome.styleCard(ui.homeQuota.root)
        CharacterHome.styleHeader(ui.homeHeader.headerMenu, ui.homeHeader.headerAdd)
        ui.root.findViewById<OrbitConnectView>(R.id.home_power).setState(state)
        ui.homeBackdrop.setEffectsVisible(false)
        return ui
    }

    private fun settle() {
        repeat(60) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Screenshots: NOCTWADE_SCREENS=<folder> [NOCTWADE_PACK=<a theme pack .zip>] gradlew ... --tests '*AppearanceTest*' */
    @Test
    fun captureScreens() {
        org.junit.Assume.assumeTrue(out != null)
        out!!.mkdirs()
        val packFile = System.getenv("NOCTWADE_PACK")?.let(::File)?.takeIf { it.isFile }
        val cases =
            mutableListOf(
                Triple("plain-sky-light", mapOf("palette" to "sky", "mode" to "light"), BaseService.State.Idle),
                Triple("plain-amethyst-dark", mapOf("palette" to "amethyst", "mode" to "dark"), BaseService.State.Connected),
                Triple(
                    "custom-gradient-dark",
                    mapOf("palette" to "sunset", "mode" to "dark", "background" to NoctwadeTheme.BG_CUSTOM, "gradient_colors" to "${0xFFFF7A9C.toInt()},${0xFF7A8CFF.toInt()},${0xFF5FD3C8.toInt()}"),
                    BaseService.State.Idle,
                ),
            )
        // A pack given in NOCTWADE_PACK: its first characters, each on her own background, light and dark, and one mixed.
        if (packFile != null) {
            val pack = packFile.inputStream().use { ThemePacks.import(app, it) }
            val characters = ThemePacks.installed(app).first { it.id == pack.id }.characters
            val backgrounds = ThemePacks.installed(app).first { it.id == pack.id }.backgrounds
            characters.take(3).forEachIndexed { i, c ->
                val dark = i == 1
                cases += Triple(
                    "pack-$i-${if (dark) "dark" else "light"}",
                    mapOf("palette" to if (dark) "amethyst" else "sky", "mode" to if (dark) "dark" else "light", "background" to (c.background?.let { "${pack.id}/$it" } ?: NoctwadeTheme.BG_GRADIENT), "character" to c.ref),
                    if (i == 0) BaseService.State.Connected else BaseService.State.Idle,
                )
            }
            if (characters.size > 1 && backgrounds.size > 1)
                cases += Triple("pack-mixed", mapOf("palette" to "mint", "mode" to "light", "background" to backgrounds.last().ref, "character" to characters[1].ref), BaseService.State.Idle)
        }
        for ((name, prefs, state) in cases) {
            NoctwadeTheme.prefs(app).edit().clear().putString("eco", "off").putBoolean("animations", false).putBoolean("live", false).apply {
                prefs.forEach { (k, v) -> putString(k, v) }
            }.commit()
            val controller = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup()
            val a = controller.get()
            home(a, state)
            settle()
            save(a, "ap-$name")
            controller.pause().stop().destroy()
        }
        for (mode in listOf("light", "dark")) {
            NoctwadeTheme.prefs(app).edit().clear().putBoolean("animations", false).putBoolean("live", false).putString("mode", mode).putString("palette", "sky").commit()
            val controller = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup()
            val a = controller.get()
            a.supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, NoctwadePersonalizationFragment()).commitNow()
            settle()
            save(a, "ap-personalization-$mode")
            controller.pause().stop().destroy()
        }
    }

    private fun save(a: DesignScreensTest.Host, name: String) {
        val root = a.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
