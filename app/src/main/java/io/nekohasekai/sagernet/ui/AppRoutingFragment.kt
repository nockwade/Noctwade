package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.appcompat.widget.PopupMenu
import android.text.TextUtils
import android.util.SparseBooleanArray
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.annotation.UiThread
import androidx.core.util.contains
import androidx.core.util.set
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppsBinding
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.confirmAction
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.route.RouteRule
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.applyListInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.matsuri.nb4a.utils.NGUtil
import kotlin.coroutines.coroutineContext

/**
 * Per-app proxy. Installed apps are selected by uid; stored packages the list cannot show (hidden, without INTERNET,
 * not installed, restored from a backup) are kept as rows of their own and written back untouched.
 */
class AppRoutingFragment : Fragment() {
    private val packageManager get()=requireContext().packageManager
    private val access=InstalledAppsAccess(this){if(view!=null)loadApps()}
    private var requestedAccess=false
    private var offeredSettings=false
    private var openedConfig=""
    private fun config()="${DataStore.proxyApps}/${DataStore.bypass}/${DataStore.individual}"
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: AppRoutingFragment? = null
        private const val SWITCH = "switch"

        private fun cachedApps(cache: PackageCache.Snapshot = PackageCache.snapshot()) =
            cache.installedPackages.toMutableMap().apply {
                remove(BuildConfig.APPLICATION_ID)
            }
    }

    /** [byName] rows stand for stored packages outside [cachedApps]: they are selected by name, not by uid. */
    private class ProxiedApp(
        private val pm: PackageManager, private val appInfo: ApplicationInfo?,
        val packageName: String, val byName: Boolean = false,
    ) {
        val name: CharSequence = appInfo?.loadLabel(pm) ?: packageName    // cached for sorting
        val icon: Drawable get() = appInfo?.loadIcon(pm) ?: pm.defaultActivityIcon
        val uid get() = appInfo?.uid ?: -1
        val sys get() = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val installed get() = appInfo != null
    }

    private inner class AppViewHolder(val binding: LayoutAppsItemBinding) : RecyclerView.ViewHolder(
        binding.root
    ),
        View.OnClickListener {
        private lateinit var item: ProxiedApp

        init {
            binding.root.setOnClickListener(this)
        }

        fun bind(app: ProxiedApp) {
            item = app
            binding.itemicon.setImageDrawable(app.icon)
            binding.title.text = app.name
            binding.desc.text = if (app.installed) {
                "${app.packageName} (${app.uid})"
            } else {
                getString(R.string.app_not_installed, app.packageName)
            }
            binding.itemcheck.isChecked = isProxiedApp(app)
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) binding.itemcheck.isChecked = isProxiedApp(item)
        }

        override fun onClick(v: View?) {
            when {
                item.byName -> if (!extraSelected.remove(item.packageName)) extraSelected.add(item.packageName)
                isProxiedApp(item) -> proxiedUids.delete(item.uid)
                else -> proxiedUids[item.uid] = true
            }
            saveSelection()

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(),
        Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            val cache = PackageCache.reload()
            if (!selectionLoaded) {
                initProxiedUids(cache)
                selectionLoaded = true
            }
            val cached = cachedApps(cache)
            val list = cached.mapNotNull { (packageName, packageInfo) ->
                coroutineContext[Job]!!.ensureActive()
                packageInfo.applicationInfo?.let { ProxiedApp(packageManager, it, packageName) }
            }.toMutableList()
            for (packageName in extraSelected) {
                list.add(ProxiedApp(packageManager, cache.installedApps[packageName], packageName, byName = true))
            }
            apps = sorted(list)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
            holder.bind(filteredApps[position])

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST") holder.handlePayload(payloads as List<String>)
                return
            }

            onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
            AppViewHolder(LayoutAppsItemBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = filteredApps.size

        /** Filters on the calling (main) thread: the list is small and a background Filter raced the toggle. */
        fun applyFilter(constraint: CharSequence) {
            var result = if (constraint.isEmpty()) apps else apps.filter {
                it.name.contains(constraint, true) || it.packageName.contains(
                    constraint, true
                ) || it.uid.toString().contains(constraint)
            }
            if (!sysApps) result = result.filter { !it.sys || it.byName }
            filteredApps = result
            notifyDataSetChanged()
        }

        private val filterImpl = object : Filter() {
            override fun performFiltering(constraint: CharSequence?) = FilterResults()

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (view != null) applyFilter(constraint ?: "")
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }

    }

    private val loading get()=binding.loading

    private lateinit var binding: LayoutAppsBinding
    private val proxiedUids = SparseBooleanArray()
    private val extraSelected = LinkedHashSet<String>()
    private var selectionLoaded = false
    private var loader: Job? = null
    private var apps = emptyList<ProxiedApp>()
    private val appsAdapter = AppsAdapter()

    private fun initProxiedUids(cache: PackageCache.Snapshot, str: String = DataStore.individual) {
        proxiedUids.clear()
        extraSelected.clear()
        val apps = cachedApps(cache)
        for (line in str.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            val uid = apps[line]?.applicationInfo?.uid
            if (uid != null) proxiedUids[uid] = true else extraSelected.add(line)
        }
    }

    private fun isProxiedApp(app: ProxiedApp) =
        if (app.byName) app.packageName in extraSelected else proxiedUids[app.uid]

    /** Selected listed apps, then the selected rows the list only knows by name. */
    private fun saveSelection() {
        val listed = apps.filter { !it.byName && isProxiedApp(it) }.map { it.packageName }
        DataStore.individual = (listed + extraSelected).distinct().joinToString("\n")
    }

    private fun sorted(list: List<ProxiedApp>) =
        list.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))

    private fun refilter() { if(view!=null){appsAdapter.applyFilter(binding.search.text?.toString() ?: "")} }

    @UiThread
    private fun loadApps() {
        loader?.cancel()
        if(access.deniedPermission()!=null){showAccessPlaceholder();return}
        binding.appPlaceholder.root.visibility=View.GONE
        loader = viewLifecycleOwner.lifecycleScope.launch {
            loading.crossFadeFrom(binding.list)
            val adapter = binding.list.adapter as AppsAdapter
            val visible=try{withContext(Dispatchers.IO) { adapter.reload();PackageCache.snapshot().installedApps.size }}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:SecurityException){showAccessPlaceholder();offerAccessSettings();return@launch}
            if(visible<=2){showAccessPlaceholder();offerAccessSettings();return@launch}
            refilter()
            if (apps.isEmpty()) {
                binding.list.visibility = View.GONE
                binding.appPlaceholder.root.crossFadeFrom(loading)
            } else {
                binding.list.crossFadeFrom(loading)
            }
        }
    }

    override fun onCreateView(inflater:LayoutInflater,parent:ViewGroup?,savedInstanceState:Bundle?):View {

        binding = LayoutAppsBinding.inflate(inflater,parent,false)

        binding.appPlaceholder.openSettings.setOnClickListener { access.openSettings() }
        binding.toolbar.visibility=View.GONE
        binding.header.setPadding(binding.header.paddingLeft,0,binding.header.paddingRight,0)
        val actions=android.widget.ImageButton(requireContext()).apply {
            setImageResource(R.drawable.nw_more);imageTintList=android.content.res.ColorStateList.valueOf(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.accent(context));background=null
            contentDescription=getString(R.string.nw_routing_more)
            setOnClickListener { anchor -> PopupMenu(requireContext(),anchor).apply { menuInflater.inflate(R.menu.per_app_proxy_menu,menu);setOnMenuItemClickListener{handleMenuItem(it)};show() } }
        }
        (binding.showSystemApps.parent as android.widget.LinearLayout).addView(actions,android.widget.LinearLayout.LayoutParams((44*resources.displayMetrics.density).toInt(),(44*resources.displayMetrics.density).toInt()))
        binding.showSystemApps.setText(R.string.nw_apps_system)
        binding.showSystemApps.layoutParams=android.widget.LinearLayout.LayoutParams(0,-2,1f)
        binding.showSystemApps.maxLines=1;binding.showSystemApps.ellipsize=android.text.TextUtils.TruncateAt.END
        (binding.showSystemApps.parent as android.widget.LinearLayout).layoutParams.apply{width=ViewGroup.LayoutParams.MATCH_PARENT}
        openedConfig=config()
        binding.appsEnabled.isChecked=DataStore.proxyApps
        binding.appsBypass.isChecked=DataStore.bypass
        binding.appsBypass.isEnabled=DataStore.proxyApps
        binding.appsEnabled.setOnCheckedChangeListener { _,enabled ->
            DataStore.proxyApps=enabled;binding.appsBypass.isEnabled=enabled
        }
        binding.appsBypass.setOnCheckedChangeListener { _,bypass -> DataStore.bypass=bypass }

        binding.list.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        binding.list.itemAnimator = DefaultItemAnimator()
        binding.list.adapter = appsAdapter

        // the app bar pads the status bar and the sides itself (applyTopInset); the list pads the navigation bar and the sides
        binding.list.applyListInsets(ime = true, horizontal = true)

        binding.search.addTextChangedListener {
            appsAdapter.applyFilter(it?.toString() ?: "")
        }

        binding.showSystemApps.isChecked = sysApps
        binding.showSystemApps.setOnCheckedChangeListener { _, isChecked ->
            sysApps = isChecked
            refilter()
            binding.list.scrollToPosition(0)
        }

        instance = this
        return binding.root
    }

    private var sysApps = false

    private fun handleMenuItem(item:MenuItem):Boolean {
        when (item.itemId) {
            R.id.action_invert_selections -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    val proxiedUidsOld = proxiedUids.clone()
                    for (app in apps) {
                        if (app.byName) continue
                        if (proxiedUidsOld.contains(app.uid)) {
                            proxiedUids.delete(app.uid)
                        } else {
                            proxiedUids[app.uid] = true
                        }
                    }
                    saveSelection()
                    apps = sorted(apps)
                    onMainDispatcher {
                        refilter()
                    }
                }

                return true
            }

            R.id.action_clear_selections -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    proxiedUids.clear()
                    extraSelected.clear()
                    DataStore.individual = ""
                    onMainDispatcher {
                        loadApps()
                    }
                }
                return true
            }

            R.id.action_add_package -> {
                PackageNameInput.show(requireContext()) { names -> addPackages(names) }
                return true
            }

            R.id.action_export_clipboard -> {
                val success =
                    SagerNet.trySetPrimaryClip("${DataStore.bypass}\n${DataStore.individual}")
                Snackbar.make(
                    binding.list,
                    if (success) R.string.action_export_msg else R.string.action_export_err,
                    Snackbar.LENGTH_LONG
                ).show()
                return true
            }

            R.id.action_import_clipboard -> {
                // This screen's export leads with the bypass flag; a plain package list (the route rule picker's
                // export, whose "unknown" entry is no package) keeps the current mode.
                val lines = SagerNet.getClipboardText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                val bypass = lines.firstOrNull()?.takeIf { it == "true" || it == "false" }?.toBoolean()
                val packages = (if (bypass != null) lines.drop(1) else lines).filter { it != RouteRule.UNKNOWN_PACKAGE }
                if (bypass == null && packages.isEmpty()) {
                    Snackbar.make(binding.list, R.string.action_import_err, Snackbar.LENGTH_LONG).show()
                    return true
                }
                if (bypass != null) {
                    binding.appsBypass.isChecked=bypass
                }
                DataStore.individual = packages.distinct().joinToString("\n")
                Snackbar.make(binding.list, R.string.action_import_msg, Snackbar.LENGTH_LONG).show()
                selectionLoaded = false
                loadApps()
                return true
            }
        }
        return false
    }

    /** Listed apps are selected by uid; any other name becomes a row of its own. */
    private fun addPackages(names: List<String>) {
        val listed = cachedApps()
        for (name in names) {
            val uid = listed[name]?.applicationInfo?.uid
            if (uid != null) proxiedUids[uid] = true else extraSelected.add(name)
        }
        saveSelection()
        loadApps()
    }

    private fun selectProxyApp() {
        requireContext().confirmAction(
            getString(R.string.confirm_auto_select_apps),
            getString(R.string.auto_select_proxy_apps_message),
            R.string.confirm_replace,
        ) {
            try {
                val needProxyAppsList = getAutoProxyApps("")
                val bypass = DataStore.bypass
                proxiedUids.clear()
                for (app in cachedApps()) {
                    val needProxy =
                        needProxyAppsList.contains(app.key) || (app.value.applicationInfo?.uid
                            ?: 0) == 1000
                    if (needProxy) {
                        if (!bypass) {
                            app.value.applicationInfo?.apply {
                                proxiedUids[uid] = true
                            }
                        }
                    } else {
                        if (bypass) {
                            app.value.applicationInfo?.apply {
                                proxiedUids[uid] = true
                            }
                        }
                    }
                }
                saveSelection()
                apps = sorted(apps)
                refilter()
            } catch (e: Exception) {
                Logs.e(e)
            }
        }
    }

    private fun getAutoProxyApps(content: String): List<String> {
        var list = listOf<String>()
        try {
            val proxyApps = if (TextUtils.isEmpty(content)) {
                NGUtil.readTextFromAssets(app, "proxy_packagename.txt")
            } else {
                content
            }
            if (!TextUtils.isEmpty(proxyApps)) {
                list = proxyApps.split("\n")
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun showAccessPlaceholder(){
        loading.visibility=View.GONE;binding.list.visibility=View.GONE;binding.appPlaceholder.root.visibility=View.VISIBLE
    }
    private fun offerAccessSettings(){if(!offeredSettings && isResumed){offeredSettings=true;access.explainSettings()}}
    override fun onResume(){
        super.onResume()
        if(!requestedAccess){requestedAccess=true;if(!access.requestIfNeeded())loadApps()}else loadApps()
    }
    override fun onPause(){
        if(openedConfig!=config()){openedConfig=config();if(DataStore.serviceState.canStop)SagerNet.reloadService()}
        super.onPause()
    }
    override fun onDestroyView(){instance=null;loader?.cancel();binding.list.adapter=null;super.onDestroyView()}
}
