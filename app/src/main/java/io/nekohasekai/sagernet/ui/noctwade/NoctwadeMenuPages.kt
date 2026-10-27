package io.nekohasekai.sagernet.ui.noctwade

import android.os.Bundle
import android.view.View
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.widget.PopupMenu
import androidx.preference.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.confirmAction
import io.nekohasekai.sagernet.ktx.triggerFullRestart
import io.nekohasekai.sagernet.ui.MainActivity

abstract class NoctwadeMenuPage : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        NoctwadePreferenceStyle.rows(preferenceScreen)
        NoctwadePreferenceStyle.list(this)
    }
}

/** Keeps locale, profile display and notification controls without duplicate theme selectors. */
class NoctwadeInterfaceSettingsFragment :
    io.nekohasekai.sagernet.ui.settings.AppearanceSettingsFragment() {
    override fun onCreatePreferences(state: Bundle?, key: String?) {
        super.onCreatePreferences(state, key)
        for (name in
            listOf("useSystemTheme", "appTheme", "nightTheme", "amoledTheme")) findPreference<
                Preference
            >(
                name
            )
            ?.let { it.parent?.removePreference(it) }
        NoctwadePreferenceStyle.rows(preferenceScreen)
    }
}

class NoctwadeAdvancedSettingsFragment : NoctwadeMenuPage() {
    override fun onCreatePreferences(state: Bundle?, key: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        setPreferencesFromResource(R.xml.global_preferences, key)
        val keep =
            setOf(
                "settingsInbound",
                "settingsTun",
                "settingsPresets",
                "settingsCore",
            )
        for (i in preferenceScreen.preferenceCount - 1 downTo 0) {
            val p = preferenceScreen.getPreference(i)
            if (p !is PreferenceCategory && p.key !in keep) preferenceScreen.removePreference(p)
        }
        val icons =
            mapOf(
                "settingsInbound" to R.drawable.nw_inbound,
                "settingsTun" to R.drawable.nw_shield,
                "settingsPresets" to R.drawable.nw_layers,
                "settingsCore" to R.drawable.nw_chip,
            )
        icons.forEach { (k, i) -> findPreference<Preference>(k)?.setIcon(i) }
        findPreference<Preference>("resetSettings")?.setOnPreferenceClickListener {
            requireContext().confirmAction(
                getString(R.string.confirm_reset_settings),
                getString(R.string.reset_settings_message),
                R.string.confirm_restore,
            ) {
                DataStore.configurationStore.reset()
                triggerFullRestart(requireContext())
            }
            true
        }
    }
}

class NoctwadeToolsMenuFragment : NoctwadeMenuPage() {
    override fun onCreatePreferences(state: Bundle?, key: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        val menu = PopupMenu(requireContext(), View(requireContext())).menu
        SupportMenuInflater(requireContext()).inflate(R.menu.main_drawer_menu, menu)
        val icons =
            mapOf(
                R.id.nav_configuration to R.drawable.nw_list,
                R.id.nav_group to R.drawable.nw_folder,
                R.id.nav_logcat to R.drawable.nw_terminal,
                R.id.nav_live_graphs to R.drawable.nw_chart,
                R.id.nav_traffic to R.drawable.nw_chart,
                R.id.nav_tools to R.drawable.nw_tools,
                R.id.nav_faq to R.drawable.nw_book,
            )
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val icon = icons[item.itemId] ?: continue
            preferenceScreen.addPreference(
                Preference(requireContext()).apply {
                    title = item.title
                    setIcon(icon)
                    setOnPreferenceClickListener {
                        (activity as? MainActivity)?.displayFragmentWithId(item.itemId)
                        true
                    }
                }
            )
        }
    }
}
