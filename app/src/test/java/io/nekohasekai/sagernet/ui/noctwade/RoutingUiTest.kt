package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAppsBinding
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeServerItemBinding
import io.nekohasekai.sagernet.route.RouteProfile
import io.nekohasekai.sagernet.route.RouteRule
import io.nekohasekai.sagernet.ui.ToolbarFragment
import io.nekohasekai.sagernet.ui.route.RouteJson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoutingUiTest {
    class Host : AppCompatActivity() {
        override fun onCreate(state: Bundle?) {
            setTheme(R.style.Theme_SagerNet)
            NoctwadeTheme.apply(this)
            super.onCreate(state)
            setContentView(FrameLayout(this).apply { id = 12345 })
        }
    }

    @Test
    fun tappingSignalNeverTriggersPingAndNumericPingStaysClickable() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val ui = LayoutNoctwadeServerItemBinding.inflate(a.layoutInflater)
        a.setContentView(ui.root)
        val density = a.resources.displayMetrics.density
        ui.root.measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        ui.root.layout(0, 0, ui.root.measuredWidth, ui.root.measuredHeight)
        assertEquals((44 * density).toInt(), ui.serverPing.width)
        assertEquals(ui.serverPing.width, ui.serverPing.height)
        assertTrue(ui.serverSignal.left >= ui.serverPing.right)
        var pings = 0
        var rows = 0
        ui.serverPing.setOnClickListener { pings++ }
        ui.root.setOnClickListener { rows++ }
        fun tap(x: Float, y: Float) {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val e = MotionEvent.obtain(0, 10, action, x, y, 0)
                ui.root.dispatchTouchEvent(e)
                e.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        ui.serverPing.render(32, false)
        tap(
            ui.serverPing.left + ui.serverPing.width / 2f,
            ui.serverPing.top + ui.serverPing.height / 2f,
        )
        assertEquals(1, pings)
        tap(
            ui.serverSignal.left + ui.serverSignal.width / 2f,
            ui.serverSignal.top + ui.serverSignal.height / 2f,
        )
        assertEquals(1, pings)
        assertEquals(1, rows)
        for (latency in listOf(32, 1400, -1)) {
            ui.serverPing.render(latency, false)
            assertTrue(ui.serverPing.isEnabled)
            ui.serverPing.performClick()
        }
        assertEquals(4, pings)
        assertNull(ui.serverPing.background)
        controller.pause().stop().destroy()
    }

    @Test
    fun backArrowPopsImmediateRoutingParent() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val parent = Fragment(android.R.layout.simple_list_item_1)
        a.supportFragmentManager.beginTransaction().add(12345, parent).commitNow()
        val child = ToolbarFragment(R.layout.layout_config_settings)
        a.supportFragmentManager
            .beginTransaction()
            .replace(12345, child)
            .addToBackStack("routing")
            .commit()
        a.supportFragmentManager.executePendingTransactions()
        fun back(v: View): View? {
            if (v.contentDescription == a.getString(R.string.navigation_back)) return v
            if (v is ViewGroup)
                for (i in 0 until v.childCount) back(v.getChildAt(i))?.let {
                    return it
                }
            return null
        }
        assertTrue(back(child.toolbar!!)!!.performClick())
        shadowOf(Looper.getMainLooper()).idle()
        a.supportFragmentManager.executePendingTransactions()
        assertSame(parent, a.supportFragmentManager.findFragmentById(12345))
        assertEquals(0, a.supportFragmentManager.backStackEntryCount)
        controller.pause().stop().destroy()
    }

    @Test
    fun routingScreensInflateInAllPalettesWithAppSwitches() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        for (key in NoctwadeTheme.keys) {
            NoctwadeTheme.prefs(a).edit().clear().putString("theme", key).commit()
            NoctwadeTheme.apply(a)
            val apps = LayoutAppsBinding.inflate(a.layoutInflater)
            assertNotNull(apps.appsEnabled)
            assertNotNull(apps.appsBypass)
            for (layout in
                listOf(
                    R.layout.layout_route_profile,
                    R.layout.layout_route_profile_header,
                    R.layout.layout_route_rule_item,
                    R.layout.layout_route_rules_title,
                )) assertNotNull(a.layoutInflater.inflate(layout, FrameLayout(a), false))
        }
        controller.pause().stop().destroy()
    }

    @Test
    fun editedProfileRoundTripKeepsAdvancedConditionsAndRuleOrder() {
        val p =
            RouteProfile().apply {
                name = "Custom"
                rules.add(
                    RouteRule().apply {
                        name = "First"
                        domain_regex.add(".*example.*")
                        package_name.add("com.example.app")
                        port_range.add("1000:2000")
                        network = "tcp"
                        outbound_id = -1
                    }
                )
                rules.add(
                    RouteRule().apply {
                        name = "Second"
                        disabled = true
                        ip_cidr.add("10.0.0.0/8")
                        action = "reject"
                        reject_method = "drop"
                    }
                )
            }
        val copy = RouteJson.profileFromJson(RouteJson.profileToJson(p))
        assertEquals(listOf("First", "Second"), copy.rules.map { it.name })
        assertEquals(p.rules[0].package_name, copy.rules[0].package_name)
        assertEquals(p.rules[0].domain_regex, copy.rules[0].domain_regex)
        assertEquals(p.rules[0].port_range, copy.rules[0].port_range)
        assertEquals("tcp", copy.rules[0].network)
        assertTrue(copy.rules[1].disabled)
        assertEquals("drop", copy.rules[1].reject_method)
    }
}
