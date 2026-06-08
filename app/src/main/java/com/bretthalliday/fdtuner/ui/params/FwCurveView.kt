package com.bretthalliday.fdtuner.ui.params

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.bretthalliday.fdtuner.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Editable field-weakening curve. X = curve-point index (RPM breakpoints), Y = ratio % (0–100).
 *
 * Read-only with respect to the controller: dragging only PREVIEWS. The hosting fragment decides
 * when to commit (on drag-release via [onCommit]) and routes the write through the confirmed
 * single-param path. A cyan dashed marker shows LimitSpeed (FW start).
 */
class FwCurveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val points = mutableListOf<Float>()
    private var limitFraction = -1f          // 0..1 across the plot; <0 = hidden
    var selectedIndex: Int = -1
        private set
    private var dragging = false

    /** Called when a point is selected (tap or drag start). */
    var onSelect: ((index: Int) -> Unit)? = null
    /** Called continuously while dragging — preview only, no write. */
    var onPreview: ((index: Int, value: Int) -> Unit)? = null
    /** Called on drag release — the fragment should confirm + write this one point. */
    var onCommit: ((index: Int, value: Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.divider); strokeWidth = dp(1f)
    }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (ContextCompat.getColor(context, R.color.pink_primary) and 0x00FFFFFF) or 0x22000000
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pink_primary); style = Paint.Style.STROKE
        strokeWidth = dp(2.5f); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pink_primary)
    }
    private val dotSelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pink_primary); style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cyan_accent); style = Paint.Style.STROKE
        strokeWidth = dp(1.5f); pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f)
    }
    private val limitText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cyan_accent); textSize = dp(10f)
    }

    private val padL get() = dp(8f)
    private val padR get() = dp(8f)
    private val padT get() = dp(14f)
    private val padB get() = dp(16f)
    private val plotW get() = width - padL - padR
    private val plotH get() = height - padT - padB

    private fun xAt(i: Int): Float =
        if (points.size <= 1) padL else padL + i * plotW / (points.size - 1)
    private fun yAt(v: Float): Float = padT + (100f - v) / 100f * plotH
    private fun valueAt(py: Float): Int =
        (((1f - (py - padT) / plotH) * 100f)).roundToInt().coerceIn(0, 100)

    fun setData(values: List<Float>, limitFrac: Float) {
        points.clear(); points.addAll(values)
        limitFraction = limitFrac
        if (selectedIndex >= points.size) selectedIndex = -1
        invalidate()
    }

    fun setSelected(index: Int) { selectedIndex = index; invalidate() }

    /** True while the user is actively dragging a point (host should not push live data). */
    fun isEditing(): Boolean = dragging

    /** Update one point's value from a numeric entry (preview, no write). */
    fun setPointValue(index: Int, value: Int) {
        if (index in points.indices) { points[index] = value.toFloat().coerceIn(0f, 100f); invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty() || width == 0) return

        var g = 0
        while (g <= 100) { val yy = yAt(g.toFloat()); canvas.drawLine(padL, yy, width - padR, yy, gridPaint); g += 25 }

        val area = Path().apply {
            moveTo(padL, yAt(0f))
            points.forEachIndexed { i, v -> lineTo(xAt(i), yAt(v)) }
            lineTo(width - padR, yAt(0f)); close()
        }
        canvas.drawPath(area, areaPaint)

        if (limitFraction in 0f..1f) {
            val lx = padL + limitFraction * plotW
            canvas.drawLine(lx, padT, lx, height - padB, limitPaint)
            canvas.drawText("LimitSpeed", lx + dp(4f), padT + dp(10f), limitText)
        }

        val line = Path()
        points.forEachIndexed { i, v -> if (i == 0) line.moveTo(xAt(i), yAt(v)) else line.lineTo(xAt(i), yAt(v)) }
        canvas.drawPath(line, linePaint)

        points.forEachIndexed { i, v ->
            val cx = xAt(i); val cy = yAt(v)
            canvas.drawCircle(cx, cy, dp(6f), if (i == selectedIndex) dotSelPaint else dotPaint)
            canvas.drawCircle(cx, cy, dp(6f), dotStroke)
        }
    }

    private fun nearestIndex(px: Float): Int {
        var best = -1; var bestD = Float.MAX_VALUE
        points.indices.forEach { i ->
            val d = abs(xAt(i) - px); if (d < bestD) { bestD = d; best = i }
        }
        return if (bestD <= dp(28f)) best else -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val i = nearestIndex(event.x)
                if (i >= 0) {
                    selectedIndex = i; dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onSelect?.invoke(i); invalidate(); return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> if (dragging && selectedIndex >= 0) {
                val v = valueAt(event.y); points[selectedIndex] = v.toFloat()
                onPreview?.invoke(selectedIndex, v); invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (selectedIndex >= 0) onCommit?.invoke(selectedIndex, points[selectedIndex].roundToInt())
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
