package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
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
    /** The theme's character and her dialogue box (which also shows the status). */
    private var character: io.nekohasekai.sagernet.ui.noctwade.CharacterHome? = null
    /** The character talks in a dialogue box that carries the status; false without a character or with dialogues off. */
    private var characterDialogue = false
    private val main
        get() = activity as? MainActivity

    /** "Hide the interface": only the wallpaper, the character and her box stay. */
    private var focus: io.nekohasekai.sagernet.ui.noctwade.HomeFocus? = null
    private var focusBack: OnBackPressedCallback? = null

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
        // With a theme-pack character, status, hint and timer live in her dialogue box. With the dialogues off there is
        // no box: the status is written under the button, below the halo, so nothing overlaps.
        val pack = io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.character(requireContext())
        val home =
            pack?.let {
                val withBox = io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.lines(requireContext())
                characterDialogue = withBox
                if (withBox) {
                    ui.homeStatus.isVisible = false
                    ui.homeSubtitle.isVisible = false
                    ui.homeTimer.isVisible = false
                } else readableStatus(ui)
                io.nekohasekai.sagernet.ui.noctwade.CharacterHome(
                        it,
                        ui.root,
                        ui.homeContent,
                        ui.homeScroll,
                        ui.homeContent.indexOfChild(ui.homeStatus),
                    )
                    .apply { attach(savedInstanceState == null) }
            }
        if (home == null) characterDialogue = false
        character = home
        ui.homePower.spinnable = true
        setupFocus(ui)
        setupHwidBanner(ui)
        io.nekohasekai.sagernet.ui.noctwade.CharacterHome.styleCard(ui.homeServer)
        io.nekohasekai.sagernet.ui.noctwade.CharacterHome.styleCard(ui.homeQuota.root)
        io.nekohasekai.sagernet.ui.noctwade.CharacterHome.styleHeader(ui.homeHeader.headerMenu, ui.homeHeader.headerAdd)
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
            val parts =
                listOf(
                    ui.homeHeader.root,
                    ui.homePower,
                    if (characterDialogue) home?.dialog ?: ui.homeStatus else ui.homeStatus,
                    ui.homeQuota.root,
                    ui.homeServer,
                )
            val rise = 18 * resources.displayMetrics.density
            parts.forEach {
                it.alpha = 0f
                it.translationY = rise
            }
            ui.homePower.scaleX = .9f
            ui.homePower.scaleY = .9f
            ui.homeBackdrop.alpha = 0f
            // Start once the first frame is on screen: during the first, busiest frames the motion used to stutter.
            view.doOnPreDraw {
                view.postOnAnimation {
                    if (binding == null) return@postOnAnimation
                    ui.homeBackdrop.animate().alpha(1f).setDuration(520L).setInterpolator(NoctwadeMotion.soft).start()
                    parts.forEachIndexed { index, child ->
                        child
                            .animate()
                            .alpha(1f)
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setStartDelay(80L + index * 70L)
                            .setDuration(620L)
                            .setInterpolator(NoctwadeMotion.soft)
                            .start()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.homePower?.setEffectsVisible(true)
        binding?.homeBackdrop?.setEffectsVisible(true)
        character?.resume()
        renderState(DataStore.serviceState)
        refreshProfile()
    }

    override fun onPause() {
        binding?.homeTimer?.removeCallbacks(timerTick)
        binding?.homePower?.setEffectsVisible(false)
        binding?.homeBackdrop?.setEffectsVisible(false)
        character?.pause()
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
                ui.homeTimer.removeCallbacks(this)
                val text = elapsed?.let(io.nekohasekai.sagernet.ui.noctwade.ConnectionClock::format)
                val home = character
                if (home != null && characterDialogue) home.setTimer(text)
                else {
                    ui.homeTimer.isVisible = text != null && focus?.hidden != true
                    if (text != null) ui.homeTimer.text = text
                }
                if (elapsed != null && isResumed)
                    ui.homeTimer.postDelayed(this, 1000L - elapsed % 1000L)
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
        val home = character
        home?.render(state, selected != null)
        if (!characterDialogue) {
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
                if (state == BaseService.State.Connected) ContextCompat.getColor(requireContext(), R.color.nw_green)
                else io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.muted(requireContext())
            )
        }
        refreshProfile()
    }

    /** The top-bar button: hides everything but the wallpaper, the character and her dialogue box, and back. */
    private fun setupFocus(ui: LayoutNoctwadeHomeBinding) {
        val context = requireContext()
        val button = ui.homeHeader.headerFocus
        focus = io.nekohasekai.sagernet.ui.noctwade.HomeFocus(ui, { character?.dialog?.takeIf { it.parent != null } }, listOf(ui.homePower))
        button.isVisible = io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.focusButton(context)
        button.imageTintList =
            android.content.res.ColorStateList.valueOf(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.accent(context))
        NoctwadeMotion.press(button)
        // The system Back brings the interface back first; it never leaves the app while everything is hidden.
        focusBack =
            object : OnBackPressedCallback(false) {
                    override fun handleOnBackPressed() = setFocus(false)
                }
                .also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        button.setOnClickListener { setFocus(focus?.hidden != true) }
    }

    private fun setFocus(hide: Boolean) {
        val ui = binding ?: return
        val motion = NoctwadeMotion.enabled(requireContext())
        if (focus?.set(hide, motion) != true) return
        focusBack?.isEnabled = hide
        ui.homeHeader.headerFocus.apply {
            setImageResource(if (hide) R.drawable.nw_eye else R.drawable.nw_focus)
            contentDescription = getString(if (hide) R.string.nw_focus_show else R.string.nw_focus_hide)
        }
        // The button slides into the corner where the + button was (that one is hidden too) and back.
        val corner = if (hide) (ui.homeHeader.headerAdd.left - ui.homeHeader.headerFocus.left).toFloat() else 0f
        if (motion) {
            ui.homeHeader.headerFocus.animate().translationX(corner).setDuration(520L).setInterpolator(NoctwadeMotion.soft).start()
        } else {
            ui.homeHeader.headerFocus.animate().cancel()
            ui.homeHeader.headerFocus.translationX = corner
        }
        character?.zoom(hide, motion, ui.homePower)
        // The timer and the hint decide for themselves whether to show once the interface is back.
        if (!hide) renderState(DataStore.serviceState)
    }

    /**
     * First start: HWID is off and subscriptions need it. Shown until it is answered (turn on / later), once; the
     * answer is kept in the theme preferences, which a reinstall does not bring back.
     */
    private fun setupHwidBanner(ui: LayoutNoctwadeHomeBinding) {
        val context = requireContext()
        val prefs = io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.prefs(context)
        val show = !DataStore.subSendHwid && !prefs.getBoolean("hwid_banner_done", false)
        ui.homeHwid.isVisible = show
        if (!show) return
        io.nekohasekai.sagernet.ui.noctwade.CharacterHome.styleCard(ui.homeHwid)
        ui.homeHwidLater.setTextColor(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.muted(context))
        fun answered(message: Int) {
            prefs.edit().putBoolean("hwid_banner_done", true).apply()
            ui.homeHwid.isVisible = false
            main?.snackbar(message)?.show()
        }
        ui.homeHwidEnable.setOnClickListener {
            DataStore.subSendHwid = true
            answered(R.string.nw_hwid_enabled)
        }
        ui.homeHwidLater.setOnClickListener { answered(R.string.nw_hwid_later_hint) }
    }

    /** A character without her box: the status under the button stays legible over a picture, and clear of the halo. */
    private fun readableStatus(ui: LayoutNoctwadeHomeBinding) {
        val d = resources.displayMetrics.density
        val glow =
            if (io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.light(requireContext())) 0xCCFFFFFF.toInt() else 0xAA000000.toInt()
        listOf(ui.homeStatus, ui.homeTimer, ui.homeSubtitle).forEach { it.setShadowLayer(10f * d, 0f, 1f * d, glow) }
        (ui.homeStatus.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.topMargin = (16 * d).toInt()
            ui.homeStatus.layoutParams = it
        }
    }

    /** "Tap to connect" is implied by the button itself, so the idle line stays hidden. */
    private fun setSubtitle(ui: LayoutNoctwadeHomeBinding, text: Int) {
        ui.homeSubtitle.setText(text)
        ui.homeSubtitle.isVisible = text != R.string.nw_tap_connect && focus?.hidden != true
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
                character?.render(DataStore.serviceState, selected != null)
                if (!characterDialogue && !DataStore.serviceState.started && DataStore.serviceState != BaseService.State.Stopping)
                    setSubtitle(ui, if (selected == null) R.string.nw_add_hint else R.string.nw_tap_connect)

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
        character?.destroy()
        character = null
        focus = null
        focusBack = null
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
