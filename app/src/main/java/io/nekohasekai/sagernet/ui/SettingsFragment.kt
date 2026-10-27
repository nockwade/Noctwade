package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.settings.SettingsScreenFragment
import io.nekohasekai.sagernet.widget.applyListInsets

/** Hosts the settings root and its sub-screens; back and the toolbar arrow return to the previous screen. */
class SettingsFragment : ToolbarFragment(R.layout.layout_config_settings),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (searchPanel?.isOpen == true) closeSearch() else childFragmentManager.popBackStack()
        }
    }

    private val searchPanel: io.nekohasekai.sagernet.ui.noctwade.MenuSearchPanel?
        get() = view?.findViewById(R.id.settings_search)

    private fun openSearch() {
        searchPanel?.open()
        syncToolbar()
    }

    private fun closeSearch() {
        searchPanel?.close()
        syncToolbar()
    }

    private val backStackListener = FragmentManager.OnBackStackChangedListener { syncToolbar() }

    /** Every settings screen's list scrolls under the navigation bar and clears side bars. */
    private val listInsets = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            (f as? PreferenceFragmentCompat)?.let {
                it.listView.applyListInsets()
                io.nekohasekai.sagernet.ui.noctwade.NoctwadePreferenceStyle.rows(it.preferenceScreen)
                io.nekohasekai.sagernet.ui.noctwade.NoctwadePreferenceStyle.list(it)
                f.arguments?.getString("searchKey")?.let{key->it.listView.post{if(f.view!=null && it.findPreference<Preference>(key)!=null)it.scrollToPreference(key)}}

            }
            io.nekohasekai.sagernet.ui.noctwade.DockScrollContent.apply(v)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.registerFragmentLifecycleCallbacks(listInsets, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        childFragmentManager.addOnBackStackChangedListener(backStackListener)
        searchPanel?.onOpen = { entry ->
            closeSearch()
            openScreen(entry.screen, getString(entry.screenTitle), Bundle().apply { putString("searchKey", entry.key) })
        }

        // A recreated activity (theme switch) restores the open sub-screen and its back stack.
        if (childFragmentManager.findFragmentById(R.id.settings) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsPreferenceFragment())
                .commitAllowingStateLoss()
            arguments?.getString(ARG_SCREEN)?.let { name ->
                openScreen(name, arguments?.getCharSequence(ARG_SCREEN_TITLE), Bundle())
            }
        }
        syncToolbar()
    }

    override fun onDestroyView() {
        childFragmentManager.removeOnBackStackChangedListener(backStackListener)
        super.onDestroyView()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val name = pref.fragment ?: return false
        openScreen(name, pref.title, pref.extras)
        return true
    }

    internal fun openScreen(name: String, title: CharSequence?, extras: Bundle) {
        val fragment = childFragmentManager.fragmentFactory.instantiate(requireContext().classLoader, name)
        fragment.arguments = Bundle(extras).apply {
            putCharSequence(SettingsScreenFragment.ARG_TITLE, title)
        }
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply { if(io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion.enabled(requireContext())) setCustomAnimations(R.anim.nw_slide_in_right,R.anim.nw_slide_out_left,R.anim.nw_slide_in_left,R.anim.nw_slide_out_right) }
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    companion object {
        private const val ARG_SCREEN = "screen"
        private const val ARG_SCREEN_TITLE = "screenTitle"

        /** The settings with the sub-screen [fragmentClass] open on top of the root. */
        fun forScreen(fragmentClass: String, title: CharSequence) = SettingsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SCREEN, fragmentClass)
                putCharSequence(ARG_SCREEN_TITLE, title)
            }
        }
    }

    private fun syncToolbar() {
        val toolbar = toolbar ?: return
        val nested = childFragmentManager.backStackEntryCount > 0
        val searching = searchPanel?.isOpen == true
        backCallback.isEnabled = nested || searching
        val current = childFragmentManager.findFragmentById(R.id.settings)
        // Source-management pages supply their own toolbar, including import/update actions.
        view?.findViewById<View>(R.id.appbar)?.visibility = if(current is ToolbarFragment) View.GONE else View.VISIBLE
        toolbar.menu.removeItem(0x7751)
        if (!nested) toolbar.menu.add(0, 0x7751, 0, if (searching) R.string.nw_search_close else R.string.nw_menu_search).apply {
            setIcon(if (searching) R.drawable.nw_close else R.drawable.nw_search)
            icon?.setTint(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(requireContext()))
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener { if (searching) closeSearch() else openSearch(); true }
        }
        toolbar.title = current?.arguments?.getCharSequence(SettingsScreenFragment.ARG_TITLE)
            ?.takeIf { nested } ?: getString(R.string.nw_menu)
        if (nested) {
            toolbar.setNavigationIcon(R.drawable.nw_back)
            toolbar.setNavigationContentDescription(R.string.navigation_back)
            toolbar.setNavigationOnClickListener { childFragmentManager.popBackStack() }
        } else {
            toolbar.setNavigationIcon(R.drawable.nw_back)
            toolbar.setNavigationContentDescription(R.string.nw_home)
            toolbar.setNavigationOnClickListener { (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_home) }
        }
        toolbar.setBackgroundColor(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.background(requireContext()))
        toolbar.setTitleTextColor(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(requireContext()))
        toolbar.navigationIcon?.setTint(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(requireContext()))
    }

}
