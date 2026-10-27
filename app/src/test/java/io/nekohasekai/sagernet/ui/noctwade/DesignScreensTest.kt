package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutMainBinding
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeHomeBinding
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeServersBinding
import io.nekohasekai.sagernet.widget.noctwade.NavigationFadeDrawable
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design screenshots with sample data. Runs only when NOCTWADE_SCREENS points to an output folder:
 * `$env:NOCTWADE_SCREENS="..\artwork\screens"; gradlew app:testFdroidReleaseUnitTest --tests
 * '*DesignScreensTest*'`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w392dp-h850dp-night-xhdpi")
class DesignScreensTest {
    class Host : AppCompatActivity() {
        lateinit var main: LayoutMainBinding

        override fun onCreate(state: Bundle?) {
            setTheme(R.style.Theme_SagerNet)
            NoctwadeTheme.apply(this)
            super.onCreate(state)
            main = LayoutMainBinding.inflate(layoutInflater)
            setContentView(main.root)
            (main.navViewBlack.parent as ViewGroup).removeView(main.navViewBlack)
            main.bottomNavigation.visibility = View.GONE
            main.navigationScrim.visibility = View.GONE
            main.fab.visibility = View.GONE
            main.fabProgress.visibility = View.GONE
            main.stats.visibility = View.GONE
            main.navigationScrim.background = NavigationFadeDrawable(NoctwadeTheme.background(this))
        }
    }

    private val out = System.getenv("NOCTWADE_SCREENS")?.let(::File)

    @Test
    fun captureScreens() {
        assumeTrue(out != null)
        out!!.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        val themes = System.getenv("NOCTWADE_THEMES")?.split(",") ?: NoctwadeTheme.keys
        for (name in listOf("nw_logo", "nw_launcher_background|nw_launcher_foreground")) {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFF1A1A22.toInt())
            for (layer in name.split("|")) {
                val drawable =
                    androidx.core.content.ContextCompat.getDrawable(
                        app,
                        app.resources.getIdentifier(layer, "drawable", app.packageName),
                    )!!
                drawable.setBounds(0, 0, 512, 512)
                drawable.draw(canvas)
            }
            File(out, "drawable-${name.substringAfterLast('|')}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        for (theme in themes) {
            NoctwadeTheme.prefs(app)
                .edit()
                .clear()
                .putString("theme", theme)
                .putString("eco", "off")
                .commit()
            capture(theme, "home") { host -> home(host) }
            capture(theme, "home-connected") { host ->
                home(host)
                (holder(host)
                        .getChildAt(holder(host).childCount - 1)
                        .findViewById<io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView>(
                            R.id.home_power
                        ))
                    .setState(io.nekohasekai.sagernet.bg.BaseService.State.Connected)
            }
            capture(theme, "servers") { host -> servers(host) }
            capture(theme, "menu-search") { host -> menuSearch(host) }
            capture(theme, "rules") { host -> rules(host) }
        }
    }

