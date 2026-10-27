package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutMainBinding
import io.nekohasekai.sagernet.widget.applyTopInset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Covers the complete startup container, including the retained Material navigation drawers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class StartupLayoutTest {
    @Test
    fun completeStartupLayoutInflatesWithEveryPaletteInDayAndNightMode() {
        val app = RuntimeEnvironment.getApplication()
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val configuration =
                Configuration(app.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }
            for (palette in NoctwadeTheme.keys) {
                NoctwadeTheme.prefs(app).edit().clear().putString("theme", palette).commit()
                val context =
                    ContextThemeWrapper(
                        app.createConfigurationContext(configuration),
                        R.style.Theme_SagerNet,
                    )
                NoctwadeTheme.apply(context)
                for ((attribute, expected) in
                    listOf(
                        android.R.attr.textColorPrimary to NoctwadeTheme.text(context),
                        android.R.attr.textColorSecondary to NoctwadeTheme.muted(context),
                    )) {
                    val value = android.util.TypedValue()
                    assertTrue(context.theme.resolveAttribute(attribute, value, true))
                    assertTrue(
                        "Material text color must resolve to a resource",
                        value.resourceId != 0,
                    )
                    assertEquals(
                        expected,
                        ContextCompat.getColorStateList(context, value.resourceId)!!.defaultColor,
                    )
                }
                val binding = LayoutMainBinding.inflate(LayoutInflater.from(context))
                assertNotNull(binding.bottomNavigation)
                assertTrue(binding.navView.menu.size() > 0)
                assertTrue(binding.navViewBlack.menu.size() > 0)
                // System bars must grow the app bar rather than squeeze its toolbar and back
                // target.
                val settings =
                    io.nekohasekai.sagernet.databinding.LayoutConfigSettingsBinding.inflate(
                        LayoutInflater.from(context)
                    )
                settings.appbar.applyTopInset()
                val density = context.resources.displayMetrics.density
                val top = (36 * density).toInt()
                ViewCompat.dispatchApplyWindowInsets(
                    settings.appbar,
                    WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                        .build(),
                )
                settings.root.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(
                        (360 * density).toInt(),
                        android.view.View.MeasureSpec.EXACTLY,
                    ),
                    android.view.View.MeasureSpec.makeMeasureSpec(
                        (800 * density).toInt(),
                        android.view.View.MeasureSpec.EXACTLY,
                    ),
                )
                settings.root.layout(
                    0,
                    0,
                    settings.root.measuredWidth,
                    settings.root.measuredHeight,
                )
                assertTrue(
                    "Toolbar must retain a 48dp touch target below system bars",
                    settings.toolbar.height >= (48 * density).toInt(),
                )
                assertTrue(settings.appbar.height >= settings.toolbar.height + top)
            }
        }
    }
}
