package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.max

/** Full-screen round-aware scaffold that layers scroll content, time, indicator and edge action. */
class ScreenScaffoldView : ViewGroup, ScreenStageProvider {
    private var provider: ScrollMetricsProvider? = null
    private var content: View? = null
    private var timeText: TimeTextView? = null
    private var scrollIndicator: ScrollIndicatorView? = null
    private var edgeButton: EdgeButtonView? = null
    private var contentLeft = -1
    private var contentTop = -1
    private var contentRight = -1
    private var contentBottom = -1
    private var edgeSpacing = -1
    private var stage = ScreenStage.New
    private var lastScroll = 0
    private var lastMetricChange = 0L
    private var idleRunnable: Runnable? = null
    private var fadeAnimator: ValueAnimator? = null
    private var listening = false

    private val metricsListener = object : WearScrollMetricsListener {
        override fun onScrollMetricsChanged(provider: ScrollMetricsProvider) = updateFromMetrics(provider)
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Screen content"
    }

    fun setScrollMetricsProvider(provider: ScrollMetricsProvider?) {
        if (this.provider === provider) return
        stopObserving()
        this.provider = provider
        lastScroll = provider?.scrollOffsetPx ?: 0
        stage = ScreenStage.New
        if (isAttachedToWindow) startObserving()
        updateFromMetrics(provider)
    }

    fun setTimeTextView(view: TimeTextView?) {
        replaceChild(timeText, view)
        timeText = view
        requestLayout()
    }

    fun setScrollIndicatorView(view: ScrollIndicatorView?) {
        replaceChild(scrollIndicator, view)
        scrollIndicator = view
        provider?.let { view?.setState(it.toIndicatorState()) }
        updateOverlayForStage(false)
        requestLayout()
    }

    fun setEdgeButton(button: EdgeButtonView?) {
        replaceChild(edgeButton, button)
        edgeButton = button
        requestLayout()
    }

    fun setContent(view: View?) {
        replaceChild(content, view)
        content = view
        requestLayout()
    }

    fun getContentView(): View? = content

    fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        contentLeft = max(0, left)
        contentTop = max(0, top)
        contentRight = max(0, right)
        contentBottom = max(0, bottom)
        requestLayout()
    }

    fun setEdgeButtonSpacing(spacingPx: Int) {
        edgeSpacing = max(dp(3f), spacingPx)
        requestLayout()
    }

    override fun getScreenStage(): ScreenStage = stage

    private fun replaceChild(old: View?, replacement: View?) {
        if (old === replacement) return
        old?.let { removeView(it) }
        replacement?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            addView(it)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
        val left = resolvedLeftInset()
        val right = resolvedRightInset()
        val top = resolvedTopInset()
        val requestedBottom = resolvedBottomInset()
        val edge = edgeButton
        edge?.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.AT_MOST)
        )
        val edgeHeight = edge?.intrinsicMaximumHeightPx() ?: 0
        val edgeGap = if (edge == null) 0 else resolvedEdgeGap()
        content?.measure(
            MeasureSpec.makeMeasureSpec(max(0, measuredWidth - left - right), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(max(0, measuredHeight - top - requestedBottom - edgeHeight - edgeGap), MeasureSpec.EXACTLY)
        )
        timeText?.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(30f), MeasureSpec.EXACTLY)
        )
        scrollIndicator?.measure(
            MeasureSpec.makeMeasureSpec(dp(26f), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(50f), MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val edge = edgeButton
        val edgeHeight = edge?.intrinsicMaximumHeightPx() ?: 0
        val edgeGap = if (edge == null) 0 else resolvedEdgeGap()
        content?.layout(
            resolvedLeftInset(), resolvedTopInset(),
            width - resolvedRightInset(), height - resolvedBottomInset() - edgeHeight - edgeGap
        )
        timeText?.let {
            val x = (width - it.measuredWidth) / 2
            val y = dp(4f)
            it.layout(x, y, x + it.measuredWidth, y + it.measuredHeight)
        }
        scrollIndicator?.let {
            val inset = dp(2f)
            val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) inset else width - inset - it.measuredWidth
            val y = (height - it.measuredHeight) / 2
            it.layout(x, y, x + it.measuredWidth, y + it.measuredHeight)
        }
        edge?.let {
            val y = height - it.measuredHeight
            it.layout(0, y, width, y + it.measuredHeight)
        }
    }

    private fun startObserving() {
        val active = provider ?: return
        if (!listening) {
            active.addListener(metricsListener)
            listening = true
        }
        active.refresh()
    }

    private fun stopObserving() {
        if (listening) provider?.removeListener(metricsListener)
        listening = false
    }

    private fun updateFromMetrics(active: ScrollMetricsProvider?) {
        active ?: return
        scrollIndicator?.setState(active.toIndicatorState())
        val current = active.scrollOffsetPx
        if (current == lastScroll) return
        val delta = current - lastScroll
        lastScroll = current
        lastMetricChange = android.os.SystemClock.uptimeMillis()
        stage = ScreenStage.Scrolling
        edgeButton?.updateRevealFromScroll(-delta)
        updateOverlayForStage(true)
        idleRunnable?.let { removeCallbacks(it) }
        idleRunnable = Runnable {
            if (android.os.SystemClock.uptimeMillis() - lastMetricChange >= 2_000L) {
                stage = ScreenStage.Idle
                edgeButton?.let { button -> button.settleRevealHeightPx(button.currentRevealHeightPx()) }
                updateOverlayForStage(true)
            }
        }.also { postDelayed(it, 2_000L) }
    }

    private fun updateOverlayForStage(animate: Boolean) {
        fadeAnimator?.cancel()
        val time = timeText
        val indicator = scrollIndicator
        // Vendored ScrollAway (material3/ScrollAway.kt): maxScrollOut 36dp drives
        // progress 0->1, lerp 1f->0.5f for scale/alpha, translation -24dp, threshold
        // 0.55 for re-show, twinned animators — DurationShort4 200ms
        // EasingStandard (0.2,0,0,1) for progress, DurationMedium1 250ms for alpha
        // (Standard on show, StandardDecelerate 0,0,0,1 on hide).
        when (stage) {
            ScreenStage.New -> {
                time?.pivotX = (time.width / 2f).takeIf { it.isFinite() } ?: 0f
                time?.pivotY = 0f
                time?.translationY = 0f
                time?.scaleX = 1f
                time?.scaleY = 1f
                time?.alpha = 1f
                indicator?.alpha = 0f
            }
            ScreenStage.Scrolling -> {
                time?.pivotX = time.width / 2f
                time?.pivotY = 0f
                time?.translationY = -dp(24f).toFloat()
                // Vendored minMotionOut 1f -> maxMotionOut 0.5f
                time?.scaleX = .5f
                time?.scaleY = .5f
                time?.alpha = .5f
                indicator?.alpha = 1f
            }
            ScreenStage.Idle -> {
                if (!animate) {
                    time?.pivotX = (time.width / 2f).takeIf { it.isFinite() } ?: 0f
                    time?.pivotY = 0f
                    time?.translationY = 0f
                    time?.scaleX = 1f
                    time?.scaleY = 1f
                    time?.alpha = 1f
                    indicator?.alpha = 0f
                } else {
                    // Split to match vendored twin-animator split: progress/scale/y
                    // at 200ms EasingStandard, alpha at 250ms (Standard on show).
                    val easedStandard = PathInterpolator(0.2f, 0f, 0f, 1f)
                    fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 250L
                        interpolator = easedStandard
                        addUpdateListener { animation ->
                            val fraction = animation.animatedValue as Float
                            time?.pivotX = time.width / 2f
                            time?.pivotY = 0f
                            // Single eased fraction represents vendored
                            // progressAnimatable (200ms) and alphaAnimatable
                            // (250ms) coalesced; 0.2,0,0,1 is the common
                            // Standard easing for the re-show path.
                            time?.translationY = -dp(24f) * (1f - fraction)
                            time?.scaleX = .5f + .5f * fraction
                            time?.scaleY = .5f + .5f * fraction
                            time?.alpha = .5f + .5f * fraction
                            indicator?.alpha = 1f - fraction
                        }
                        start()
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startObserving()
        updateOverlayForStage(false)
    }

    override fun onDetachedFromWindow() {
        stopObserving()
        idleRunnable?.let { removeCallbacks(it) }
        idleRunnable = null
        fadeAnimator?.cancel()
        fadeAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("super", super.onSaveInstanceState())
            putInt("stage", stage.ordinal)
            putInt("scroll", lastScroll)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            @Suppress("DEPRECATION")
            super.onRestoreInstanceState(state.getParcelable("super"))
            stage = ScreenStage.values()[state.getInt("stage", 0).coerceIn(0, ScreenStage.values().lastIndex)]
            lastScroll = state.getInt("scroll", 0)
        } else super.onRestoreInstanceState(state)
    }

    private fun resolvedLeftInset(): Int = if (contentLeft >= 0) contentLeft else (width * .052f).toInt()
    private fun resolvedRightInset(): Int = if (contentRight >= 0) contentRight else (width * .052f).toInt()
    private fun resolvedTopInset(): Int = if (contentTop >= 0) contentTop else (minOf(width, height) * .10f).toInt()
    private fun resolvedBottomInset(): Int = if (contentBottom >= 0) contentBottom else (minOf(width, height) * .10f).toInt()
    private fun resolvedEdgeGap(): Int = (if (edgeSpacing >= 0) edgeSpacing else dp(16f)).coerceAtLeast(dp(3f))
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
