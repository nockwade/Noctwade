package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import io.nekohasekai.sagernet.outbound.config.ConfigGenerator
import io.nekohasekai.sagernet.outbound.config.GeneratorSettings
import io.nekohasekai.sagernet.outbound.config.ProfileProvider
import io.nekohasekai.sagernet.outbound.config.RoutingInput
import io.nekohasekai.sagernet.route.OutboundIds
import io.nekohasekai.sagernet.route.RouteProfile
import io.nekohasekai.sagernet.route.RouteRule
import io.nekohasekai.sagernet.route.RuleSetCatalog
import io.nekohasekai.sagernet.route.RuleSets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Release122Test {
    private fun build(profile: RouteProfile, catalog: RuleSetCatalog): JSONObject {
        val proxy =
            io.nekohasekai.sagernet.outbound.types.Socks().apply {
                server = "192.0.2.1"
                serverPort = 1080
            }
        val result =
            ConfigGenerator(
                    ProfileProvider { if (it == 1L) proxy else null },
                    GeneratorSettings(dnsQueryTimeout = "5s"),
                    routing = RoutingInput(profile, catalog),
                )
                .build(1L)
        assertNull(result.error)
        return JSONObject(result.coreConfig)
    }

    private fun sets(config: JSONObject): Map<String, JSONObject> {
        val array = config.getJSONObject("route").getJSONArray("rule_set")
        return (0 until array.length()).associate {
            val o = array.getJSONObject(it)
            o.getString("tag") to o
        }
    }

    @Test
    fun outipUsesTheOriginalGeositeNames() {
        val p =
            NoctwadeRouting.outip(
                """{"wld":{"domain_suffix":["example.ru"]},"wldc":{"domain_suffix":["other.ru"]}}"""
            )
        val used = p.rules.flatMap { it.rule_set }
        assertTrue(used.isNotEmpty())
        assertTrue("no client-prefixed names", used.none { "noctwade" in it })
        assertTrue(used.all { it.startsWith("geosite-") || it.startsWith("geoip-") })
        assertTrue("geosite-category-ads-all" in used && "geoip-private" in used)
    }

    @Test
    fun namesKnownToTheCatalogStayLiveAndStartFromTheSnapshot() {
        val p =
            RouteProfile.defaultProfile().apply {
                default_outbound_id = OutboundIds.PROXY
                rules.add(
                    RouteRule().apply {
                        name = "mixed"
                        rule_set.addAll(
                            listOf("geosite-private", "geosite-torrent", "geosite-noctwade-steam")
                        )
                        outbound_id = OutboundIds.DIRECT
                    }
                )
            }
        val catalog =
            RuleSetCatalog(listOf("geosite-private" to "https://example.com/geosite-private.srs"))
        val config = build(p, catalog)
        val byTag = sets(config)
        val live = byTag.getValue("geosite-private")
        assertEquals("remote", live.getString("type"))
        assertEquals("https://example.com/geosite-private.srs", live.getString("url"))
        assertEquals("noctwade-rule-sets/meta-private.srs", live.getString("initial_path"))
        // not in the catalog: local only (offline, nothing to fetch)
        assertEquals("local", byTag.getValue("geosite-torrent").getString("type"))
        // a profile saved by an earlier build still resolves
        val legacy = byTag.getValue("geosite-noctwade-steam")
        assertEquals("local", legacy.getString("type"))
        assertEquals("noctwade-rule-sets/meta-steam.srs", legacy.getString("path"))
    }

    @Test
    fun legacyNamesMapToTheSameFiles() {
        assertEquals("geosite-private", RuleSets.canonicalName("geosite-noctwade-private"))
        assertEquals("geoip-private", RuleSets.canonicalName("geoip-noctwade-private"))
        assertEquals("geosite-ru-blocked", RuleSets.canonicalName("geosite-ru-blocked"))
        assertNotNull(RuleSets.bundledFile("geosite-noctwade-oisd-big"))
        assertEquals(
            RuleSets.bundledFile("geosite-oisd-big"),
            RuleSets.bundledFile("geosite-noctwade-oisd-big"),
        )
    }

    @Test
    fun templateOptionsPutChosenRuleSetsFirstAndNeverTouchDns() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val base = NoctwadeRouting.preset("proxy")
        val before = base.rules.size
        val choice =
            TemplateOptions.Choice(
                setOf(TemplateOptions.RuleSet.BLOCKED, TemplateOptions.RuleSet.RUSSIA)
            )
        val p = TemplateOptions.apply(context, base, choice)
        assertFalse(p.custom_dns)
        assertNull(p.dns)
        assertEquals(before + 2, p.rules.size)
        assertEquals(OutboundIds.PROXY, p.rules[0].outbound_id)
        assertEquals(OutboundIds.DIRECT, p.rules[1].outbound_id)
        assertTrue(p.rules[0].rule_set.all { it.contains("runetfreedom/russia-v2ray-rules-dat") })
        val untouched =
            TemplateOptions.apply(
                context,
                NoctwadeRouting.preset("proxy"),
                TemplateOptions.Choice(emptySet()),
            )
        assertFalse(untouched.rules.any { it.rule_set.any { s -> "runetfreedom" in s } })
    }
}
