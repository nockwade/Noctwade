package io.nekohasekai.sagernet.ui.noctwade

import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.AppRoutingFragment
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion
import io.nekohasekai.sagernet.widget.noctwade.SegmentedControl

class NoctwadeRoutingFragment : Fragment() {
    private var selected = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selected = state?.getInt("tab") ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", selected)
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val d = resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = NoctwadeTheme.menuGradient(context)
            addView(
                SegmentedControl(context).apply {
                    select(selected, false)
                    onSelected = { show(it, true) }
                },
                LinearLayout.LayoutParams(-1, (52 * d).toInt()).apply {
                    setMargins(
                        (24 * d).toInt(),
                        (4 * d).toInt(),
                        (24 * d).toInt(),
                        (12 * d).toInt(),
                    )
                },
            )
            addView(
                FrameLayout(context).apply { id = R.id.nw_route_pane },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        if (childFragmentManager.findFragmentById(R.id.nw_route_pane) == null) show(selected, false)
    }

    private fun show(index: Int, animate: Boolean) {
        val forward = index > selected
        selected = index
        childFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                if (animate && NoctwadeMotion.enabled(requireContext()))
                    setCustomAnimations(
                        if (forward) R.anim.nw_slide_in_right else R.anim.nw_slide_in_left,
                        if (forward) R.anim.nw_slide_out_left else R.anim.nw_slide_out_right,
                    )
            }
            .replace(
                R.id.nw_route_pane,
                when (index) {
                    0 -> NoctwadeRoutingProfilesFragment()
                    1 -> AppRoutingFragment()
                    else -> NoctwadeDnsFragment()
                },
            )
            .commit()
    }
}
