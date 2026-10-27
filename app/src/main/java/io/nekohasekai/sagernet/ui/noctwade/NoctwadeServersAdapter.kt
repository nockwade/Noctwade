package io.nekohasekai.sagernet.ui.noctwade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.test.TestSpec
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeGroupHeaderBinding
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeServerItemBinding
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeServersEmptyBinding
import io.nekohasekai.sagernet.ui.test.RowPhase
import io.nekohasekai.sagernet.ui.test.RowState
import io.nekohasekai.sagernet.ui.test.TestUiState
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import io.nekohasekai.sagernet.widget.noctwade.RefreshSpin

/** "24 servers", or "7 of 24" while a search hides some of them. */
internal fun countLabel(res: android.content.res.Resources, visible: Int, total: Int): String =
    if (visible == total) res.getQuantityString(R.plurals.nw_servers_count, total, total)
    else res.getString(R.string.nw_servers_filtered, visible, total)

internal sealed class ServerListItem {
    abstract val key: Long

    data class Group(
        val id: Long,
        val name: String,
        val detail: String,
        val expanded: Boolean,
        val subscription: Boolean,
        val serverCount: Int = 0,
        val visibleCount: Int = serverCount,
    ) : ServerListItem() {
        override val key
            get() = -id - 1
    }

    data class Server(
        val id: Long,
        val groupId: Long,
        val name: String,
        val detail: String,
        val country: String,
        val latency: Int,
        val search: String,
        val security: String = "",
    ) : ServerListItem() {
        override val key
            get() = id
    }

    data class Empty(val searching: Boolean, val failed: Boolean = false) : ServerListItem() {
        override val key
            get() = Long.MIN_VALUE
    }
}

