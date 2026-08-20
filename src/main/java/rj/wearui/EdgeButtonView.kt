package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Colors used by [EdgeButtonView]. */
data class WearEdgeButtonColors(
    val containerColor: Int = WearUiTheme.from(null).colors.primary,
    val contentColor: Int = WearUiTheme.from(null).colors.onPrimary,
    val outlineColor: Int = Color.TRANSPARENT
)

/** A bottom-edge action container with the round-screen top-circle/lower-ellipse silhouette. */
class EdgeButtonView : ViewGroup {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shape = Path()
    private var size = EdgeButtonSize.Medium
    private var colors = WearEdgeButtonColors()
    private var content: View? = null
    private var clickListener: OnClickListener? = null
    private var revealHeightPx = Int.MAX_VALUE
    private var revealAnimator: ValueAnimator? = null
    private var intrinsicHeightPx = 0
    private var lastScrollAt = 0L
    private var pendingRevealPx = Int.MAX_VALUE
    private var reducedMotion = false

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) {
            revealAnimator?.cancel()
            revealAnimator = null
        }
    }

    fun isReducedMotionEnabled(): Boolean = reducedMotion

    /** Cubic-bezier(0.25,0,0.75,1) fade-in for the reveal alpha ramps (common curve with the edge silhouette). */
    private val fadeEase = PathInterpolator(0.25f, 0f, 0.75f, 1f)

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setWillNotDraw(false)
        clipChildren = false
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Edge action"
        border.strokeWidth = dp(1f).toFloat()
    }

    fun setSize(size: EdgeButtonSize) {
        if (this.size == size) return
        this.size = size
        requestLayout()
    }

    /** Applies a direct scroll update. Such updates deliberately never animate. */
    fun setRevealHeightPx(height: Int) {
        revealAnimator?.cancel()
        pendingRevealPx = height.coerceAtLeast(0)
        revealHeightPx = pendingRevealPx
        applyVisualState()
    }

    fun setColors(colors: WearEdgeButtonColors) {
        this.colors = colors
        applyVisualState()
    }

    fun setContent(view: View?) {
        if (content === view) return
        content?.let { removeView(it) }
        content = view
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view)
            view.isDuplicateParentStateEnabled = true
        }
        requestLayout()
    }

    fun setEdgeButtonOnClickListener(listener: OnClickListener?) {
        clickListener = listener
        isClickable = listener != null
        isFocusable = listener != null
    }

    /**
     * The scaffold calls this only after it has stopped receiving active scroll samples.  It avoids
     * a reveal animation unless the idle change exceeds the documented 16dp threshold.
     */
    internal fun settleRevealHeightPx(height: Int) {
        val next = height.coerceAtLeast(0)
        if (!isEnabled || abs(next - revealHeightPx) <= dp(16f)) {
            setRevealHeightPx(next)
            return
        }
        revealAnimator?.cancel()
        pendingRevealPx = next
        val shouldSnap = reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
        val spec = WearMotionSpec(
            durationMillis = WearMotionDurations.Short200,
            interpolator = WearMotionEasings.StandardDecelerate
        ).withReducedMotion(shouldSnap)
        if (spec.durationMillis == 0L) {
            revealHeightPx = next
            applyVisualState()
            return
        }
        revealAnimator = ValueAnimator.ofInt(revealHeightPx, next).apply {
            duration = spec.durationMillis
            interpolator = spec.interpolator
            addUpdateListener { animation ->
                revealHeightPx = animation.animatedValue as Int
                applyVisualState()
            }
            start()
        }
    }

    /** Converts an upward scroll delta into a direct reveal update. */
    internal fun updateRevealFromScroll(deltaPx: Int) {
        if (deltaPx == 0) return
        lastScrollAt = android.os.SystemClock.uptimeMillis()
        val full = intrinsicHeightPx.coerceAtLeast(dp(4f))
        setRevealHeightPx((revealHeightPx + deltaPx).coerceIn(0, full))
    }

    internal fun intrinsicMaximumHeightPx(): Int = intrinsicHeightPx
    internal fun currentRevealHeightPx(): Int = revealHeightPx

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val maxButtonHeight = sizeHeightPx()
        val outer = dp(3f)
        content?.measure(
            MeasureSpec.makeMeasureSpec(max(0, availableWidth - dp(24f)), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(maxButtonHeight, MeasureSpec.AT_MOST)
        )
        // The edge button always reserves its semantic maximum, even if a short content view is
        // supplied; this prevents the content scaffold from relaying out while it collapses.
        intrinsicHeightPx = maxButtonHeight + outer * 2
        val desiredWidth = max(suggestedMinimumWidth, (content?.measuredWidth ?: 0) + dp(32f))
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(intrinsicHeightPx, heightMeasureSpec))
        if (revealHeightPx == Int.MAX_VALUE) revealHeightPx = intrinsicHeightPx
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = content ?: return
        val childLeft = (width - child.measuredWidth) / 2
        val childTop = height - dp(3f) - child.measuredHeight
        child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || revealHeightPx <= 0) return
        val fullHeight = intrinsicHeightPx.coerceAtLeast(height)
        val visible = revealHeightPx.coerceIn(0, fullHeight)
        canvas.save()
        canvas.clipRect(0, height - visible, width, height)
        buildShape()
        fill.color = withAlpha(colors.containerColor, containerAlpha())
        canvas.drawPath(shape, fill)
        if (Color.alpha(colors.outlineColor) != 0) {
            border.color = withAlpha(colors.outlineColor, containerAlpha())
            canvas.drawPath(shape, border)
        }
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val child = content
        val prior = child?.alpha
        if (child != null) child.alpha = contentAlpha()
        super.dispatchDraw(canvas)
        if (child != null && prior != null) child.alpha = prior
    }

    private fun buildShape() {
        val outer = dp(3f).toFloat()
        val cx = width / 2f
        val edgeRadius = max(0f, cx - outer)
        val normalHeight = dp(58f) + (sizeHeightPx() - dp(46f)) * 1.42f
        val top = max(0f, height - normalHeight)
        shape.reset()
        // The two cubic shoulders are a circle at the top and an ellipse at the bottom. The shape
        // is intentionally not a rounded rectangle; the wider lower boundary reads as edge-bound.
        shape.moveTo(outer, height.toFloat())
        shape.cubicTo(outer, top + edgeRadius * .20f, cx - edgeRadius, top, cx, top)
        shape.cubicTo(cx + edgeRadius, top, width - outer, top + edgeRadius * .20f, width - outer, height.toFloat())
        shape.close()
    }

    private fun applyVisualState() {
        if (!isEnabled) {
            alpha = WearUiDisabledAlpha.Content
        } else {
            alpha = 1f
        }
        invalidate()
    }

    private fun containerAlpha(): Float {
        val range = ((revealHeightPx - dp(4f)).toFloat() / max(1, dp(30f) - dp(4f))).coerceIn(0f, 1f)
        val eased = fadeEase.getInterpolation(range)
        return eased * if (isEnabled) 1f else WearUiDisabledAlpha.Container / WearUiDisabledAlpha.Content
    }

    private fun contentAlpha(): Float {
        val range = ((revealHeightPx - dp(30f)).toFloat() / max(1, dp(38f) - dp(30f))).coerceIn(0f, 1f)
        val eased = fadeEase.getInterpolation(range)
        return eased * if (isEnabled) 1f else WearUiDisabledAlpha.Content
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        // Keep all inherited state transitions visible without introducing a second state model.
        val press = if (isPressed || isFocused || isActivated) .92f else 1f
        scaleX = press
        scaleY = press
        applyVisualState()
    }

    override fun performClick(): Boolean {
        super.performClick()
        clickListener?.onClick(this)
        return clickListener != null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (isPressed) performClick()
                isPressed = false
                true
            }
            MotionEvent.ACTION_CANCEL -> { isPressed = false; true }
            MotionEvent.ACTION_DOWN -> { isPressed = true; true }
            else -> true
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        content?.isEnabled = enabled
        if (!enabled) revealAnimator?.cancel()
        applyVisualState()
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.isClickable = isClickable
        info.isEnabled = isEnabled
        if (info.contentDescription == null) info.contentDescription = contentDescription
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("super", super.onSaveInstanceState())
            putInt("edge.size", size.ordinal)
            putInt("edge.reveal", revealHeightPx)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            @Suppress("DEPRECATION")
            super.onRestoreInstanceState(state.getParcelable("super"))
            size = EdgeButtonSize.values()[state.getInt("edge.size", size.ordinal).coerceIn(0, EdgeButtonSize.values().lastIndex)]
            revealHeightPx = state.getInt("edge.reveal", revealHeightPx)
            pendingRevealPx = revealHeightPx
            applyVisualState()
        } else super.onRestoreInstanceState(state)
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        revealAnimator = null
        super.onDetachedFromWindow()
    }

    private fun sizeHeightPx(): Int = dp(when (size) {
        EdgeButtonSize.ExtraSmall -> 46f
        EdgeButtonSize.Small -> 56f
        EdgeButtonSize.Medium -> 70f
        EdgeButtonSize.Large -> 96f
    })

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color) * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
    )
}