    private fun capture(theme: String, screen: String, build: (Host) -> Unit) {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val host = controller.get()
        build(host)
        settle()
        val root = host.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(out, "$screen-$theme.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        controller.pause().stop().destroy()
    }

    /** Lets background wallpaper decoding finish and posts run. */
    private fun settle() {
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun holder(host: Host) = host.main.fragmentHolder as ViewGroup

    private fun home(host: Host) {
        val ui = LayoutNoctwadeHomeBinding.inflate(host.layoutInflater, holder(host), false)
        holder(host).addView(ui.root)
        host.main.bottomNavigation.select(0, false)
        val d = host.resources.displayMetrics.density
        ui.homeContent.setPadding(
            (20 * d).toInt(),
            (28 * d).toInt(),
            (20 * d).toInt(),
            (20 * d).toInt(),
        )
        ui.homeServerName.setTitle("Netherlands · Amsterdam 2")
        ui.homeServerIcon.setCountry("NL")
        ui.homeServerDetail.text = "Noctwade Premium · VLESS · REALITY"
        ui.homePing.text = host.getString(R.string.nw_ping, 42)
        ui.homeSubtitle.visibility = View.GONE
        ui.homeQuota.apply {
            quotaName.text = "Noctwade Premium"
            quotaAmount.text = "440 ГБ"
            quotaAllowance.text = "/ ∞"
            quotaDetail.text = "Данные провайдера · обновлено 23.10.26, 01:12"
            quotaMeter.visibility = View.GONE
        }
        ui.homeBackdrop.setEffectsVisible(false)
    }

    private fun rules(host: Host) {
        val column = android.widget.LinearLayout(host).apply { orientation = android.widget.LinearLayout.VERTICAL; setBackgroundColor(NoctwadeTheme.background(host)) }
        holder(host).addView(column, ViewGroup.LayoutParams(-1, -1))
        val d = host.resources.displayMetrics.density
        column.setPadding(0, (40 * d).toInt(), 0, 0)
        val title = io.nekohasekai.sagernet.databinding.LayoutRouteRulesTitleBinding.inflate(host.layoutInflater, column, false)
        title.title.text = "Правила (4)"
        title.domainStrategyValue.text = "По умолчанию"
        column.addView(title.root)
        fun row(name: String, summary: String, action: String, color: Int, on: Boolean) {
            val r = io.nekohasekai.sagernet.databinding.LayoutRouteRuleItemBinding.inflate(host.layoutInflater, column, false)
            r.ruleName.text = name
            r.ruleSummary.text = summary
            r.ruleAction.text = action
            r.ruleAction.setTextColor(color)
            r.ruleEnabled.isChecked = on
            column.addView(r.root)
        }
        row("private-ips", "geoip-private", "direct", 0xFF2ED8A7.toInt(), true)
        row("category-ads-all", "geosite-category-ads-all", "reject", 0xFFFF8FA3.toInt(), true)
        row("Блокированное в РФ", "geosite-ru-blocked, geoip-ru-blocked", "proxy", NoctwadeTheme.accent(host), true)
        row("category-ru", "geosite-category-ru, geoip-ru", "direct", 0xFF2ED8A7.toInt(), false)
    }

    private fun menuSearch(host: Host) {
        val ui =
            io.nekohasekai.sagernet.databinding.LayoutConfigSettingsBinding.inflate(
                host.layoutInflater,
                holder(host),
                false,
            )
        holder(host).addView(ui.root)
        host.main.bottomNavigation.select(2, false)
        ui.toolbar.title = host.getString(R.string.nw_menu)
        ui.toolbar.setTitleTextColor(NoctwadeTheme.text(host))
        ui.settingsSearch.visibility = View.VISIBLE
        ui.settingsSearch.input.setText("dns")
    }

    private fun servers(host: Host) {
        val ui = LayoutNoctwadeServersBinding.inflate(host.layoutInflater, holder(host), false)
        holder(host).addView(ui.root)
        host.main.bottomNavigation.select(1, false)
        val d = host.resources.displayMetrics.density
        ui.serversContent.setPadding((20 * d).toInt(), (28 * d).toInt(), (20 * d).toInt(), 0)
        val adapter = NoctwadeServersAdapter({}, { _, _ -> }, {}, {}, { _, _ -> }, {})
        ui.serversList.layoutManager = LinearLayoutManager(host)
        ui.serversList.adapter = adapter
        ui.serversList.setPadding(0, 0, 0, (110 * d).toInt())
        fun server(
            id: Long,
            name: String,
            cc: String,
            country: String,
            ping: Int,
            proto: String = "VLESS",
        ) =
            ServerListItem.Server(
                id,
                1,
                name,
                "$proto · REALITY",
                cc,
                ping,
                "$name $country",
                "REALITY",
            )
        adapter.submitList(
            listOf(
                ServerListItem.Group(1, "Noctwade Premium", "38.4 ГБ / 100 ГБ", true, true, 24, 24),
                server(11, "Amsterdam 2", "NL", "Netherlands", 42),
                server(12, "Frankfurt", "DE", "Germany", 58),
                server(13, "Helsinki", "FI", "Finland", 187, "Trojan"),
                server(14, "Tokyo", "JP", "Japan", 412, "Hysteria2"),
                server(15, "New York", "US", "United States", 0),
                ServerListItem.Group(2, "Мои серверы", "", false, false, 3, 3),
            )
        )
        adapter.select(11)
        adapter.groupProgress(mapOf(1L to (7 to 24)))
        ui.serversBackdrop.setEffectsVisible(false)
    }
}
