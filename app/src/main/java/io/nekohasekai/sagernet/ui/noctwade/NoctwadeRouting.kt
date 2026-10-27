package io.nekohasekai.sagernet.ui.noctwade

import io.nekohasekai.sagernet.route.*
import org.json.JSONObject

/**
 * Maps published RoscomVPN categories to the same publisher's sing-box rule sets. No foreign config
 * is executed.
 */
internal object NoctwadeRouting {
    const val ROSCOM_SOURCE =
        "https://raw.githubusercontent.com/hydraponique/roscomvpn-routing/HEAD/HAPP/DEFAULT.JSON"

    /**
     * The improved rule order supplied by the user, without the superseded rules in the example
     * YAML.
     */
    fun outip(localLists: String): RouteProfile =
        RouteProfile.defaultProfile().apply {
            name = "Noctwade · OutIP"
            default_outbound_id = OutboundIds.PROXY
            fun addSet(label: String, tag: String, outbound: Long, drop: Boolean = false) {
                rules.add(
                    RouteRule().apply {
                        name = label
                        rule_set.add(tag)
                        outbound_id = outbound
                        if (outbound == OutboundIds.BLOCK) {
                            action = "reject"
                            outbound_id = OutboundIds.DIRECT
                            if (drop) reject_method = "drop"
                        }
                    }
                )
            }
            addSet("private-ips", "geoip-private", OutboundIds.DIRECT)
            addSet("private", "geosite-private", OutboundIds.DIRECT)
            rules.add(
                RouteRule().apply {
                    name = "Reject IPv6"
                    ip_cidr.add("::/0")
                    action = "reject"
                }
            )
            for (tag in listOf("oisd-big", "category-ads-all", "win-spy", "torrent")) addSet(
                tag,
                "geosite-$tag",
                OutboundIds.BLOCK,
                true,
            )
            for (tag in listOf("category-ru", "microsoft", "steam", "whitelist")) addSet(
                tag,
                "geosite-$tag",
                OutboundIds.DIRECT,
            )
            val lists = JSONObject(localLists)
            for (key in listOf("wld", "wldc")) rules.add(
                RouteRule().apply {
                    name = key
                    outbound_id = OutboundIds.DIRECT
                    val source = lists.getJSONObject(key)
                    for ((kind, target) in
                        listOf("domain" to domain, "domain_suffix" to domain_suffix)) {
                        val values = source.optJSONArray(kind) ?: continue
                        for (i in 0 until values.length()) target.add(values.getString(i))
                    }
                    require(domain.isNotEmpty() || domain_suffix.isNotEmpty()) { "Empty $key list" }
                }
            )
        }

    fun preset(key: String): RouteProfile =
        RouteProfile.defaultProfile().apply {
            name = "Noctwade · $key"
            default_outbound_id = if (key == "direct") OutboundIds.DIRECT else OutboundIds.PROXY
            if (key == "smart") {
                rules.add(
                    RouteRule().apply {
                        name = "Local network"
                        ip_is_private = true
                        outbound_id = OutboundIds.DIRECT
                    }
                )
                rules.add(
                    RouteRule().apply {
                        name = "Russia and Belarus"
                        rule_set = mutableListOf("geosite-category-ru", "geoip-ru", "geoip-by")
                        outbound_id = OutboundIds.DIRECT
                    }
                )
            }
        }

    fun roscom(text: String): RouteProfile {
        val j = JSONObject(text)
        require(j.optString("RouteOrder") == "block-proxy-direct") {
            "Unsupported RoscomVPN route order"
        }
        val p =
            RouteProfile.defaultProfile().apply {
                name = "Noctwade · RoscomVPN"
                default_outbound_id = OutboundIds.PROXY
            }
        for ((prefix, outbound) in
            listOf(
                "Block" to OutboundIds.DIRECT,
                "Proxy" to OutboundIds.PROXY,
                "Direct" to OutboundIds.DIRECT,
            )) {
            val rule =
                RouteRule().apply {
                    name = "RoscomVPN · $prefix"
                    action = if (prefix == "Block") "reject" else "route"
                    outbound_id = outbound
                }
            for ((suffix, kind) in listOf("Sites" to "geosite", "Ip" to "geoip")) {
                val values = j.optJSONArray(prefix + suffix) ?: continue
                for (i in 0 until values.length()) {
                    val value = values.getString(i).trim()
                    when {
                        value.startsWith("$kind:") -> {
                            val category = value.substringAfter(':')
                            require(Regex("[a-z0-9!_-]+").matches(category)) {
                                "Unsupported rule category"
                            }
                            rule.rule_set.add(
                                "https://raw.githubusercontent.com/hydraponique/roscomvpn-$kind/master/release/sing-box/$category.srs"
                            )
                        }
                        suffix == "Ip" -> rule.ip_cidr.add(value)
                        value.startsWith("domain:") ->
                            rule.domain_suffix.add(value.substringAfter(':'))
                        value.startsWith("full:") -> rule.domain.add(value.substringAfter(':'))
                        value.startsWith("regexp:") ->
                            rule.domain_regex.add(value.substringAfter(':'))
                        value.startsWith("keyword:") ->
                            rule.domain_keyword.add(value.substringAfter(':'))
                        !value.contains(':') -> rule.domain_suffix.add(value)
                        else -> error("Unsupported rule: $value")
                    }
                }
            }
            // Different sing-box condition families are ANDed. Separate rules preserve
            // the publisher's union of domains, rule sets and literal IP networks.
            val families =
                listOf(
                    "rule_set" to rule.rule_set,
                    "ip_cidr" to rule.ip_cidr,
                    "domain_suffix" to rule.domain_suffix,
                    "domain" to rule.domain,
                    "domain_regex" to rule.domain_regex,
                    "domain_keyword" to rule.domain_keyword,
                )
            for ((kind, values) in families) if (values.isNotEmpty())
                p.rules.add(
                    RouteRule().apply {
                        name = rule.name
                        action = rule.action
                        outbound_id = rule.outbound_id
                        when (kind) {
                            "rule_set" -> rule_set.addAll(values)
                            "ip_cidr" -> ip_cidr.addAll(values)
                            "domain_suffix" -> domain_suffix.addAll(values)
                            "domain" -> domain.addAll(values)
                            "domain_regex" -> domain_regex.addAll(values)
                            "domain_keyword" -> domain_keyword.addAll(values)
                        }
                    }
                )
        }
        require(p.rules.size > 1) { "RoscomVPN returned no routing rules" }
        return p
    }
}
