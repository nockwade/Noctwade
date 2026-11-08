package io.nekohasekai.sagernet.ui.noctwade

import android.view.View
import android.view.ViewGroup
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutNoctwadeHomeBinding

/**
 * "Hide the interface" on Home: everything fades out and stops taking touches (the views become invisible, not just
 * transparent), and only the wallpaper, the character, her dialogue box and the button that brings it all back
 * stay. Views that were already hidden stay hidden when the interface comes back.
 */
internal class HomeFocus(
    private val ui: LayoutNoctwadeHomeBinding,
    /** The character's dialogue box when there is one: it stays. */
    private val dialog: () -> View?,
    /** Stay visible too (the connect button with its halo). */
    private val alsoKeep: List<View> = emptyList(),
) {
    var hidden = false
        private set

    private val faded = ArrayList<View>()

    /** @return true when the state changed. */
    fun set(hide: Boolean, animate: Boolean): Boolean {
        if (hide == hidden) return false
        hidden = hide
        val ms = if (animate) DURATION else 0L
        if (hide) {
            faded.clear()
            val keep = dialog()
            val header = ui.homeHeader.root as ViewGroup
            val targets = ArrayList<View>()
            for (i in 0 until ui.homeContent.childCount) {
                val child = ui.homeContent.getChildAt(i)
                if (child !== keep && child !== header && child !in alsoKeep) targets += child
            }
            for (i in 0 until header.childCount) {
                val child = header.getChildAt(i)
                if (child.id !in headerKept) targets += child
            }
            for (view in targets)
                if (view.visibility == View.VISIBLE) {
                    faded += view
                    fade(view, 0f, ms, 0L) { if (hidden) view.visibility = View.INVISIBLE }
                }
        } else {
            for (view in faded) {
                view.visibility = View.VISIBLE
                fade(view, 1f, ms, if (animate) RETURN_DELAY else 0L) {}
            }
            faded.clear()
        }
        return true
    }

    private fun fade(view: View, to: Float, ms: Long, delay: Long, end: () -> Unit) {
        view.animate().cancel()
        if (ms == 0L) {
            view.alpha = to
            end()
            return
        }
        view.animate().alpha(to).setStartDelay(delay).setDuration(ms).withEndAction(end).start()
    }

    companion object {
        /** The menu button and the app's name stay too, so the screen still reads as the app. */
        private val headerKept = setOf(R.id.header_focus, R.id.header_menu, R.id.header_title)

        private const val DURATION = 400L

        /** On the way back her box settles first, then the cards fade in, so it never slides out from under them. */
        private const val RETURN_DELAY = 300L
    }
}
