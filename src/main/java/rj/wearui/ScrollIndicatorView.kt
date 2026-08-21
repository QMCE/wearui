package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Visible and disabled colors for [ScrollIndicatorView]. */
data class WearScrollIndicatorColors(
    val indicatorColor: Int = Color.WHITE,
    val disabledIndicatorColor: Int = Color.argb(97, 255, 255, 255),
    val trackColor: Int = 0xFF333333.toInt()
)

/**
 * Android port of Wear Compose Material3's curved three-segment scroll indicator. The top and
 * bottom arcs are track; the middle arc is the visible viewport. Segment boundaries move with
 * scroll position, so this is not a thumb centered on a progress value.
 */
class ScrollIndicatorView : View {
    private var state: IndicatorState? = null
    private var reverseDirection = false
    private var colors: WearScrollIndicatorColors? = null
    private var positionSpec: WearMotionSpec = WearMotionSpec.StandardDecelerate
    private var snapToPosition = false
    private var reducedMotion = false
    private var position = 0f
    private var sizeFraction = 0f
    private var receivedRealState = false
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = null
        setWillNotDraw(false)
        alpha = 0f
    }

    fun setState(state: IndicatorState) {
        this.state = state
        val maxScroll = (state.contentExtentPx - state.viewportExtentPx).coerceAtLeast(0)
        val real = maxScroll > 0 || state.scrollOffsetPx != 0
        val nextPosition = if (state.positionFractionOverride.isFinite()) {
            state.positionFractionOverride
        } else if (maxScroll == 0) 0f else state.scrollOffsetPx.toFloat() / maxScroll
        val nextSize = when {
            state.sizeFractionOverride.isFinite() -> state.sizeFractionOverride
            state.contentExtentPx <= 0 -> MIN_SIZE_FRACTION
            else -> (state.viewportExtentPx.toFloat() / state.contentExtentPx)
                .coerceIn(MIN_SIZE_FRACTION, MAX_SIZE_FRACTION)
        }
        val shouldSnap = snapToPosition || !receivedRealState || !real ||
            reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
        receivedRealState = receivedRealState || real
        if (shouldSnap) {
            cancelAnimation()
            position = nextPosition.coerceIn(0f, 1f)
            sizeFraction = nextSize
            invalidate()
        } else {
            animateTo(nextPosition.coerceIn(0f, 1f), nextSize)
        }
        alpha = if (real) 1f else 0f
    }

    fun setReverseDirection(reverse: Boolean) {
        if (reverseDirection != reverse) {
            reverseDirection = reverse
            invalidate()
        }
    }

    fun setColors(colors: WearScrollIndicatorColors) {
        this.colors = colors
        invalidate()
    }

    fun setPositionAnimationSpec(spec: WearMotionSpec) {
        positionSpec = spec
    }

    fun setSnapToPosition(snap: Boolean) {
        snapToPosition = snap
        if (snap) {
            cancelAnimation()
            invalidate()
        }
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) cancelAnimation()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenWidthDp = resources.configuration.screenWidthDp.coerceAtLeast(1)
        val indicatorWidthDp = if (screenWidthDp >= LARGE_SCREEN_WIDTH_DP) 6 else 5
        val paddingDp = 2
        val indicatorHeightDp = 50
        val radiusDp = screenWidthDp / 2f - paddingDp - indicatorWidthDp / 2f
        val halfHeight = indicatorHeightDp / 2f
        val projectionDp = radiusDp -
            sqrt((radiusDp * radiusDp - halfHeight * halfHeight).coerceAtLeast(0f)) +
            paddingDp + indicatorWidthDp
        val density = resources.displayMetrics.density
        val width = ceilPx(projectionDp * density)
        val height = ceilPx((indicatorHeightDp + indicatorWidthDp) * density)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || alpha <= 0f) return

        val configuration = resources.configuration
        val density = resources.displayMetrics.density
        val screenWidthDp = configuration.screenWidthDp.coerceAtLeast(1)
        val indicatorWidthDp = if (screenWidthDp >= LARGE_SCREEN_WIDTH_DP) 6 else 5
        val paddingHorizontalPx = dp(2f)
        val indicatorWidthPx = dp(indicatorWidthDp.toFloat())
        val gapHeightPx = dp(3f)
        val diameter = screenWidthDp * density
        val usableRadius = diameter / 2f - paddingHorizontalPx
        val arcRadius = usableRadius - indicatorWidthPx / 2f
        val sweepDegrees = pixelsToDegrees(dp(50f), usableRadius)
        val gapSweep = pixelsToDegrees(indicatorWidthPx + gapHeightPx, usableRadius)

        val indicatorOnTheRight = layoutDirection != LAYOUT_DIRECTION_RTL
        val visualPosition = if (reverseDirection) 1f - position else position
        val indicatorStart = visualPosition * (1f - sizeFraction)
        val startAngleOffset = if (indicatorOnTheRight) 0f else 180f
        val startTopArc = startAngleOffset - sweepDegrees / 2f
        val sweepTopArc = sweepDegrees * indicatorStart
        val startMidArc = startTopArc + sweepTopArc
        val sweepMidArc = sweepDegrees * sizeFraction
        val startBottomArc = startMidArc + sweepMidArc
        val sweepBottomArc = sweepDegrees * (1f - sizeFraction - indicatorStart)

        val arcSize = RectF(
            0f,
            0f,
            diameter - 2f * paddingHorizontalPx - indicatorWidthPx,
            diameter - 2f * paddingHorizontalPx - indicatorWidthPx
        )
        arcSize.offsetTo(
            indicatorWidthPx / 2f + if (indicatorOnTheRight) width - diameter + paddingHorizontalPx else paddingHorizontalPx,
            (height - diameter) / 2f + paddingHorizontalPx + indicatorWidthPx / 2f
        )

        val indicatorColor = resolvedColors().first
        val trackColor = resolvedColors().second
        drawSegment(canvas, arcSize, arcRadius, startTopArc, sweepTopArc, trackColor, indicatorWidthPx, gapSweep)
        drawSegment(canvas, arcSize, arcRadius, startMidArc, sweepMidArc, indicatorColor, indicatorWidthPx, gapSweep)
        drawSegment(canvas, arcSize, arcRadius, startBottomArc, sweepBottomArc, trackColor, indicatorWidthPx, gapSweep)
    }

    private fun drawSegment(
        canvas: Canvas,
        oval: RectF,
        radius: Float,
        startAngle: Float,
        sweep: Float,
        color: Int,
        indicatorWidthPx: Float,
        gapSweep: Float
    ) {
        paint.color = color
        paint.alpha = if (isEnabled) 255 else 97
        paint.strokeWidth = indicatorWidthPx
        if (sweep <= gapSweep) {
            // Compose collapses a segment smaller than its gap into a gradually shrinking dot.
            val fraction = if (gapSweep <= 0f) 1f else (sweep / gapSweep).coerceIn(0f, 1f)
            val dotRadius = indicatorWidthPx / 2f * fraction
            if (dotRadius <= 0f) return
            val angle = Math.toRadians(((startAngle + sweep / 2f).toDouble()))
            val centerX = oval.centerX() + radius * cos(angle).toFloat()
            val centerY = oval.centerY() + radius * sin(angle).toFloat()
            paint.alpha = (paint.alpha * fraction).toInt().coerceIn(0, 255)
            canvas.drawCircle(centerX, centerY, dotRadius, paint)
        } else {
            canvas.drawArc(oval, startAngle + gapSweep / 2f, sweep - gapSweep, false, paint)
        }
    }

    private fun animateTo(nextPosition: Float, nextSize: Float) {
        val startPosition = position
        val startSize = sizeFraction
        val spec = positionSpec.withReducedMotion(reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context))
        if (spec.durationMillis <= 0L) {
            position = nextPosition
            sizeFraction = nextSize
            invalidate()
            return
        }
        cancelAnimation()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = spec.durationMillis
            interpolator = spec.interpolator
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                position = startPosition + (nextPosition - startPosition) * fraction
                sizeFraction = startSize + (nextSize - startSize) * fraction
                invalidate()
            }
            start()
        }
    }

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    private fun resolvedColors(): Pair<Int, Int> {
        val custom = colors
        val indicator = custom?.indicatorColor ?: Color.WHITE
        val track = custom?.trackColor ?: 0xFF333333.toInt()
        return indicator to track
    }

    private fun pixelsToDegrees(heightPx: Float, radiusPx: Float): Float =
        2f * Math.toDegrees(asin((heightPx / 2f / radiusPx.coerceAtLeast(1f)).toDouble()).toDouble()).toFloat()

    private fun ceilPx(value: Float): Int = (value + 0.5f).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) cancelAnimation()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("super", super.onSaveInstanceState())
            putFloat("position", position)
            putFloat("size", sizeFraction)
            putBoolean("reverse", reverseDirection)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("super"))
            position = state.getFloat("position", 0f)
            sizeFraction = state.getFloat("size", 0f)
            reverseDirection = state.getBoolean("reverse", false)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        const val LARGE_SCREEN_WIDTH_DP = 225
        const val MIN_SIZE_FRACTION = 0.30f
        const val MAX_SIZE_FRACTION = 0.70f
    }
}
