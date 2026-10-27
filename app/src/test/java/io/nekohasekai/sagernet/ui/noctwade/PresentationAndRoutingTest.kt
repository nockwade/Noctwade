package io.nekohasekai.sagernet.ui.noctwade

import io.nekohasekai.sagernet.route.OutboundIds
import org.junit.Assert.*
import org.junit.Test

class PresentationAndRoutingTest {
    @Test
    fun flagsAndCountries() {
        assertEquals("PL", ServerPresentation.country("🇵🇱 | PL Fast UDP"))
        assertEquals("DE", ServerPresentation.country("Germany · Frankfurt"))
        assertEquals("NL", ServerPresentation.country("🇩🇪 DE", tested = "NL"))
        assertEquals("", ServerPresentation.country("fast node 42"))
    }

    @Test
    fun protocolIsIndependentOfEngine() {
        assertEquals("VLESS", ServerPresentation.protocol("xrayvless"))
        assertEquals("VLESS", ServerPresentation.protocol("xray_vless"))
        assertEquals("TROJAN", ServerPresentation.protocol("trojan"))
    }

    @Test
    fun roscomPriorityAndSources() {
        val p =
            NoctwadeRouting.roscom(
                """{"RouteOrder":"block-proxy-direct","BlockSites":["geosite:category-ads"],"ProxySites":["geosite:youtube"],"DirectSites":["geosite:category-ru"],"DirectIp":["geoip:direct"]}"""
            )
        assertEquals(OutboundIds.PROXY, p.default_outbound_id)
        assertEquals("reject", p.rules[1].action)
        assertEquals(OutboundIds.PROXY, p.rules[2].outbound_id)
        assertEquals(OutboundIds.DIRECT, p.rules[3].outbound_id)
        assertTrue(
            p.rules[3].rule_set.any {
                it.contains("roscomvpn-geoip/master/release/sing-box/direct.srs")
            }
        )
        assertEquals(1, p.directSites().size)
        assertEquals(1, p.directIps().size)
        assertTrue(p.directSites()[0].startsWith("ruleset:category-ru-srs-"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refuseUnknownRoutingFormat() {
        NoctwadeRouting.roscom("""{"RouteOrder":"unexpected"}""")
    }

    @Test
    fun mixedRoscomSelectorsRemainAlternatives() {
        val p =
            NoctwadeRouting.roscom(
                """{"RouteOrder":"block-proxy-direct","DirectSites":["geosite:private","full:example.com"],"DirectIp":["10.0.0.0/8"]}"""
            )
        assertEquals(4, p.rules.size)
        assertTrue(
            p.rules.drop(1).all {
                listOf(it.rule_set, it.ip_cidr, it.domain).count { v -> v.isNotEmpty() } == 1
            }
        )
    }

    @Test
    fun improvedOutipRulesKeepPriorityAndSilentDrops() {
        val p =
            NoctwadeRouting.outip(
                """{"wld":{"domain_suffix":["example.ru"]},"wldc":{"domain_suffix":["other.ru"]}}"""
            )
        assertEquals(
            listOf(
                "private-ips",
                "private",
                "Reject IPv6",
                "oisd-big",
                "category-ads-all",
                "win-spy",
                "torrent",
                "category-ru",
                "microsoft",
                "steam",
                "whitelist",
                "wld",
                "wldc",
            ),
            p.rules.drop(1).map { it.name },
        )
        assertEquals(listOf("::/0"), p.rules[3].ip_cidr)
        assertTrue(
            p.rules.subList(4, 8).all { it.action == "reject" && it.reject_method == "drop" }
        )
        assertEquals(OutboundIds.PROXY, p.default_outbound_id)
        assertFalse(p.custom_dns)
    }

    @Test
    fun bundledRulesAreLocalInGeneratedCoreConfig() {
        val p =
            NoctwadeRouting.outip(
                """{"wld":{"domain_suffix":["example.ru"]},"wldc":{"domain_suffix":["other.ru"]}}"""
            )
        val proxy =
            io.nekohasekai.sagernet.outbound.types.Socks().apply {
                server = "192.0.2.1"
                serverPort = 1080
            }
        val result =
            io.nekohasekai.sagernet.outbound.config
                .ConfigGenerator(
                    io.nekohasekai.sagernet.outbound.config.ProfileProvider {
                        if (it == 1L) proxy else null
                    },
                    io.nekohasekai.sagernet.outbound.config.GeneratorSettings(
                        dnsQueryTimeout = "5s"
                    ),
                    routing =
                        io.nekohasekai.sagernet.outbound.config.RoutingInput(
                            p,
                            io.nekohasekai.sagernet.route.RuleSetCatalog(emptyList()),
                        ),
                )
                .build(1L)
        assertNull(result.error)
        val sets =
            org.json.JSONObject(result.coreConfig).getJSONObject("route").getJSONArray("rule_set")
        assertEquals(10, sets.length())
        for (i in 0 until sets.length()) assertEquals(
            "local",
            sets.getJSONObject(i).getString("type"),
        )
    }
}
