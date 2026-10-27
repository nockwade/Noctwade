package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import io.nekohasekai.sagernet.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RenameDialogTest {
    class Host : AppCompatActivity() {
        override fun onCreate(state: Bundle?) {
            setTheme(R.style.Theme_SagerNet)
            NoctwadeTheme.apply(this)
            super.onCreate(state)
            setContentView(FrameLayout(this).apply { id = 12345 })
        }
    }

    @Test
    fun renameOpensValidatesAndSavesWithoutCrashing() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val host = Fragment(android.R.layout.simple_list_item_1)
        a.supportFragmentManager.beginTransaction().add(12345, host).commitNow()
        var saved: String? = null
        NoctwadeDialogs.groupName(host, "Original") { saved = it }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        fun find(v: android.view.View): TextInputEditText? {
            if (v is TextInputEditText) return v
            if (v is android.view.ViewGroup)
                for (i in 0 until v.childCount) {
                    find(v.getChildAt(i))?.let {
                        return it
                    }
                }
            return null
        }
        val input = find(dialog.window!!.decorView)!!
        assertTrue(input.parent is android.view.ViewGroup)
        input.setText("  ")
        dialog.getButton(-1).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(saved)
        assertTrue(dialog.isShowing)
        input.setText("New name")
        dialog.getButton(-1).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("New name", saved)
        controller.pause().stop().destroy()
    }

    @Test
    fun everyThemeCanInflateTheInputDialog() {
        for (key in NoctwadeTheme.keys) {
            val controller = Robolectric.buildActivity(Host::class.java).setup()
            val a = controller.get()
            NoctwadeTheme.prefs(a).edit().putString("theme", key).commit()
            NoctwadeTheme.apply(a)
            val host = Fragment(android.R.layout.simple_list_item_1)
            a.supportFragmentManager.beginTransaction().add(12345, host).commitNow()
            NoctwadeDialogs.groupName(host, "Group") {}
            assertTrue(ShadowDialog.getLatestDialog().isShowing)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun styledMenuPreservesDropdownAndSwitchWidgets() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        val manager = androidx.preference.PreferenceManager(a)
        val screen = manager.createPreferenceScreen(a)
        screen.addPreference(
            moe.matsuri.nb4a.ui.SimpleMenuPreference(a).apply {
                key = "test-dropdown"
                isPersistent = false
                title = "Choice"
                entries = arrayOf("One", "Two")
                entryValues = arrayOf("1", "2")
                value = "1"
            }
        )
        screen.addPreference(
            androidx.preference.SwitchPreferenceCompat(a).apply {
                key = "test-switch"
                isPersistent = false
                title = "Switch"
                isChecked = true
            }
        )
        NoctwadePreferenceStyle.rows(screen)
        val adapter = androidx.preference.PreferenceGroupAdapter(screen)
        for (i in 0 until adapter.itemCount) {
            val holder = adapter.onCreateViewHolder(FrameLayout(a), adapter.getItemViewType(i))
            adapter.onBindViewHolder(holder, i)
            if (i == 0)
                assertNotNull(holder.itemView.findViewById<android.widget.Spinner>(R.id.spinner))
        }
        controller.pause().stop().destroy()
    }

    @Test
    fun clientScreensInflateWithEveryTheme() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        val a = controller.get()
        for (key in NoctwadeTheme.keys) {
            NoctwadeTheme.prefs(a).edit().putString("theme", key).commit()
            NoctwadeTheme.apply(a)
            for (layout in
                listOf(
                    R.layout.layout_noctwade_home,
                    R.layout.layout_noctwade_servers,
                    R.layout.layout_noctwade_server_item,
                )) {
                val view = a.layoutInflater.inflate(layout, FrameLayout(a), false)
                assertNotNull(view)
            }
        }
        controller.pause().stop().destroy()
    }
}
