package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Parcelable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Visible and disabled colors for [ScrollIndicatorView]. */
data class WearScrollIndicatorColors(
    val indicatorColor: Int = Color.WHITE,
    val disabledIndicatorColor: Int = Color.argb(97, 255, 255, 255)
)

/** Curved three-part scroll position indicator for a round screen edge. */
class ScrollIndicatorView : View {
    private var state: IndicatorState? = null
    private var reverseDirection = false
    private var colors: WearScrollIndicatorColors? = null
    private var positionSpec: rj.wearui.WearMotionSpec? = null
    private var snapToPosition = false
    private var reducedMotion = false
    private var position = 0.5f
    private var targetPosition = 0.5f
    private var thumbLength = 0.40f
    private var overscroll = 0f
    private var receivedRealState = false
    private var lastRoundedPosition = Int.MIN_VALUE
    private var positionAnimator: ValueAnimator? = null
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
        val parsed = parseState(state)
        val real = parsed.first
        val next = parsed.second
        thumbLength = parsed.third
        overscroll = parsed.fourth
        val shouldSnap = snapToPosition || !receivedRealState || !real
            || reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
        receivedRealState = receivedRealState || real
        targetPosition = next
        if (shouldSnap) {
            positionAnimator?.cancel()
            position = next
            updatePositionIfPixelChanged()
        } else {
            animatePosition(next)
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

    fun setPositionAnimationSpec(spec: rj.wearui.WearMotionSpec) {
        positionSpec = spec
    }

    fun setSnapToPosition(snap: Boolean) {
        snapToPosition = snap
        if (snap) {
            positionAnimator?.cancel()
            position = targetPosition
            invalidate()
        }
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) {
            positionAnimator?.cancel()
            position = targetPosition
        }
        invalidate()
    }

    private fun parseState(value: IndicatorState): Quad {
        val max = number(value, "maxValue", "max", "range", "scrollRange", "contentSize", "contentExtentPx")
        val viewport = number(value, "viewportSize", "viewport", "extent", "viewPort", "viewportExtentPx")
        val offset = number(value, "offset", "position", "scrollOffset", "scrollOffsetPx", "value")
        val direct = number(value, "fraction", "positionFraction", "scrollFraction", "progress")
        val over = number(value, "overscroll", "overScroll", "overscrollFraction")
        val real = (max > 0f && (max > viewport || offset != 0f)) || direct in 0f..1f && hasMember(value, "fraction", "positionFraction", "scrollFraction", "progress")
        val raw = if (hasMember(value, "fraction", "positionFraction", "scrollFraction", "progress")) direct else if (max > viewport) offset / (max - viewport) else 0f
        val length = if (max > 0f && viewport > 0f) (viewport / max).coerceIn(0.30f, 0.70f) else 0.40f
        return Quad(real, raw.coerceIn(0f, 1f), length, over.coerceIn(-1f, 1f))
    }

    private class Quad(val first: Boolean, val second: Float, val third: Float, val fourth: Float)

    private fun number(instance: Any, vararg names: String): Float {
        for (name in names) {
            try {
                val field = instance.javaClass.getDeclaredField(name)
                field.isAccessible = true
                val result = field.get(instance)
                if (result is Number) return result.toFloat()
            } catch (_: Throwable) { }
            try {
                val method = instance.javaClass.methods.firstOrNull {
                    it.parameterTypes.isEmpty() && (it.name == "get" + name.replaceFirstChar { c -> c.uppercase() } || it.name == name)
                }
                val result = method?.invoke(instance)
                if (result is Number) return result.toFloat()
            } catch (_: Throwable) { }
        }
        return 0f
    }

    private fun hasMember(instance: Any, vararg names: String): Boolean = names.any { name ->
        try { instance.javaClass.getDeclaredField(name); true } catch (_: Throwable) {
            instance.javaClass.methods.any { it.parameterTypes.isEmpty() && it.name.equals("get" + name.replaceFirstChar { c -> c.uppercase() }, true) }
        }
    }

    private fun animatePosition(next: Float) {
        positionAnimator?.cancel()
        val shouldSnap = reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
        val baseSpec = (positionSpec ?: rj.wearui.WearMotionSpec.StandardDecelerate)
            .withReducedMotion(shouldSnap)
        if (baseSpec.durationMillis == 0L) {
            position = next
            updatePositionIfPixelChanged()
            return
        }
        positionAnimator = ValueAnimator.ofFloat(position, next).apply {
            duration = baseSpec.durationMillis
            interpolator = baseSpec.interpolator
            addUpdateListener {
                position = it.animatedValue as Float
                updatePositionIfPixelChanged()
            }
            start()
        }
    }

    private fun updatePositionIfPixelChanged() {
        val px = (position * resources.displayMetrics.density * 100f).toInt()
        if (px != lastRoundedPosition) {
            lastRoundedPosition = px
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (resources.displayMetrics.widthPixels >= dp(280f)) dp(6f) else dp(5f)
        setMeasuredDimension(resolveSize(dp(24f), widthMeasureSpec), resolveSize(dp(50f), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || alpha <= 0f) return
        val large = resources.displayMetrics.widthPixels >= dp(280f)
        val stroke = dp(if (large) 6f else 5f).toFloat()
        paint.strokeWidth = stroke
        val indicatorColor = color("indicatorColor", Color.WHITE)
        // Inactive (top/bottom) segments use a track/background role; reflect over the provided
        // colors so a trackColor-like field is honored when present, else a dimmed background.
        val trackColor = color("trackColor", Color.argb(97, 255, 255, 255))
        paint.alpha = if (isEnabled) 255 else 97
        val edge = dp(4f).toFloat()
        val sideRight = layoutDirection != LAYOUT_DIRECTION_RTL
        val centerX = if (sideRight) width.toFloat() - edge - stroke / 2f else edge + stroke / 2f
        val diameter = max(width, height).toFloat()
        val radius = max(stroke, diameter / 2f - edge - stroke / 2f)
        val centerY = height / 2f
        val oval = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val fullSweep = 72f
        val visualPosition = if (reverseDirection) 1f - position else position
        val centerAngle = 270f + (visualPosition - .5f) * (fullSweep - 18f)
        val visibleLength = thumbLength * 24f * (if (reducedMotion) 1f else 1f - abs(overscroll).coerceAtMost(.10f))
        val start = centerAngle - visibleLength / 2f
        val gap = 2f
        val segment = max(1f, (visibleLength - gap * 2f) / 3f)
        repeat(3) { index ->
            paint.color = if (index == 1) indicatorColor else trackColor
            canvas.drawArc(oval, start + index * (segment + gap), segment, false, paint)
        }
    }

    private fun color(name: String, fallback: Int): Int {
        val source = colors ?: return fallback
        return try {
            val field = source.javaClass.getDeclaredField(name)
            field.isAccessible = true
            (field.get(source) as? Number)?.toInt() ?: fallback
        } catch (_: Throwable) { fallback }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) positionAnimator?.cancel()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        positionAnimator?.cancel()
        positionAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable("super", super.onSaveInstanceState())
        state.putFloat("position", position)
        state.putBoolean("reverse", reverseDirection)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("super"))
            position = state.getFloat("position", .5f)
            targetPosition = position
            reverseDirection = state.getBoolean("reverse", false)
        } else super.onRestoreInstanceState(state)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
