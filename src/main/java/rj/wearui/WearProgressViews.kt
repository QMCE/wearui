package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/** Colors shared by native determinate and indeterminate indicators. */
data class WearProgressColors(
    val indicatorColor: Int = Color.rgb(233, 221, 255),
    val trackColor: Int = Color.rgb(51, 46, 60),
    val disabledIndicatorColor: Int = Color.rgb(148, 143, 154)
)

abstract class WearProgressBase(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : View(context, attrs, defStyleAttr) {
    var progress = 0f
        protected set
    protected var rangeMin = 0f
    protected var rangeMax = 1f
    var colors = WearProgressColors()
        set(value) { field = value; invalidate() }
    var indeterminate = false
        set(value) {
            if (field == value) return
            field = value
            updateIndeterminateAnimation()
            invalidate()
        }
    protected var indeterminatePhase = 0f
    private var indeterminateAnimator: ValueAnimator? = null
    private var progressAnimator: ValueAnimator? = null
    var strokeWidthDp: Int = ProgressIndicatorTokens.CircularLargeStrokeWidthDp.toInt()
        set(value) { field = value.coerceAtLeast(1); invalidate() }
    /** Stroke width used for the moving segment of the indeterminate animation. */
    var indeterminateStrokeWidthDp: Int = ProgressIndicatorTokens.CircularIndeterminateStrokeWidthDp.toInt()
        set(value) { field = value.coerceAtLeast(1); invalidate() }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun setProgress(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(rangeMin, rangeMax)
        progressAnimator?.cancel()
        val shouldSnap = !animate || !isAttachedToWindow || !isEnabled || isReducedMotionRequested(context)
        val spec = WearMotionSpec(WearMotionDurations.Long500, WearMotionEasings.StandardDecelerate).withReducedMotion(shouldSnap)
        if (spec.durationMillis == 0L || progress == target) { progress = target; invalidate(); return }
        progressAnimator = ValueAnimator.ofFloat(progress, target).apply { duration = spec.durationMillis; interpolator = spec.interpolator; addUpdateListener { progress = it.animatedValue as Float; invalidate() }; start() }
    }
    fun setProgressRange(min: Float, max: Float) { rangeMin = min; rangeMax = if (max > min) max else min + 1f; progress = progress.coerceIn(rangeMin, rangeMax); invalidate() }
    fun isIndeterminate(): Boolean = indeterminate
    protected fun fraction(): Float = if (rangeMax <= rangeMin) 0f else ((progress - rangeMin) / (rangeMax - rangeMin)).coerceIn(0f, 1f)
    protected fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun updateIndeterminateAnimation() {
        indeterminateAnimator?.cancel(); indeterminateAnimator = null
        if (!indeterminate || !isAttachedToWindow || visibility != VISIBLE || isReducedMotionRequested(context)) return
        indeterminateAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator(); addUpdateListener { indeterminatePhase = it.animatedValue as Float; invalidate() }; start() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateIndeterminateAnimation() }
    override fun onDetachedFromWindow() { indeterminateAnimator?.cancel(); progressAnimator?.cancel(); indeterminateAnimator = null; progressAnimator = null; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateIndeterminateAnimation() }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.ProgressBar::class.java.name
        info.isEnabled = isEnabled
        if (!indeterminate) info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, rangeMin, rangeMax, progress)
        if (Build.VERSION.SDK_INT >= 30) info.stateDescription = if (indeterminate) "Loading" else "${(fraction() * 100f).toInt()} percent"
    }
    override fun onSaveInstanceState(): Parcelable { val state = WearControlSavedState(super.onSaveInstanceState()); state.value = progress; state.checked = indeterminate; return state }
    override fun onRestoreInstanceState(state: Parcelable?) { if (state is WearControlSavedState) { super.onRestoreInstanceState(state.superState); progress = state.value.coerceIn(rangeMin, rangeMax); indeterminate = state.checked; updateIndeterminateAnimation(); invalidate() } else super.onRestoreInstanceState(state) }
}

