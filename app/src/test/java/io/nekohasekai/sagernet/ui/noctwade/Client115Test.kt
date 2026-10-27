package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.RouteProfileEntity
import io.nekohasekai.sagernet.database.RouteRuleEntity
import io.nekohasekai.sagernet.databinding.LayoutMainBinding
import io.nekohasekai.sagernet.route.*
import io.nekohasekai.sagernet.ui.InstalledAppsAccess
import io.nekohasekai.sagernet.widget.noctwade.SegmentedControl
import io.nekohasekai.sagernet.widget.noctwade.SpaceBackdropView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Client115Test {
    class Host : AppCompatActivity() {
        override fun onCreate(state: Bundle?) {
            setTheme(R.style.Theme_SagerNet)
            NoctwadeTheme.apply(this)
            super.onCreate(state)
            setContentView(FrameLayout(this).apply { id = 12345 })
        }
    }

    @Test
    fun automaticPingRunsOncePerConnectionAndAgainAfterReconnect() {
        val gate = ConnectionPingGate()
        assertFalse(gate.update(false, 10))
        assertTrue(gate.update(true, 10))
        assertFalse(gate.update(true, 10))
        assertTrue(gate.update(true, 20))
        assertFalse(gate.update(true, 20))
        assertFalse(gate.update(false, 20))
        assertTrue(gate.update(true, 20))
        assertFalse(gate.update(true, 0))
    }

    @Test
    fun searchFindsDnsAndClientRoutingAndUsesAllQueryWords() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val entries = MenuSearchIndex.entries()
        assertTrue(entries.size > 130)
        assertTrue(
            MenuSearchIndex.filter(a, entries, "DNS").any {
                it.screen.endsWith("DnsSettingsFragment")
            }
        )
        val routing = entries.first { it.key == "routing" }
        assertTrue(
            MenuSearchIndex.filter(a, entries, a.getString(routing.title)).any {
                it.screen.endsWith("NoctwadeRoutingFragment")
            }
        )
        assertTrue(MenuSearchIndex.filter(a, entries, "DNS nonexistent-xyz").isEmpty())
        assertTrue(MenuSearchIndex.filter(a, entries, "  ").isEmpty())
        controller.pause().stop().destroy()
    }

    @Test
    fun savedRejectRulesKeepSelectorsAndDropWithoutUnusedOutbound() {
        val original =
            RouteRule().apply {
                name = "Block ads"
                action = "reject"
                outbound_id = OutboundIds.BLOCK
                reject_method = "drop"
                domain_suffix.add("example.test")
            }
        val profile = RouteProfileEntity().toModel(listOf(RouteRuleEntity.of(1, 0, original)))
        val loaded = profile.rules.single()
        assertEquals(OutboundIds.DIRECT, loaded.outbound_id)
        assertEquals(original.domain_suffix, loaded.domain_suffix)
        val json = org.json.JSONObject(loaded.toRuleJson(false).toCompact())
        assertEquals("reject", json.getString("action"))
        assertEquals("drop", json.getString("method"))
        assertFalse(json.has("outbound"))
        assertEquals("example.test", json.getJSONArray("domain_suffix").getString(0))
        assertFalse(
            loaded.validate(RuleEnv(RuleSetCatalog(emptyList()))).any { it.attr == "outbound_id" }
        )
        val legacy = RouteRule().apply { outbound_id = OutboundIds.BLOCK }
        legacy.normalizeActionOptions()
        assertEquals("reject", legacy.effectiveAction())
        assertEquals(OutboundIds.BLOCK, legacy.outbound_id)
    }

    @Test
    fun segmentedControlKeepsSelectionWithoutPressedBackground() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val control = SegmentedControl(a)
        var calls = 0
        control.onSelected = { calls++ }
        control.select(0, false)
        assertTrue(control.getChildAt(0).isSelected)
        assertFalse(control.getChildAt(1).isSelected)
        control.getChildAt(1).performClick()
        assertEquals(1, calls)
        assertEquals(1, control.selectedIndex)
        assertTrue(control.getChildAt(1).isSelected)
        assertNull(control.getChildAt(1).background)
        control.getChildAt(1).performClick()
        assertEquals(1, calls)
        controller.pause().stop().destroy()
    }

    @Test
    fun menuBaseIsOpaqueAndRoutingFieldsKeepDropdownWidgets() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val main = LayoutMainBinding.inflate(a.layoutInflater)
        assertFalse(main.navigationBackdrop is SpaceBackdropView)
        assertNotNull(main.navigationBackdrop.background)
        val manager = PreferenceManager(a)
        val screen = manager.createPreferenceScreen(a)
        screen.addPreference(
            EditTextPreference(a).apply {
                key = "domain"
                isPersistent = false
                title = "Domain"
                text = "example.test"
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
        )
        screen.addPreference(
            moe.matsuri.nb4a.ui.SimpleMenuPreference(a).apply {
                key = "output"
                isPersistent = false
                title = "Output"
                entries = arrayOf("proxy", "direct")
                entryValues = entries
                value = "proxy"
            }
        )
        NoctwadePreferenceStyle.routeRows(screen)
        val adapter = PreferenceGroupAdapter(screen)
        for (i in 0 until adapter.itemCount) {
            val holder = adapter.onCreateViewHolder(FrameLayout(a), adapter.getItemViewType(i))
            adapter.onBindViewHolder(holder, i)
            assertNotNull(holder.itemView.findViewById<TextView>(android.R.id.title))
            if (i == 1)
                assertNotNull(holder.itemView.findViewById<android.widget.Spinner>(R.id.spinner))
        }
        controller.pause().stop().destroy()
    }

    class AccessHost : Fragment(android.R.layout.simple_list_item_1) {
        internal val access = InstalledAppsAccess(this) {}
    }

    @Test
    fun notificationIconContainsTheWhiteLogoSilhouette() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val parser = a.resources.getXml(R.drawable.nw_notification)
        var paths = 0
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (
                parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "path"
            ) {
                paths++
                assertFalse(
                    parser
                        .getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")
                        .isNullOrBlank()
                )
                assertEquals(
                    "#ffffffff",
                    parser
                        .getAttributeValue(
                            "http://schemas.android.com/apk/res/android",
                            "fillColor",
                        )
                        .lowercase(),
                )
            }
        }
        parser.close()
        assertTrue("Notification mask must include crescent, star and mountains", paths >= 3)
        controller.pause().stop().destroy()
    }

    @Test
    fun stockAndroidDoesNotRequestUnrelatedApplicationPermissions() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val f = AccessHost()
        a.supportFragmentManager.beginTransaction().add(12345, f).commitNow()
        assertNull(f.access.deniedPermission())
        assertFalse(f.access.requestIfNeeded())
        controller.pause().stop().destroy()
    }
}
