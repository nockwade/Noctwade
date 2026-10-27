package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore

/**
 * The one DNS server shared by every routing profile (Routing › DNS): a ready-made encrypted DNS
 * provider, or Manual, which keeps the full DNS settings as they were.
 */
internal object GlobalDns {
    enum class Provider(val title: Int, val summary: Int, val url: String?) {
        CLOUDFLARE(R.string.nw_dns_cloudflare, R.string.nw_dns_cloudflare_sum, "https://1.1.1.1/dns-query"),
        GOOGLE(R.string.nw_dns_google, R.string.nw_dns_google_sum, "https://8.8.8.8/dns-query"),
        QUAD9(R.string.nw_dns_quad9, R.string.nw_dns_quad9_sum, "https://9.9.9.9/dns-query"),
        MANUAL(R.string.nw_dns_manual, R.string.nw_dns_manual_sum, null),
    }

    private const val MANUAL_KEY = "dns_manual"

    /** Manual when chosen so; otherwise the provider whose address is the remote DNS now, else Manual. */
    fun current(context: Context): Provider {
        if (NoctwadeTheme.prefs(context).getBoolean(MANUAL_KEY, false)) return Provider.MANUAL
        val remote = DataStore.remoteDns
        return Provider.entries.firstOrNull { it.url == remote } ?: Provider.MANUAL
    }

    /** Applies [provider] to the whole routing and restarts a running connection with it. */
    fun select(context: Context, provider: Provider) {
        NoctwadeTheme.prefs(context).edit().putBoolean(MANUAL_KEY, provider == Provider.MANUAL).apply()
        val url = provider.url ?: return
        if (DataStore.remoteDns == url) return
        DataStore.remoteDns = url
        if (DataStore.serviceState.canStop) SagerNet.reloadService()
    }
}
