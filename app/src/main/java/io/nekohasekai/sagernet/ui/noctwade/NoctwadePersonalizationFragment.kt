package io.nekohasekai.sagernet.ui.noctwade

import android.content.BroadcastReceiver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.SettingsFragment
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeEco
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import io.nekohasekai.sagernet.widget.noctwade.SpaceBackdropView
import java.util.Locale
import kotlin.math.roundToInt

/** Colours, light/dark/system, backgrounds, theme-pack characters and packs, animation and economy mode. */
class NoctwadePersonalizationFragment : Fragment() {

    private var preview: SpaceBackdropView? = null

    private val importPack =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importPack(uri)
        }
    private val pickImage =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importGallery(uri)
        }
    private val importHalo =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importHalo(uri)
        }

    private val exportHalo =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportHalo(uri)
        }
    private var ecoStatus: TextView? = null
    private var ecoReceiver: BroadcastReceiver? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        shownNight = NoctwadeTheme.night(context)
        shownPalette = paletteSignature(context)
        val scroll =
            NestedScrollView(context).apply {
                background = NoctwadeTheme.menuGradient(context)
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
            }
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(6), dp(24), dp(24))
            }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))

        addHero(body)
        // The screen is split into pages so each stays short; the chosen page is remembered.
        val tabs =
            listOf(
                "colors" to R.string.nw_tab_colors,
                "background" to R.string.nw_tab_background,
                "halo" to R.string.nw_tab_halo,
                "more" to R.string.nw_tab_more,
            )
        pages = tabs.associate { (key, _) -> key to LinearLayout(context).apply { orientation = LinearLayout.VERTICAL } }
        addTabs(body, tabs)
        for (page in pages.values) body.addView(page, LinearLayout.LayoutParams(-1, -2))
        buildPages()

        body.addView(
            MaterialButton(context).apply {
                setText(R.string.nw_language_notifications)
                setOnClickListener {
                    (parentFragment as? SettingsFragment)?.openScreen(
                        NoctwadeInterfaceSettingsFragment::class.java.name,
                        getString(R.string.nw_language_notifications),
                        Bundle(),
                    )
                }
            }
        )
        scrollView = scroll
        // After a recreate (a new colour theme) the page stays where it was instead of jumping to the top.
        if (pendingScroll > 0) {
            val y = pendingScroll
            pendingScroll = 0
            scroll.doOnLayout { scroll.scrollTo(0, y) }
        }
        return scroll
    }

    private companion object {
        var lastTab = "colors"
        var pendingScroll = 0
    }

    private var scrollView: NestedScrollView? = null
    private var pages: Map<String, LinearLayout> = emptyMap()
    private val kit by lazy { PersonalizationTiles(requireContext()) }
    private var hero: FrameLayout? = null

    /** The look the screen was built with: only a change of these needs the screen recreated. */
    private var shownNight = false
    private var shownPalette = ""

    private fun paletteSignature(context: android.content.Context) =
        "${NoctwadeTheme.palette(context)}:${NoctwadeTheme.accentSwatch(context)}:${NoctwadeTheme.secondSwatch(context)}"

    /** Pages that hold their content; the others are filled when first shown. */
    private val built = HashSet<String>()

    /**
     * Empties every page and fills the one in view; called again to show added or removed items without recreating
     * the screen. Building all four at once (dozens of picture tiles) made a theme switch slow; the rest follow when
     * opened, or quietly once the screen has settled ([buildRestLater]).
     */
    private fun buildPages() {
        kit.reset()
        built.clear()
        for (page in pages.values) page.removeAllViews()
        build(lastTab)
        buildRestLater()
    }

    private fun build(key: String) {
        if (!built.add(key)) return
        val page = pages[key] ?: return
        page.removeAllViews()
        when (key) {
            "colors" -> {
                addMode(page)
                addColors(page)
            }
            "background" -> {
                addBackgrounds(page)
                addCharacters(page)
                addPacks(page)
            }
            "halo" -> addHalo(page)
            "more" -> {
                addHomeScreen(page)
                addAnimationSettings(page)
                addEconomyMode(page)
                addAppIcons(page)
            }
        }
    }

    /** After the theme reveal has played, one hidden page per idle moment, so switching tabs is instant later. */
    private fun buildRestLater() {
        val root = scrollView ?: pages.values.firstOrNull() ?: return
        root.postDelayed(
            {
                if (view == null) return@postDelayed
                android.os.Looper.myQueue().addIdleHandler {
                    if (view == null) return@addIdleHandler false
                    val next = pages.keys.firstOrNull { it !in built } ?: return@addIdleHandler false
                    build(next)
                    pages.keys.any { it !in built }
                }
            },
            900,
        )
    }

    /** One panel with all the pages as equal tabs (the same control as Routing's Rules / Apps / DNS). */
    private fun addTabs(body: LinearLayout, tabs: List<Pair<String, Int>>) {
        val control =
            io.nekohasekai.sagernet.widget.noctwade.SegmentedControl(body.context).apply {
                setTabs(tabs.map { it.second })
                select(tabs.indexOfFirst { it.first == lastTab }.coerceAtLeast(0), false)
                onSelected = { index ->
                    val wasHalo = lastTab == "halo"
                    lastTab = tabs[index].first
                    showPage()
                    // The preview shows the halo on the Halo page and the character elsewhere.
                    if (wasHalo != (lastTab == "halo")) refreshHero(true)
                }
            }
        lastTab = tabs[control.selectedIndex].first
        showPage()
        body.addView(control, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
    }

    private fun showPage() {
        if (pages.isNotEmpty() && built.isNotEmpty()) build(lastTab)
        for ((key, page) in pages) page.visibility = if (key == lastTab) View.VISIBLE else View.GONE
    }

    private fun caption(body: LinearLayout, text: String) {
        body.addView(
            TextView(body.context).apply {
                this.text = text
                textSize = 13f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(16), dp(4), dp(10))
            }
        )
    }

    /** Section title row. */
    private fun header(body: LinearLayout, text: String): LinearLayout {
        val row = LinearLayout(body.context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(
            TextView(body.context).apply {
                this.text = text
                textSize = 13f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(16), dp(4), dp(10))
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    /** The preview on top: the chosen background with the chosen character, as on Home; on the Halo page, the halo. */
    private fun addHero(body: LinearLayout) {
        val context = body.context
        hero =
            FrameLayout(context).apply {
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(22).toFloat()
                        setColor(NoctwadeTheme.background(context))
                    }
                clipToOutline = true
            }
        body.addView(hero, LinearLayout.LayoutParams(-1, dp(190)))
        refreshHero(false)
    }

    /** Builds the preview again and cross-fades to it: a new background or character never blinks the screen. */
    private fun refreshHero(animate: Boolean) {
        val frame = hero ?: return
        val context = frame.context
        val content = FrameLayout(context)
        val backdrop = SpaceBackdropView(context).apply { previewMode = true }
        content.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        if (lastTab == "halo")
            content.addView(
                io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView(context).apply {
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener(null)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setEffectsVisible(true)
                },
                FrameLayout.LayoutParams(dp(164), dp(164), Gravity.CENTER),
            )
        else
            NoctwadeTheme.character(context)?.let { character ->
                content.addView(kit.sprite(character.sprites.idle, 2.1f, .66f), FrameLayout.LayoutParams(-1, -1))
                content.addView(kit.label(context, character.name, character.subtitle), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        val old = (0 until frame.childCount).map { frame.getChildAt(it) }
        preview?.setEffectsVisible(false)
        preview = backdrop
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        if (isResumed) backdrop.setEffectsVisible(true)
        if (animate && old.isNotEmpty() && NoctwadeMotion.enabled(context)) {
            content.alpha = 0f
            content
                .animate()
                .alpha(1f)
                .setDuration(340)
                .setInterpolator(NoctwadeMotion.soft)
                .withEndAction { old.forEach { frame.removeView(it) } }
                .start()
        } else old.forEach { frame.removeView(it) }
    }

    private fun themeChanged(context: android.content.Context) =
        NoctwadeTheme.night(context) != shownNight || paletteSignature(context) != shownPalette

    /** Recreates the screen with the new look, which spreads out from [origin] over the old one. */
    private fun recreateWithReveal(origin: View?) {
        val activity = requireActivity()
        pendingScroll = scrollView?.scrollY ?: 0
        ThemeReveal.capture(activity, origin) {
            if (activity.isFinishing || activity.isDestroyed) return@capture
            io.nekohasekai.sagernet.utils.Theme.applyNightTheme()
            activity.recreate()
        }
    }

    /** A new colour theme or light/dark: recreated (with the reveal) only when the look really changes. */
    private fun applyTheme(origin: View?) {
        val activity = requireActivity()
        if (themeChanged(activity)) recreateWithReveal(origin)
        // Dark -> System on a dark phone (and the like) looks the same: nothing to redraw, no blink.
        else io.nekohasekai.sagernet.utils.Theme.applyNightTheme()
    }

    /** Items were added or removed: the pages are rebuilt in place (no recreate, no jump to the top). */
    private fun applyAppearance(origin: View? = null) {
        val activity = requireActivity()
        if (themeChanged(activity)) return recreateWithReveal(origin)
        buildPages()
        refreshHero(true)
    }

    private fun addMode(body: LinearLayout) {
        val context = body.context
        caption(body, getString(R.string.nw_mode_title))
        val modes =
            listOf(
                NoctwadeTheme.MODE_LIGHT to R.string.nw_mode_light,
                NoctwadeTheme.MODE_DARK to R.string.nw_mode_dark,
                NoctwadeTheme.MODE_SYSTEM to R.string.nw_mode_system,
            )
        val control =
            io.nekohasekai.sagernet.widget.noctwade.SegmentedControl(context).apply {
                setTabs(modes.map { it.second })
                select(modes.indexOfFirst { it.first == NoctwadeTheme.mode(context) }.coerceAtLeast(0), false)
            }
        control.onSelected = { index ->
            NoctwadeTheme.prefs(context).edit().putString("mode", modes[index].first).commit()
            // At once: the new look spreads out from the tapped option (a pause here felt like lag).
            control.post { if (isAdded) applyTheme(control.getChildAt(index)) }
        }
        body.addView(control, LinearLayout.LayoutParams(-1, dp(50)))
    }

    private fun useCustomPalette(accent: Int, second: Int, origin: View?) {
        NoctwadeTheme.prefs(requireContext())
            .edit()
            .putString("palette", NoctwadeTheme.CUSTOM)
            .putInt("accent_swatch", accent)
            .putInt("second_swatch", second)
            .commit()
        applyTheme(origin)
    }

    /** Opens the picker for a new accent + second colour pair, saves it and applies it. */
    private fun addCustomPalette(origin: View?) {
        val context = requireContext()
        AppearancePickers.palette(context, NoctwadeTheme.accentSwatch(context), NoctwadeTheme.secondSwatch(context)) { a, s ->
            NoctwadeTheme.addPalette(context, a, s)
            useCustomPalette(a, s, origin)
        }
    }

    private fun choosePreset(key: String, origin: View?) {
        if (NoctwadeTheme.palette(requireContext()) != key) {
            NoctwadeTheme.prefs(requireContext()).edit().putString("palette", key).commit()
            applyTheme(origin)
        }
    }

    private fun confirmDelete(message: String, delete: () -> Unit) =
        AppearancePickers.confirm(requireContext(), message, getString(R.string.nw_delete), delete)

    /** All colour themes at once in a grid (no sideways scrolling), your own after them, "add" at the very bottom. */
    private fun addColors(body: LinearLayout) {
        val context = body.context
        val current = NoctwadeTheme.palette(context)
        val a = NoctwadeTheme.accentSwatch(context)
        val s = NoctwadeTheme.secondSwatch(context)
        val saved = NoctwadeTheme.savedPalettes(context)
        caption(body, getString(R.string.nw_colors_title))
        val columns = 4
        fun chip(title: String, colors: IntArray, selected: Boolean, longClick: (() -> Unit)?, click: (View) -> Unit): View =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
                isClickable = true
                isFocusable = true
                contentDescription = title
                setOnClickListener { click(getChildAt(0)) }
                if (longClick != null)
                    setOnLongClickListener {
                        longClick()
                        true
                    }
                addView(
                    View(context).apply {
                        background =
                            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                                shape = GradientDrawable.OVAL
                                setStroke(dp(if (selected) 3 else 1), if (selected) NoctwadeTheme.text(context) else NoctwadeTheme.stroke(context))
                            }
                    },
                    LinearLayout.LayoutParams(dp(46), dp(46)),
                )
                // The name is as wide as the cell and centred under the colour.
                addView(
                    TextView(context).apply {
                        text = title
                        textSize = 11f
                        gravity = Gravity.CENTER_HORIZONTAL
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setTextColor(if (selected) NoctwadeTheme.text(context) else NoctwadeTheme.muted(context))
                        setPadding(dp(2), dp(5), dp(2), 0)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(-1, -2),
                )
                NoctwadeMotion.press(this)
            }
        val chips = ArrayList<View>()
        for (p in NoctwadeTheme.presets)
            chips += chip(getString(p.title), intArrayOf(Swatches.colors[p.accent], Swatches.colors[p.second]), current == p.key, null) { choosePreset(p.key, it) }
        val custom = current == NoctwadeTheme.CUSTOM
        // A custom colour that was never saved (made before saving existed) is shown first among yours.
        if (custom && saved.none { it.accent == a && it.second == s })
            chips += chip(getString(R.string.nw_colors_custom), intArrayOf(Swatches.colors[a], Swatches.colors[s]), true, null) { addCustomPalette(it) }
        saved.forEachIndexed { i, p ->
            chips +=
                chip(
                    getString(R.string.nw_colors_saved, i + 1),
                    intArrayOf(Swatches.colors[p.accent], Swatches.colors[p.second]),
                    custom && a == p.accent && s == p.second,
                    {
                        confirmDelete(getString(R.string.nw_colors_delete)) {
                            NoctwadeTheme.removePalette(context, p)
                            applyAppearance()
                        }
                    },
                ) {
                    useCustomPalette(p.accent, p.second, it)
                }
        }
        for (rowChips in chips.chunked(columns)) {
            val line = LinearLayout(context)
            for (c in rowChips) line.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            repeat(columns - rowChips.size) { line.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            body.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
        if (saved.isNotEmpty())
            body.addView(
                TextView(context).apply {
                    setText(R.string.nw_colors_hold_hint)
                    textSize = 12f
                    setTextColor(NoctwadeTheme.muted(context))
                    setPadding(dp(4), dp(8), dp(4), 0)
                }
            )
        body.addView(
            MaterialButton(context).apply {
                setText(R.string.nw_colors_add)
                setOnClickListener { addCustomPalette(it) }
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) },
        )
    }

    private fun addBackgrounds(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        val current = NoctwadeTheme.backgroundKey(context)
        caption(body, getString(R.string.nw_bg_title))
        val saved = NoctwadeTheme.savedGradients(context)
        val shown = NoctwadeTheme.SavedGradient(NoctwadeTheme.gradientColors(context), NoctwadeTheme.gradientAngle(context))
        val shownSaved = saved.indexOfFirst { it.sameAs(shown) }
        val chosen =
            if (current != NoctwadeTheme.BG_CUSTOM) current else if (shownSaved >= 0) "saved:$shownSaved" else "custom"
        /** A new background is shown at once: the mark moves and the preview cross-fades; nothing is recreated. */
        fun choose(key: String, store: (android.content.SharedPreferences.Editor) -> Unit) {
            prefs.edit().also(store).commit()
            kit.select("bg", key)
            refreshHero(true)
        }
        val tiles = ArrayList<View>()
        // The gallery comes first: tapping the chosen picture picks a new one.
        val hasGallery = NoctwadeTheme.galleryFile(context).isFile
        tiles +=
            kit.tile(
                "bg",
                NoctwadeTheme.BG_GALLERY,
                getString(R.string.nw_bg_gallery),
                "",
                chosen == NoctwadeTheme.BG_GALLERY,
                backdrop = if (hasGallery) NoctwadeTheme.BG_GALLERY else null,
                glyph = if (hasGallery) null else TileGlyph.Kind.PHOTO,
                longClick =
                    if (hasGallery) {
                        {
                            confirmDelete(getString(R.string.nw_gallery_delete)) {
                                NoctwadeTheme.galleryFile(context).delete()
                                if (NoctwadeTheme.backgroundKey(context) == NoctwadeTheme.BG_GALLERY) prefs.edit().putString("background", NoctwadeTheme.BG_GRADIENT).commit()
                                applyAppearance()
                            }
                        }
                    } else null,
            ) {
                if (hasGallery && NoctwadeTheme.backgroundKey(context) != NoctwadeTheme.BG_GALLERY)
                    choose(NoctwadeTheme.BG_GALLERY) { it.putString("background", NoctwadeTheme.BG_GALLERY) }
                else pickImage.launch("image/*")
            }
        tiles +=
            kit.tile("bg", "custom", getString(R.string.nw_bg_custom_gradient), getString(R.string.nw_bg_new), chosen == "custom", backdrop = NoctwadeTheme.BG_CUSTOM, glyph = TileGlyph.Kind.PLUS) {
                AppearancePickers.gradient(context, NoctwadeTheme.gradientColors(context), NoctwadeTheme.gradientAngle(context)) { colors, angle ->
                    NoctwadeTheme.addGradient(context, NoctwadeTheme.SavedGradient(colors, angle))
                    prefs.edit()
                        .putString("gradient_colors", colors.joinToString(","))
                        .putInt("gradient_angle", angle)
                        .putString("background", NoctwadeTheme.BG_CUSTOM)
                        .commit()
                    // A new saved gradient tile appears: the page is rebuilt in place.
                    applyAppearance()
                }
            }
        tiles +=
            kit.tile("bg", NoctwadeTheme.BG_GRADIENT, getString(R.string.nw_bg_gradient), "", chosen == NoctwadeTheme.BG_GRADIENT, backdrop = NoctwadeTheme.BG_GRADIENT) {
                choose(NoctwadeTheme.BG_GRADIENT) { it.putString("background", NoctwadeTheme.BG_GRADIENT) }
            }
        saved.forEachIndexed { i, g ->
            tiles +=
                kit.tile(
                    "bg",
                    "saved:$i",
                    getString(R.string.nw_bg_saved, i + 1),
                    "",
                    chosen == "saved:$i",
                    gradient = NoctwadeTheme.softened(context, g.colors, g.angle),
                    longClick = {
                        confirmDelete(getString(R.string.nw_bg_delete_gradient)) {
                            NoctwadeTheme.removeGradient(context, g)
                            applyAppearance()
                        }
                    },
                ) {
                    choose("saved:$i") { e ->
                        e.putString("gradient_colors", g.colors.joinToString(","))
                            .putInt("gradient_angle", g.angle)
                            .putString("background", NoctwadeTheme.BG_CUSTOM)
                    }
                }
        }
        for (pack in ThemePacks.installed(context))
            for (b in pack.backgrounds)
                tiles +=
                    kit.tile("bg", b.ref, b.name, pack.name, chosen == b.ref, backdrop = b.ref, longClick = { removeEntry(ThemePacks.Kind.BACKGROUNDS, pack.id, b.id, b.name) }) {
                        choose(b.ref) { it.putString("background", b.ref) }
                    }
        kit.grid(body, tiles)
        holdHint(body)
    }

    private fun holdHint(body: LinearLayout) {
        body.addView(
            TextView(body.context).apply {
                setText(R.string.nw_hold_delete_hint)
                textSize = 12f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(6), dp(4), 0)
            }
        )
    }

    /** Deletes one background, character or halo of a pack (the pack goes when it is empty) after a confirmation. */
    private fun removeEntry(kind: ThemePacks.Kind, pack: String, id: String, name: String) {
        confirmDelete(getString(R.string.nw_pack_entry_delete, name)) {
            val context = requireContext()
            val prefs = NoctwadeTheme.prefs(context)
            val ref = "$pack/$id"
            val edit = prefs.edit()
            when (kind) {
                ThemePacks.Kind.BACKGROUNDS -> if (NoctwadeTheme.backgroundKey(context) == ref) edit.putString("background", NoctwadeTheme.BG_GRADIENT)
                ThemePacks.Kind.CHARACTERS -> if (NoctwadeTheme.characterRef(context) == ref) edit.putString("character", "")
                ThemePacks.Kind.HALOS -> if (NoctwadeTheme.halo(context) == HaloShaders.PACK + ref) edit.putString("halo", HaloShaders.CLASSIC)
            }
            edit.commit()
            ThemePacks.removeEntry(context, kind, pack, id)
            applyAppearance()
        }
    }

    private fun addCharacters(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        val packs = ThemePacks.installed(context)
        if (packs.none { it.characters.isNotEmpty() }) return
        val current = NoctwadeTheme.characterRef(context).takeIf { NoctwadeTheme.character(context) != null }.orEmpty()
        caption(body, getString(R.string.nw_ch_title))
        fun choose(ref: String) {
            prefs.edit().putString("character", ref).commit()
            kit.select("ch", ref)
            refreshHero(true)
        }
        val tiles = ArrayList<View>()
        tiles += kit.tile("ch", "", getString(R.string.nw_ch_none), "", current.isEmpty(), backdrop = NoctwadeTheme.backgroundKey(context)) { choose("") }
        for (pack in packs)
            for (c in pack.characters)
                tiles +=
                    kit.tile(
                        "ch",
                        c.ref,
                        c.name,
                        c.subtitle,
                        current == c.ref,
                        backdrop = c.background?.let { "${pack.id}/$it" } ?: NoctwadeTheme.backgroundKey(context),
                        sprite = c.sprites.idle,
                        longClick = { removeEntry(ThemePacks.Kind.CHARACTERS, pack.id, c.id, c.name) },
                    ) {
                        choose(c.ref)
                    }
        kit.grid(body, tiles)
        holdHint(body)
        fun toggle(title: Int, key: String) {
            body.addView(
                SwitchCompat(context).apply {
                    text = getString(title)
                    textSize = 15f
                    setTextColor(NoctwadeTheme.text(context))
                    setPadding(dp(4), dp(10), dp(4), dp(10))
                    isChecked = prefs.getBoolean(key, true)
                    setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean(key, value).apply() }
                }
            )
        }
        toggle(R.string.nw_ch_lines, "ch_lines")
        if (packs.any { p -> p.characters.any { it.halo != null } }) toggle(R.string.nw_ch_halo, "ch_halo")
    }

    /**
     * The halo around the connect button: "no halo" first, the canvas styles, the shader halos (Android 13+) and the
     * pack rings, and a last "+" tile that adds a shader, a zip of shaders or a theme pack.
     */
    private fun addHalo(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        val current = HaloShaders.usable(context, NoctwadeTheme.halo(context))
        caption(body, getString(R.string.nw_halo_title))
        fun choose(key: String) {
            prefs.edit().putString("halo", key).commit()
            kit.select("halo", key)
            refreshHero(true)
        }
        val tiles = ArrayList<View>()
        fun add(key: String, title: String, delete: (() -> Unit)? = null) {
            tiles += kit.tile("halo", key, title, "", key == current, halo = key, longClick = delete) { choose(key) }
        }
        add(HaloShaders.NONE, getString(R.string.nw_halo_none))
        add(HaloShaders.CLASSIC, getString(R.string.nw_halo_classic))
        add(HaloShaders.RINGS, getString(R.string.nw_halo_rings))
        if (HaloShaders.supported()) for (e in HaloShaders.builtIn(context)) add(e.key, e.name)
        // Rings from theme packs (their picture works on every Android version).
        for (pack in ThemePacks.installed(context))
            for (h in pack.halos) add(HaloShaders.PACK + h.ref, h.name) { removeEntry(ThemePacks.Kind.HALOS, pack.id, h.id, h.name) }
        if (HaloShaders.supported())
            for (e in HaloShaders.imported(context))
                add(e.key, e.name) {
                    confirmDelete(getString(R.string.nw_halo_delete, e.name)) {
                        HaloShaders.delete(context, e.key)
                        if (NoctwadeTheme.halo(context) == e.key) prefs.edit().putString("halo", HaloShaders.CLASSIC).commit()
                        applyAppearance()
                    }
                }
        tiles += kit.tile("halo-add", "+", getString(R.string.nw_halo_add), getString(R.string.nw_halo_add_hint), false, glyph = TileGlyph.Kind.HALO) { importHalo.launch(arrayOf("*/*")) }
        kit.grid(body, tiles)
        body.addView(
            TextView(context).apply {
                setText(if (HaloShaders.supported()) R.string.nw_halo_hint else R.string.nw_halo_need13)
                textSize = 12.5f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(10), dp(4), dp(8))
            }
        )
        if (!HaloShaders.supported()) return
        body.addView(
            MaterialButton(context).apply {
                setText(R.string.nw_halo_guide_btn)
                setOnClickListener { showHaloGuide() }
            }
        )
        body.addView(
            MaterialButton(context).apply {
                setText(R.string.nw_halo_example)
                setOnClickListener { exportHalo.launch("my-halo.agsl") }
            }
        )
    }

    /** The guide with the AI prompt, in the device language (Russian or English), from the app's assets. */
    private fun guideText(): String {
        val lang = if (Locale.getDefault().language == "ru") "ru" else "en"
        return runCatching { requireContext().assets.open("halo-guide.$lang.md").use { it.readBytes().decodeToString() } }
            .getOrDefault(getString(R.string.nw_halo_hint))
    }

    /** The guide in a tidy dialog; it ends with a ready prompt for an AI chat. */
    private fun showHaloGuide() {
        val context = requireContext()
        val guide = guideText()
        fun copy(text: String, done: Int) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Noctwade halo shader guide", text))
            android.widget.Toast.makeText(context, done, android.widget.Toast.LENGTH_SHORT).show()
        }
        HaloGuideDialog.show(
            context,
            guide,
            copyAll = { copy(guide, R.string.nw_guide_copied) },
            copyPrompt = { copy(HaloGuideDialog.prompt(guide).ifEmpty { guide }, R.string.nw_guide_prompt_copied) },
            share = {
                runCatching {
                    val file = java.io.File(context.cacheDir, "log/noctwade-halo-guide.txt").apply { parentFile?.mkdirs(); writeText(guide) }
                    moe.matsuri.nb4a.utils.SendLog.share(context, file, "Noctwade halo shader guide")
                }
            },
        )
    }
    private fun displayName(uri: android.net.Uri): String =
        runCatching {
                requireContext().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }
            .getOrNull()
            ?: uri.lastPathSegment
            ?: "halo.agsl"

    /** True when the zip at [uri] is a theme pack (has a pack.json); reads only the entry names. */
    private fun isThemePack(context: android.content.Context, uri: android.net.Uri): Boolean =
        runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.util.zip.ZipInputStream(java.io.BufferedInputStream(input)).use { zip ->
                        var seen = 0
                        while (seen++ < 2000) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name.replace('\\', '/').substringAfterLast('/') == "pack.json") return@runCatching true
                        }
                        false
                    }
                } ?: false
            }
            .getOrDefault(false)

    /** The "+" halo tile: one .agsl, a .zip of shaders, or a theme pack .zip (its halos, backgrounds and characters). */
    private fun importHalo(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        val name = displayName(uri)
        viewLifecycleOwner.lifecycleScope.launch {
            val pack = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { isThemePack(context, uri) }
            if (pack) return@launch importPack(uri)
            val result =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.let { HaloShaders.importAny(context, it, name) }
                            ?: throw HaloShaders.ImportException("cannot read the file")
                    }
                }
            if (!isAdded) return@launch
            result
                .onSuccess { done ->
                    val skipped = done.skipped
                    var message =
                        if (done.added.size == 1 && skipped.isEmpty()) getString(R.string.nw_halo_imported, done.added[0].name)
                        else getString(R.string.nw_halo_imported_many, done.added.size)
                    if (skipped.isNotEmpty())
                        message += "\n" + getString(R.string.nw_halo_skipped, skipped.size, skipped.take(3).joinToString("; ") { "${it.first}: ${it.second}" })
                    // A single new halo is switched on at once; with many, the choice stays yours.
                    if (done.added.size == 1) NoctwadeTheme.prefs(context).edit().putString("halo", done.added[0].key).commit()
                    if (done.added.isEmpty()) {
                        android.widget.Toast.makeText(context, getString(R.string.nw_halo_failed, skipped.firstOrNull()?.let { "${it.first}: ${it.second}" } ?: "?"), android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        applyAppearance()
                    }
                }
                .onFailure {
                    android.widget.Toast.makeText(
                            context,
                            getString(R.string.nw_halo_failed, it.message ?: it.javaClass.simpleName),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        .show()
                }
        }
    }

    private fun exportHalo(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        val text = HaloShaders.example(context)
        viewLifecycleOwner.lifecycleScope.launch {
            val ok =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
                }
            if (isAdded)
                android.widget.Toast.makeText(context, if (ok) R.string.nw_halo_saved else R.string.nw_gallery_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private val iconMarks = ArrayList<Pair<String, Pair<View, TextView>>>()

    private fun renderIconSelection() {
        val context = requireContext()
        val current = AppIconSwitcher.current(context)
        for ((key, views) in iconMarks) {
            val on = key == current
            views.first.visibility = if (on) View.VISIBLE else View.GONE
            views.second.setTextColor(if (on) NoctwadeTheme.accent(context) else NoctwadeTheme.muted(context))
        }
    }

    /** Alternative launcher icons: one manifest alias each; the chosen one is enabled, the rest disabled. Always open, four to a row. */
    private fun addAppIcons(body: LinearLayout) {
        val context = body.context
        iconMarks.clear()
        header(body, getString(R.string.nw_icons_title))
        val columns = 4
        for (chunk in AppIconTable.all.chunked(columns)) {
            val line = LinearLayout(context)
            for (icon in chunk) {
                val title = getString(icon.title)
                val cell =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(2), dp(8), dp(2), dp(6))
                        isClickable = true
                        isFocusable = true
                        contentDescription = title
                        setOnClickListener {
                            AppIconSwitcher.apply(context, icon.key)
                            renderIconSelection()
                            android.widget.Toast.makeText(context, R.string.nw_icons_changed, android.widget.Toast.LENGTH_LONG).show()
                        }
                        NoctwadeMotion.press(this)
                    }
                val frame = FrameLayout(context)
                frame.addView(
                    android.widget.ImageView(context).apply {
                        setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, icon.preview))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        clipToOutline = true
                        outlineProvider =
                            object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(15).toFloat())
                            }
                    },
                    FrameLayout.LayoutParams(dp(58), dp(58)),
                )
                // The chosen icon carries a small check; there is no frame or card behind the icons.
                val check =
                    TextView(context).apply {
                        text = "✓"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(NoctwadeTheme.accent(context)) }
                        visibility = View.GONE
                    }
                frame.addView(check, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM or Gravity.END))
                cell.addView(frame, LinearLayout.LayoutParams(dp(58), dp(58)))
                // The name slides left and right when it is longer than the cell.
                val label =
                    TextView(context).apply {
                        text = title
                        textSize = 11.5f
                        gravity = Gravity.CENTER_HORIZONTAL
                        setTextColor(NoctwadeTheme.muted(context))
                        setPadding(dp(4), 0, dp(4), 0)
                        setSingleLine(true)
                        ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                        marqueeRepeatLimit = -1
                        // A long name slides left and right; a short one stays put.
                        isSelected = true
                    }
                cell.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                iconMarks += icon.key to (check to label)
                line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(columns - chunk.size) { line.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            body.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
        renderIconSelection()
        body.addView(
            TextView(context).apply {
                setText(R.string.nw_icons_hint)
                textSize = 12.5f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(10), dp(4), dp(14))
            }
        )
    }
    private fun addPacks(body: LinearLayout) {
        val context = body.context
        caption(body, getString(R.string.nw_packs_title))
        body.addView(
            TextView(context).apply {
                setText(R.string.nw_packs_hint)
                textSize = 12.5f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), 0, dp(4), dp(8))
            }
        )
        for (pack in ThemePacks.installed(context)) {
            val row =
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(6), dp(10))
                    background =
                        GradientDrawable().apply {
                            cornerRadius = dp(16).toFloat()
                            setColor(NoctwadeTheme.surface(context))
                        }
                }
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(
                TextView(context).apply {
                    text = pack.name
                    textSize = 15f
                    setTextColor(NoctwadeTheme.text(context))
                }
            )
            texts.addView(
                TextView(context).apply {
                    text =
                        listOf(pack.author, getString(R.string.nw_packs_counts, pack.backgrounds.size, pack.characters.size, pack.halos.size))
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    textSize = 12f
                    setTextColor(NoctwadeTheme.muted(context))
                }
            )
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(
                androidx.appcompat.widget.AppCompatImageButton(context).apply {
                    setImageResource(R.drawable.nw_delete)
                    background = null
                    contentDescription = getString(R.string.nw_delete)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setOnClickListener {
                        confirmDelete(getString(R.string.nw_packs_delete, pack.name)) { deletePack(pack.id) }
                    }
                }
            )
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        body.addView(
            MaterialButton(context).apply {
                setText(R.string.nw_packs_import)
                setOnClickListener {
                    importPack.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
            }
        )
    }

    private fun deletePack(id: String) {
        val context = requireContext()
        val prefs = NoctwadeTheme.prefs(context)
        val edit = prefs.edit()
        if (NoctwadeTheme.characterRef(context).startsWith("$id/")) edit.putString("character", "")
        if (NoctwadeTheme.backgroundKey(context).startsWith("$id/")) edit.putString("background", NoctwadeTheme.BG_GRADIENT)
        if (NoctwadeTheme.halo(context).startsWith("${HaloShaders.PACK}$id/")) edit.putString("halo", HaloShaders.CLASSIC)
        edit.commit()
        ThemePacks.delete(context, id)
        applyAppearance()
    }

    private fun importPack(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { ThemePacks.import(context, it) }
                            ?: throw ThemePacks.ImportException("cannot read the file")
                    }
                }
            if (!isAdded) return@launch
            result
                .onSuccess {
                    android.widget.Toast.makeText(context, getString(R.string.nw_packs_imported, it.name), android.widget.Toast.LENGTH_SHORT).show()
                    applyAppearance()
                }
                .onFailure {
                    android.widget.Toast.makeText(
                            context,
                            getString(R.string.nw_packs_failed, it.message ?: it.javaClass.simpleName),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        .show()
                }
        }
    }

    /** Copies the picture (downscaled to about screen size, orientation applied) and makes it the background. */
    private fun importGallery(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                            val max = 2400
                            val bitmap =
                                if (android.os.Build.VERSION.SDK_INT >= 28)
                                    android.graphics.ImageDecoder.decodeBitmap(
                                        android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                    ) { decoder, info, _ ->
                                        val w = info.size.width
                                        val h = info.size.height
                                        val scale = minOf(1f, max.toFloat() / maxOf(w, h))
                                        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                    }
                                else {
                                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                                    var sample = 1
                                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
                                    context.contentResolver.openInputStream(uri)?.use {
                                        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                                    }
                                } ?: error("decode")
                            val file = NoctwadeTheme.galleryFile(context)
                            file.parentFile?.mkdirs()
                            val temp = java.io.File(file.parentFile, "gallery.tmp")
                            temp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                            if (!temp.renameTo(file)) {
                                file.delete()
                                temp.renameTo(file)
                            }
                            file.isFile
                        }
                        .getOrDefault(false)
                }
            if (!isAdded) return@launch
            if (ok) {
                NoctwadeTheme.prefs(context).edit().putString("background", NoctwadeTheme.BG_GALLERY).commit()
                applyAppearance()
            } else android.widget.Toast.makeText(context, R.string.nw_gallery_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Switches for the Home screen itself. */
    private fun addHomeScreen(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        caption(body, getString(R.string.nw_home_screen))
        body.addView(
            SwitchCompat(context).apply {
                text = getString(R.string.nw_focus_setting)
                textSize = 15f
                setTextColor(NoctwadeTheme.text(context))
                setPadding(dp(4), dp(10), dp(4), dp(10))
                isChecked = NoctwadeTheme.focusButton(context)
                setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("focus_button", value).apply() }
            }
        )
    }

    private fun addAnimationSettings(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        caption(body, getString(R.string.nw_animation_settings))

        fun toggle(title: Int, key: String) {
            body.addView(
                SwitchCompat(context).apply {
                    text = getString(title)
                    textSize = 15f
                    setTextColor(NoctwadeTheme.text(context))
                    setPadding(dp(4), dp(10), dp(4), dp(10))
                    isChecked = prefs.getBoolean(key, true)
                    setOnCheckedChangeListener { _, value ->
                        prefs.edit().putBoolean(key, value).apply()
                        refreshPreview()
                    }
                }
            )
        }

        fun slider(title: Int, key: String, min: Float, max: Float, default: Float) {
            val label =
                TextView(context).apply {
                    textSize = 13f
                    setTextColor(NoctwadeTheme.muted(context))
                    setPadding(dp(4), dp(12), 0, 0)
                }
            body.addView(label)
            val bar =
                SeekBar(context).apply {
                    this.max = 100
                    progress =
                        (((prefs.getFloat(key, default) - min) / (max - min)) * 100)
                            .roundToInt()
                            .coerceIn(0, 100)
                }
            fun value() = min + (max - min) * bar.progress / 100f
            fun update() {
                val v = value()
                val shown =
                    if (key == "speed") String.format(Locale.getDefault(), "%.1f×", v)
                    else "${(v * 100).roundToInt()}%"
                label.text = getString(title) + " · " + shown
            }
            update()
            bar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(v: SeekBar?, p: Int, user: Boolean) = update()

                    override fun onStartTrackingTouch(v: SeekBar?) {}

                    override fun onStopTrackingTouch(v: SeekBar?) {
                        prefs.edit().putFloat(key, value()).apply()
                        refreshPreview()
                    }
                }
            )
            body.addView(bar, LinearLayout.LayoutParams(-1, dp(44)))
        }

        toggle(R.string.nw_live_background, "live")
        toggle(R.string.nw_interface_motion, "animations")
        slider(R.string.nw_motion_speed, "speed", .5f, 1.5f, 1f)
        slider(R.string.nw_motion_intensity, "intensity", .2f, 1f, .65f)
    }

    private fun addEconomyMode(body: LinearLayout) {
        val context = body.context
        caption(body, getString(R.string.nw_eco_mode))
        body.addView(
            TextView(context).apply {
                text =
                    getString(R.string.nw_eco_hint) + "\n" + getString(R.string.nw_eco_auto_explain)
                textSize = 13f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), 0, dp(4), dp(10))
            }
        )

        val accent = NoctwadeTheme.accent(context)
        val options =
            listOf(
                NoctwadeEco.AUTO to R.string.nw_eco_auto,
                NoctwadeEco.ON to R.string.nw_eco_on,
                NoctwadeEco.OFF to R.string.nw_eco_off,
            )
        val group =
            LinearLayout(context).apply {
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background =
                    GradientDrawable().apply {
                        setColor(NoctwadeTheme.surface(context))
                        cornerRadius = dp(100).toFloat()
                    }
            }
        val chips =
            options.map { (mode, title) ->
                TextView(context).apply {
                    tag = mode
                    setText(title)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    isClickable = true
                    isFocusable = true
                }
            }
        fun render() {
            val mode = NoctwadeEco.mode(context)
            for (chip in chips) {
                val selected = chip.tag == mode
                chip.isSelected = selected
                chip.setTextColor(
                    if (selected) NoctwadeTheme.text(context) else NoctwadeTheme.muted(context)
                )
                // Same translucent filled pill as the selected bottom tab, no outline.
                chip.background =
                    if (selected)
                        GradientDrawable().apply {
                            setColor(ColorUtils.setAlphaComponent(accent, 46))
                            cornerRadius = dp(100).toFloat()
                        }
                    else null
            }
            renderEcoStatus()
        }
        for (chip in chips) {
            chip.setOnClickListener {
                NoctwadeEco.setMode(context, chip.tag as String)
                render()
                refreshPreview()
            }
            group.addView(chip, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        body.addView(group, LinearLayout.LayoutParams(-1, -2))

        ecoStatus =
            TextView(context).apply {
                textSize = 13f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(8), dp(4), dp(16))
            }
        body.addView(ecoStatus)
        render()
    }

    private fun renderEcoStatus() {
        val view = ecoStatus ?: return
        val context = view.context
        view.setText(
            when {
                !NoctwadeEco.active(context) -> R.string.nw_eco_status_off
                NoctwadeEco.mode(context) != NoctwadeEco.AUTO -> R.string.nw_eco_status_on
                else ->
                    when (NoctwadeEco.autoReason(context)) {
                        NoctwadeEco.Reason.BATTERY_SAVER -> R.string.nw_eco_reason_battery_saver
                        NoctwadeEco.Reason.LOW_RAM -> R.string.nw_eco_reason_low_ram
                        NoctwadeEco.Reason.LOW_BATTERY -> R.string.nw_eco_reason_low_battery
                        null -> R.string.nw_eco_status_on
                    }
            }
        )
    }

    private fun refreshPreview() {
        preview?.setEffectsVisible(false)
        preview?.setEffectsVisible(true)
    }

    override fun onResume() {
        super.onResume()
        preview?.setEffectsVisible(true)
        renderEcoStatus()
        ecoReceiver = NoctwadeEco.observe(requireContext()) { renderEcoStatus() }
    }

    override fun onPause() {
        NoctwadeEco.stopObserving(requireContext(), ecoReceiver)
        ecoReceiver = null
        preview?.setEffectsVisible(false)
        super.onPause()
    }

    override fun onDestroyView() {
        preview?.setEffectsVisible(false)
        preview = null
        ecoStatus = null
        scrollView = null
        super.onDestroyView()
    }
}
