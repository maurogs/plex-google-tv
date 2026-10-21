package dev.plexlite.tv.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class MenuEntry(
    val label: String,
    val value: String? = null,
    val checked: Boolean = false,
    val action: () -> Unit,
)

class OverlayMenu(private val context: Context, root: FrameLayout) {

    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xF01C1E23.toInt())
        }
        visibility = View.GONE
    }
    private val titleView = TextView(context).apply {
        setTextColor(0xFF8A8F99.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(24), dp(4), dp(24), dp(8))
    }
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val stack = ArrayDeque<Pair<String, () -> List<MenuEntry>>>()

    var onClosed: () -> Unit = {}

    val isVisible: Boolean get() = panel.visibility == View.VISIBLE

    init {
        panel.addView(titleView)
        panel.addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(rows)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(
            panel,
            FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
                .apply { setMargins(0, dp(48), dp(32), dp(110)) },
        )
    }

    fun open(title: String, entries: () -> List<MenuEntry>) {
        stack.clear()
        push(title, entries)
    }

    fun push(title: String, entries: () -> List<MenuEntry>) {
        stack.addLast(title to entries)
        render()
    }

    fun back(): Boolean {
        if (!isVisible) return false
        stack.removeLastOrNull()
        if (stack.isEmpty()) close() else render()
        return true
    }

    fun close() {
        stack.clear()
        panel.visibility = View.GONE
        onClosed()
    }

    private fun render() {
        val (title, entries) = stack.lastOrNull() ?: return close()
        titleView.text = title.uppercase()
        rows.removeAllViews()
        val list = entries()
        val views = list.map { entry -> row(entry).also(rows::addView) }
        views.firstOrNull()?.let { it.nextFocusUpId = it.id }
        views.lastOrNull()?.let { it.nextFocusDownId = it.id }
        panel.visibility = View.VISIBLE
        (views.getOrNull(list.indexOfFirst { it.checked }) ?: views.firstOrNull())?.requestFocus()
    }

    private fun row(entry: MenuEntry): View = LinearLayout(context).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(14), dp(24), dp(14))
        isFocusable = true
        isClickable = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(0x55E5A00D))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        addView(TextView(context).apply {
            text = if (entry.checked) "✓" else ""
            setTextColor(0xFFE5A00D.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = entry.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            })
            if (!entry.value.isNullOrEmpty()) {
                addView(TextView(context).apply {
                    text = entry.value
                    setTextColor(0xFFB8BCC6.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener { entry.action() }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
