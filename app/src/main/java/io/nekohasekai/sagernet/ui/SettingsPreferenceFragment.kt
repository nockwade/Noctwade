package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.confirmAction
import io.nekohasekai.sagernet.ktx.triggerFullRestart

/**
 * The settings root: one entry per sub-screen (io.nekohasekai.sagernet.ui.settings, opened by [SettingsFragment]),
 * the backup screen and the reset action.
 */
class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayoutManager(listView)
        io.nekohasekai.sagernet.ui.noctwade.NoctwadePreferenceStyle.list(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        addPreferencesFromResource(R.xml.global_preferences)
        val keep=setOf("settingsGeneral","settingsAppearance","settingsRouting","settingsSubscriptions","settingsTesting","settingsDiagnostics","settingsBackup")
        for(i in preferenceScreen.preferenceCount-1 downTo 0) {
            val p=preferenceScreen.getPreference(i)
            if(p.key !in keep) preferenceScreen.removePreference(p)
        }
        findPreference<Preference>("settingsAppearance")?.apply { title=getString(R.string.nw_personalization); fragment="io.nekohasekai.sagernet.ui.noctwade.NoctwadePersonalizationFragment"; setIcon(R.drawable.nw_palette); order=0 }
        findPreference<Preference>("settingsRouting")?.apply { fragment="io.nekohasekai.sagernet.ui.noctwade.NoctwadeRoutingFragment"; order=2 }
        val icons=mapOf("settingsGeneral" to R.drawable.nw_sliders,"settingsRouting" to R.drawable.nw_route,"settingsSubscriptions" to R.drawable.nw_subscriptions,"settingsTesting" to R.drawable.nw_ping_all,"settingsDiagnostics" to R.drawable.nw_terminal,"settingsBackup" to R.drawable.nw_backup)
        icons.forEach { (key,icon) -> findPreference<Preference>(key)?.setIcon(icon) }
        preferenceScreen.addPreference(Preference(preferenceManager.context).apply { key="clientAdvanced"; title=getString(R.string.nw_advanced); summary=getString(R.string.nw_advanced_hint); fragment="io.nekohasekai.sagernet.ui.noctwade.NoctwadeAdvancedSettingsFragment"; setIcon(R.drawable.nw_chip) })
        preferenceScreen.addPreference(Preference(preferenceManager.context).apply { key="clientTools"; title=getString(R.string.nw_tools_title); summary=getString(R.string.nw_tools_hint); fragment="io.nekohasekai.sagernet.ui.noctwade.NoctwadeToolsMenuFragment"; setIcon(R.drawable.nw_tools) })
        preferenceScreen.addPreference(Preference(preferenceManager.context).apply { key="clientAbout"; title=getString(R.string.menu_about); summary="Noctwade ${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME}"; setIcon(R.drawable.nw_info); setOnPreferenceClickListener { (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_about); true } })
        listOf("settingsAppearance","settingsGeneral","settingsRouting","settingsSubscriptions","settingsTesting","clientTools","settingsDiagnostics","settingsBackup","clientAdvanced","clientAbout").forEachIndexed { order,key -> findPreference<Preference>(key)?.order=order }
        io.nekohasekai.sagernet.ui.noctwade.NoctwadePreferenceStyle.rows(preferenceScreen)

        findPreference<Preference>(KEY_BACKUP)!!.setOnPreferenceClickListener {
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_tools)
            true
        }


    }

    private companion object {
        const val KEY_BACKUP = "settingsBackup"
        const val KEY_RESET_SETTINGS = "resetSettings"
    }
}
