package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeServerItemBinding
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeEco
import io.nekohasekai.sagernet.widget.noctwade.SignalStrengthView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Polish118Test {
    class Host : AppCompatActivity() {
        override fun onCreate(state: Bundle?) {
            setTheme(R.style.Theme_SagerNet)
            NoctwadeTheme.apply(this)
            super.onCreate(state)
            setContentView(FrameLayout(this))
        }
    }

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After
    fun resetEco() {
        NoctwadeEco.setMode(context, NoctwadeEco.AUTO)
    }

    @Test
    fun signalUsesThreeLevels() {
        assertEquals(3, SignalStrengthView.BARS)
        assertEquals(0, SignalStrengthView.levelFor(80, running = true))
        assertEquals(0, SignalStrengthView.levelFor(0, running = false))
        assertEquals(0, SignalStrengthView.levelFor(-1, running = false))
        assertEquals(3, SignalStrengthView.levelFor(149, running = false))
        assertEquals(2, SignalStrengthView.levelFor(150, running = false))
        assertEquals(2, SignalStrengthView.levelFor(299, running = false))
        assertEquals(1, SignalStrengthView.levelFor(300, running = false))
        assertEquals(1, SignalStrengthView.levelFor(4000, running = false))
    }

    @Test
    fun pingTextFitsItsSquareAndKeepsGapToBars() {
        val activity = Robolectric.buildActivity(Host::class.java).setup().get()
        val ui = LayoutNoctwadeServerItemBinding.inflate(activity.layoutInflater)
        activity.setContentView(ui.root)
        val density = activity.resources.displayMetrics.density
        ui.root.measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        ui.root.layout(0, 0, ui.root.measuredWidth, ui.root.measuredHeight)
        assertTrue(ui.serverSignal.left - ui.serverPing.right >= (4 * density).toInt())

        for (latency in listOf(32, 999, 4321)) {
            ui.serverPing.render(latency, false)
            val label =
                (0 until ui.serverPing.childCount)
                    .map { ui.serverPing.getChildAt(it) }
                    .filterIsInstance<TextView>()
                    .single()
            val lines = label.text.toString().split("\n")
            assertEquals(
                listOf(latency.toString(), activity.getString(R.string.nw_ping_unit)),
                lines,
            )
            // The widest line (the number) must fit inside the 44dp square.
            assertTrue(label.paint.measureText(lines[0]) <= ui.serverPing.width)
        }
    }

    @Test
    fun logoAndLauncherLayersInflate() {
        for (id in
            listOf(
                R.drawable.nw_logo,
                R.drawable.nw_launcher_foreground,
                R.drawable.nw_launcher_background,
                R.drawable.nw_launcher_monochrome,
            )) {
            val drawable = ContextCompat.getDrawable(context, id)!!
            assertTrue(drawable.intrinsicWidth > 0)
        }
    }

    @Test
    fun economyModeFollowsSettingAndPowerState() {
        val power = shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        val battery = shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        battery.setIsCharging(false)
        battery.setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 80)
        power.setIsPowerSaveMode(false)

        assertEquals(NoctwadeEco.AUTO, NoctwadeEco.mode(context))
        assertFalse(NoctwadeEco.active(context))

        power.setIsPowerSaveMode(true)
        assertEquals(NoctwadeEco.Reason.BATTERY_SAVER, NoctwadeEco.autoReason(context))
        assertTrue(NoctwadeEco.active(context))

        NoctwadeEco.setMode(context, NoctwadeEco.OFF)
        assertFalse(NoctwadeEco.active(context))

        power.setIsPowerSaveMode(false)
        battery.setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 15)
        NoctwadeEco.setMode(context, NoctwadeEco.AUTO)
        assertEquals(NoctwadeEco.Reason.LOW_BATTERY, NoctwadeEco.autoReason(context))

        battery.setIsCharging(true)
        assertNull(NoctwadeEco.autoReason(context))

        NoctwadeEco.setMode(context, NoctwadeEco.ON)
        assertTrue(NoctwadeEco.active(context))
    }

    @Test
    fun inAppModeChangeNotifiesObservers() {
        var calls = 0
        val receiver = NoctwadeEco.observe(context) { calls++ }
        NoctwadeEco.setMode(context, NoctwadeEco.ON)
        NoctwadeEco.stopObserving(context, receiver)
        NoctwadeEco.setMode(context, NoctwadeEco.OFF)
        assertEquals(1, calls)
    }
}
