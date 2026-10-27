package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import java.text.Collator
import java.util.Locale

/** A presentation order: changing it never rewrites the subscription's stored userOrder. */
internal object ServerListOrder {
    const val KEY = "noctwadeServerOrder"

    enum class Mode(val key: String) {
        SUBSCRIPTION("subscription"),
        PING("ping"),
        NAME("name"),
    }

    fun prefs(context: Context) =
        context.getSharedPreferences("noctwade_lists", Context.MODE_PRIVATE)

    fun mode(context: Context) =
        Mode.entries.firstOrNull { it.key == prefs(context).getString(KEY, null) }
            ?: Mode.SUBSCRIPTION

    fun sort(
        servers: List<ServerListItem.Server>,
        mode: Mode,
        locale: Locale = Locale.getDefault(),
    ): List<ServerListItem.Server> {
        if (mode == Mode.SUBSCRIPTION) return servers
        if (mode == Mode.PING)
            return servers.sortedWith(
                compareBy<ServerListItem.Server> {
                        when {
                            it.latency > 0 -> 0
                            it.latency ==
                                io.nekohasekai.sagernet.database.ProxyEntity.LATENCY_CONNECT_ONLY ->
                                1
                            it.latency == 0 -> 2
                            else -> 3
                        }
                    }
                    .thenBy { if (it.latency > 0) it.latency else 0 }
            )
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return servers.sortedWith { a, b ->
            collator.compare(
                a.name.dropWhile { !it.isLetterOrDigit() },
                b.name.dropWhile { !it.isLetterOrDigit() },
            )
        }
    }
}
