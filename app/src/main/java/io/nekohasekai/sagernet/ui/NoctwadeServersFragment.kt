package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.test.TestSpec
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupRepo
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeServersBinding
import io.nekohasekai.sagernet.group.SubscriptionClient
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ui.noctwade.ConnectionImportDialog
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeDialogs
import io.nekohasekai.sagernet.ui.noctwade.NoctwadeServersAdapter
import io.nekohasekai.sagernet.ui.noctwade.ServerListItem
import io.nekohasekai.sagernet.ui.noctwade.SubscriptionQuota
import io.nekohasekai.sagernet.ui.profile.profileSettingsIntent
import io.nekohasekai.sagernet.ui.profiles.ProfilesDbWatcher
import io.nekohasekai.sagernet.ui.test.RowPhase
import io.nekohasekai.sagernet.ui.test.TestFailure
import io.nekohasekai.sagernet.ui.test.TestFormat
import io.nekohasekai.sagernet.ui.test.TestSessionClient
import io.nekohasekai.sagernet.ui.test.TestUiState
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoctwadeServersFragment : ToolbarFragment(R.layout.layout_noctwade_servers) {
    companion object {
        fun forGroup(groupId: Long) =
            NoctwadeServersFragment().apply { arguments = bundleOf("focusGroup" to groupId) }

        /**
         * The list inside [io.nekohasekai.sagernet.ui.noctwade.ServersSheet]: no backdrop or top
         * bar.
         */
        fun forSheet(groupId: Long) =
            NoctwadeServersFragment().apply {
                arguments = bundleOf("focusGroup" to groupId, "sheet" to true)
            }
    }

    private var binding: LayoutNoctwadeServersBinding? = null
    private val inSheet
        get() = arguments?.getBoolean("sheet") == true

    private var adapter: NoctwadeServersAdapter? = null
    private var loadJob: Job? = null
    private var selectionJob: Job? = null
    private var groups = emptyList<ServerGroup>()
    private val collapsed = mutableSetOf<Long>()
    private var query = ""
    private var focusGroup = 0L
    private var searchBack: OnBackPressedCallback? = null
    private var loaded = false
    private var pingWasRunning = false
    private var frozenPingOrder: Map<Long, Int>? = null
    private val imports = ConnectionImportDialog(this)
    private val main
        get() = activity as? MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = LayoutNoctwadeServersBinding.bind(view)
        binding = ui
        loaded = false
        collapsed.clear()
        savedInstanceState?.getLongArray("collapsed")?.let { collapsed.addAll(it.toList()) }
        query = savedInstanceState?.getString("query").orEmpty()
        focusGroup = if (savedInstanceState == null) arguments?.getLong("focusGroup") ?: 0 else 0
        adapter =
            NoctwadeServersAdapter(
                ::selectServer,
                ::showServerActions,
                { id ->
                    if (!collapsed.add(id)) collapsed.remove(id)
                    renderRows()
                },
                ::pingServer,
                ::showGroupActions,
                ::refreshGroup,
                ::testGroup,
            )
        ui.serversList.layoutManager = LinearLayoutManager(requireContext())
        ui.serversList.adapter = adapter
        ui.serversList.itemAnimator =
            if (NoctwadeMotion.enabled(requireContext()))
                DefaultItemAnimator().apply {
                    supportsChangeAnimations =
                        false // Streaming ping updates never crossfade the whole row.
                    addDuration = 160L
                    removeDuration = 120L
                    moveDuration = 180L
                }
            else null
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            val padding = (20 * resources.displayMetrics.density).toInt()
            ui.serversContent.setPadding(
                padding + bars.left,
                if (inSheet) 0 else bars.top,
                padding + bars.right,
                0,
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            ui.serversList.setPadding(
                0,
                0,
                0,
                (24 * resources.displayMetrics.density).toInt() + maxOf(bars.bottom, keyboard),
            )
            ui.serversList.clipToPadding = false
            insets
        }
        ViewCompat.requestApplyInsets(view)
        // Keep the list reachable on short landscape windows and with large fonts.
        val compact =
            resources.configuration.screenHeightDp < 600 || resources.configuration.fontScale > 1.4f
        ui.serversHeroSpace.isVisible = !compact && !inSheet
        if (inSheet) {
            view.background = null
            ui.serversBackdrop.isVisible = false
            ui.serversHeader.root.isVisible = false
        }
        // Servers opens from Home, so the top-left button goes back there.
        ui.serversHeader.headerMenu.setImageResource(R.drawable.nw_back)
        ui.serversHeader.headerMenu.imageTintList =
            android.content.res.ColorStateList.valueOf(
                io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(requireContext())
            )
        ui.serversHeader.headerMenu.contentDescription = getString(R.string.nw_home)
        ui.serversHeader.headerMenu.setOnClickListener {
            main?.displayFragmentWithId(R.id.nav_home)
        }
        ui.serversHeader.headerAdd.setOnClickListener { imports.show(ui.serversHeader.headerAdd) }
        ui.serversSearchToggle.setOnClickListener { setSearchVisible(!ui.serversQuery.isVisible) }
        listOf(ui.serversHeader.headerAdd, ui.serversSearchToggle).forEach(NoctwadeMotion::press)
        searchBack =
            object : OnBackPressedCallback(false) {
                    override fun handleOnBackPressed() = setSearchVisible(false)
                }
                .also {
                    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
                }
        ui.serversQuery.setText(query)
        setSearchVisible(savedInstanceState?.getBoolean("searching") == true, false)
        ui.serversQuery.doAfterTextChanged {
            query = it?.toString().orEmpty()
            if (loaded) renderRows()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loadGroups()
                ProfilesDbWatcher(::loadGroups, ::loadGroups).start(this)
                launch { TestSessionClient.state.collect(::renderTest) }
                launch { SubscriptionClient.states.collect { adapter?.updating(it.keys) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.serversBackdrop?.setEffectsVisible(true)
        refreshProfileState()
    }

    override fun onPause() {
        binding?.serversBackdrop?.setEffectsVisible(false)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray("collapsed", collapsed.toLongArray())
        outState.putString("query", query)
        outState.putBoolean("searching", binding?.serversQuery?.isVisible == true)
    }

    private fun setSearchVisible(visible: Boolean, focus: Boolean = true) {
        val ui = binding ?: return
        ui.serversQuery.isVisible = visible
        searchBack?.isEnabled = visible
        ui.serversSearchToggle.setImageResource(
            if (visible) R.drawable.nw_close else R.drawable.nw_search
        )
        ui.serversSearchToggle.contentDescription =
            getString(if (visible) R.string.nw_search_close else R.string.nw_search)
        if (visible && focus) {
            ui.serversQuery.requestFocus()
            ViewCompat.getWindowInsetsController(ui.serversQuery)
                ?.show(WindowInsetsCompat.Type.ime())
        } else if (!visible) {
            ui.serversQuery.setText("")
            query = ""
            ui.serversQuery.clearFocus()
            ViewCompat.getWindowInsetsController(ui.serversQuery)
                ?.hide(WindowInsetsCompat.Type.ime())
        }
    }

    private fun loadGroups() {
        val ui = binding ?: return
        val context = requireContext()
        loadJob?.cancel()
        loadJob =
            viewLifecycleOwner.lifecycleScope.launch {
                val result =
                    try {
                        withContext(Dispatchers.IO) {
                            val allGroups = GroupRepo.all()
                            val members = SagerDatabase.proxyDao.getAll().groupBy { it.groupId }
                            allGroups.map { group ->
                                val name = group.displayName()
                                val quota = SubscriptionQuota.from(context, group)
                                val list =
                                    members[group.id]
                                        .orEmpty()
                                        .sortedWith(
                                            compareBy<ProxyEntity> { it.userOrder }.thenBy { it.id }
                                        )
                                        .map { profile ->
                                            val title =
                                                io.nekohasekai.sagernet.ui.noctwade
                                                    .ServerPresentation
                                                    .title(
                                                        profile.name?.takeIf(String::isNotBlank)
                                                            ?: profile.displayName()
                                                    )
                                            val country =
                                                io.nekohasekai.sagernet.ui.noctwade
                                                    .ServerPresentation
                                                    .country(profile)
                                            val countryName =
                                                if (country.isNotEmpty())
                                                    TestFormat.countryName(country)
                                                else ""
                                            val security =
                                                io.nekohasekai.sagernet.ui.noctwade
                                                    .ServerPresentation
                                                    .security(profile)
                                            // The flag already shows the country: protocol and
                                            // security are the useful second line.
                                            val detail =
                                                listOf(
                                                        io.nekohasekai.sagernet.ui.noctwade
                                                            .ServerPresentation
                                                            .protocol(profile.type),
                                                        security.takeIf { it != "—" }.orEmpty(),
                                                    )
                                                    .filter(String::isNotBlank)
                                                    .joinToString(" · ")
                                            ServerListItem.Server(
                                                profile.id,
                                                group.id,
                                                title,
                                                detail,
                                                country,
                                                profile.latency,
                                                "$title $name $detail $country $countryName"
                                                    .lowercase(Locale.ROOT),
                                                security,
                                            )
                                        }
                                val description =
                                    if (
                                        group.isSubscription &&
                                            group.subInfo.valid &&
                                            group.subInfo.hasQuota
                                    ) {
                                        context.getString(
                                            R.string.nw_group_usage,
                                            quota.amount,
                                            quota.allowance,
                                        )
                                    } else "" // the server count alone says enough
                                ServerGroup(group.id, name, description, list, group.isSubscription)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Logs.w(error)
                        null
                    }
                if (binding !== ui) return@launch
                loaded = true
                if (result == null) {
                    if (groups.isEmpty())
                        adapter?.submitList(listOf(ServerListItem.Empty(false, true)))
                    return@launch
                }
                groups = result
                renderRows()
                refreshProfileState()
            }
    }

    private fun renderRows() {
        val needle = query.trim().lowercase(Locale.ROOT)
        val rows = ArrayList<ServerListItem>()
        val order = io.nekohasekai.sagernet.ui.noctwade.ServerListOrder.mode(requireContext())
        for (group in groups) {
            val ordered =
                if (
                    order == io.nekohasekai.sagernet.ui.noctwade.ServerListOrder.Mode.PING &&
                        frozenPingOrder != null
                )
                    group.servers.sortedBy { frozenPingOrder?.get(it.id) ?: Int.MAX_VALUE }
                else io.nekohasekai.sagernet.ui.noctwade.ServerListOrder.sort(group.servers, order)
            val members =
                if (needle.isEmpty() || needle in group.name.lowercase(Locale.ROOT)) ordered
                else ordered.filter { needle in it.search }
            if (
                needle.isNotEmpty() &&
                    members.isEmpty() &&
                    needle !in group.name.lowercase(Locale.ROOT)
            )
                continue
            val expanded = group.id !in collapsed
            rows.add(
                ServerListItem.Group(
                    group.id,
                    group.name,
                    group.detail,
                    expanded,
                    group.subscription,
                    group.servers.size,
                    members.size,
                )
            )
            if (expanded) rows.addAll(members)
        }
        if (rows.isEmpty() || groups.all { it.servers.isEmpty() })
            rows.add(ServerListItem.Empty(needle.isNotEmpty()))
        val target = focusGroup
        adapter?.submitList(rows) {
            if (target > 0 && focusGroup == target) {
                val position = rows.indexOfFirst { it is ServerListItem.Group && it.id == target }
                if (position >= 0) binding?.serversList?.scrollToPosition(position)
                focusGroup = 0
            }
        }
    }

    fun refreshProfileState() {
        adapter?.select(DataStore.selectedProxy)
    }

    private fun selectServer(server: ServerListItem.Server) {
        if (DataStore.serviceState == BaseService.State.Stopping || selectionJob?.isActive == true)
            return
        selectionJob =
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (ProfileManager.getProfile(server.id) == null) return@withContext
                        val previous = DataStore.selectedProxy
                        GroupRepo.setCurrent(server.groupId)
                        if (previous != server.id) {
                            DataStore.selectedProxy = server.id
                            ProfileManager.postUpdate(previous, noTraffic = true)
                            ProfileManager.postUpdate(server.id, noTraffic = true)
                            if (DataStore.serviceState.canStop) SagerNet.reloadService()
                        }
                    }
                    refreshProfileState()
                    // Picked from the Home sheet: close it, like an iOS action sheet.
                    (parentFragment as? androidx.fragment.app.DialogFragment)?.dismiss()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Logs.w(error)
                    main?.snackbar(R.string.nw_load_failed)?.show()
                }
            }
    }

    /** Group ping button: starts a test of that group, or stops our running test. */
    private fun testGroup(groupId: Long) {
        val state = TestSessionClient.state.value
        if (state.running) {
            if (state.kind == TestSpec.KIND_URL && !state.testCurrent) TestSessionClient.stop()
            else main?.snackbar(R.string.nw_ping_busy)?.show()
            return
        }
        val ids = groups.firstOrNull { it.id == groupId }?.servers?.map { it.id }.orEmpty()
        if (ids.isEmpty()) {
            main?.snackbar(R.string.nw_empty_servers)?.show()
            return
        }
        if (!TestSessionClient.startProfiles(TestSpec.KIND_URL, ids, groupId))
            main?.snackbar(R.string.nw_ping_busy)?.show()
    }

    private var reportedFailure: TestFailure? = null

    private fun renderTest(state: TestUiState) {
        adapter?.testing(state)
        if (state.running != pingWasRunning) {
            pingWasRunning = state.running
            frozenPingOrder =
                if (state.running)
                    adapter
                        ?.currentList
                        ?.filterIsInstance<ServerListItem.Server>()
                        ?.mapIndexed { i, row -> row.id to i }
                        ?.toMap()
                else null
            // Reorder once after all results have been saved, never underneath a pressed row.
            if (!state.running && loaded) loadGroups()
        }
        val ourKind = state.kind == TestSpec.KIND_URL && !state.testCurrent
        // Progress lives in each group's own line, so nothing above the list appears or vanishes.
        adapter?.groupProgress(
            if (!state.running || !ourKind) emptyMap()
            else
                groups
                    .mapNotNull { group ->
                        val rows = group.servers.mapNotNull { state.rows[it.id] }
                        if (rows.isEmpty()) null
                        else group.id to (rows.count { it.phase == RowPhase.DONE } to rows.size)
                    }
                    .toMap()
        )
        val failure = if (ourKind && state.finished) state.failure else null
        if (failure != null && failure != reportedFailure) {
            val message =
                when (failure) {
                    TestFailure.EMPTY -> R.string.test_panel_failure_empty
                    TestFailure.BUSY -> R.string.test_panel_failure_busy
                    TestFailure.UNREACHABLE -> R.string.test_panel_failure_unreachable
                    TestFailure.CORE_DIED -> R.string.test_panel_failure_core_died
                    else -> null
                }
            message?.let { main?.snackbar(it)?.show() }
        }
        reportedFailure = failure
    }

    private fun pingServer(server: ServerListItem.Server) {
        if (!TestSessionClient.startProfiles(TestSpec.KIND_URL, listOf(server.id), server.groupId))
            main?.snackbar(R.string.nw_ping_busy)?.show()
    }

    private fun showServerActions(server: ServerListItem.Server, anchor: View) {
        NoctwadeDialogs.actions(
            this,
            anchor,
            listOf(
                NoctwadeDialogs.Action(R.string.edit) { editServer(server) },
                NoctwadeDialogs.Action(R.string.nw_test_server) { pingServer(server) },
                NoctwadeDialogs.Action(R.string.nw_delete_server, destructive = true) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.nw_delete_server)
                        .setMessage(getString(R.string.nw_delete_server_confirm, server.name))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.nw_delete) { _, _ ->
                            mutate {
                                val result =
                                    ProfileManager.batchDeleteProfiles(
                                        listOf(server.id),
                                        stopRunning = true,
                                    )
                                check(result.ok) { getString(R.string.nw_load_failed) }
                            }
                        }
                        .show()
                },
            ),
        )
    }

    private val editor =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val key =
                io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity.EXTRA_RESTART_NEEDED
            if (result.data?.getBooleanExtra(key, false) == true) needReload()
            if (binding != null) loadGroups()
        }

    /** The same profile editor as in Throne's own server list. */
    private fun editServer(server: ServerListItem.Server) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (profile, group) =
                withContext(Dispatchers.IO) {
                    ProfileManager.getProfile(server.id) to GroupRepo.get(server.groupId)
                }
            profile ?: return@launch
            editor.launch(
                profile.profileSettingsIntent(requireContext(), group?.isSubscription == true)
            )
        }
    }

    private fun refreshGroup(id: Long) {
        if (id !in SubscriptionClient.states.value)
            SubscriptionClient.refreshGroup(id, showDiff = false)
    }

    private fun showGroupActions(group: ServerListItem.Group, anchor: View) {
        NoctwadeDialogs.actions(
            this,
            anchor,
            buildList {
                if (group.subscription)
                    add(NoctwadeDialogs.Action(R.string.nw_refresh_group) { refreshGroup(group.id) })
                add(
                    NoctwadeDialogs.Action(R.string.nw_rename_group) {
                        NoctwadeDialogs.groupName(this@NoctwadeServersFragment, group.name) { name
                            ->
                            mutate { GroupRepo.rename(group.id, name) }
                        }
                    }
                )
                add(
                    NoctwadeDialogs.Action(R.string.nw_delete_group, destructive = true) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.nw_delete_group)
                            .setMessage(getString(R.string.nw_delete_group_confirm, group.name))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.nw_delete) { _, _ ->
                                mutate {
                                    if (GroupRepo.ids().size == 1)
                                        GroupRepo.add(
                                            ProxyGroup(
                                                name = getString(R.string.group_default_name)
                                            )
                                        )
                                    check(GroupRepo.delete(group.id)) {
                                        getString(R.string.nw_load_failed)
                                    }
                                }
                            }
                            .show()
                    }
                )
            },
        )
    }

    private fun mutate(action: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                loadGroups()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logs.w(error)
                main?.snackbar(R.string.nw_load_failed)?.show()
            }
        }
    }

    override fun onDestroyView() {
        binding?.serversBackdrop?.setEffectsVisible(false)
        binding?.serversList?.adapter = null
        loadJob?.cancel()
        selectionJob?.cancel()
        loadJob = null
        selectionJob = null
        adapter = null
        binding = null
        searchBack = null
        super.onDestroyView()
    }

    private data class ServerGroup(
        val id: Long,
        val name: String,
        val detail: String,
        val servers: List<ServerListItem.Server>,
        val subscription: Boolean,
    )
}
