package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupRepo
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeHomeBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.noctwade.ConnectionImportDialog
import io.nekohasekai.sagernet.ui.noctwade.SubscriptionQuota
import io.nekohasekai.sagernet.ui.profiles.ProfilesDbWatcher
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoctwadeHomeFragment : ToolbarFragment(R.layout.layout_noctwade_home) {
    private var binding: LayoutNoctwadeHomeBinding? = null
    private var profileJob: Job? = null
    private var selected: ProxyEntity? = null
    private var selectedGroupId = 0L
    private val imports = ConnectionImportDialog(this)
    private val main
        get() = activity as? MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = LayoutNoctwadeHomeBinding.bind(view)
        binding = ui
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            val padding = (20 * resources.displayMetrics.density).toInt()
            ui.homeContent.setPadding(padding + bars.left, bars.top, padding + bars.right, padding)
            ui.homeScroll.setPadding(
                0,
                0,
                0,
                (24 * resources.displayMetrics.density).toInt() + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
        ui.homePower.setOnClickListener {
            if (DataStore.serviceState.canStop || selected != null) main?.toggleService()
            else imports.show(ui.homeHeader.headerAdd)
        }
        ui.homeHeader.headerMenu.setOnClickListener {
            main?.displayFragmentWithId(R.id.nav_settings)
        }
        ui.homeHeader.headerAdd.setOnClickListener { imports.show(ui.homeHeader.headerAdd) }
        ui.homeServer.setOnClickListener {
            io.nekohasekai.sagernet.ui.noctwade.ServersSheet.show(this, selectedGroupId)
        }
        ui.homePing.setOnClickListener {
            if (DataStore.serviceState.connected)
                io.nekohasekai.sagernet.ui.test.TestSessionClient.startCurrent(
                    io.nekohasekai.sagernet.bg.test.TestSpec.KIND_URL
                )
            else
                selected?.let {
                    io.nekohasekai.sagernet.ui.test.TestSessionClient.startProfiles(
                        io.nekohasekai.sagernet.bg.test.TestSpec.KIND_URL,
                        listOf(it.id),
                    )
                }
        }
        ui.homeQuota.root.setOnClickListener {
            io.nekohasekai.sagernet.ui.noctwade.ServersSheet.show(this, selectedGroupId)
        }
        ui.homeQuota.quotaRefresh.setOnClickListener {
            val id = selectedGroupId
            if (id > 0 && id !in io.nekohasekai.sagernet.group.SubscriptionClient.states.value)
                io.nekohasekai.sagernet.group.SubscriptionClient.refreshGroup(id, showDiff = false)
        }
        listOf(ui.homeServer, ui.homeQuota.root, ui.homeHeader.headerAdd)
            .forEach(NoctwadeMotion::press)
        renderState(DataStore.serviceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                refreshProfile()
                ProfilesDbWatcher(::refreshProfile, ::refreshProfile).start(this)
                launch {
                    io.nekohasekai.sagernet.ui.test.TestSessionClient.state.collect { renderPing() }
                }
                launch {
                    io.nekohasekai.sagernet.group.SubscriptionClient.states.collect { updating ->
                        binding?.homeQuota?.quotaRefresh?.let {
                            io.nekohasekai.sagernet.widget.noctwade.RefreshSpin.set(
                                it,
                                selectedGroupId in updating,
                            )
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null && NoctwadeMotion.enabled(requireContext())) {
            listOf(
                    ui.homeHeader.root,
                    ui.homePower,
                    ui.homeStatus,
                    ui.homeSubtitle,
                    ui.homeServer,
                    ui.homeQuota.root,
                )
                .forEachIndexed { index, child ->
                    child.alpha = 0f
                    child.translationY = 10 * resources.displayMetrics.density
                    child
                        .animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(index * 35L)
                        .setDuration(340L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.homePower?.setEffectsVisible(true)
        binding?.homeBackdrop?.setEffectsVisible(true)
        renderState(DataStore.serviceState)
        refreshProfile()
    }

    override fun onPause() {
        binding?.homeTimer?.removeCallbacks(timerTick)
        binding?.homePower?.setEffectsVisible(false)
        binding?.homeBackdrop?.setEffectsVisible(false)
        super.onPause()
    }

    /** Ticks the session timer once a second while connected and visible. */
    private val timerTick =
        object : Runnable {
            override fun run() {
                val ui = binding ?: return
                val elapsed =
                    io.nekohasekai.sagernet.ui.noctwade.ConnectionClock.elapsed(
                        requireContext(),
                        DataStore.serviceState,
                    )
                ui.homeTimer.isVisible = elapsed != null
                ui.homeTimer.removeCallbacks(this)
                if (elapsed != null) {
                    ui.homeTimer.text =
                        io.nekohasekai.sagernet.ui.noctwade.ConnectionClock.format(elapsed)
                    if (isResumed) ui.homeTimer.postDelayed(this, 1000L - elapsed % 1000L)
                }
            }
        }

    fun renderState(state: BaseService.State) {
        val ui = binding ?: return
        timerTick.run()
        ui.homePower.setState(state)
        ui.homePower.contentDescription =
            getString(
                if (state.canStop) R.string.nw_disconnect_action else R.string.nw_connect_action
            )
        val connected = state == BaseService.State.Connected
        ui.homeStatus.setText(
            when (state) {
                BaseService.State.Connected -> R.string.nw_connected
                BaseService.State.Connecting -> R.string.nw_connecting
                BaseService.State.Stopping -> R.string.nw_stopping
                else -> R.string.nw_offline
            }
        )
        setSubtitle(
            ui,
            when (state) {
                BaseService.State.Connected -> R.string.nw_secure
                BaseService.State.Connecting -> R.string.nw_wait
                BaseService.State.Stopping -> R.string.nw_wait_stop
                else -> if (selected == null) R.string.nw_add_hint else R.string.nw_tap_connect
            },
        )
        ui.homeSubtitle.setTextColor(
            if (connected) ContextCompat.getColor(requireContext(), R.color.nw_green)
            else io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.muted(requireContext())
        )
        refreshProfile()
    }

    /** "Tap to connect" is implied by the button itself, so the idle line stays hidden. */
    private fun setSubtitle(ui: LayoutNoctwadeHomeBinding, text: Int) {
        ui.homeSubtitle.setText(text)
        ui.homeSubtitle.isVisible = text != R.string.nw_tap_connect
    }

    // No transfer card on Home; MainActivity can continue forwarding service samples.
    fun renderSpeed(@Suppress("UNUSED_PARAMETER") stats: SpeedDisplayData) = Unit

    fun refreshProfile() {
        val ui = binding ?: return
        val context = requireContext()
        profileJob?.cancel()
        profileJob =
            viewLifecycleOwner.lifecycleScope.launch {
                val info =
                    try {
                        withContext(Dispatchers.IO) {
                            val id =
                                if (DataStore.serviceState.started && DataStore.currentProfile > 0)
                                    DataStore.currentProfile
                                else DataStore.selectedProxy
                            val profile = ProfileManager.getProfile(id)
                            val group =
                                if (profile != null) GroupRepo.get(profile.groupId)
                                else GroupRepo.current()
                            HomeProfile(
                                profile,
                                profile?.displayName().orEmpty(),
                                group?.displayName().orEmpty(),
                                profile
                                    ?.let {
                                        io.nekohasekai.sagernet.ui.noctwade.ServerPresentation
                                            .protocol(it.type) +
                                            " · " +
                                            io.nekohasekai.sagernet.ui.noctwade.ServerPresentation
                                                .security(it)
                                    }
                                    .orEmpty(),
                                SubscriptionQuota.from(context, group),
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Logs.w(error)
                        null
                    }
                if (binding !== ui) return@launch
                selected = info?.profile
                ui.homeServerName.setTitle(
                    info?.name?.takeIf(String::isNotBlank) ?: getString(R.string.nw_no_server)
                )
                ui.homeServerIcon.setCountry(
                    info
                        ?.profile
                        ?.let(io.nekohasekai.sagernet.ui.noctwade.ServerPresentation::country)
                        .orEmpty()
                )
                ui.homeServerDetail.text =
                    info?.let {
                        listOf(it.group, it.type).filter(String::isNotBlank).joinToString(" · ")
                    } ?: getString(R.string.nw_locations)
                renderPing()
                ui.homeServer.contentDescription =
                    getString(R.string.nw_choose_action, ui.homeServerName.text)
                val quota = info?.quota ?: SubscriptionQuota.from(context, null)
                selectedGroupId = quota.groupId
                ui.homeQuota.apply {
                    quotaName.text = quota.name
                    quotaRefresh.isVisible = quota.subscription
                    io.nekohasekai.sagernet.widget.noctwade.RefreshSpin.set(
                        quotaRefresh,
                        quota.groupId in
                            io.nekohasekai.sagernet.group.SubscriptionClient.states.value,
                    )
                    quotaAmount.text = quota.amount
                    quotaAllowance.text = quota.allowance
                    quotaDetail.text = quota.detail
                    quotaMeter.isVisible = quota.percent != null
                    quotaMeter.setIndicatorColor(
                        if (quota.exhausted) ContextCompat.getColor(context, R.color.nw_error)
                        else io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.accent(context)
                    )
                    quota.percent?.let { value ->
                        if (quotaMeter.progress != value)
                            quotaMeter.setProgressCompat(
                                value,
                                isResumed && NoctwadeMotion.enabled(context),
                            )
                    }
                    root.contentDescription =
                        listOf(
                                getString(R.string.nw_quota_title),
                                quota.name,
                                quota.amount,
                                quota.allowance,
                                quota.detail,
                            )
                            .joinToString(", ")
                }
                if (
                    !DataStore.serviceState.started &&
                        DataStore.serviceState != BaseService.State.Stopping
                ) {
                    setSubtitle(
                        ui,
                        if (selected == null) R.string.nw_add_hint else R.string.nw_tap_connect,
                    )
                }
            }
    }

    private fun renderPing() {
        val ui = binding ?: return
        val profile = selected
        val state = io.nekohasekai.sagernet.ui.test.TestSessionClient.state.value
        val urlTest = state.kind == io.nekohasekai.sagernet.bg.test.TestSpec.KIND_URL
        val row = profile?.takeIf { urlTest }?.let { state.rows[it.id] }
        val checking =
            urlTest &&
                state.running &&
                (row != null || (state.testCurrent && DataStore.serviceState.connected))
        val latency =
            if (row?.phase == io.nekohasekai.sagernet.ui.test.RowPhase.DONE && !row.aborted) {
                if (row.failed) -1 else row.latency
            } else profile?.latency ?: 0
        ui.homePing.isVisible = profile != null
        ui.homePing.text =
            when {
                checking -> "…"
                latency > 0 -> getString(R.string.nw_ping, latency)
                latency == ProxyEntity.LATENCY_CONNECT_ONLY ->
                    getString(R.string.test_panel_band_connect_only)
                latency < 0 -> getString(R.string.nw_unavailable)
                else -> getString(R.string.nw_ping_unknown)
            }
        ui.homePing.setTextColor(
            if (latency > 0 && !checking && DataStore.serviceState.connected)
                ContextCompat.getColor(requireContext(), R.color.nw_green)
            else io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.muted(requireContext())
        )
        ui.homePing.isEnabled = !checking
    }

    override fun onDestroyView() {
        binding?.homePower?.setEffectsVisible(false)
        binding?.homeBackdrop?.setEffectsVisible(false)
        binding?.homeContent?.let { content ->
            for (index in 0 until content.childCount) content.getChildAt(index).animate().cancel()
        }
        profileJob?.cancel()
        profileJob = null
        binding = null
        super.onDestroyView()
    }

    private data class HomeProfile(
        val profile: ProxyEntity?,
        val name: String,
        val group: String,
        val type: String,
        val quota: SubscriptionQuota,
    )
}
