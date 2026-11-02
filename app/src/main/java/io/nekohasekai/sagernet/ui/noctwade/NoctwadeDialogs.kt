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
    /**
     * Single choice in a soft card: the title, then one row per option; the chosen row is a tinted
     * pill with a check. Picking an option moves the highlight, then closes. Used for every list
     * setting instead of the stock grey radio dialog.
     */
    fun choice(
        context: android.content.Context,
        title: CharSequence?,
        labels: Array<out CharSequence>,
        selected: Int,
        pick: (Int) -> Unit,
    ) {
        fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
        val accent = NoctwadeTheme.accent(context)
        val text = NoctwadeTheme.text(context)
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(20), dp(10), dp(10))
                background =
                    GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(NoctwadeTheme.gradientStart(context), NoctwadeTheme.gradientEnd(context)),
                        )
                        .apply {
                            cornerRadius = dp(28).toFloat()
                            setStroke(dp(1), NoctwadeTheme.stroke(context))
                        }
            }
        if (!title.isNullOrBlank())
            card.addView(
                android.widget.TextView(context).apply {
                    this.text = title
                    textSize = 18f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(text)
                    setPadding(dp(14), 0, dp(14), dp(12))
                }
            )
        val dialog =
            MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Noctwade_Dialog_Soft)
                .setView(card)
                .create()
        val rows = ArrayList<android.widget.TextView>()
        fun paint(current: Int) {
            rows.forEachIndexed { index, row ->
                val on = index == current
                row.setTextColor(if (on) accent else text)
                row.typeface =
                    android.graphics.Typeface.create(
                        if (on) "sans-serif-medium" else "sans-serif",
                        android.graphics.Typeface.NORMAL,
                    )
                row.background =
                    android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(NoctwadeTheme.color(context, R.attr.nwRipple)),
                        if (on)
                            GradientDrawable().apply {
                                cornerRadius = dp(18).toFloat()
                                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 36))
                            }
                        else null,
                        GradientDrawable().apply {
                            cornerRadius = dp(18).toFloat()
                            setColor(android.graphics.Color.WHITE)
                        },
                    )
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null,
                    null,
                    if (on)
                        ContextCompat.getDrawable(context, R.drawable.nw_check)?.mutate()?.apply {
                            setTint(accent)
                            setBounds(0, 0, dp(20), dp(20))
                        }
                    else null,
                    null,
                )
            }
        }
        labels.forEachIndexed { index, label ->
            val row =
                android.widget.TextView(context).apply {
                    this.text = label
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(52)
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    compoundDrawablePadding = dp(12)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        paint(index)
                        // Let the highlight land before the card closes.
                        postDelayed(
                            {
                                if (dialog.isShowing) dialog.dismiss()
                                if (index != selected) pick(index)
                            },
                            if (NoctwadeMotion.enabled(context)) 160L else 0L,
                        )
                    }
                }
            rows.add(row)
            card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        }
        paint(selected)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(.32f)
            setWindowAnimations(if (NoctwadeMotion.enabled(context)) R.style.NwSoftDialogAnimation else 0)
        }
        dialog.show()
        dialog.window?.setLayout(
            minOf(context.resources.displayMetrics.widthPixels - dp(48), dp(400)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
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
