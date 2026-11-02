package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.noctwade.AtmosphereView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Saved colours and gradients, halo shaders, app icons, the new personalization sections, and the fog stripe. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w392dp-h850dp-notnight-xhdpi")
class Personalization123Test {
    private val app = RuntimeEnvironment.getApplication()
    private val out = System.getenv("NOCTWADE_SCREENS")?.let(::File)

    @After
    fun clean() {
        ThemePacks.root(app).deleteRecursively()
        HaloShaders.root(app).deleteRecursively()
        NoctwadeTheme.prefs(app).edit().clear().commit()
    }

    // --- saved colours and gradients ---

    @Test
    fun savedPalettesRoundTripAndDropJunk() {
        val list = listOf(NoctwadeTheme.SavedPalette(1, 5), NoctwadeTheme.SavedPalette(7, 2))
        assertEquals(list, NoctwadeTheme.parsePalettes(NoctwadeTheme.formatPalettes(list)))
        assertEquals(list, NoctwadeTheme.parsePalettes("1:5;junk;7:2;99999:1;3:x;1:5"))
        assertTrue(NoctwadeTheme.parsePalettes(null).isEmpty())

        NoctwadeTheme.addPalette(app, 4, 9)
        NoctwadeTheme.addPalette(app, 4, 9) // same pair twice stays one
        NoctwadeTheme.addPalette(app, 2, 3)
        assertEquals(listOf(NoctwadeTheme.SavedPalette(4, 9), NoctwadeTheme.SavedPalette(2, 3)), NoctwadeTheme.savedPalettes(app))
        NoctwadeTheme.removePalette(app, NoctwadeTheme.SavedPalette(4, 9))
        assertEquals(listOf(NoctwadeTheme.SavedPalette(2, 3)), NoctwadeTheme.savedPalettes(app))
        repeat(40) { NoctwadeTheme.addPalette(app, it % Swatches.colors.size, (it * 7) % Swatches.colors.size) }
        assertTrue(NoctwadeTheme.savedPalettes(app).size <= NoctwadeTheme.MAX_SAVED)
    }

