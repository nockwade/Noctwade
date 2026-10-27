package io.nekohasekai.sagernet.widget.noctwade

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme

/**
 * Economy mode: backgrounds stay as static pictures and endless decorative animations (live
 * atmosphere, connect-button pulse) stop.
 *
 * "auto" turns it on with system battery saver, on low-RAM devices and when the battery is at or
 * below [LOW_BATTERY_PERCENT] and not charging.
 */
internal object NoctwadeEco {
    const val PREF_KEY = "eco"
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"
    val modes = listOf(AUTO, ON, OFF)
    const val LOW_BATTERY_PERCENT = 20

    fun mode(context: Context): String =
        NoctwadeTheme.prefs(context).getString(PREF_KEY, AUTO).takeIf { it in modes } ?: AUTO

    fun setMode(context: Context, mode: String) {
        require(mode in modes)
        NoctwadeTheme.prefs(context).edit().putString(PREF_KEY, mode).apply()
        // In-app change: no system broadcast, so tell attached views directly.
        listeners.values.forEach { it() }
    }

    private val listeners = java.util.concurrent.ConcurrentHashMap<BroadcastReceiver, () -> Unit>()

    fun active(context: Context): Boolean =
        when (mode(context)) {
            ON -> true
            OFF -> false
            else -> autoReason(context) != null
        }

    enum class Reason {
        BATTERY_SAVER,
        LOW_RAM,
        LOW_BATTERY,
    }

    /** Why "auto" would be active right now, or null if it would not. */
    fun autoReason(context: Context): Reason? {
        val app = context.applicationContext
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isPowerSaveMode == true) return Reason.BATTERY_SAVER
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activity?.isLowRamDevice == true) return Reason.LOW_RAM
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (battery != null && !battery.isCharging) {
            // 0 / Int.MIN_VALUE mean "unknown" on some devices and emulators.
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 1..LOW_BATTERY_PERCENT) return Reason.LOW_BATTERY
        }
        return null
    }

    /**
     * Calls [onChange] when power conditions that affect "auto" change. Unregister with the
     * returned receiver.
     */
    fun observe(context: Context, onChange: () -> Unit): BroadcastReceiver {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onChange()
            }
        val filter =
            IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        // Only protected system broadcasts: nobody else can send them, and on Android < 13
        // RECEIVER_NOT_EXPORTED would demand an extra manifest permission.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        listeners[receiver] = onChange
        return receiver
    }

    fun stopObserving(context: Context, receiver: BroadcastReceiver?) {
        if (receiver == null) return
        listeners.remove(receiver)
        runCatching { context.unregisterReceiver(receiver) }
    }
}