/** Circular progress indicator that derives all geometry from its measured bounds. */
open class CircularProgressIndicatorView : WearProgressBase {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    var startAngle: Float = -90f
        set(value) { field = value; invalidate() }
    var sweepDegrees: Float = 360f
        set(value) { field = value.coerceIn(1f, 360f); invalidate() }
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { minimumWidth = dp(36f).toInt(); minimumHeight = dp(36f).toInt() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = dp(strokeWidthDp.toFloat())
        val inset = stroke / 2f + max(paddingLeft, paddingTop)
        val bounds = RectF(inset, inset, width - inset, height - inset)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        paint.strokeWidth = stroke
        paint.color = colors.trackColor
        canvas.drawArc(bounds, startAngle, sweepDegrees, false, paint)
        paint.color = if (isEnabled) colors.indicatorColor else colors.disabledIndicatorColor
        if (indeterminate) {
            paint.strokeWidth = dp(indeterminateStrokeWidthDp.toFloat())
            val phase = (indeterminatePhase * (sweepDegrees + 70f)) % (sweepDegrees + 70f)
            val segmentStart = startAngle + phase - 70f
            canvas.drawArc(bounds, segmentStart, min(70f, sweepDegrees), false, paint)
        } else {
            paint.strokeWidth = stroke
            val radius = bounds.width() / 2f
            if (radius > 0f) {
                val endGapDegrees = (stroke / 3f / radius * 180f / Math.PI).toFloat()
                canvas.drawArc(bounds, startAngle, (sweepDegrees * fraction() - endGapDegrees).coerceAtLeast(0f), false, paint)
            } else canvas.drawArc(bounds, startAngle, sweepDegrees * fraction(), false, paint)
        }
    }
}

/** Circular progress subdivided into discrete arcs with a configurable inter-segment gap. */
class SegmentedCircularProgressIndicatorView : CircularProgressIndicatorView {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    var segmentCount: Int = 5
        set(value) { field = value.coerceAtLeast(1); invalidate() }
    var gapDegrees: Float = 4f // vendored calculateRecommendedGapSize(stroke/3) -> ~4deg for 12dp/80px radius, dynamic below
        set(value) { field = value.coerceAtLeast(0f); invalidate() }
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    override fun onDraw(canvas: Canvas) {
        val stroke = dp(strokeWidthDp.toFloat())
        val inset = stroke / 2f + max(paddingLeft, paddingTop)
        val bounds = RectF(inset, inset, width - inset, height - inset)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        // Vendored gapSweep = asin((gap+stroke)/width)*360/PI, gap=stroke/3
        val radius = bounds.width() / 2f
        val dynamicGap = if (radius > 0f) (stroke / 3f / radius * 180f / Math.PI.toFloat()) else gapDegrees
        val effectiveGap = if (gapDegrees == 4f) dynamicGap else gapDegrees
        val anglePer = sweepDegrees / segmentCount
        val segmentSweep = (anglePer - effectiveGap).coerceAtLeast(0f)
        paint.strokeWidth = stroke
        for (index in 0 until segmentCount) {
            val start = startAngle + index * anglePer + effectiveGap / 2f
            paint.color = colors.trackColor
            canvas.drawArc(bounds, start, segmentSweep, false, paint)
            val complete = if (isIndeterminate()) ((indeterminatePhase * segmentCount).toInt() % segmentCount) >= index else fraction() * segmentCount > index
            if (complete) {
                paint.color = if (isEnabled) colors.indicatorColor else colors.disabledIndicatorColor
                canvas.drawArc(bounds, start, segmentSweep, false, paint)
            }
        }
    }
}

/** Rounded horizontal native progress indicator. */
open class LinearProgressIndicatorView : WearProgressBase {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    var trackHeightDp: Int = 12 // large 12dp (small 8dp per CircularProgressIndicatorDefaults)
        set(value) { field = value.coerceAtLeast(1); requestLayout(); invalidate() }
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { minimumHeight = dp(24f).toInt(); minimumWidth = dp(48f).toInt() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = dp(trackHeightDp.toFloat())
        val top = (height - h) / 2f
        val rect = RectF(paddingLeft.toFloat(), top, (width - paddingRight).toFloat(), top + h)
        paint.color = colors.trackColor
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        val fraction = if (isIndeterminate()) .25f else fraction()
        val phase = if (isIndeterminate()) (indeterminatePhase * 1.25f) % 1.25f - .25f else 0f
        val activeStart = if (isIndeterminate()) rect.left + rect.width() * phase else rect.left
        val activeEnd = (activeStart + rect.width() * fraction).coerceIn(rect.left, rect.right)
        if (activeEnd > activeStart) {
            paint.color = if (isEnabled) colors.indicatorColor else colors.disabledIndicatorColor
            canvas.drawRoundRect(RectF(activeStart.coerceAtLeast(rect.left), rect.top, activeEnd, rect.bottom), h / 2f, h / 2f, paint)
        }
    }
}

