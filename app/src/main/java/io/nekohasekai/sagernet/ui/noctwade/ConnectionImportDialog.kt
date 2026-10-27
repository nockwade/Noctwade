package io.nekohasekai.sagernet.ui.noctwade

import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.profiles.ProfileImports

/** The original Throne + menu and import handlers, without any new intermediate dialogs. */
internal class ConnectionImportDialog(private val host: Fragment) {
    private val imports = ProfileImports(host)

    fun show(anchor: View) {
        PopupMenu(host.requireContext(), anchor).apply {
            inflate(R.menu.noctwade_import_menu)
            imports.prepare(menu)
            setOnMenuItemClickListener(imports::onMenuItemClick)
            show()
        }
    }
}
