package io.nekohasekai.sagernet.ui.noctwade

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon by enabling one `AppIcon_<key>` activity alias of the manifest and
 * disabling the others. The new one is enabled first so the app never loses its launcher entry.
 */
internal object AppIconSwitcher {
    private const val DEFAULT = "default"

    private fun component(context: Context, key: String) =
        ComponentName(context.packageName, "io.nekohasekai.sagernet.AppIcon_$key")

    private fun enabled(context: Context, key: String): Boolean =
        when (runCatching { context.packageManager.getComponentEnabledSetting(component(context, key)) }.getOrNull()) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // Not changed yet: the manifest's own value (only the default alias is enabled there).
            else -> key == DEFAULT
        }

    private fun set(context: Context, key: String, on: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component(context, key),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** The key of the icon in use. */
    fun current(context: Context): String =
        AppIconTable.all.firstOrNull { enabled(context, it.key) }?.key ?: DEFAULT

    fun apply(context: Context, key: String) {
        if (AppIconTable.all.none { it.key == key }) return
        set(context, key, true)
        for (other in AppIconTable.all) if (other.key != key) set(context, other.key, false)
        for (old in AppIconTable.legacy) set(context, old, false)
    }

    /**
     * Run at start. A phone must always have exactly one launcher entry: if an icon that is no longer offered is
     * still the enabled one, or none is enabled at all (the app was updated while such an icon was chosen), the
     * default icon is switched back on. Without this the home-screen icon would only open Android's "App info".
     */
    fun repair(context: Context) {
        val old = AppIconTable.legacy.any { enabled(context, it) }
        val any = AppIconTable.all.any { enabled(context, it.key) }
        if (old || !any) apply(context, DEFAULT)
    }
}
