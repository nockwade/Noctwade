package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.appUserAgent
import io.nekohasekai.sagernet.route.RouteProfile
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Ready-made routing profiles offered in "Routing → Ready-made profiles". */
internal object RoutingPresets {

    data class Preset(val key: String, val title: Int, val description: Int)

    val all =
        listOf(
            Preset("roscom", R.string.nw_route_roscom, R.string.nw_route_roscom_hint),
            Preset("outip", R.string.nw_route_outip, R.string.nw_route_outip_hint),
            Preset("proxy", R.string.nw_route_proxy, R.string.nw_route_proxy_hint),
            Preset("smart", R.string.nw_route_smart, R.string.nw_route_smart_hint),
            Preset("direct", R.string.nw_route_direct, R.string.nw_route_direct_hint),
        )

    private const val MAX_DOWNLOAD_CHARS = 2_000_000

    /** Builds a new, unsaved profile for [key]. RoscomVPN is downloaded; others are local. */
    suspend fun build(context: Context, key: String): RouteProfile =
        withContext(Dispatchers.IO) {
            when (key) {
                "roscom" -> NoctwadeRouting.roscom(download(NoctwadeRouting.ROSCOM_SOURCE))
                "outip" ->
                    NoctwadeRouting.outip(
                        context.assets.open("noctwade-outip.json").bufferedReader().use {
                            it.readText()
                        }
                    )
                else -> NoctwadeRouting.preset(key)
            }
        }

    private suspend fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", appUserAgent())
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = reader.read(buffer)
                    if (n < 0) break
                    require(result.length + n <= MAX_DOWNLOAD_CHARS) { "Routing file is too large" }
                    result.append(buffer, 0, n)
                }
                result.toString()
            }
        } finally {
            connection.disconnect()
        }
    }
}
