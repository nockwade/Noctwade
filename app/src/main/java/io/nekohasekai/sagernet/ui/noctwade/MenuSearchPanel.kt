package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.noctwade.NoctwadeMotion

/**
 * Inline menu search: a field at the top of the menu and live results under it. Opened from the
 * toolbar's search icon; no separate screen.
 */
class MenuSearchPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private val d = resources.displayMetrics.density
    private val entries by lazy { MenuSearchIndex.entries() }
    private var rows: List<MenuSearchIndex.Entry> = emptyList()

    /** Called with the chosen result; the host opens its screen. */
    internal var onOpen: ((MenuSearchIndex.Entry) -> Unit)? = null

    val query: String
        get() = input.text?.toString().orEmpty()

    val input =
        EditText(context).apply {
            id = android.R.id.input
            setSingleLine(true)
            setTextColor(NoctwadeTheme.text(context))
            setHintTextColor(NoctwadeTheme.muted(context))
            setHint(R.string.nw_menu_search_hint)
            textSize = 16f
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setBackgroundResource(R.drawable.nw_input_field)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nw_search, 0, 0, 0)
            compoundDrawablePadding = (12 * d).toInt()
            compoundDrawablesRelative[0]?.setTint(NoctwadeTheme.muted(context))
        }

    private val empty =
        TextView(context).apply {
            textSize = 14f
            setTextColor(NoctwadeTheme.muted(context))
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (28 * d).toInt(), (24 * d).toInt(), 0)
        }

    private val adapter =
        object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount() = rows.size

            override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
                val column =
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        setPadding(
                            (24 * d).toInt(),
                            (14 * d).toInt(),
                            (24 * d).toInt(),
                            (14 * d).toInt(),
                        )
                        setBackgroundResource(R.drawable.nw_preference_card)
                        isClickable = true
                        isFocusable = true
                        layoutParams = RecyclerView.LayoutParams(-1, -2)
                    }
                val title =
                    TextView(context).apply {
                        textSize = 15f
                        setTextColor(NoctwadeTheme.text(context))
                    }
                val subtitle =
                    TextView(context).apply {
                        textSize = 12f
                        setTextColor(NoctwadeTheme.muted(context))
                        setPadding(0, (4 * d).toInt(), 0, 0)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                column.addView(title)
                column.addView(subtitle)
                return Holder(column, title, subtitle)
            }

            override fun onBindViewHolder(holder: Holder, index: Int) {
                val e = rows[index]
                holder.title.setText(e.title)
                holder.subtitle.text =
                    context.getString(e.screenTitle) +
                        (if (e.summary != 0) " · " + context.getString(e.summary) else "")
                holder.itemView.setOnClickListener { onOpen?.invoke(e) }
            }
        }

    init {
        orientation = VERTICAL
        background = NoctwadeTheme.menuGradient(context)
        isClickable = true // keep touches away from the menu underneath
        addView(
            input,
            LayoutParams(-1, (52 * d).toInt()).apply {
                setMargins((20 * d).toInt(), (4 * d).toInt(), (20 * d).toInt(), (8 * d).toInt())
            },
        )
        addView(empty)
        addView(
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                clipToPadding = false
                setPadding(0, 0, 0, (120 * d).toInt())
                adapter = this@MenuSearchPanel.adapter
            },
            LayoutParams(-1, 0, 1f),
        )
        input.doAfterTextChanged { filter() }
        filter()
    }

    private fun filter() {
        rows = MenuSearchIndex.filter(context, entries, query)
        @Suppress("NotifyDataSetChanged") adapter.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(
            if (query.isBlank()) R.string.nw_menu_search_hint else R.string.nw_menu_search_empty
        )
    }

    val isOpen: Boolean
        get() = visibility == View.VISIBLE

    fun open() {
        if (isOpen) return
        visibility = View.VISIBLE
        if (NoctwadeMotion.enabled(context)) {
            alpha = 0f
            translationY = -8 * d
            animate().alpha(1f).translationY(0f).setDuration(180L).start()
        }
        input.requestFocus()
        input.post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun close() {
        if (!isOpen) return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
        input.setText("")
        input.clearFocus()
        animate().cancel()
        alpha = 1f
        translationY = 0f
        visibility = View.GONE
    }

    private class Holder(v: View, val title: TextView, val subtitle: TextView) :
        RecyclerView.ViewHolder(v)
}
