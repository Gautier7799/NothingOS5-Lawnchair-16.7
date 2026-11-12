package dev.widgetloft

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * Resolves WidgetLoft's surface, text and accent colours.
 *
 * On Android 12+ the framework exposes the wallpaper-derived Monet palette as `system_*` colour
 * resources, so the board follows Material You automatically. Where those are absent the helper
 * falls back to the theme's own attributes and, last, to fixed light/dark values. Every call is
 * cheap and safe to repeat, so callers do not cache colours across configuration changes.
 */
class WidgetLoftTheme(private val context: Context) {

    val isNight: Boolean
        get() = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** The board background: a low-chroma neutral surface. */
    fun surface(): Int = systemColor(
        lightName = "system_neutral1_50",
        darkName = "system_neutral1_900",
        fallbackAttr = android.R.attr.colorBackground,
        hardFallback = if (isNight) 0xFF121317.toInt() else 0xFFF7F8FB.toInt(),
    )

    /** A card background, slightly raised from the surface. */
    fun cardSurface(): Int = systemColor(
        lightName = "system_neutral1_100",
        darkName = "system_neutral1_800",
        fallbackAttr = android.R.attr.colorBackground,
        hardFallback = if (isNight) 0xFF1E1F25.toInt() else 0xFFFFFFFF.toInt(),
    )

    fun onSurface(): Int = themeColor(android.R.attr.textColorPrimary, if (isNight) 0xFFECEDEF.toInt() else 0xFF1A1C1E.toInt())

    fun onSurfaceVariant(): Int = themeColor(android.R.attr.textColorSecondary, if (isNight) 0xFFB6B8BD.toInt() else 0xFF5A5D63.toInt())

    /** The accent used for the resize handle and interactive labels. */
    fun accent(): Int {
        val palette = systemColorOrNull(
            if (isNight) "system_accent1_200" else "system_accent1_600",
        )
        if (palette != null) return palette
        return themeColor(android.R.attr.colorAccent, 0xFF4A6BFF.toInt())
    }

    /** A translucent accent for the card outline. */
    fun accentOutline(): Int {
        val a = accent()
        return (0x50 shl 24) or (a and 0x00FFFFFF)
    }

    fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeDp > 0f) {
                setStroke(dp(strokeDp), strokeColor)
            }
        }

    private fun systemColor(
        lightName: String,
        darkName: String,
        fallbackAttr: Int,
        hardFallback: Int,
    ): Int {
        val name = if (isNight) darkName else lightName
        systemColorOrNull(name)?.let { return it }
        return themeColor(fallbackAttr, hardFallback)
    }

    private fun systemColorOrNull(name: String): Int? {
        val id = context.resources.getIdentifier(name, "color", "android")
        if (id == 0) return null
        return runCatching { context.resources.getColor(id, context.theme) }.getOrNull()
    }

    private fun themeColor(attribute: Int, hardFallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attribute, value, true)) return hardFallback
        return when {
            value.resourceId != 0 ->
                runCatching { context.resources.getColor(value.resourceId, context.theme) }
                    .getOrDefault(hardFallback)
            value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                value.type <= TypedValue.TYPE_LAST_COLOR_INT -> value.data
            else -> hardFallback
        }
    }

    fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
