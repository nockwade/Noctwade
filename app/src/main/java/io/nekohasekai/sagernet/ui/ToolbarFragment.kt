package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.applyTopInset

open class ToolbarFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    // Null when the layout has no toolbar or the view is not created yet
    var toolbar: Toolbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<AppBarLayout>(R.id.appbar)?.applyTopInset()
        toolbar = view.findViewById(R.id.toolbar)
        toolbar?.setNavigationIcon(R.drawable.nw_back)
        toolbar?.setNavigationContentDescription(R.string.navigation_back)
        toolbar?.setTitleTextColor(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(requireContext()))
        toolbar?.navigationIcon?.setTint(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(requireContext()))
        toolbar?.setNavigationOnClickListener {
            if(parentFragmentManager.backStackEntryCount>0) parentFragmentManager.popBackStack()
            else (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_settings)
        }
    }

    open fun onKeyDown(ketCode: Int, event: KeyEvent) = false
}
