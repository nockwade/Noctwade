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
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.SettingsFragment
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeEco
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import io.nekohasekai.sagernet.widget.noctwade.SpaceBackdropView
import java.util.Locale
import kotlin.math.roundToInt

/** Themes, accent, animation and economy-mode settings. */
class NoctwadePersonalizationFragment : Fragment() {

    private var preview: SpaceBackdropView? = null
    private var ecoStatus: TextView? = null
    private var ecoReceiver: BroadcastReceiver? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
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

        preview =
            SpaceBackdropView(context).apply {
                previewMode = true
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(22).toFloat()
                        setColor(NoctwadeTheme.background(context))
                    }
                clipToOutline = true
            }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(180)))

        addThemes(body)
        addAccents(body)
        addAnimationSettings(body)
        addEconomyMode(body)

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
        return scroll
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

    private fun addThemes(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        val current = NoctwadeTheme.name(context)
        caption(body, getString(R.string.nw_theme_choose))
        for (pair in NoctwadeTheme.palettes.chunked(2)) {
            val row = LinearLayout(context)
            pair.forEachIndexed { index, palette ->
                val key = palette.key
                val title = getString(palette.title)
                val selected = key == current
                val tile =
                    FrameLayout(context).apply {
                        background = NoctwadeTheme.surfaceGradient(context, dp(18).toFloat())
                        clipToOutline = true
                        isClickable = true
                        isFocusable = true
                        contentDescription = title
                        foreground =
                            GradientDrawable().apply {
                                cornerRadius = dp(18).toFloat()
                                setColor(Color.TRANSPARENT)
                                setStroke(
                                    dp(if (selected) 2 else 1),
                                    if (selected) NoctwadeTheme.accent(context)
                                    else NoctwadeTheme.stroke(context),
                                )
                            }
                        setOnClickListener {
                            if (!selected) {
                                prefs.edit().putString("theme", key).apply()
                                requireActivity().recreate()
                            }
                        }
                    }
                tile.addView(
                    SpaceBackdropView(context).apply {
                        previewMode = true
                        previewTheme = key
                    },
                    FrameLayout.LayoutParams(-1, -1),
                )
                tile.addView(
                    TextView(context).apply {
                        text = title
                        textSize = 15f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(Color.WHITE)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background =
                            GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(0x10000000, 0xCC000000.toInt()),
                            )
                    },
                    FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM),
                )
                NoctwadeMotion.press(tile)
                row.addView(
                    tile,
                    LinearLayout.LayoutParams(0, dp(126), 1f).apply {
                        if (index == 0) marginEnd = dp(10)
                        bottomMargin = dp(10)
                    },
                )
            }
            body.addView(row)
        }
    }

    private fun addAccents(body: LinearLayout) {
        val context = body.context
        val prefs = NoctwadeTheme.prefs(context)
        val selectedIndex = prefs.getInt("accent", -1)
        caption(body, getString(R.string.nw_accent_choose))
        // null = automatic accent taken from the theme.
        val accents =
            listOf<String?>(null, "#CE9CFF", "#FFABCB", "#82CFFF", "#72DDB7", "#F6C177", "#FF9D91")
        val colors = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        accents.forEachIndexed { index, hex ->
            val selected = selectedIndex == index - 1
            val chip =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    text = if (hex == null) "A" else ""
                    textSize = 13f
                    setTextColor(NoctwadeTheme.text(context))
                    isClickable = true
                    isFocusable = true
                    contentDescription = hex ?: getString(R.string.nw_accent_auto)
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(hex?.let(Color::parseColor) ?: NoctwadeTheme.surface(context))
                            setStroke(
                                dp(if (selected) 3 else 1),
                                if (selected) NoctwadeTheme.text(context)
                                else NoctwadeTheme.stroke(context),
                            )
                        }
                    setOnClickListener {
                        prefs.edit().putInt("accent", index - 1).apply()
                        requireActivity().recreate()
                    }
                }
            colors.addView(
                chip,
                LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) },
            )
        }
        body.addView(colors)
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
        super.onDestroyView()
    }
}
