package io.nekohasekai.sagernet.ui.noctwade

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.updateBasePadding

internal object NoctwadePreferenceStyle {
    fun rows(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            if (p is PreferenceGroup) {
                if (p is PreferenceCategory) p.layoutResource = R.layout.layout_noctwade_category
                rows(p)
            } else {
                p.layoutResource =
                    if (p is androidx.preference.DropDownPreference)
                        R.layout.layout_noctwade_dropdown
                    else R.layout.layout_noctwade_preference
                p.icon?.mutate()?.setTint(NoctwadeTheme.accent(group.context))
            }
        }
    }

    fun routeRows(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            if (p is PreferenceGroup) {
                if (p is PreferenceCategory) p.layoutResource = R.layout.layout_noctwade_category
                routeRows(p)
            } else if (p is androidx.preference.DropDownPreference)
                p.layoutResource = R.layout.layout_noctwade_route_dropdown
            else if (
                p is androidx.preference.EditTextPreference ||
                    p is io.nekohasekai.sagernet.widget.StringLinesPreference
            )
                p.layoutResource = R.layout.layout_noctwade_route_field
            else p.layoutResource = R.layout.layout_noctwade_preference
        }
    }

    fun list(fragment: PreferenceFragmentCompat) {
        val list = fragment.listView
        val context = list.context
        val d = context.resources.displayMetrics.density
        fun spacing(width: Int) {
            val gutter = maxOf((24 * d).toInt(), (width - (600 * d).toInt()) / 2)
            list.updateBasePadding(
                start = gutter,
                top = (6 * d).toInt(),
                end = gutter,
                bottom =
                    ((24 +
                            if (fragment.activity is io.nekohasekai.sagernet.ui.MainActivity) 8
                            else 0) * d)
                        .toInt(),
            )
        }
        spacing(list.width)
        list.clipToPadding = false
        list.isVerticalScrollBarEnabled = true
        list.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        list.verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
        list.background = NoctwadeTheme.menuGradient(context)
        fragment.setDivider(null)
        if (list.getTag(R.id.nw_menu_styled) == null) {
            list.setTag(R.id.nw_menu_styled, true)
            list.addOnLayoutChangeListener { _, l, _, r, _, ol, _, orr, _ ->
                if (r - l != orr - ol) spacing(r - l)
            }
        }
        if (Build.VERSION.SDK_INT >= 29)
            list.verticalScrollbarThumbDrawable =
                GradientDrawable().apply {
                    setColor(NoctwadeTheme.muted(context))
                    cornerRadius = 4 * d
                    setSize((2 * d).toInt(), (32 * d).toInt())
                }
    }
}
