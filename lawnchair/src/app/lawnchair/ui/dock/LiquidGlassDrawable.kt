package app.lawnchair.ui.dock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Draws the iOS-style "Liquid Glass" dock surface: a near-transparent vertical glass tint plus a
 * specular highlight that is bright along the top/left edge and fades toward the bottom.
 *
 * Kept free of launcher types so the capsule code stays testable.
 */
class LiquidGlassDrawable(
    private val fillColor: Int,
    private val cornerRadiusPx: Float,
    private val borderWidthPx: Float,
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidthPx
    }
    private val rect = RectF()

    /** Top/bottom opacity of the glass tint, relative to [fillColor]'s own alpha. */
    private val tintTopScale = 1.0f
    private val tintBottomScale = 0.55f

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat().coerceAtLeast(top + 1f)
        val left = bounds.left.toFloat()
        val right = bounds.right.toFloat().coerceAtLeast(left + 1f)
        val baseAlpha = Color.alpha(fillColor)

        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            withScaledAlpha(fillColor, baseAlpha, tintTopScale),
            withScaledAlpha(fillColor, baseAlpha, tintBottomScale),
            Shader.TileMode.CLAMP,
        )
        // The sheen is the refraction: stronger at the top, almost gone at the bottom.
        sheenPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            Color.argb(56, 255, 255, 255),
            Color.argb(14, 255, 255, 255),
            Shader.TileMode.CLAMP,
        )
        // Specular highlight along the top-left edge, fading across the surface (diagonal).
        borderPaint.shader = LinearGradient(
            left,
            top,
            right,
            bottom,
            intArrayOf(
                Color.argb(166, 255, 255, 255),
                Color.argb(38, 255, 255, 255),
                Color.argb(102, 255, 255, 255),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val radius = cornerRadiusPx.coerceAtMost(rect.height() / 2f)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, sheenPaint)
        val inset = borderWidthPx / 2f
        rect.inset(inset, inset)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        rect.inset(-inset, -inset)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        sheenPaint.alpha = alpha
        borderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable", ReplaceWith("android.graphics.PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    private fun withScaledAlpha(color: Int, baseAlpha: Int, scale: Float): Int = Color.argb(
        (baseAlpha * scale).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}