    @Test
    fun savedGradientsRoundTrip() {
        val g = NoctwadeTheme.SavedGradient(intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt()), 135)
        NoctwadeTheme.addGradient(app, g)
        NoctwadeTheme.addGradient(app, NoctwadeTheme.SavedGradient(g.colors.copyOf(), 135)) // duplicate
        NoctwadeTheme.addGradient(app, NoctwadeTheme.SavedGradient(intArrayOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt(), 0xFFFF0000.toInt()), 45))
        val saved = NoctwadeTheme.savedGradients(app)
        assertEquals(2, saved.size)
        assertTrue(saved[0].sameAs(g))
        assertEquals(3, saved[1].colors.size)
        NoctwadeTheme.removeGradient(app, g)
        assertEquals(1, NoctwadeTheme.savedGradients(app).size)
        assertTrue(NoctwadeTheme.parseGradients("1|135;1,2|77;1,2|0").let { it.size == 1 && it[0].angle == 0 })
    }

    // --- halo shaders ---

    @Test
    fun haloShaderChecks() {
        fun refused(source: String) =
            try {
                HaloShaders.check(source)
                fail("accepted: ${source.take(30)}")
            } catch (_: HaloShaders.ImportException) {}
        refused("")
        refused("   \n")
        refused("uniform float iTime;") // no main
        refused("half4 main(float2 p){return half4(0);}" + "x".repeat(HaloShaders.MAX_SOURCE))
        refused("half4 main(float2 p){return half4(0);}\u0000")
        HaloShaders.check("half4 main(float2 p) { return half4(0); }")

        assertEquals("Pretty", HaloShaders.name("// name: Pretty\nhalf4 main(", "x"))
        assertEquals("fallback", HaloShaders.name("half4 main(", "fallback"))
        assertEquals(setOf("iTime", "iColor"), HaloShaders.uniforms("uniform float iTime;\nuniform  float4   iColor ;\nfloat a;"))
        assertEquals("my-halo-2", HaloShaders.idFor("My Halo 2.agsl"))
        assertEquals("halo", HaloShaders.idFor("...agsl"))
    }

    @Test
    fun importedShadersAreStoredListedAndDeleted() {
        val source = "// name: Mine\nhalf4 main(float2 p) { return half4(0); }"
        val entry = HaloShaders.import(app, ByteArrayInputStream(source.toByteArray()), "Mine Halo.agsl", validate = false)
        assertEquals("shader:mine-halo", entry.key)
        assertEquals("Mine", entry.name)
        assertEquals(listOf("shader:mine-halo"), HaloShaders.imported(app).map { it.key })
        assertEquals(source, HaloShaders.read(app, entry.key))
        // The stored key stays usable only where shaders run (Android 13+; the test device is API 35).
        assertEquals(entry.key, HaloShaders.usable(app, entry.key))
        HaloShaders.delete(app, entry.key)
        assertNull(HaloShaders.read(app, entry.key))
        assertEquals(HaloShaders.CLASSIC, HaloShaders.usable(app, entry.key))
        assertEquals(HaloShaders.CLASSIC, HaloShaders.usable(app, "shader:../../etc"))
        HaloShaders.delete(app, "shader:../../x") // must not leave the folder
    }

    @Test
    fun builtInShadersFollowTheContract() {
        val built = HaloShaders.builtIn(app)
        assertTrue("built-in shaders: $built", built.size >= 3)
        for (e in built) {
            val source = HaloShaders.read(app, e.key)
            assertNotNull(e.key, source)
            HaloShaders.check(source!!)
            val uniforms = HaloShaders.uniforms(source)
            assertTrue(e.key, "iResolution" in uniforms && "iTime" in uniforms && "iColor" in uniforms)
        }
        assertTrue(HaloShaders.example(app).contains("half4 main"))
    }

    // --- pack halo ---

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

    private fun png(): ByteArray {
        val b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        b.eraseColor(0xFFFFFFFF.toInt())
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun packJson(halo: String, id: String = "demo") =
        """{"format":1,"id":"$id","characters":[{"id":"mia","name":"Mia","sprites":{"idle":"idle.png"},"halo":$halo}]}"""

    @Test
    fun packCharactersCanCarryAHaloPictureOrShader() {
        val agsl = "half4 main(float2 p){ return half4(0); }".toByteArray()
        ThemePacks.import(
            app,
            ByteArrayInputStream(zip(mapOf("pack.json" to packJson("""{"shader":"halo.agsl","x":0.4,"y":0.02,"size":0.6}""").toByteArray(), "idle.png" to png(), "halo.agsl" to agsl))),
        )
        val h = ThemePacks.installed(app).single().characters.single().halo!!
        assertNotNull(h.shader)
        assertNull(h.image)
        assertEquals(.4f, h.x, .001f)
        assertEquals(.6f, h.size, .001f)

        ThemePacks.import(
            app,
            ByteArrayInputStream(zip(mapOf("pack.json" to packJson("""{"image":"ring.png"}""", "other").toByteArray(), "idle.png" to png(), "ring.png" to png()))),
        )
        val p = ThemePacks.installed(app).first { it.id == "other" }.characters.single().halo!!
        assertNotNull(p.image)
        assertNull(p.shader)
        assertEquals(.5f, p.x, .001f) // defaults
    }

    @Test
    fun packHaloShaderMustStayInsideThePack() {
        try {
            ThemePacks.import(
                app,
                ByteArrayInputStream(zip(mapOf("pack.json" to packJson("""{"shader":"../outside.agsl"}""").toByteArray(), "idle.png" to png()))),
            )
            fail("accepted a shader outside the pack")
        } catch (_: ThemePacks.ImportException) {}
    }

    // --- pack halos (rings for the button) ---

    @Test
    fun packHalosBecomeButtonRings() {
        val json = """{"format":1,"id":"rings","halos":[{"id":"one","name":{"en":"One","ru":"Один"},"image":"one.png"}]}"""
        ThemePacks.import(app, ByteArrayInputStream(zip(mapOf("pack.json" to json.toByteArray(), "one.png" to png()))))
        val pack = ThemePacks.installed(app).single()
        assertEquals(0, pack.characters.size)
        assertEquals(listOf("rings/one"), pack.halos.map { it.ref })
        assertEquals("pack:rings/one", HaloShaders.usable(app, "pack:rings/one"))
        assertEquals(HaloShaders.CLASSIC, HaloShaders.usable(app, "pack:rings/missing"))
        assertEquals(HaloShaders.CLASSIC, HaloShaders.usable(app, "pack:../x"))

        // A square picture fills the button square, so a point far from the disc and the rings is covered.
        fun cornerAlpha(key: String): Int {
            val view = io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView(app).apply { haloKey = key }
            view.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 240, 240)
            val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return bitmap.getPixel(30, 30) ushr 24
        }
        assertTrue(cornerAlpha("pack:rings/one") > 100)
        assertEquals(0, cornerAlpha(HaloShaders.NONE))
        assertEquals(1, ThemePacks.installed(app).single().halos.size)
    }

    @Test
    fun packHaloCanBeAShaderWithAPictureForOldPhones() {
        val agsl = "// name: Spin\nhalf4 main(float2 p){ return half4(0); }"
        val json = """{"format":1,"id":"shiny","halos":[{"id":"spin","name":"Spin","shader":"h/spin.agsl","image":"h/spin.png"},{"id":"flat","name":"Flat","shader":"h/spin.agsl"}]}"""
        ThemePacks.import(app, ByteArrayInputStream(zip(mapOf("pack.json" to json.toByteArray(), "h/spin.agsl" to agsl.toByteArray(), "h/spin.png" to png()))))
        val halos = ThemePacks.installed(app).single().halos
        assertNotNull(halos[0].shader)
        assertNotNull(halos[0].image)
        assertNull(halos[1].image)
        assertEquals(agsl, HaloShaders.read(app, "pack:shiny/spin"))
        assertEquals("pack:shiny/spin", HaloShaders.usable(app, "pack:shiny/spin"))
        // A halo needs a shader or a picture.
        try {
            ThemePacks.import(app, ByteArrayInputStream(zip(mapOf("pack.json" to """{"format":1,"id":"bad","halos":[{"id":"x"}]}""".toByteArray()))))
            fail("accepted a halo with nothing to draw")
        } catch (_: ThemePacks.ImportException) {}
    }

    @Test
    fun haloPackWithoutAnythingIsRefused() {
        try {
            ThemePacks.import(app, ByteArrayInputStream(zip(mapOf("pack.json" to """{"format":1,"id":"empty"}""".toByteArray()))))
            fail("accepted an empty pack")
        } catch (_: ThemePacks.ImportException) {}
    }

    // --- app icons ---

    @Test
    fun everyIconHasAnAliasAndOnlyTheDefaultIsEnabled() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val aliases = Regex("""<activity-alias\s+android:name="io\.nekohasekai\.sagernet\.AppIcon_(\w+)"\s+android:enabled="(\w+)"""").findAll(manifest).associate { it.groupValues[1] to it.groupValues[2] }
        // Removed icons keep a disabled alias so an update can never leave a phone without a launcher entry.
        assertEquals((AppIconTable.all.map { it.key } + AppIconTable.legacy).toSet(), aliases.keys)
        assertEquals(listOf("default"), aliases.filterValues { it == "true" }.keys.toList())
        assertTrue(AppIconTable.legacy.isNotEmpty() && AppIconTable.legacy.none { legacy -> AppIconTable.all.any { it.key == legacy } })
        AppIconSwitcher.repair(app) // must not throw, and leaves the default icon in place
        assertEquals("default", AppIconSwitcher.current(app))
        assertTrue("many icons to choose from", AppIconTable.all.size >= 15)
        // The launcher entry moved to the aliases; the activity itself keeps only the TV one.
        val activity = Regex("""<activity\s+android:name="io\.nekohasekai\.sagernet\.ui\.MainActivity".*?</activity>""", RegexOption.DOT_MATCHES_ALL).find(manifest)!!.value
        assertFalse(activity.contains("android.intent.category.LAUNCHER\""))
        assertTrue(activity.contains("LEANBACK_LAUNCHER"))
        for (icon in AppIconTable.all) assertNotNull(icon.key, app.resources.getResourceName(icon.preview))
        assertEquals("default", AppIconSwitcher.current(app))
    }

    // --- screens ---

    private fun settle() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun views(view: View, into: MutableList<View> = ArrayList()): List<View> {
        into += view
        if (view is ViewGroup) for (i in 0 until view.childCount) views(view.getChildAt(i), into)
        return into
    }

    private fun personalization(): Pair<DesignScreensTest.Host, NestedScrollView> {
        NoctwadeTheme.prefs(app).edit().clear().putBoolean("animations", false).putBoolean("live", false).putString("eco", "off").putString("palette", "sky").commit()
        val a = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup().get()
        a.supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, NoctwadePersonalizationFragment()).commitNow()
        settle()
        val scroll = views(a.window.decorView).filterIsInstance<NestedScrollView>().first()
        return a to scroll
    }

    @Test
    fun galleryIsTheFirstBackgroundTileAndListsExpand() {
        val (a, scroll) = personalization()
        val all = views(scroll)
        val order = all.mapNotNull { it.contentDescription?.toString() }
        val gallery = order.indexOf(a.getString(R.string.nw_bg_gallery))
        val mine = order.indexOf(a.getString(R.string.nw_bg_custom_gradient))
        val theme = order.indexOf(a.getString(R.string.nw_bg_gradient))
        assertTrue("gallery tile present", gallery >= 0)
        assertTrue("gallery, then my gradient, then the theme gradient: $gallery $mine $theme", gallery < mine && mine < theme)

        val text = all.filterIsInstance<TextView>().map { it.text.toString() }
        for (needed in listOf(R.string.nw_colors_add, R.string.nw_halo_title, R.string.nw_icons_title, R.string.nw_halo_add))
            assertTrue(a.getString(needed), text.contains(a.getString(needed)))

        // No list scrolls sideways or needs expanding: the tiles are a grid of equal cells.
        val tile = views(scroll).first { it.contentDescription?.toString() == a.getString(R.string.nw_halo_classic) }
        assertEquals(0, tile.layoutParams.width)
        assertTrue(views(scroll).none { it is android.widget.HorizontalScrollView })
    }

    @Test
    fun appIconsAreAnOpenFourColumnGridNearTheBottom() {
        val (a, scroll) = personalization()
        val all = views(scroll)
        // No expand link for the icons, and no removed icons.
        val names = AppIconTable.all.map { a.getString(it.title) }
        for (gone in listOf("night_star", "sky_star", "sunset_star", "lime_star")) assertFalse(AppIconTable.all.any { it.key == gone })
        // An icon cell holds the picture frame first (the colour chips with the same names hold a plain circle).
        val cells = all.filter { it is android.widget.LinearLayout && it.contentDescription?.toString() in names && it.getChildAt(0) is android.widget.FrameLayout }
        assertEquals(AppIconTable.all.size, cells.size)
        assertEquals(4, (cells.first().parent as ViewGroup).childCount)
        // Almost the last block: after the economy mode block, only the language button follows.
        val order = all.filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(order.indexOf(a.getString(R.string.nw_icons_title)) > order.indexOf(a.getString(R.string.nw_eco_mode)))
        assertTrue(order.indexOf(a.getString(R.string.nw_icons_title)) > order.indexOf(a.getString(R.string.nw_animation_settings)))
        // Names are always shown (and slide sideways by themselves when they are too long for the cell).
        val labels = all.filterIsInstance<TextView>().filter { it.text.toString() in names && it.ellipsize == android.text.TextUtils.TruncateAt.MARQUEE }
        assertEquals(AppIconTable.all.size, labels.size)
        assertTrue(labels.all { it.isSelected && it.visibility == View.VISIBLE })
    }

    @Test
    fun personalizationIsFourPagesInOnePanel() {
        val (a, scroll) = personalization()
        val all = views(scroll)
        fun shown(title: Int) = all.filterIsInstance<TextView>().any { it.text.toString() == a.getString(title) && it.isShown }
        // One panel (the control Routing uses) with four tabs, no sideways scrolling.
        val panel = all.filterIsInstance<io.nekohasekai.sagernet.widget.noctwade.SegmentedControl>().first { it.childCount == 4 }
        assertEquals(4, panel.childCount)
        assertTrue(all.none { it is android.widget.HorizontalScrollView })
        // The colours page is first: the halo and icon sections are on other pages.
        assertTrue(shown(R.string.nw_mode_title))
        assertFalse(shown(R.string.nw_halo_title))
        assertFalse(shown(R.string.nw_icons_title))
        panel.getChildAt(2).performClick()
        settle()
        assertTrue(shown(R.string.nw_halo_title))
        assertFalse(shown(R.string.nw_mode_title))
        panel.getChildAt(3).performClick()
        assertTrue(shown(R.string.nw_icons_title))
        assertTrue(shown(R.string.nw_animation_settings))
        panel.getChildAt(0).performClick()
        assertTrue(shown(R.string.nw_mode_title))
    }

    @Test
    fun allColoursAreInAGridWithAddAtTheBottom() {
        val (a, scroll) = personalization()
        val all = views(scroll)
        val chips = NoctwadeTheme.presets.map { a.getString(it.title) }
        // Every theme is on the page at once, four to a row, none inside a horizontal scroller.
        // (App icons have names like "Sky" too; their cells start with a picture frame, a colour chip with a plain circle.)
        val cells = all.filter { it is android.widget.LinearLayout && it.contentDescription?.toString() in chips && it.getChildAt(0) !is android.widget.FrameLayout }
        assertEquals(chips.size, cells.size)
        assertEquals(4, (cells.first().parent as ViewGroup).childCount)
        var parent: android.view.ViewParent? = cells.first().parent
        while (parent != null && parent !== scroll) {
            assertFalse(parent is android.widget.HorizontalScrollView)
            parent = parent.parent
        }
        // "Add colour" comes after the last colour row.
        val add = all.filterIsInstance<TextView>().first { it.text.toString() == a.getString(R.string.nw_colors_add) }
        val order = views(scroll)
        assertTrue(order.indexOf(add) > order.indexOf(cells.last()))
    }

    @Test
    fun shadersImportFromAZipInOneGo() {
        fun zipOf(files: Map<String, String>) = zip(files.mapValues { it.value.toByteArray() })
        val ok = "// name: %s\nhalf4 main(float2 p) { return half4(0); }"
        val archive =
            zipOf(
                mapOf(
                    "pack/one.agsl" to ok.format("One"),
                    "pack/two.AGSL" to ok.format("Two"),
                    "other/one.agsl" to ok.format("One again"), // same file name in another folder: kept, renamed
                    "pack/broken.agsl" to "uniform float iTime;", // no main(): skipped, the rest still go in
                    "__MACOSX/pack/.one.agsl" to "junk",
                    "readme.txt" to "not a shader",
                )
            )
        val done = HaloShaders.importAny(app, ByteArrayInputStream(archive), "halos.zip", validate = false)
        assertEquals(3, done.added.size)
        assertEquals(listOf("broken.agsl"), done.skipped.map { it.first })
        assertEquals(setOf("One", "Two", "One again"), done.added.map { it.name }.toSet())
        assertEquals(3, HaloShaders.imported(app).size)
        // A plain .agsl still works and an empty archive is refused.
        assertEquals(1, HaloShaders.importAny(app, ByteArrayInputStream(ok.format("Solo").toByteArray()), "solo.agsl", validate = false).added.size)
        try {
            HaloShaders.importAny(app, ByteArrayInputStream(zipOf(mapOf("a.txt" to "x"))), "none.zip", validate = false)
            fail("accepted an archive without shaders")
        } catch (_: HaloShaders.ImportException) {}
    }

    @Test
    fun theGuideSplitsIntoSectionsWithAPrompt() {
        for (lang in listOf("en", "ru")) {
            val text = app.assets.open("halo-guide.$lang.md").use { it.readBytes().decodeToString() }
            val sections = HaloGuideDialog.sections(text)
            assertTrue("$lang sections: ${sections.map { it.title }}", sections.size >= 7)
            val prompt = HaloGuideDialog.prompt(text)
            assertTrue(lang, prompt.contains("half4 main(float2 fragCoord)") && prompt.length > 600)
            assertFalse(lang, prompt.startsWith("-----"))
        }
    }
    // --- deleting single entries of a pack ---

    @Test
    fun removingOneEntryKeepsTheRestAndItsFiles() {
        val json =
            """{"format":1,"id":"duo","backgrounds":[{"id":"a","day":"a.png"},{"id":"b","day":"b.png"}],""" +
                """"characters":[{"id":"mia","name":"Mia","sprites":{"idle":"mia.png"},"background":"a"}]}"""
        ThemePacks.import(app, ByteArrayInputStream(zip(mapOf("pack.json" to json.toByteArray(), "a.png" to png(), "b.png" to png(), "mia.png" to png()))))
        val dir = File(ThemePacks.root(app), "duo")
        assertTrue(ThemePacks.removeEntry(app, ThemePacks.Kind.BACKGROUNDS, "duo", "a"))
        val pack = ThemePacks.installed(app).single()
        assertEquals(listOf("b"), pack.backgrounds.map { it.id })
        assertEquals(1, pack.characters.size)
        // (Windows keeps decoded test images open, so the deleted file itself is not asserted here.)
        assertTrue(File(dir, "b.png").exists())
        assertFalse(File(dir, "pack.json").readText().contains("\"a.png\""))
        assertFalse(ThemePacks.removeEntry(app, ThemePacks.Kind.BACKGROUNDS, "duo", "a")) // already gone
        assertFalse(ThemePacks.removeEntry(app, ThemePacks.Kind.BACKGROUNDS, "../duo", "b"))
        assertTrue(ThemePacks.removeEntry(app, ThemePacks.Kind.CHARACTERS, "duo", "mia"))
        assertTrue(ThemePacks.removeEntry(app, ThemePacks.Kind.BACKGROUNDS, "duo", "b"))
        assertTrue("an empty pack goes away", ThemePacks.installed(app).isEmpty())
    }

    @Test
    fun theHaloGuideShipsInBothLanguagesWithAPrompt() {
        for ((lang, marker) in listOf("en" to "PROMPT FOR AN AI", "ru" to "ПРОМТ ДЛЯ НЕЙРОСЕТИ")) {
            val text = app.assets.open("halo-guide.$lang.md").use { it.readBytes().decodeToString() }
            assertTrue(lang, text.contains(marker))
            assertTrue(lang, text.contains("half4 main(float2 fragCoord)"))
            for (uniform in listOf("iResolution", "iTime", "iColor2", "iConnected", "iConnecting")) assertTrue("$lang $uniform", text.contains(uniform))
        }
    }

    @Test
    fun colourNamesAreCentredUnderTheirColour() {
        val (a, scroll) = personalization()
        val name = views(scroll).filterIsInstance<TextView>().first { it.text.toString() == a.getString(R.string.nw_theme_p_sky) }
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, name.layoutParams.width)
        assertEquals(View.TEXT_ALIGNMENT_CENTER, name.textAlignment)
    }

    @Test
    fun fogHasNoHardHorizontalEdge() {
        NoctwadeTheme.prefs(app).edit().clear().putBoolean("live", false).putString("mode", "dark").commit()
        val view = AtmosphereView(app) { _, _ -> }
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, 800)
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        // Average opacity per row: the fog fades smoothly, so neighbouring rows never differ by much.
        val alphas = DoubleArray(800) { y -> (0 until 400 step 4).sumOf { x -> (bitmap.getPixel(x, y) ushr 24).toDouble() } / 100.0 }
        var worst = 0.0
        for (y in 1 until 800) worst = maxOf(worst, kotlin.math.abs(alphas[y] - alphas[y - 1]))
        assertTrue("largest row-to-row opacity jump $worst", worst < 3.0)
    }

    /** NOCTWADE_HALOPACK=<a pack with halos .zip>: every ring on a dark card, as drawn around the button. */
    @Test
    fun captureHaloRings() {
        val file = System.getenv("NOCTWADE_HALOPACK")?.let(::File)?.takeIf { it.isFile }
        org.junit.Assume.assumeTrue(out != null && file != null)
        out!!.mkdirs()
        file!!.inputStream().use { ThemePacks.import(app, it) }
        val halos = ThemePacks.installed(app).flatMap { it.halos }
        val cell = 360
        val columns = 4
        val sheet = Bitmap.createBitmap(columns * cell, ((halos.size + columns - 1) / columns) * cell, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF14182C.toInt())
        halos.forEachIndexed { i, h ->
            val view = io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView(app).apply { haloKey = HaloShaders.PACK + h.ref }
            view.measure(View.MeasureSpec.makeMeasureSpec(cell, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(cell, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, cell, cell)
            val save = canvas.save()
            canvas.translate(((i % columns) * cell).toFloat(), ((i / columns) * cell).toFloat())
            view.draw(canvas)
            canvas.restoreToCount(save)
        }
        File(out, "p123-halo-rings.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A "top" pack picture fades out completely: no unmasked row stays at its (fractional) bottom edge. */
    @Test
    fun topPictureLeavesNoLineAtItsEdge() {
        val type = Class.forName("io.nekohasekai.sagernet.widget.noctwade.SpaceBackdropView\$PictureView")
        val view = type.getDeclaredConstructor(android.content.Context::class.java).apply { isAccessible = true }.newInstance(app) as View
        val picture = Bitmap.createBitmap(300, 227, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF203040.toInt()) }
        type.getDeclaredMethod("set", Bitmap::class.java, ThemePacks.Fit::class.java, Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(view, picture, ThemePacks.Fit.TOP, 0f)
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, 800)
        val out = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(out))
        // 227 * 400 / 300 = 302.67: everything from row 300 down must be (nearly) clear.
        for (y in 300 until 310) for (x in 0 until 400 step 20) assertTrue("row $y alpha ${out.getPixel(x, y) ushr 24}", (out.getPixel(x, y) ushr 24) <= 3)
        // and the picture is still there higher up
        assertTrue((out.getPixel(200, 60) ushr 24) > 200)
    }

    /** The guide dialog and a delete question, drawn as cards (NOCTWADE_SCREENS). */
    @Test
    fun captureDialogs() {
        org.junit.Assume.assumeTrue(out != null)
        out!!.mkdirs()
        NoctwadeTheme.prefs(app).edit().clear().putBoolean("live", false).putString("palette", "forest").putString("mode", "dark").commit()
        val a = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup().get()
        val guide = a.assets.open("halo-guide.ru.md").use { it.readBytes().decodeToString() }
        val card = HaloGuideDialog.build(a, guide, {}, {}, {})
        val width = (360 * a.resources.displayMetrics.density).toInt()
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
        val bitmap = Bitmap.createBitmap(card.measuredWidth, card.measuredHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF0B1512.toInt())
        card.draw(Canvas(bitmap))
        File(out, "p123-guide-dialog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The prompt box scrolls out of view in the card; draw its section on its own too.
        val content = ((card.getChildAt(1)) as android.widget.ScrollView).getChildAt(0)
        content.measure(View.MeasureSpec.makeMeasureSpec(width - (44 * a.resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val tall = Bitmap.createBitmap(content.measuredWidth, content.measuredHeight, Bitmap.Config.ARGB_8888)
        tall.eraseColor(0xFF16241F.toInt())
        content.draw(Canvas(tall))
        File(out, "p123-guide-full.png").outputStream().use { tall.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // A settings text field opens a plain AlertDialog (androidx preference): it should wear the same card.
        val field = android.widget.EditText(a).apply { setText("2080") }
        val dialog =
            androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Порт прокси")
                .setMessage("Локальный порт SOCKS и HTTP")
                .setView(field)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        shadowOf(Looper.getMainLooper()).idle()
        val decor = dialog.window!!.decorView
        val screen = a.resources.displayMetrics.widthPixels
        decor.measure(View.MeasureSpec.makeMeasureSpec(screen, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        val shot = Bitmap.createBitmap(decor.measuredWidth, decor.measuredHeight, Bitmap.Config.ARGB_8888)
        shot.eraseColor(0xFF0B1512.toInt())
        decor.draw(Canvas(shot))
        File(out, "p123-settings-dialog.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dialog.dismiss()
    }
    @Test
    fun captureScreens() {
        org.junit.Assume.assumeTrue(out != null)
        out!!.mkdirs()
        // Optional packs make the pages look like on the phone.
        for (env in listOf("NOCTWADE_PACK", "NOCTWADE_HALOPACK"))
            System.getenv(env)?.let(::File)?.takeIf { it.isFile }?.inputStream()?.use { ThemePacks.import(app, it) }
        val (a, scroll) = personalization()
        NoctwadeTheme.prefs(app).edit().putString("mode", "dark").commit()
        val panel = views(scroll).filterIsInstance<io.nekohasekai.sagernet.widget.noctwade.SegmentedControl>().first { it.childCount == 4 }
        for (page in 0 until 4) {
            panel.getChildAt(page).performClick()
            settle()
            val body = scroll.getChildAt(0)
            body.measure(View.MeasureSpec.makeMeasureSpec(scroll.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            body.layout(0, 0, body.measuredWidth, body.measuredHeight)
            settle()
            val bitmap = Bitmap.createBitmap(body.width, minOf(body.height, 4800), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).also { c ->
                c.drawColor(NoctwadeTheme.background(a))
                body.draw(c)
            }
            File(out, "p123-page-$page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

}
