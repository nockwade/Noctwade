package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.route.OutboundIds
import io.nekohasekai.sagernet.route.RouteProfile
import io.nekohasekai.sagernet.route.RouteRule

/**
 * Options applied to a ready-made routing profile before it opens as a draft: optional Runet rule
 * sets (runetfreedom/russia-v2ray-rules-dat, sing-box format). The last choice is remembered as the
 * default for the next template. DNS is not chosen per profile: see [GlobalDns].
 */
internal object TemplateOptions {
    private const val RUNET =
        "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/sing-box"

    /**
     * Each list is one rule; order matters (first match wins): ads, blocked → VPN, Russian →
     * direct.
     */
    enum class RuleSet(
        val title: Int,
        val key: String,
        val action: String,
        val outbound: Long,
        val urls: List<String>,
    ) {
        ADS(
            R.string.nw_tpl_rs_ads,
            "ads",
            "reject",
            OutboundIds.DIRECT,
            listOf("$RUNET/rule-set-geosite/geosite-category-ads-all.srs"),
        ),
        BLOCKED(
            R.string.nw_tpl_rs_blocked,
            "blocked",
            "route",
            OutboundIds.PROXY,
            listOf(
                "$RUNET/rule-set-geosite/geosite-ru-blocked.srs",
                "$RUNET/rule-set-geoip/geoip-ru-blocked.srs",
            ),
        ),
        RUSSIA(
            R.string.nw_tpl_rs_russia,
            "russia",
            "route",
            OutboundIds.DIRECT,
            listOf(
                "$RUNET/rule-set-geosite/geosite-category-ru.srs",
                "$RUNET/rule-set-geoip/geoip-ru.srs",
            ),
        ),
    }

    data class Choice(val ruleSets: Set<RuleSet>)

    private fun prefs(context: Context) = NoctwadeTheme.prefs(context)

    fun saved(context: Context): Choice {
        val p = prefs(context)
        val sets = RuleSet.entries.filter { p.getBoolean("tpl_rs_${it.key}", false) }.toSet()
        return Choice(sets)
    }

    fun save(context: Context, choice: Choice) {
        prefs(context)
            .edit()
            .apply {
                RuleSet.entries.forEach { putBoolean("tpl_rs_${it.key}", it in choice.ruleSets) }
            }
            .apply()
    }

    /** Returns [profile] with the chosen rule sets placed before the template's own rules. */
    fun apply(context: Context, profile: RouteProfile, choice: Choice): RouteProfile {
        val added =
            RuleSet.entries
                .filter { it in choice.ruleSets }
                .map { set ->
                    RouteRule().apply {
                        name = context.getString(set.title)
                        action = set.action
                        outbound_id = set.outbound
                        rule_set.addAll(set.urls)
                    }
                }
        profile.rules.addAll(0, added)
        return profile
    }

    /** The options form shown under a template's description. */
    fun form(context: Context, description: CharSequence): Pair<View, () -> Choice> {
        val d = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * d).toInt()
        val saved = saved(context)
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(4), dp(24), 0)
            }
        fun caption(text: Int) =
            root.addView(
                TextView(context).apply {
                    setText(text)
                    textSize = 13f
                    setTextColor(NoctwadeTheme.muted(context))
                    setPadding(0, dp(14), 0, dp(4))
                }
            )
        root.addView(
            TextView(context).apply {
                text = description
                textSize = 14f
                setTextColor(NoctwadeTheme.text(context))
            }
        )
        caption(R.string.nw_tpl_rulesets)
        val boxes =
            RuleSet.entries.associateWith { set ->
                CheckBox(context).apply {
                    setText(set.title)
                    setTextColor(NoctwadeTheme.text(context))
                    isChecked = set in saved.ruleSets
                    root.addView(this)
                }
            }
        root.addView(
            TextView(context).apply {
                setText(R.string.nw_tpl_rulesets_source)
                textSize = 11f
                setTextColor(NoctwadeTheme.muted(context))
                setPadding(0, dp(6), 0, 0)
            }
        )
        return root to { Choice(boxes.filterValues { it.isChecked }.keys) }
    }
}
