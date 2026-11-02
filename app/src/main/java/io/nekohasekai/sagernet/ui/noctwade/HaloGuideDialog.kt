package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import java.util.Locale

/**
 * The halo shader guide as a tidy dialog: section headings, normal text, code in boxes, and the AI prompt in its own box
 * with a button. A row of three buttons below copies everything, copies only the prompt, or shares the guide as a file.
 */
internal object HaloGuideDialog {
    class Section(val title: String, val lines: List<String>)

    /** Splits the guide into its upper-case headings and their lines. */
    fun sections(guide: String): List<Section> {
        val result = ArrayList<Section>()
        var title: String? = null
        var body = ArrayList<String>()
        for (line in guide.lines()) {
            val head = line.substringBefore(" (").trim()
            val heading =
                line.isNotBlank() && !line.startsWith(" ") && head.any(Char::isLetter) && head == head.uppercase(Locale.ROOT) && head.length in 3..70
            if (heading) {
                title?.let { result += Section(it, body) }
                title = line.trim()
                body = ArrayList()
            } else body += line
        }
        title?.let { result += Section(it, body) }
        return result
    }

    /** The text between the two dashed lines of the prompt section, or "" when there is none. */
    fun prompt(guide: String): String {
        val lines = guide.lines()
        val dashes = lines.indices.filter { lines[it].startsWith("-----") }
        return if (dashes.size >= 2) lines.subList(dashes[0] + 1, dashes[1]).joinToString("\n").trim() else ""
    }

    fun show(context: Context, guide: String, copyAll: () -> Unit, copyPrompt: () -> Unit, share: () -> Unit) =
        AppearancePickers.showCard(context, build(context, guide, copyAll, copyPrompt, share), 420)

    /** The dialog's card (also built on its own for screenshots in the tests). */
    fun build(context: Context, guide: String, copyAll: () -> Unit, copyPrompt: () -> Unit, share: () -> Unit): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val ink = NoctwadeTheme.text(context)
        val accent = NoctwadeTheme.accent(context)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun paragraph(lines: List<String>) {
            val joined = lines.joinToString("\n").trim()
            if (joined.isEmpty()) return
            content.addView(
                TextView(context).apply {
                    text = joined
                    textSize = 13f
                    setLineSpacing(0f, 1.18f)
                    setTextColor(ink)
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) },
            )
        }

        fun codeBox(lines: List<String>, edge: Boolean = false): TextView {
            val trimmed = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            val indent = trimmed.filter { it.isNotBlank() }.minOfOrNull { it.length - it.trimStart().length } ?: 0
            return TextView(context).apply {
                text = trimmed.joinToString("\n") { it.drop(minOf(indent, it.length)) }
                textSize = 11.5f
                typeface = Typeface.MONOSPACE
                setTextColor(ink)
                setTextIsSelectable(true)
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(ColorUtils.setAlphaComponent(ink, 14))
                        if (edge) setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 110))
                    }
            }
        }

        /** Text and code in order: indented lines are code; with [wholeCode] everything but "(note)" lines is. */
        fun body(lines: List<String>, wholeCode: Boolean) {
            val group = ArrayList<String>()
            var code = false
            fun flush() {
                if (group.isEmpty()) return
                if (code) content.addView(codeBox(group), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) else paragraph(group.toList())
                group.clear()
            }
            for (line in lines) {
                val isCode = line.startsWith("    ") || (wholeCode && line.isNotBlank() && !line.startsWith("("))
                if (line.isNotBlank() && isCode != code) {
                    flush()
                    code = isCode
                }
                group += line
            }
            flush()
        }

        // The first heading is the guide's own title, which the dialog already shows.
        for ((index, section) in sections(guide).withIndex()) {
            val title = index == 0
            if (title) continue
            content.addView(
                TextView(context).apply {
                    text = if (title) section.title.substringAfter(" · ", section.title) else section.title
                    textSize = if (title) 13f else 12f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    letterSpacing = .06f
                    setTextColor(accent)
                    setPadding(0, dp(if (title) 0 else 16), 0, dp(6))
                },
                LinearLayout.LayoutParams(-1, -2),
            )
            val upper = section.title.uppercase(Locale.ROOT)
            val isPrompt = upper.contains("PROMPT") || upper.contains("ПРОМТ")
            val isExample = !isPrompt && (upper.contains("EXAMPLE") || upper.contains("ПРИМЕР"))
            val dashes = section.lines.indices.filter { section.lines[it].startsWith("-----") }
            if (isPrompt && dashes.size >= 2) {
                body(section.lines.subList(0, dashes[0]), false)
                content.addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(codeBox(section.lines.subList(dashes[0] + 1, dashes[1]), edge = true), LinearLayout.LayoutParams(-1, -2))
                        addView(
                            pill(context, context.getString(R.string.nw_guide_prompt), accent, copyPrompt),
                            LinearLayout.LayoutParams(-2, dp(36)).apply {
                                topMargin = dp(8)
                                gravity = Gravity.END
                            },
                        )
                    },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) },
                )
                body(section.lines.subList(dashes[1] + 1, section.lines.size), false)
            } else body(section.lines, isExample)
        }

        val root = AppearancePickers.compactCard(context)
        root.addView(
            TextView(context).apply {
                setText(R.string.nw_halo_guide_btn)
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ink)
                setPadding(0, 0, 0, dp(10))
            }
        )
        root.addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, (context.resources.displayMetrics.heightPixels * .54f).toInt()))
        val row = LinearLayout(context).apply { setPadding(0, dp(12), 0, dp(4)) }
        fun action(label: Int, click: () -> Unit) =
            row.addView(
                pill(context, context.getString(label), accent, click),
                LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                },
            )
        action(R.string.nw_guide_copy, copyAll)
        action(R.string.nw_guide_prompt, copyPrompt)
        action(R.string.nw_guide_share, share)
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    /** A rounded one-line button that never wraps its label. */
    private fun pill(context: Context, label: String, color: Int, click: () -> Unit) =
        TextView(context).apply {
            val density = context.resources.displayMetrics.density
            text = label
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(color)
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            background =
                GradientDrawable().apply {
                    cornerRadius = 100f * density
                    setColor(ColorUtils.setAlphaComponent(color, 34))
                }
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
}