/** Diffed, recycled rows; selecting a server only rebinds the old/new row backgrounds. */
internal class NoctwadeServersAdapter(
    private val onSelect: (ServerListItem.Server) -> Unit,
    private val onLongPress: (ServerListItem.Server, View) -> Unit,
    private val onToggleGroup: (Long) -> Unit,
    private val onPing: (ServerListItem.Server) -> Unit,
    private val onGroupActions: (ServerListItem.Group, View) -> Unit,
    private val onRefresh: (Long) -> Unit,
    private val onPingGroup: (Long) -> Unit = {},
) :
    ListAdapter<ServerListItem, RecyclerView.ViewHolder>(
        object : DiffUtil.ItemCallback<ServerListItem>() {
            override fun areItemsTheSame(old: ServerListItem, new: ServerListItem) =
                old.key == new.key

            override fun areContentsTheSame(old: ServerListItem, new: ServerListItem) = old == new
        }
    ) {
    private var selection = 0L
    private var testing = emptySet<Long>()
    private var updating = emptySet<Long>()
    private var results = emptyMap<Long, RowState>()
    private var progress = emptyMap<Long, Pair<Int, Int>>()

    /** Per-group ping progress (done, total), shown in the group line instead of a status row. */
    fun groupProgress(value: Map<Long, Pair<Int, Int>>) {
        if (value == progress) return
        val changed = (progress.keys + value.keys).filter { progress[it] != value[it] }.toSet()
        progress = value
        currentList.forEachIndexed { i, row ->
            if (row is ServerListItem.Group && row.id in changed) notifyItemChanged(i, "progress")
        }
    }

    fun testing(state: TestUiState) {
        val rows = if (state.kind == TestSpec.KIND_URL) state.rows else emptyMap()
        val pending =
            if (state.running) rows.filterValues { it.phase != RowPhase.DONE }.keys else emptySet()
        val changed =
            (results.keys + rows.keys).filterTo(mutableSetOf()) { results[it] != rows[it] } +
                (testing - pending) +
                (pending - testing)
        results = rows
        testing = pending
        currentList.forEachIndexed { i, row ->
            if (row is ServerListItem.Server && row.id in changed) notifyItemChanged(i, "ping")
        }
    }

    fun updating(ids: Set<Long>) {
        val changed = (updating - ids) + (ids - updating)
        updating = ids
        currentList.forEachIndexed { i, row ->
            if (row is ServerListItem.Group && row.id in changed) notifyItemChanged(i, "refresh")
        }
    }

    init {
        setHasStableIds(true)
        stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemId(position: Int) = getItem(position).key

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is ServerListItem.Group -> 0
            is ServerListItem.Server -> 1
            is ServerListItem.Empty -> 2
        }

    fun select(id: Long) {
        if (selection == id) return
        val previous = selection
        selection = id
        currentList.forEachIndexed { index, item ->
            if (item is ServerListItem.Server && (item.id == previous || item.id == id))
                notifyItemChanged(index, "selection")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> GroupHolder(LayoutNoctwadeGroupHeaderBinding.inflate(inflater, parent, false))
            1 -> ServerHolder(LayoutNoctwadeServerItemBinding.inflate(inflater, parent, false))
            else -> EmptyHolder(LayoutNoctwadeServersEmptyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupHolder -> holder.bind(getItem(position) as ServerListItem.Group)
            is ServerHolder -> holder.bind(getItem(position) as ServerListItem.Server)
            is EmptyHolder -> holder.bind(getItem(position) as ServerListItem.Empty)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        holder.itemView.animate().cancel()
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        super.onViewRecycled(holder)
    }

    private inner class GroupHolder(private val ui: LayoutNoctwadeGroupHeaderBinding) :
        RecyclerView.ViewHolder(ui.root) {
        init {
            NoctwadeMotion.press(ui.groupActions)
            NoctwadeMotion.press(ui.groupRefresh)
            NoctwadeMotion.press(ui.groupPing)
        }

        private var boundId = 0L

        fun bind(item: ServerListItem.Group) {
            ui.groupRefresh.isVisible = item.subscription
            if (boundId != item.id) RefreshSpin.reset(ui.groupRefresh)
            RefreshSpin.set(ui.groupRefresh, item.id in updating)
            ui.groupRefresh.setOnClickListener { onRefresh(item.id) }
            ui.groupActions.setOnClickListener { onGroupActions(item, ui.groupActions) }
            ui.root.setOnLongClickListener {
                onGroupActions(item, ui.groupActions)
                true
            }
            ui.groupName.text = item.name
            val res = ui.root.context.resources
            ui.groupCount.text = countLabel(res, item.visibleCount, item.serverCount)
            val running = progress[item.id]
            val detail =
                running?.let { (done, total) ->
                    res.getString(R.string.nw_group_testing, done, total)
                } ?: item.detail
            ui.groupDetail.text = if (detail.isBlank()) "" else "  ·  $detail"
            ui.groupPing.setImageResource(
                if (running != null) R.drawable.nw_close else R.drawable.nw_ping_all
            )
            ui.groupPing.contentDescription =
                res.getString(
                    if (running != null) R.string.nw_ping_stop else R.string.nw_ping_group
                )
            ui.groupPing.setOnClickListener { onPingGroup(item.id) }
            ui.groupCount.contentDescription =
                res.getQuantityString(
                    R.plurals.nw_servers_count,
                    item.serverCount,
                    item.serverCount,
                )
            val rotation = if (item.expanded) 90f else 0f
            ui.groupArrow.animate().cancel()
            if (boundId == item.id && NoctwadeMotion.enabled(ui.root.context)) {
                ui.groupArrow.animate().rotation(rotation).setDuration(180L).start()
            } else ui.groupArrow.rotation = rotation
            boundId = item.id
            ui.root.contentDescription =
                ui.root.context.getString(R.string.nw_group_toggle, item.name) +
                    ", " +
                    ui.groupCount.contentDescription
            ui.root.setOnClickListener { onToggleGroup(item.id) }
        }
    }

    private inner class ServerHolder(private val ui: LayoutNoctwadeServerItemBinding) :
        RecyclerView.ViewHolder(ui.root) {
        init {
            // No press-scale on server rows: holding one opens a menu and the row must stay put.
            ViewCompat.setAccessibilityDelegate(
                ui.root,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = RadioButton::class.java.name
                        info.isCheckable = true
                        info.isChecked = host.isSelected
                    }
                },
            )
        }

        fun bind(item: ServerListItem.Server) {
            val context = ui.root.context
            val chosen = item.id == selection
            ui.root.isSelected = chosen
            ui.serverName.setTitle(item.name)
            ui.serverDetail.text = item.detail
            ui.serverFlag.setCountry(item.country)
            val result = results[item.id]?.takeIf { it.phase == RowPhase.DONE && !it.aborted }
            val latency = result?.let { if (it.failed) -1 else it.latency } ?: item.latency
            ui.serverPing.render(latency, item.id in testing)
            ui.serverSignal.render(latency, item.id in testing)
            ui.serverPing.setOnClickListener { onPing(item) }
            ui.root.contentDescription =
                listOf(
                        context.getString(
                            if (chosen) R.string.nw_selected_server else R.string.nw_select_server,
                            item.name,
                        ),
                        item.detail,
                        ui.serverPing.contentDescription,
                    )
                    .joinToString(", ")
            ui.root.setOnClickListener { onSelect(item) }
            ui.root.setOnLongClickListener {
                onLongPress(item, ui.root)
                true
            }
        }
    }

    private class EmptyHolder(private val ui: LayoutNoctwadeServersEmptyBinding) :
        RecyclerView.ViewHolder(ui.root) {
        fun bind(item: ServerListItem.Empty) {
            ui.emptyTitle.setText(
                if (item.failed) R.string.nw_load_failed
                else if (item.searching) R.string.nw_no_matches else R.string.nw_empty_servers
            )
            ui.emptyHint.setText(
                if (item.searching) R.string.nw_no_matches_hint else R.string.nw_empty_servers_hint
            )
            ui.emptyHint.isVisible = !item.failed
        }
    }
}
