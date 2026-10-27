package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePaddingRelative
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceDataStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.test.TestSpec
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SettingsRegistry
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.backup.BackupEntry
import io.nekohasekai.sagernet.databinding.LayoutMainBinding
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.isPreview
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.database.SettingsMapper
import io.nekohasekai.sagernet.outbound.`import`.ProfileImport
import io.nekohasekai.sagernet.ui.route.RouteImports
import io.nekohasekai.sagernet.ui.settings.DnsSettingsFragment
import io.nekohasekai.sagernet.ui.settings.XrayGeoSettingsFragment
import io.nekohasekai.sagernet.ui.test.TestSessionClient
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.MessageStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.database.RouteManager
import io.nekohasekai.sagernet.utils.AppShortcuts
import io.nekohasekai.sagernet.utils.PlatformNotifications
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.WifiStateAccess
import io.nekohasekai.sagernet.widget.bars
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import androidx.lifecycle.lifecycleScope
import androidx.drawerlayout.widget.DrawerLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ThemedActivity(),
    SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener,
    NavigationView.OnNavigationItemSelectedListener {

    companion object {
        /** Texts to import (the QR scanner): URLs and deep links one by one, the rest as one import. */
        const val EXTRA_IMPORT_TEXTS = "importTexts"
    }

    lateinit var binding: LayoutMainBinding
    lateinit var navigation: NavigationView
    private var currentMainFragment: ToolbarFragment? = null
    private val connectionPingGate=io.nekohasekai.sagernet.ui.noctwade.ConnectionPingGate()
    private var automaticPing:kotlinx.coroutines.Job?=null
    var homeSpeed = SpeedDisplayData()
        private set

    private var bottomContentInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MessageStore.setCurrentActivity(this)
        val animateInitialControls = savedInstanceState == null

        binding = LayoutMainBinding.inflate(layoutInflater)
        binding.coordinator.setBackgroundColor(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.background(this))
        // No bottom tab bar: Menu opens from the top bar, Servers from the Home server card.
        binding.bottomNavigation.visibility = View.GONE
        binding.navigationScrim.visibility = View.GONE
        binding.bottomNavigation.onTabSelected = { tab ->
            val target = when (tab) {
                0 -> R.id.nav_home
                1 -> R.id.nav_servers
                else -> R.id.nav_settings
            }
            val current = currentMainFragment
            val alreadyThere = when (tab) {
                0 -> current is NoctwadeHomeFragment
                1 -> current is NoctwadeServersFragment
                else -> current is SettingsFragment
            }
            if (!alreadyThere) displayFragmentWithId(target)
        }
        binding.navigationBackdrop.background=io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.menuGradient(this)
        binding.navigationScrim.background=io.nekohasekai.sagernet.widget.noctwade.NavigationFadeDrawable(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.background(this))
        binding.fab.initProgress(binding.fabProgress)
        // White theme: a dark FAB with a light icon (white on white is unreadable)
        if (Theme.isWhiteTheme()) {
            binding.fab.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.color_white_theme_fab)
            )
        }
        if (themeResId !in intArrayOf(
                R.style.Theme_SagerNet_Black,
                // The White theme and its night fallback read ?itemShapeFillColor too, so the selection stays visible
                R.style.Theme_SagerNet_White,
                R.style.Theme_SagerNet_White_Night
            )
        ) {
            navigation = binding.navView
            binding.drawerLayout.removeView(binding.navViewBlack)
        } else {
            navigation = binding.navViewBlack
            binding.drawerLayout.removeView(binding.navView)
        }
        navigation.setNavigationItemSelectedListener(this)
        // NavigationView pads its header and menu itself (top/bottom); its side follows side bars and cutouts.
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val bars = insets.bars()
            val rtl = navigation.layoutDirection == View.LAYOUT_DIRECTION_RTL
            navigation.updatePaddingRelative(start = if (rtl) bars.right else bars.left)
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val density = resources.displayMetrics.density
            val reserved = (16 * density).toInt() + safe.bottom
            binding.bottomNavigation.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (12 * density).toInt() + safe.bottom
                leftMargin = (16 * density).toInt() + safe.left
                rightMargin = (16 * density).toInt() + safe.right
            }
            bottomContentInset = reserved
            val scene = currentMainFragment
            binding.fragmentHolder.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (scene is NoctwadeHomeFragment || scene is NoctwadeServersFragment || scene is SettingsFragment) 0 else reserved
            }
            binding.navigationScrim.updateLayoutParams<ViewGroup.LayoutParams> { height = reserved + (88 * density).toInt() }
            binding.stats.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = reserved }
            insets
        }
        // The drawer's surface runs under the status bar: the icons follow it while the drawer is mostly open.
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            private var overDrawer = false
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if ((slideOffset > 0.5f) == overDrawer) return
                overDrawer = !overDrawer
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    if (overDrawer) !Theme.usingNightMode() else if (currentMainFragment is NoctwadeHomeFragment || currentMainFragment is NoctwadeServersFragment) false else lightStatusBar
            }
        })

        if (savedInstanceState == null) {
            displayFragmentWithId(R.id.nav_home)
        } else {
            currentMainFragment =
                supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        }
        onBackPressedDispatcher.addCallback {
            if (binding.drawerLayout.isOpen) {
                binding.drawerLayout.closeDrawers()
            } else if (supportFragmentManager.findFragmentById(R.id.fragment_holder) is NoctwadeHomeFragment) {
                moveTaskToBack(true)
            } else {
                displayFragmentWithId(R.id.nav_home)
            }
        }

        binding.fab.setOnClickListener { toggleService() }
        binding.stats.setOnClickListener { if (DataStore.serviceState.connected) binding.stats.testConnection() }
        binding.stats.setOnLongClickListener {
            showStatsMenu()
            true
        }

        setContentView(binding.root)
        currentMainFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
                ?: currentMainFragment
        if (!animateInitialControls) {
            syncMainControls(showWhenConnected = false, animate = false)
        }
        changeState(
            BaseService.State.Idle,
            animate = false,
            animateControls = animateInitialControls,
        )
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)

        if (savedInstanceState == null) intent?.let(::handleImportIntent)
        SubscriptionReportDialog.observe(this)

        refreshNavMenu(DataStore.apiDashboardEnabled)

        if (savedInstanceState == null) {
            firstStart.start()
            if (intent?.getBooleanExtra(PlatformNotifications.EXTRA_WIFI_PERMISSION, false) == true) wifiFlow.run()
        }
        runOnDefaultDispatcher { AppShortcuts.publish(applicationContext) }

        if (isPreview && DataStore.previewHintDismissedVersion != BuildConfig.VERSION_NAME) {
            MaterialAlertDialogBuilder(this)
                .setTitle(BuildConfig.VERSION_NAME)
                .setMessage(R.string.preview_version_hint)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.preview_hint_dont_show_again) { _, _ ->
                    DataStore.previewHintDismissedVersion = BuildConfig.VERSION_NAME
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        MessageStore.setCurrentActivity(this)
        DataStore.serviceError.takeIf { it.isNotEmpty() }?.let(::showServiceError)

        if (DataStore.hideFromRecentApps) {
            applyHideFromRecentApps(DataStore.hideFromRecentApps)
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        val restoredFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        if (restoredFragment != null && restoredFragment !== currentMainFragment) {
            currentMainFragment = restoredFragment
            syncMainControls(
                fragment = restoredFragment,
                showWhenConnected = DataStore.serviceState == BaseService.State.Connected,
                animate = false,
            )
        }
    }

    fun applyHideFromRecentApps(hide: Boolean) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.appTasks
            if (tasks.isNotEmpty()) {
                val task = tasks[0]
                task.setExcludeFromRecents(hide)
            }
        } catch (e: Exception) {
            Logs.w("Failed to set excludeFromRecents: ${e.message}")
        }
    }

    /** The dashboard item follows the sing-box API (core_box_api_port), which serves the dashboard. */
    fun refreshNavMenu(dashboard: Boolean) {
        if (::navigation.isInitialized) {
            navigation.menu.findItem(R.id.nav_traffic)?.isVisible = dashboard
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.getBooleanExtra(PlatformNotifications.EXTRA_WIFI_PERMISSION, false)) {
            wifiFlow.run()
            return
        }

        handleImportIntent(intent)
    }

    /** VIEW links and files, SEND streams and texts, and the texts of the QR scanner ([EXTRA_IMPORT_TEXTS]). */
    private fun handleImportIntent(intent: Intent) {
        intent.getStringArrayListExtra(EXTRA_IMPORT_TEXTS)?.let {
            SubscribeFlows.importTexts(this, it)
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> openLink(intent.data ?: return)
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) {
                    openFile(stream)
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { SubscribeFlows.importText(this, it) }
                }
            }
        }
    }

    /**
     * Files go to the backup restore or the text import; subscription links show the Groups screen and ask; route and
     * other throne:// commands follow the desktop's deep links; profile links are confirmed before the import.
     */
    private fun openLink(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "content" || scheme == "file") return openFile(uri)
        val link = uri.toString()
        if (SubscribeFlows.subscribeLink(link) != null) displayFragmentWithId(R.id.nav_group)
        if (scheme == "clash" || scheme == "throne" && !uri.host.equals("add", ignoreCase = true)) {
            SubscribeFlows.importText(this, link)
        } else {
            runOnDefaultDispatcher { importProfile(uri) }
        }
    }

    private fun openFile(uri: Uri) {
        // Sniffing the backup magic may read a cloud file over the network.
        runOnDefaultDispatcher {
            if (!BackupEntry.open(this@MainActivity, uri)) SubscribeFlows.importUri(this@MainActivity, uri)
        }
    }

    /** throne://route and throne://remoteroute links (deep link, QR code, clipboard); the Routes screen shows the result. */
    fun importRouteLink(text: String) {
        RouteImports.importLink(this, text) { displayFragmentWithId(R.id.nav_route) }
    }

    /** Opens one settings sub-screen, e.g. Settings › Routing, with the settings root below it. */
    fun openSettingsScreen(fragmentClass: String, title: CharSequence) {
        displayFragment(SettingsFragment.forScreen(fragmentClass, title))
        navigation.menu.findItem(R.id.nav_settings).isChecked = true
    }

    fun urlTest(): Int {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error("not started")
        }
        return connection.service!!.urlTest()
    }

    /** A profile link opened from outside: confirmed, then imported into the current group. */
    suspend fun importProfile(uri: Uri) {
        val link = uri.toString()
        val result = try {
            ProfileImport.parse(link, SettingsMapper.xrayVlessPreference(), SettingsMapper.buildContext())
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
            }
            return
        }
        for (message in result.messages) Logs.w(message)
        val profile = result.outbounds.firstOrNull()
        if (profile == null) {
            val report = result.report()
            onMainDispatcher {
                if (report.hasProblems()) {
                    SubscriptionReportDialog.show(this@MainActivity, getString(R.string.validation_import_title), report.text())
                } else {
                    alert(getString(R.string.no_proxies_found)).show()
                }
            }
            return
        }

        onMainDispatcher {
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.profile_import)
                .setMessage(getString(R.string.profile_import_message, profile.displayName()))
                .setPositiveButton(R.string.yes) { _, _ ->
                    displayFragmentWithId(R.id.nav_configuration)
                    SubscribeFlows.importText(this@MainActivity, link)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (item.isChecked) binding.drawerLayout.closeDrawers() else {
            return displayFragmentWithId(item.itemId)
        }
        return true
    }


    @SuppressLint("CommitTransaction")
    fun displayFragment(fragment: ToolbarFragment) {
        // The menu sits to the left of Home (it opens from the left, like a drawer); lists to the right.
        fun position(value:Any?)=when(value){is NoctwadeHomeFragment->0;is NoctwadeServersFragment,is ConfigurationFragment,is GroupFragment->1;is SettingsFragment->-1;else->2}
        val previous=currentMainFragment
        val forward=position(fragment)>=position(previous)
        currentMainFragment = fragment
        val transaction = supportFragmentManager.beginTransaction()
        if (previous!=null && NoctwadeMotion.enabled(this)) {
            if(forward) transaction.setCustomAnimations(R.anim.nw_slide_in_right,R.anim.nw_slide_out_left)
            else transaction.setCustomAnimations(R.anim.nw_slide_in_left,R.anim.nw_slide_out_right)
        }
        transaction
            .setReorderingAllowed(true)
            .replace(R.id.fragment_holder, fragment)
            .commitAllowingStateLoss()
        binding.drawerLayout.closeDrawers()
        syncMainControls(fragment, showWhenConnected = false, animate = true)
    }

    private fun syncMainControls(
        fragment: Any? = currentMainFragment
            ?: supportFragmentManager.findFragmentById(R.id.fragment_holder),
        showWhenConnected: Boolean,
        animate: Boolean,
    ) {
        when (fragment) {
            is NoctwadeHomeFragment -> binding.bottomNavigation.select(0, animate)
            is NoctwadeServersFragment, is ConfigurationFragment, is GroupFragment -> binding.bottomNavigation.select(1, animate)
            is SettingsFragment -> binding.bottomNavigation.select(2, animate)
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = if (fragment is NoctwadeHomeFragment || fragment is NoctwadeServersFragment) false else lightStatusBar
            isAppearanceLightNavigationBars = false
        }
        val immersive = fragment is NoctwadeHomeFragment || fragment is NoctwadeServersFragment
        binding.navigationBackdrop.visibility = if (immersive) View.GONE else View.VISIBLE
        binding.fragmentHolder.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = if (immersive || fragment is SettingsFragment) 0 else bottomContentInset.takeIf { it > 0 } ?: (16 * resources.displayMetrics.density).toInt()
        }
        val showControls = fragment is ConfigurationFragment
        binding.stats.visibility = if (showControls) View.VISIBLE else View.GONE
        binding.stats.useExternalScrollDriver = fragment is ConfigurationFragment
        binding.stats.syncMainControls(
            showControls,
            DataStore.serviceState,
            showWhenConnected,
            animate,
        )
        binding.fab.animate().cancel()
        if (showControls) {
            binding.fab.show()
            // a start error shown on another screen sits at the bottom, where the FAB now comes back
            errorBar?.takeIf { it.isShown }?.anchorAboveFab()
        } else {
            errorBar?.takeIf { it.isShown }?.anchorView = null
            binding.fab.hideProgress()
            binding.fabProgress.hide()
            binding.fabProgress.visibility = View.INVISIBLE
            if (animate && binding.fab.isLaidOut) {
                binding.fab.hide()
            } else {
                binding.fab.visibility = View.INVISIBLE
            }
        }
    }

    private fun refreshConfigurationProfileState() {
        val fragment = currentMainFragment
            ?: supportFragmentManager.findFragmentById(R.id.fragment_holder)
        (fragment as? ConfigurationFragment)?.refreshProfileState()
    }

    fun driveBottomBar(scrollDy: Int) {
        binding.stats.onListScrolled(scrollDy)
    }

    fun displayServers(groupId: Long = 0L) {
        displayFragment(NoctwadeServersFragment.forGroup(groupId))
        navigation.menu.findItem(R.id.nav_servers).isChecked = true
    }

    fun displayFragmentWithId(@IdRes id: Int): Boolean {
        when (id) {
            R.id.nav_home -> displayFragment(NoctwadeHomeFragment())
            R.id.nav_servers -> displayFragment(NoctwadeServersFragment())
            R.id.nav_configuration -> {
                displayFragment(ConfigurationFragment())
            }

            R.id.nav_group -> displayFragment(GroupFragment())
            R.id.nav_route -> displayFragment(SettingsFragment.forScreen(io.nekohasekai.sagernet.ui.noctwade.NoctwadeRoutingFragment::class.java.name, getString(R.string.menu_route)))
            R.id.nav_settings -> displayFragment(SettingsFragment())
            R.id.nav_traffic -> displayFragment(DashboardFragment())
            R.id.nav_tools -> displayFragment(ToolsFragment())
            R.id.nav_logcat -> displayFragment(LogcatFragment())
            R.id.nav_live_graphs -> displayFragment(LiveGraphsFragment())
            R.id.nav_faq -> {
                launchCustomTab("https://github.com/nockwade/Noctwade")
                return false
            }

            R.id.nav_about -> displayFragment(AboutFragment())

            else -> return false
        }
        navigation.menu.findItem(id).isChecked = true
        return true
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
        animateControls: Boolean = animate,
    ) {
        DataStore.serviceState = state
        io.nekohasekai.sagernet.ui.noctwade.ConnectionClock.onState(this, state)
        val runningId=DataStore.currentProfile.takeIf{it>0}?:DataStore.selectedProxy
        if(connectionPingGate.update(state.connected,runningId)){
            automaticPing?.cancel()
            automaticPing=lifecycleScope.launch {
                val client=io.nekohasekai.sagernet.ui.test.TestSessionClient
                if(!(client.state.value.running && client.state.value.testCurrent)){
                    client.state.first{!it.running}
                    if(DataStore.serviceState.connected && (DataStore.currentProfile.takeIf{it>0}?:DataStore.selectedProxy)==runningId)
                        client.startCurrent(io.nekohasekai.sagernet.bg.test.TestSpec.KIND_URL)
                }
            }
        }else if(!state.connected){automaticPing?.cancel();automaticPing=null}
        refreshConfigurationProfileState()
        when (val fragment = currentMainFragment ?: supportFragmentManager.findFragmentById(R.id.fragment_holder)) {
            is NoctwadeHomeFragment -> fragment.renderState(state)
            is NoctwadeServersFragment -> fragment.refreshProfileState()
            is RouteFragment -> fragment.onServiceStateChanged()
            is SettingsFragment -> (fragment.childFragmentManager.findFragmentById(R.id.settings) as? RouteFragment)?.onServiceStateChanged()
            is DashboardFragment -> fragment.onServiceStateChanged()
        }

        if (!state.connected) homeSpeed = SpeedDisplayData()

        binding.fab.changeState(state, DataStore.serviceState, animate)
        binding.stats.changeState(state)
        syncMainControls(
            showWhenConnected = state == BaseService.State.Connected,
            animate = animateControls,
        )
        if (state == BaseService.State.Connecting) errorBar?.dismiss()
        if (msg == null) return
        if (msg == DataStore.serviceError) showServiceError(msg) else snackbar(getString(R.string.vpn_error, msg)).show()
    }

    private var errorBar: Snackbar? = null
    private var errorShown = ""

    /** A failed start stays until the next start, its action or a swipe; the service keeps it for a later visit. */
    private fun showServiceError(message: String) {
        if (errorBar?.isShownOrQueued == true && errorShown == message) return
        errorShown = message
        val bar = snackbar(message).setDuration(Snackbar.LENGTH_INDEFINITE)
        if (DataStore.serviceErrorDns) {
            bar.setAction(R.string.settings_dns) {
                openSettingsScreen(DnsSettingsFragment::class.java.name, getString(R.string.settings_dns))
            }
        } else if (DataStore.serviceErrorGeo) {
            bar.setAction(R.string.xray_geo_assets_action) {
                openSettingsScreen(XrayGeoSettingsFragment::class.java.name, getString(R.string.xray_geo_assets))
            }
        } else if (DataStore.serviceErrorRuleSets) {
            bar.setAction(R.string.rule_set_deferred_action) { confirmStartWithoutRuleSets() }
        } else {
            bar.setAction(R.string.menu_log) { displayFragmentWithId(R.id.nav_logcat) }
        }
        errorBar = bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_ACTION) {
                    DataStore.serviceError = ""
                    DataStore.serviceErrorDns = false
                    DataStore.serviceErrorGeo = false
                    DataStore.serviceErrorRuleSets = false
                }
            }
        }).also { it.show() }
    }

    /** The next start, within a few minutes, goes without the rule-sets it could not download (DeferredRuleSets). */
    private fun confirmStartWithoutRuleSets() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rule_set_deferred_title)
            .setMessage(R.string.rule_set_deferred_message)
            .setPositiveButton(R.string.connect) { _, _ ->
                if (DataStore.serviceState.canStop) return@setPositiveButton
                DataStore.startWithoutRuleSets = System.currentTimeMillis()
                startFromUi()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Shows group [id] when the profiles screen is open; otherwise it opens there next, being the current group. */
    fun showGroup(id: Long) {
        (supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ConfigurationFragment)?.showGroup(id)
    }

    /** The stats bar's long-press menu: the live graphs, and a speed test of the running connection. */
    private fun showStatsMenu() {
        val popup = PopupMenu(this, binding.stats)
        popup.menuInflater.inflate(R.menu.stats_bar_menu, popup.menu)
        popup.menu.findItem(R.id.action_test_speed_current).isEnabled = DataStore.serviceState.connected
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_live_graphs -> displayFragmentWithId(R.id.nav_live_graphs)
                R.id.action_test_speed_current -> {
                    if (!TestSessionClient.startCurrent(TestSpec.KIND_SPEED)) {
                        snackbar(R.string.profiles_test_busy).show()
                    }
                }
            }
            true
        }
        popup.show()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG).apply {
            if (binding.fab.visibility == View.VISIBLE) anchorAboveFab()
            else anchorView = null
        }
    }

    // Visibility, not isShown: the saved start error is shown from the first onResume, before the window is attached.
    // The layout listener places the bar once the FAB has been laid out.
    private fun Snackbar.anchorAboveFab() {
        anchorView = binding.fab
        isAnchorViewLayoutListenerEnabled = true
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    override fun onServiceConnected(service: ISagerNetService) = changeState(
        try {
            BaseService.State.values()[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) snackbar(R.string.vpn_permission_denied).show()
    }

    private val firstStart = FirstStartPrompts(this)
    private val wifiFlow = WifiPermissionFlow(this)

    fun toggleService() {
        if (DataStore.serviceState == BaseService.State.Stopping) return
        if (DataStore.serviceState.canStop) SagerNet.stopService() else startFromUi()
    }

    /** Wi-Fi rules of the current route profile ask for location access once before a start from here. */
    private fun startFromUi() {
        if (DataStore.wifiPermissionAsked || WifiStateAccess.status(this) == WifiStateAccess.Status.OK) {
            return launchConnect()
        }
        lifecycleScope.launch {
            val usesWifi = withContext(Dispatchers.IO) {
                runCatching {
                    RouteManager.current().rules.any { rule ->
                        !rule.disabled && (rule.wifi_ssid.any { it.isNotBlank() } || rule.wifi_bssid.any { it.isNotBlank() })
                    }
                }.getOrDefault(false)
            }
            if (usesWifi) {
                DataStore.wifiPermissionAsked = true
                wifiFlow.run { launchConnect() }
            } else {
                launchConnect()
            }
        }
    }

    private fun launchConnect() {
        try {
            connect.launch(null)
        } catch (_: ActivityNotFoundException) {
            VpnRequestActivity.showConsentUnavailable(this)
        } catch (_: SecurityException) {
            VpnRequestActivity.showConsentUnavailable(this)
        }
    }

    // may NOT called when app is in background
    // ONLY do UI update here, write DB in bg process
    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        homeSpeed = stats
        (currentMainFragment as? NoctwadeHomeFragment)?.renderSpeed(stats)
        binding.stats.updateSpeed(stats.txRateProxy, stats.rxRateProxy)
    }

    override suspend fun cbTrafficUpdate(data: TrafficDataBatch) {
        ProfileManager.postUpdate(data.items)
    }

    override fun cbSelectorUpdate(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        DataStore.currentProfile = id
        refreshConfigurationProfileState()
        (currentMainFragment as? NoctwadeHomeFragment)?.refreshProfile()
        (currentMainFragment as? NoctwadeServersFragment)?.refreshProfileState()
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.PROFILE_ID, Key.PROFILE_CURRENT -> binding.root.post {
                (currentMainFragment as? NoctwadeHomeFragment)?.refreshProfile()
                (currentMainFragment as? NoctwadeServersFragment)?.refreshProfileState()
            }
            Key.SERVICE_MODE -> onBinderDied()
            // The JSON editors of custom_inbound and dns_object write here from their own activity.
            Key.PROXY_APPS, Key.BYPASS_MODE, Key.INDIVIDUAL,
            SettingsRegistry.CUSTOM_INBOUND.key, SettingsRegistry.DNS_OBJECT.key -> {
                if (DataStore.serviceState.canStop) {
                    snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                        SagerNet.reloadService()
                    }.show()
                }
            }
        }
    }

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // the drawer sits at the start edge: the right one in RTL layouts
        val rtl = binding.root.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val towardsDrawer = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val awayFromDrawer = if (rtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        when (keyCode) {
            towardsDrawer -> {
                if (super.onKeyDown(keyCode, event)) return true
                // Row buttons, grid columns, tabs and toolbar items come first; the drawer opens at the edge.
                val direction = if (rtl) View.FOCUS_RIGHT else View.FOCUS_LEFT
                if (!binding.drawerLayout.isOpen && screenFocusTarget(direction) == null) {
                    binding.drawerLayout.open()
                    navigation.requestFocus()
                    return true
                }
            }

            awayFromDrawer -> {
                if (binding.drawerLayout.isOpen) {
                    binding.drawerLayout.close()
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> if (!binding.drawerLayout.isOpen && focusMainControls()) return true

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (SagerNet.isTv) {
                toggleService()
                return true
            }
        }

        if (super.onKeyDown(keyCode, event)) return true
        if (binding.drawerLayout.isOpen) return false

        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        return fragment != null && fragment.onKeyDown(keyCode, event)
    }

    /** Where focus would move inside the current screen, when that view is on screen (other tabs' pages are not). */
    private fun screenFocusTarget(direction: Int): View? {
        val focused = currentFocus ?: return null
        val root = supportFragmentManager.findFragmentById(R.id.fragment_holder)?.view as? ViewGroup ?: return null
        if (!focused.isInside(root)) return null
        return FocusFinder.getInstance().findNextFocus(root, focused, direction)
            ?.takeIf { it.getGlobalVisibleRect(Rect()) }
    }

    /** D-pad down past the end of the screen's content reaches the FAB, then the stats bar (they float over it). */
    private fun focusMainControls(): Boolean {
        val focused = currentFocus ?: return false
        val root = supportFragmentManager.findFragmentById(R.id.fragment_holder)?.view ?: return false
        if (!focused.isInside(root)) return false
        var parent = focused.parent
        while (parent is View && parent !== root) {
            if (parent.canScrollVertically(1)) return false
            parent = parent.parent
        }
        // Other tabs' pages beside the pager are focusable too: go to what is visible below, if anything.
        screenFocusTarget(View.FOCUS_DOWN)?.let { return it.requestFocus() }
        return when {
            binding.fab.isShown && binding.fab.isFocusable -> binding.fab.requestFocus()
            binding.stats.isShown && binding.stats.isFocusable -> binding.stats.requestFocus()
            else -> false
        }
    }

    private fun View.isInside(root: View): Boolean {
        var view: View? = this
        while (view != null) {
            if (view === root) return true
            view = view.parent as? View
        }
        return false
    }

}
