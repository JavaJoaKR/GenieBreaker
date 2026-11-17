package kr.javajoa.geniebreaker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/** Shared TV palette and focus treatment used by the shell, shelves and device rows. */
internal object TvDesign {
    const val canvas = 0xff0d1420.toInt()
    const val surface = 0xff172233.toInt()
    const val raised = 0xff1b293b.toInt()
    const val border = 0xff304057.toInt()
    const val focus = 0xff2d4665.toInt()
    const val blue = 0xff9dc5ff.toInt()
    const val ink = 0xfff3f6fc.toInt()
    const val muted = 0xffaab7c9.toInt()
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value + .5f).toInt()
    fun shape(context: Context, color: Int, radius: Int = 14, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat(); setStroke(dp(context, 2), stroke)
    }
    fun selector(context: Context, nav: Boolean = false, primary: Boolean = false) = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), shape(context, surface, 12, border))
        addState(intArrayOf(android.R.attr.state_focused), shape(context, if (primary) 0xffa4c9ff.toInt() else focus, 10, ink))
        addState(intArrayOf(android.R.attr.state_pressed), shape(context, focus, 12, blue))
        addState(intArrayOf(android.R.attr.state_selected), shape(context, 0xff193554.toInt(), 12, if (nav) border else blue))
        addState(intArrayOf(), shape(context, if (primary) blue else if (nav) Color.TRANSPARENT else raised, 10, Color.TRANSPARENT))
    }
}
