package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.view.View
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.noctwade.RefreshSpin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Design119Test {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun trafficUsesBinaryUnitsLikeOtherClients() {
        val gib = 1024L * 1024 * 1024
        // 38.4 GiB: decimal division used to show "41.23 GB" here.
        assertEquals("38.4 GB", SubscriptionQuota.formatBytes(context, (38.4 * gib).toLong()))
        assertEquals("440 GB", SubscriptionQuota.formatBytes(context, 440 * gib))
        assertEquals("1.5 TB", SubscriptionQuota.formatBytes(context, 1536 * gib))
        assertEquals("512 MB", SubscriptionQuota.formatBytes(context, 512L * 1024 * 1024))
        assertEquals("0 MB", SubscriptionQuota.formatBytes(context, -5))
    }

    @Test
    fun unlimitedAllowanceIsInfinity() {
        assertEquals("/ ∞", context.getString(R.string.nw_quota_unlimited))
        assertEquals("/ 100 GB", context.getString(R.string.nw_quota_of, "100 GB"))
    }

    @Test
    @Config(qualifiers = "ru")
    fun serverCountIsWrittenOut() {
        val res = context.resources
        assertEquals("1 сервер", countLabel(res, 1, 1))
        assertEquals("24 сервера", countLabel(res, 24, 24))
        assertEquals("25 серверов", countLabel(res, 25, 25))
        assertEquals("7 из 24", countLabel(res, 7, 24))
    }

    @Test
    fun refreshSpinDisablesWhileUpdatingAndSettlesAfter() {
        val view = View(context)
        RefreshSpin.set(view, true)
        assertFalse(view.isEnabled)
        RefreshSpin.set(view, false)
        assertTrue(view.isEnabled)
        RefreshSpin.reset(view)
        assertEquals(0f, view.rotation)
    }

    @Test
    fun oldRoscomLinksDownloadFromTheCurrentBranch() {
        val old =
            "https://raw.githubusercontent.com/hydraponique/roscomvpn-geosite/main/release/sing-box/apple.srs"
        val rs = io.nekohasekai.sagernet.route.RuleSets
        assertEquals(old.replace("/main/", "/master/"), rs.mirrorLink(old, rs.MIRROR_GITHUB))
        assertEquals(
            "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geosite@master/release/sing-box/apple.srs",
            rs.mirrorLink(old, rs.MIRROR_CDN),
        )
        // the tag stays derived from the stored link, so rules and definitions still match
        assertEquals(rs.tagFor(old), rs.tagFor(old.trim()))
        val other = "https://raw.githubusercontent.com/someone/rules/main/x.srs"
        assertEquals(other, rs.mirrorLink(other, rs.MIRROR_GITHUB))
    }

    @Test
    fun sessionTimerFormatsAndResetsWithTheSession() {
        assertEquals("00:07", ConnectionClock.format(7_400))
        assertEquals("12:04", ConnectionClock.format((12 * 60 + 4) * 1000L))
        assertEquals("1:05:09", ConnectionClock.format((3600 + 5 * 60 + 9) * 1000L))
        val s = io.nekohasekai.sagernet.bg.BaseService.State.Connected
        ConnectionClock.onState(context, io.nekohasekai.sagernet.bg.BaseService.State.Connecting)
        ConnectionClock.onState(context, s)
        val start = System.currentTimeMillis()
        assertTrue(ConnectionClock.elapsed(context, s, start + 3_000)!! in 2_000L..4_000L)
        // a later Connected sample keeps the original start
        ConnectionClock.onState(context, s)
        assertTrue(ConnectionClock.elapsed(context, s, start + 3_000)!! >= 2_000L)
        ConnectionClock.onState(context, io.nekohasekai.sagernet.bg.BaseService.State.Stopped)
        assertEquals(
            null,
            ConnectionClock.elapsed(context, io.nekohasekai.sagernet.bg.BaseService.State.Stopped),
        )
    }
}
