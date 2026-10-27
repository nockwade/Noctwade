package io.nekohasekai.sagernet.ui.noctwade

import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion

internal object NoctwadeDialogs {
    data class Action(
        @StringRes val title: Int,
        val destructive: Boolean = false,
        val run: () -> Unit,
    )

    /**
     * The same plain popup as the "+" menu, opened from [anchor]: text only, no icons, no heading
     * (the row it opens from already shows the name) and no scale or slide, just a short fade.
     */
    fun actions(host: Fragment, anchor: View, actions: List<Action>) {
        if (!host.isAdded || host.view == null || !anchor.isAttachedToWindow) return
        val context = host.requireContext()
        val popup = PopupMenu(context, anchor, Gravity.END)
        actions.forEachIndexed { index, action ->
            val label = SpannableString(context.getString(action.title))
            if (action.destructive)
                label.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.nw_error)),
                    0,
                    label.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            popup.menu.add(Menu.NONE, index, index, label)
        }
        popup.setOnMenuItemClickListener { item ->
            actions.getOrNull(item.itemId)?.run?.invoke()
            true
        }
        val owner = host.viewLifecycleOwner
        val observer =
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = popup.dismiss()
            }
        owner.lifecycle.addObserver(observer)
        popup.setOnDismissListener { owner.lifecycle.removeObserver(observer) }
        popup.show()
    }
    fun groupName(host: Fragment, initial: String, save: (String) -> Unit) {
        val context = host.requireContext()
        fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(8))
            }
        val field =
            TextInputLayout(context).apply {
                hint = context.getString(R.string.nw_group_name)
                isHintAnimationEnabled = false
                isExpandedHintEnabled = false
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                )
                boxStrokeColor = io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.accent(context)
                boxBackgroundColor = NoctwadeTheme.surface(context)
            }
        val input =
            TextInputEditText(field.context).apply {
                setSingleLine(true)
                setText(initial)
                selectAll()
                background = null
                setTextColor(io.nekohasekai.sagernet.ui.noctwade.NoctwadeTheme.text(context))
                textSize = 16f
                setPadding(dp(16), dp(18), dp(16), dp(18))
            }
        field.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(field)
        val dialog =
            MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Noctwade_Dialog)
                .setTitle(R.string.nw_rename_group)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        input.requestFocus()
        dialog.window?.apply {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            attributes =
                attributes.apply {
                    y = dp(if (context.resources.configuration.screenHeightDp < 500) 8 else 72)
                }
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            setWindowAnimations(
                if (NoctwadeMotion.enabled(context)) R.style.NwFixedDialogAnimation else 0
            )
        }
        dialog.setOnShowListener {
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val name = input.text.toString().trim()
                    if (name.isEmpty()) field.error = context.getString(R.string.nw_name_required)
                    else {
                        save(name)
                        dialog.dismiss()
                    }
                }
        }
        dialog.show()
        val owner = host.viewLifecycleOwner
        val observer =
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = dialog.dismiss()
            }
        owner.lifecycle.addObserver(observer)
        dialog.setOnDismissListener { owner.lifecycle.removeObserver(observer) }
    }
}
