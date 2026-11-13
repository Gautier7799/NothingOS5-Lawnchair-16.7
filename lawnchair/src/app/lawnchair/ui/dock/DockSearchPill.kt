package app.lawnchair.ui.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.android.launcher3.R

/**
 * Small translucent "Search" pill that floats just above the iOS-style dock, mirroring the
 * search affordance of the iOS 26 dock. Purely visual; the click action is wired by the host.
 */
class DockSearchPill @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val label = context.getString(R.string.label_search)
    private val iconSize = dp(10f)
    private val sidePad = dp(12f)
    private val gap = dp(5f)
    private val pillHeight = dp(26f)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4D1C2026
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, 0.5f * density)
        color = 0x33FFFFFF
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        strokeCap = Paint.Cap.ROUND
        color = 0xE6FFFFFF.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics,
        )
    }
    private val rect = RectF()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val content = sidePad * 2 + iconSize + gap + textPaint.measureText(label)
        setMeasuredDimension(
            resolveSize(content.toInt(), widthSpec),
            resolveSize(pillHeight.toInt(), heightSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val half = strokePaint.strokeWidth / 2f
        rect.set(half, half, w - half, h - half)
        val r = rect.height() / 2f
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(rect, r, r, strokePaint)

        val textW = textPaint.measureText(label)
        val contentW = iconSize + gap + textW
        val startX = (w - contentW) / 2f
        val cy = h / 2f

        // Magnifier glyph.
        val circleR = iconSize * 0.36f
        val cx = startX + circleR
        val cyIcon = cy - iconSize * 0.08f
        canvas.drawCircle(cx, cyIcon, circleR, iconPaint)
        val off = circleR * 0.72f
        canvas.drawLine(
            cx + off, cyIcon + off,
            startX + iconSize * 0.95f, cyIcon + iconSize * 0.60f + off * 0.2f,
            iconPaint,
        )

        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, startX + iconSize + gap, baseline, textPaint)
    }
}
