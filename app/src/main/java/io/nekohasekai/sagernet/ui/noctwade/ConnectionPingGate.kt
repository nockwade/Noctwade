package io.nekohasekai.sagernet.ui.noctwade
internal class ConnectionPingGate {
    private var current = 0L

    fun update(connected: Boolean, profileId: Long): Boolean {
        if (!connected) {
            current = 0
            return false
        }
        if (profileId <= 0 || current == profileId) return false
        current = profileId
        return true
    }
}
