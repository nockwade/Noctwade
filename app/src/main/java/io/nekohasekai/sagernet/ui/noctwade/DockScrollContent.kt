package io.nekohasekai.sagernet.ui.noctwade

import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.widget.updateBasePadding

/**
 * Reserve touch clearance inside each scrolling viewport, keeping drawing continuous behind the
 * dock.
 */
internal object DockScrollContent {
    fun apply(view: View) {
        if (view is RecyclerView || view is NestedScrollView) {
            if (view.getTag(R.id.nw_menu_styled) != null) return
            if (view.getTag(R.id.nw_dock_scroll_padding) == null) {
                view.setTag(R.id.nw_dock_scroll_padding, true)
                view.updateBasePadding(
                    bottom =
                        view.paddingBottom + (24 * view.resources.displayMetrics.density).toInt()
                )
                view.applyListInsets(ime = true, horizontal = false)
            }
            return
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i))
    }
}
