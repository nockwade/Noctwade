package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import io.nekohasekai.sagernet.bg.BaseService

/**
 * Remembers when the current VPN session became Connected, for the timer under the Home status.
 * Wall-clock time in preferences survives activity recreation; a session first seen already
 * connected (e.g. the app was killed) starts counting from that moment.
 */
internal object ConnectionClock {
    private const val KEY = "connected_since"

    fun onState(context: Context, state: BaseService.State) {
        val prefs = NoctwadeTheme.prefs(context)
        when (state) {
            BaseService.State.Connected ->
                if (prefs.getLong(KEY, 0L) <= 0L)
                    prefs.edit().putLong(KEY, System.currentTimeMillis()).apply()
            // A new attempt starts a new session, also clearing a stamp left by a VPN that was
            // stopped while the app was not running.
            BaseService.State.Connecting,
            BaseService.State.Stopping,
            BaseService.State.Stopped -> prefs.edit().remove(KEY).apply()
            // Idle is UI-only (e.g. before the service binds).
            else -> Unit
        }
    }

    /** Milliseconds connected, or null when not connected. */
    fun elapsed(
        context: Context,
        state: BaseService.State,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        if (state != BaseService.State.Connected) return null
        val since = NoctwadeTheme.prefs(context).getLong(KEY, 0L)
        return if (since > 0L) (now - since).coerceAtLeast(0L) else 0L
    }

    /** "05:42" below an hour, "1:05:42" after. */
    fun format(millis: Long): String {
        val total = millis / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.ROOT, "%02d:%02d", m, s)
    }
}
