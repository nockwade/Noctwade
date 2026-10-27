package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeGroupHeaderBinding
import io.nekohasekai.sagernet.widget.noctwade.CountryFlagView
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ServerLists116Test {
    private fun row(id: Long, name: String = "Node", ping: Int = 0) =
        ServerListItem.Server(id, 1, name, "VLESS", "PL", ping, name)

    @Test
    fun pingOrderKeepsTiesStableAndPlacesUnknownAndFailedLast() {
        val rows =
            listOf(
                row(1, ping = -1),
                row(2, ping = 80),
                row(3, ping = 0),
                row(4, ping = 32),
                row(5, ping = 32),
                row(6, ping = ProxyEntity.LATENCY_CONNECT_ONLY),
            )
        assertEquals(
            listOf(4L, 5L, 2L, 6L, 3L, 1L),
            ServerListOrder.sort(rows, ServerListOrder.Mode.PING).map { it.id },
        )
        assertSame(rows, ServerListOrder.sort(rows, ServerListOrder.Mode.SUBSCRIPTION))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), rows.map { it.id })
    }

    @Test
    fun alphabeticalOrderIgnoresDecorationsAndCase() {
        val rows = listOf(row(1, "🇵🇱 | Zebra"), row(2, "beta"), row(3, "Alpha"), row(4, "alpha"))
        assertEquals(
            listOf(3L, 4L, 2L, 1L),
            ServerListOrder.sort(rows, ServerListOrder.Mode.NAME, Locale.ENGLISH).map { it.id },
        )
        val russian = listOf(row(1, "Япония"), row(2, "Германия"), row(3, "Австрия"))
        assertEquals(
            listOf(3L, 2L, 1L),
            ServerListOrder.sort(russian, ServerListOrder.Mode.NAME, Locale.forLanguageTag("ru"))
                .map { it.id },
        )
    }

    // Native typed IME insets require API 30+. A synthetic API 28 WindowInsets round trip
    // cannot carry the compat-only override; it derives keyboard height from a real window.
    @Test
    @Config(sdk = [35])
    fun dockPaddingSurvivesRepeatedInsetsWithoutAccumulation() {
        val controller = Robolectric.buildActivity(RoutingUiTest.Host::class.java).setup()
        val a = controller.get()
        val density = a.resources.displayMetrics.density
        val list = RecyclerView(a).apply { setPadding(2, 3, 4, 14) }
        a.setContentView(list)
        DockScrollContent.apply(list)
        DockScrollContent.apply(list)
        val bars =
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                .build()
        repeat(3) { ViewCompat.dispatchApplyWindowInsets(list, bars) }
        assertFalse(list.clipToPadding)
        assertEquals(14 + (24 * density).toInt() + 24, list.paddingBottom)
        assertEquals(2, list.paddingLeft)
        assertEquals(3, list.paddingTop)
        val ime =
            WindowInsetsCompat.Builder(bars)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 200))
                .build()
        ViewCompat.dispatchApplyWindowInsets(list, ime)
        assertEquals(14 + (24 * density).toInt() + 200, list.paddingBottom)
        controller.pause().stop().destroy()
    }

    @Test
    fun countryFlagsAreClippedImagesAndResetTheirFallbackTint() {
        val controller = Robolectric.buildActivity(RoutingUiTest.Host::class.java).setup()
        val a = controller.get()
        val flag = CountryFlagView(a)
        assertEquals(1, flag.childCount)
        assertTrue(flag.getChildAt(0) is ImageView)
        assertTrue(flag.clipToOutline)
        val image = flag.getChildAt(0) as ImageView
        flag.setCountry("pl")
        assertNotNull(image.drawable)
        assertNull(image.imageTintList)
        assertEquals(0, image.paddingLeft)
        flag.setCountry("unknown")
        assertNotNull(image.imageTintList)
        assertTrue(image.paddingLeft > 0)
        flag.setCountry("DE")
        assertNull(image.imageTintList)
        assertEquals(0, image.paddingLeft)
        assertEquals("PL Fast UDP", ServerPresentation.title("🇵🇱 | PL Fast UDP"))
        assertEquals("💻 PL Fast UDP", ServerPresentation.title("🇵🇱 | 💻 PL Fast UDP"))
        assertEquals("Node", ServerPresentation.title("Node"))
        controller.pause().stop().destroy()
    }

    @Test
    fun collapsedGroupsKeepTheirTotalAndSearchCount() {
        val controller = Robolectric.buildActivity(RoutingUiTest.Host::class.java).setup()
        val a = controller.get()
        val adapter = NoctwadeServersAdapter({}, { _, _ -> }, {}, {}, { _, _ -> }, {})
        val group = ServerListItem.Group(1, "Subscription", "Unlimited", false, true, 24, 7)
        adapter.submitList(listOf(group))
        shadowOf(Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(FrameLayout(a), 0)
        adapter.onBindViewHolder(holder, 0)
        val ui = LayoutNoctwadeGroupHeaderBinding.bind(holder.itemView)
        assertEquals("7 of 24", ui.groupCount.text.toString())
        assertEquals("Subscription", ui.groupName.text.toString())
        assertTrue(ui.groupCount.contentDescription.toString().contains("24"))
        assertFalse(group.expanded)
        assertTrue(
            MenuSearchIndex.entries().any {
                it.key == ServerListOrder.KEY && it.title == R.string.nw_server_order
            }
        )
        controller.pause().stop().destroy()
    }
}
