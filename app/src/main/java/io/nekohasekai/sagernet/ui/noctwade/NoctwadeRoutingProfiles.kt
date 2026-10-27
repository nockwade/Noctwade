package io.nekohasekai.sagernet.ui.noctwade

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.route.RouteProfile
import io.nekohasekai.sagernet.route.RouteShare
import io.nekohasekai.sagernet.ui.RouteFragment
import io.nekohasekai.sagernet.ui.SettingsFragment
import io.nekohasekai.sagernet.ui.route.*
import kotlinx.coroutines.*

/**
 * Profile -> ordered rules -> rule editor. Child editors keep the complete navigation stack intact.
 */
class NoctwadeRoutingProfilesFragment : Fragment() {
    private var body: LinearLayout? = null
    private var status: TextView? = null
    private var profilesView: LinearLayout? = null
    private var job: Job? = null
    private var loading: Job? = null
    private var activateAfterSave = false
    private val editor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val id = result.data?.getLongExtra(RouteProfileActivity.EXTRA_SAVED_ID, 0L) ?: 0L
                if (id > 0L && (activateAfterSave || id == DataStore.currentRouteId)) activate(id)
                else refresh()
            } else refresh()
            activateAfterSave = false
        }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        activateAfterSave = state?.getBoolean("activateAfterSave") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("activateAfterSave", activateAfterSave)
    }

    private fun text(value: CharSequence, size: Float = 14f, muted: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(if (muted) NoctwadeTheme.muted(context) else NoctwadeTheme.text(context))
        }

    private fun section(label: Int) {
        body?.addView(
            text(getString(label), 12f, true).apply {
                setPadding(dp(2), dp(24), 0, dp(10))
                typeface = Typeface.create("sans-serif-medium", 0)
            }
        )
    }

    private fun action(
        parent: LinearLayout,
        title: String,
        summary: String,
        icon: Int,
        run: () -> Unit,
    ): TextView {
        val row =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(66)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener { run() }
            }
        row.addView(
            ImageView(requireContext()).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(NoctwadeTheme.accent(context))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(16) },
        )
        val labels = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 15f))
        val subtitle = text(summary, 12f, true).apply { setPadding(0, dp(4), 0, 0) }
        labels.addView(subtitle)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(
            ImageView(requireContext()).apply {
                setImageResource(R.drawable.nw_chevron)
                imageTintList = ColorStateList.valueOf(NoctwadeTheme.muted(context))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(18), dp(18)),
        )
        parent.addView(row)
        return subtitle
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val scroll =
            NestedScrollView(requireContext()).apply {
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                background = NoctwadeTheme.menuGradient(context)
            }
        val content =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(6), dp(24), dp(30))
            }
        body = content
        scroll.addView(content)
        status =
            text(getString(R.string.nw_route_loading), 14f, true).apply {
                setPadding(0, dp(8), 0, dp(14))
            }
        content.addView(status)
        section(R.string.nw_routing_profiles_title)
        profilesView = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(profilesView)
        action(
            content,
            getString(R.string.nw_routing_create),
            getString(R.string.nw_routing_create_hint),
            R.drawable.nw_plus,
        ) {
            create()
        }
        action(
            content,
            getString(R.string.nw_routing_presets),
            "RoscomVPN · OutIP",
            R.drawable.nw_route,
        ) {
            presets()
        }
        action(
            content,
            getString(R.string.route_import_title),
            getString(R.string.nw_routing_import_hint),
            R.drawable.nw_download,
        ) {
            importClipboard()
        }
        action(
            content,
            getString(R.string.nw_routing_more),
            getString(R.string.nw_routing_more_hint),
            R.drawable.nw_settings,
        ) {
            (generateSequence(parentFragment) { it.parentFragment }
                    .filterIsInstance<SettingsFragment>()
                    .firstOrNull())
                ?.openScreen(
                    RouteFragment::class.java.name,
                    getString(R.string.nw_route_profiles),
                    Bundle(),
                )
        }
        content.addView(
            text(getString(R.string.nw_routing_order_hint), 12f, true).apply {
                setPadding(dp(4), dp(20), dp(4), 0)
            }
        )
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (view == null) return
        loading?.cancel()
        loading =
            viewLifecycleOwner.lifecycleScope.launch {
                val (profiles, current) =
                    withContext(Dispatchers.IO) { RouteManager.all() to RouteManager.current() }
                val container = profilesView ?: return@launch
                container.removeAllViews()
                status?.text = getString(R.string.nw_route_active, current.name)
                for (p in profiles) profileRow(container, p, p.id == current.id)
            }
    }

    private fun profileRow(parent: LinearLayout, p: RouteProfile, selected: Boolean) {
        val context = requireContext()
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(78)
                setPadding(dp(12), dp(6), dp(4), dp(6))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(16).toFloat()
                        setColor(NoctwadeTheme.surface(context))
                        if (selected)
                            setStroke(dp(1).coerceAtLeast(1), NoctwadeTheme.accent(context))
                    }
                isClickable = !p.is_raw
                isFocusable = !p.is_raw
                isSelected = selected
                setOnClickListener { if (!p.is_raw) activate(p.id) }
                setOnLongClickListener {
                    profileActions(p, it)
                    true
                }
            }
        row.addView(
            ImageView(context).apply {
                setImageResource(if (selected) R.drawable.nw_check else R.drawable.nw_route)
                imageTintList =
                    ColorStateList.valueOf(
                        if (selected) NoctwadeTheme.accent(context)
                        else NoctwadeTheme.muted(context)
                    )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(23), dp(23)).apply { marginEnd = dp(12) },
        )
        row.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(p.name, 15f))
                addView(
                    text(
                            getString(
                                R.string.nw_routing_profile_info,
                                p.rules.count { !it.disabled },
                                p.rules.size,
                            ),
                            12f,
                            true,
                        )
                        .apply { setPadding(0, dp(5), 0, 0) }
                )
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        row.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.nw_edit)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                imageTintList = ColorStateList.valueOf(NoctwadeTheme.accent(context))
                background = null
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.nw_routing_edit_profile, p.name)
                setOnClickListener { edit(p.id) }
            },
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun edit(id: Long) {
        activateAfterSave = false
        editor.launch(
            Intent(requireContext(), RouteProfileActivity::class.java)
                .putExtra(RouteProfileActivity.EXTRA_PROFILE_ID, id)
        )
    }

    private fun openDraft(p: RouteProfile) {
        activateAfterSave = true
        editor.launch(
            Intent(requireContext(), RouteProfileActivity::class.java)
                .putExtra(RouteProfileActivity.EXTRA_PROFILE_JSON, RouteJson.profileToJson(p))
        )
    }

    private fun create() {
        openDraft(
            RouteProfile.defaultProfile().apply { name = getString(R.string.nw_routing_my_profile) }
        )
    }

    private fun presets() {
        val presets = RoutingPresets.all
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.nw_routing_presets)
            .setItems(presets.map { getString(it.title) }.toTypedArray()) { _, i ->
                val preset = presets[i]
                // Description plus template options: DNS provider and Runet rule sets.
                val (form, read) =
                    TemplateOptions.form(requireContext(), getString(preset.description))
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preset.title)
                    .setView(
                        androidx.core.widget.NestedScrollView(requireContext()).apply {
                            addView(form)
                        }
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.nw_routing_configure) { _, _ ->
                        val choice = read()
                        TemplateOptions.save(requireContext(), choice)
                        prepare(preset.key, choice)
                    }
                    .show()
            }
            .show()
    }

    private fun prepare(key: String, choice: TemplateOptions.Choice) {
        if (job?.isActive == true) return
        val context = requireContext()
        status?.setText(R.string.nw_route_loading)
        job =
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    openDraft(
                        TemplateOptions.apply(context, RoutingPresets.build(context, key), choice)
                    )
                    refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status?.text = getString(R.string.nw_route_error, e.message.orEmpty())
                }
            }
    }

    private fun activate(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                if (RouteManager.get(id)?.is_raw == false) {
                    DataStore.currentRouteId = id
                    if (DataStore.serviceState.canStop) SagerNet.reloadService()
                }
            }
            refresh()
        }
    }

    private fun importClipboard() {
        val input = SagerNet.getClipboardText()
        if (RouteImports.isRemoteRouteLink(input)) {
            val entries = RouteImports.remoteEntries(requireContext(), input) ?: return
            RouteImports.promptRemote(
                requireActivity() as androidx.appcompat.app.AppCompatActivity,
                entries,
            ) {
                refresh()
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val imported = RouteImports.parse(input)
                val p = imported.profile
                if (p == null) throw IllegalArgumentException(imported.fatal)
                p.name = RouteImports.nameOf(requireContext(), p)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.route_import_title)
                    .setMessage(
                        RouteImports.withNotes(requireContext(), p.name, imported.warnings)
                    )
                    .setPositiveButton(R.string.nw_routing_configure) { _, _ -> openDraft(p) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status?.text = getString(R.string.nw_route_error, e.message.orEmpty())
            }
        }
    }

    private fun profileActions(p: RouteProfile, anchor: View) {
        NoctwadeDialogs.actions(
            this,
            anchor,
            listOf(
                NoctwadeDialogs.Action(R.string.nw_routing_edit) { edit(p.id) },
                NoctwadeDialogs.Action(R.string.nw_routing_duplicate) {
                    openDraft(
                        p.copy().apply {
                            id = 0
                            name += " · " + getString(R.string.nw_routing_copy)
                        }
                    )
                },
                NoctwadeDialogs.Action(R.string.action_export_clipboard) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val link =
                            withContext(Dispatchers.IO) {
                                RouteShare.toShareLink(p, RouteManager::profileName)
                            }
                        SagerNet.trySetPrimaryClip(link)
                    }
                },
                NoctwadeDialogs.Action(R.string.delete, destructive = true) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.delete)
                        .setMessage(p.name)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val removed =
                                    withContext(Dispatchers.IO + NonCancellable) {
                                        val current = p.id == DataStore.currentRouteId
                                        val result = RouteManager.delete(p.id)
                                        if (result && current && DataStore.serviceState.canStop)
                                            SagerNet.reloadService()
                                        result
                                    }
                                if (!removed)
                                    Toast.makeText(
                                            requireContext(),
                                            R.string.nw_routing_last_profile,
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                refresh()
                            }
                        }
                        .show()
                },
            ),
        )
    }

    override fun onDestroyView() {
        job?.cancel()
        loading?.cancel()
        body = null
        status = null
        profilesView = null
        super.onDestroyView()
    }
}
