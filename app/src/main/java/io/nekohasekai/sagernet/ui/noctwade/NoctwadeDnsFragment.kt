package io.nekohasekai.sagernet.ui.noctwade

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.SettingsFragment
import io.nekohasekai.sagernet.ui.settings.DnsSettingsFragment
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion

/**
 * Routing › DNS: one DNS server for every routing profile. Cloudflare, Google and Quad9 switch in
 * place; Manual opens the full DNS settings.
 */
class NoctwadeDnsFragment : Fragment() {
    private val rows = linkedMapOf<GlobalDns.Provider, DnsOptionRow>()

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(6), dp(24), dp(30))
            }
        content.addView(
            TextView(context).apply {
                setText(R.string.nw_dns_hint)
                textSize = 13f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(dp(4), dp(8), dp(4), dp(16))
            }
        )
        rows.clear()
        for (provider in GlobalDns.Provider.entries) {
            val row = DnsOptionRow(context, provider)
            row.setOnClickListener { choose(provider) }
            NoctwadeMotion.press(row)
            rows[provider] = row
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        return NestedScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            background = NoctwadeTheme.menuGradient(context)
            addView(content)
        }
    }

    override fun onResume() {
        super.onResume()
        render(false)
    }

    private fun render(animate: Boolean) {
        val chosen = GlobalDns.current(requireContext())
        rows.forEach { (provider, row) -> row.setChecked(provider == chosen, animate) }
        rows[GlobalDns.Provider.MANUAL]?.setSummary(
            if (chosen == GlobalDns.Provider.MANUAL)
                getString(R.string.nw_dns_manual_current, DataStore.remoteDns)
            else getString(GlobalDns.Provider.MANUAL.summary)
        )
    }

    private fun choose(provider: GlobalDns.Provider) {
        val context = requireContext()
        if (provider == GlobalDns.Provider.MANUAL || GlobalDns.current(context) != provider) {
            GlobalDns.select(context, provider)
            render(true)
        }
        if (provider == GlobalDns.Provider.MANUAL) openFullSettings()
    }

    private fun openFullSettings() {
        generateSequence(parentFragment) { it.parentFragment }
            .filterIsInstance<SettingsFragment>()
            .firstOrNull()
            ?.openScreen(
                DnsSettingsFragment::class.java.name,
                getString(R.string.settings_dns),
                Bundle(),
            )
    }

    override fun onDestroyView() {
        rows.clear()
        super.onDestroyView()
    }
}

/** A selectable card whose fill, outline and check fade in together when it becomes the choice. */
private class DnsOptionRow(context: Context, provider: GlobalDns.Provider) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val surface = NoctwadeTheme.surface(context)
    private val accent = NoctwadeTheme.accent(context)
    private val hairline = ColorUtils.setAlphaComponent(NoctwadeTheme.text(context), 28)
    private val card = GradientDrawable().apply { cornerRadius = 16 * density }
    private val ring = View(context)
    private val check = ImageView(context)
    private val summary = TextView(context)
    private var progress = 0f
    private var checked = false
    private var animator: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (68 * density).toInt()
        setPadding((16 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
        background = card
        isClickable = true
        isFocusable = true
        val indicator = FrameLayout(context)
        ring.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0)
                setStroke((2 * density).toInt(), NoctwadeTheme.muted(context))
            }
        check.setImageResource(R.drawable.nw_check)
        check.imageTintList = ColorStateList.valueOf(accent)
        check.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        indicator.addView(ring, FrameLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt(), Gravity.CENTER))
        indicator.addView(check, FrameLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt(), Gravity.CENTER))
        addView(indicator, LayoutParams((26 * density).toInt(), (26 * density).toInt()).apply { marginEnd = (16 * density).toInt() })
        val labels = LinearLayout(context).apply { orientation = VERTICAL }
        labels.addView(
            TextView(context).apply {
                setText(provider.title)
                textSize = 15f
                setTextColor(NoctwadeTheme.text(context))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        )
        summary.apply {
            setText(provider.summary)
            textSize = 12f
            setTextColor(NoctwadeTheme.muted(context))
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        labels.addView(summary)
        addView(labels, LayoutParams(0, -2, 1f))
        if (provider == GlobalDns.Provider.MANUAL)
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.nw_chevron)
                    imageTintList = ColorStateList.valueOf(NoctwadeTheme.muted(context))
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LayoutParams((18 * density).toInt(), (18 * density).toInt()),
            )
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = checked
                }
            },
        )
        paint(0f)
    }

    fun setSummary(text: CharSequence) {
        summary.text = text
    }

    fun setChecked(value: Boolean, animate: Boolean) {
        val target = if (value) 1f else 0f
        if (value == checked && progress == target && animator == null) return
        checked = value
        isSelected = value
        animator?.cancel()
        animator = null
        if (animate && isLaidOut && NoctwadeMotion.enabled(context)) {
            animator =
                ValueAnimator.ofFloat(progress, target).apply {
                    duration = 240
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { paint(it.animatedValue as Float) }
                    start()
                }
        } else paint(target)
    }

    private fun paint(value: Float) {
        progress = value
        card.setColor(
            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, (30 * value).toInt()), surface)
        )
        card.setStroke(
            (1 * density).toInt().coerceAtLeast(1),
            ColorUtils.blendARGB(hairline, accent, value),
        )
        check.alpha = value
        check.scaleX = .6f + .4f * value
        check.scaleY = .6f + .4f * value
        ring.alpha = 1f - value
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        paint(if (checked) 1f else 0f)
        super.onDetachedFromWindow()
    }
}