/** Discrete vertical or horizontal level indicator used for volume and status levels. */
class LevelIndicatorView : WearProgressBase {
    // Vendored LevelIndicator: curved arc via IndicatorImpl, sweep 120deg, stroke 4dp, edgePadding 4dp, FractionPositionStateAdapter
    init { colors = WearProgressColors(indicatorColor = Color.rgb(186, 195, 255), trackColor = Color.argb(77, 51, 46, 60)) } // track 0.3 alpha per LevelIndicatorTokens
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var sweepAngle: Float = 120f // vendored LevelIndicatorDefaults.SweepAngle 120
    var levelStrokeWidthDp: Float = 4f // LevelIndicatorDefaults.StrokeWidth 4dp
    var levelCount: Int = 5
        set(value) { field = value.coerceAtLeast(1); invalidate() }
    var vertical: Boolean = true
        set(value) { field = value; requestLayout(); invalidate() }
    var gapDp: Int = 3
        set(value) { field = value.coerceAtLeast(0); invalidate() }
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { minimumWidth = dp(16f).toInt(); minimumHeight = dp(48f).toInt() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = dp(gapDp.toFloat())
        val count = levelCount
        val available = if (vertical) height - paddingTop - paddingBottom else width - paddingLeft - paddingRight
        val length = ((available - gap * (count - 1)) / count).coerceAtLeast(1f)
        val activeLevels = if (isIndeterminate()) ((indeterminatePhase * count).toInt() % (count + 1)) else kotlin.math.ceil(fraction() * count).toInt()
        for (index in 0 until count) {
            paint.color = if (index < activeLevels) { if (isEnabled) colors.indicatorColor else colors.disabledIndicatorColor } else colors.trackColor
            val rect = if (vertical) {
                val y = height - paddingBottom - (index + 1) * length - index * gap
                RectF(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y + length)
            } else {
                val x = paddingLeft + index * (length + gap)
                RectF(x, paddingTop.toFloat(), x + length, (height - paddingBottom).toFloat())
            }
            canvas.drawRoundRect(rect, min(rect.width(), rect.height()) / 2f, min(rect.width(), rect.height()) / 2f, paint)
        }
    }
}

/** Arc progress indicator - vendored from ArcProgressIndicator.kt, gap = stroke/3, indeterminate head/tail 360 sweep */
class ArcProgressIndicatorView : View {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    var startAngle: Float = 65f
    var endAngle: Float = 65f + 280f // fullSweep 280 default (65 to 345)
    var strokeWidthDp: Float = 3f
        set(v) { field = v; invalidate() }
    var gapSizeDp: Float = 0f // 0 uses stroke/3
    var colors = WearProgressColors()
    var indeterminate = true
    private var phase = 0f
    private var animator: ValueAnimator? = null
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        minimumWidth = dp(48f).toInt(); minimumHeight = dp(48f).toInt()
    }
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (indeterminate) startAnim()
    }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    private fun startAnim() {
        animator?.cancel()
        if (isReducedMotionRequested(context)) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = dp(strokeWidthDp)
        val gap = if (gapSizeDp == 0f) stroke / 3f else dp(gapSizeDp) // vendored calculateRecommendedGapSize(stroke/3)
        val inset = stroke / 2f + max(paddingLeft, paddingTop)
        val bounds = RectF(inset, inset, width - inset, height - inset)
        if (bounds.width() <= 0) return
        paint.strokeWidth = stroke
        val fullSweep = ((endAngle - startAngle) % 360 + 360) % 360
        if (fullSweep <= 0) return
        val gapSweep = Math.toDegrees(Math.asin((gap + stroke).toDouble() / bounds.width().toDouble())).toFloat() * 2f
        // Track
        paint.color = colors.trackColor
        canvas.drawArc(bounds, startAngle, fullSweep, false, paint)
        // Indicator head/tail
        paint.color = colors.indicatorColor
        val head = phase
        val tail = (phase + 0.5f) % 1f
        val arcSweep = (tail - head + 1f) % 1f * fullSweep
        val arcStart = startAngle + head * fullSweep
        canvas.drawArc(bounds, arcStart, (arcSweep - gapSweep).coerceAtLeast(0f), false, paint)
    }
}
