package io.nekohasekai.sagernet.ui.noctwade

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.NoctwadeServersFragment

/**
 * iOS-style sheet with the server list, opened from the Home server and subscription cards. Rounded
 * top, grabber, dimmed backdrop; choosing a server closes it.
 */
class ServersSheet : BottomSheetDialogFragment() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun getTheme() = R.style.ThemeOverlay_Noctwade_BottomSheet

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val radius = dp(28).toFloat()
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                ColorUtils.blendARGB(
                                    NoctwadeTheme.background(context),
                                    NoctwadeTheme.gradientStart(context),
                                    .55f,
                                ),
                                NoctwadeTheme.background(context),
                            ),
                        )
                        .apply {
                            cornerRadii =
                                floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                            setStroke(
                                dp(1).coerceAtLeast(1),
                                ColorUtils.setAlphaComponent(NoctwadeTheme.text(context), 22),
                            )
                        }
            }
        // Grabber
        root.addView(
            View(context).apply {
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(3).toFloat()
                        setColor(ColorUtils.setAlphaComponent(NoctwadeTheme.text(context), 70))
                    }
            },
            LinearLayout.LayoutParams(dp(38), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
                bottomMargin = dp(4)
            },
        )
        root.addView(
            FragmentContainerView(context).apply { id = R.id.nw_sheet_container },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        return root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        if (childFragmentManager.findFragmentById(R.id.nw_sheet_container) == null)
            childFragmentManager
                .beginTransaction()
                .replace(
                    R.id.nw_sheet_container,
                    NoctwadeServersFragment.forSheet(arguments?.getLong(ARG_GROUP) ?: 0L),
                )
                .commitNow()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val window = dialog.window
        window?.setDimAmount(DIM)
        val sheet =
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        sheet.background = ColorDrawable(Color.TRANSPARENT)
        sheet.layoutParams.height = (resources.displayMetrics.heightPixels * .88f).toInt()
        // Every way of closing (pick, swipe, back, tap outside) slides the sheet all the way
        // down; the content is only torn down once it is off screen. The window exit animation
        // is a no-op and the backdrop dims out with the sheet instead of vanishing at the end.
        dialog.dismissWithAnimation = true
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isHideable = true
            state = BottomSheetBehavior.STATE_EXPANDED
            addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) = Unit

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        val parentHeight = (bottomSheet.parent as? View)?.height ?: return
                        val height = bottomSheet.height.takeIf { it > 0 } ?: return
                        val visible =
                            ((parentHeight - bottomSheet.top).toFloat() / height).coerceIn(0f, 1f)
                        window?.setDimAmount(DIM * visible)
                    }
                }
            )
        }
    }

    companion object {
        private const val DIM = .45f
        private const val ARG_GROUP = "group"

        fun show(host: androidx.fragment.app.Fragment, groupId: Long) {
            if (host.childFragmentManager.findFragmentByTag(TAG) != null) return
            ServersSheet()
                .apply { arguments = bundleOf(ARG_GROUP to groupId) }
                .show(host.childFragmentManager, TAG)
        }

        private const val TAG = "servers-sheet"
    }
}
