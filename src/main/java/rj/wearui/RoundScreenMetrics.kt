package rj.wearui

import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.view.View
import kotlin.math.sqrt

/** Measured geometry for a rectangular or circular wearable host. */
data class RoundScreenMetrics(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val isRound: Boolean,
    val width: Int,
    val height: Int,
    val safeHorizontalInsetLeft: Int,
    val safeHorizontalInsetRight: Int
) {
    /** Conservative horizontal inset that keeps a central rectangle inside a round display. */
    val safeHorizontalInset: Int get() = maxOf(safeHorizontalInsetLeft, safeHorizontalInsetRight)
    val safeHorizontalInsets: IntArray get() = intArrayOf(safeHorizontalInsetLeft, safeHorizontalInsetRight)
    val measuredWidth: Int get() = width
    val measuredHeight: Int get() = height

    fun contains(x: Float, y: Float): Boolean {
        if (!isRound) return x >= 0f && x <= width && y >= 0f && y <= height
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }

    fun circleBounds(): RectF = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

    /** Horizontal bounds of the circular display at a measured y coordinate. */
    fun horizontalBoundsAt(y: Float): ClosedFloatingPointRange<Float> {
        if (!isRound || radius <= 0f) return 0f..width.toFloat()
        val dy = (y - centerY).coerceIn(-radius, radius)
        val half = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        return (centerX - half)..(centerX + half)
    }

    companion object {
        @JvmStatic
        fun from(view: View): RoundScreenMetrics {
            val width = (if (view.measuredWidth > 0) view.measuredWidth else view.width).coerceAtLeast(0)
            val height = (if (view.measuredHeight > 0) view.measuredHeight else view.height).coerceAtLeast(0)
            val configuration = view.resources.configuration
            val isRound = if (Build.VERSION.SDK_INT >= 23) {
                configuration.isScreenRound
            } else {
                (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH &&
                    width == height && width > 0
            }
            return fromBounds(width, height, isRound)
        }

        /** Useful for hosts that know their physical shape before attachment. */
        @JvmStatic
        fun fromBounds(width: Int, height: Int, isRound: Boolean): RoundScreenMetrics {
            val safeWidth = width.coerceAtLeast(0)
            val safeHeight = height.coerceAtLeast(0)
            val radius = if (isRound) minOf(safeWidth, safeHeight) / 2f else 0f
            // The central square whose corners are exactly inside the circle uses r / sqrt(2).
            val inset = if (isRound) (radius * (1f - 1f / sqrt(2f))).toInt().coerceAtLeast(0) else 0
            return RoundScreenMetrics(
                centerX = safeWidth / 2f,
                centerY = safeHeight / 2f,
                radius = radius,
                isRound = isRound,
                width = safeWidth,
                height = safeHeight,
                safeHorizontalInsetLeft = inset,
                safeHorizontalInsetRight = inset
            )
        }
    }
}
