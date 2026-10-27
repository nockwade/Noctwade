package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context

internal object MenuSearchIndex {
    data class Entry(
        val key: String,
        val title: Int,
        val summary: Int,
        val screen: String,
        val screenTitle: Int,
    ) {
        fun searchable(c: Context) =
            listOf(
                    c.getString(title),
                    if (summary != 0) c.getString(summary) else "",
                    key,
                    c.getString(screenTitle),
                )
                .joinToString(" ")
    }

    fun entries(): List<Entry> = MenuSearchEntries.all

    fun filter(context: Context, entries: List<Entry>, query: String): List<Entry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.isEmpty()) emptyList()
        else
            entries.filter { e ->
                val text = e.searchable(context).lowercase()
                words.all { text.contains(it) }
            }
    }
}
