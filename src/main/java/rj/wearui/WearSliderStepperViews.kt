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
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val ACCESSIBILITY_INCREMENT = 0x01010001
private const val ACCESSIBILITY_DECREMENT = 0x01010002

/** A native Wear slider with tap, drag, keyboard, DPAD, and accessibility semantics. */
open class SliderView : View {
    private var value = 0f
    private var rangeMin = 0f
    private var rangeMax = 1f
    private var stepCount = 0
    private var valueListener: WearValueChangeListener? = null
    private var finishedListener: (() -> Unit)? = null
    private var valueAnimator: ValueAnimator? = null
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var lastPointerId = -1
    private var reducedMotion = false
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    var activeColor: Int = Color.rgb(208, 188, 255) // Primary SelectedBar
        set(value) { field = value; invalidate() }
    var inactiveColor: Int = Color.argb(77, 0, 0, 0) // Background #000000 0.3 per SliderTokens UnselectedBarColor
        set(value) { field = value; invalidate() }
    var thumbColor: Int = Color.rgb(246, 237, 255)
        set(value) { field = value; invalidate() }
    var trackWidthDp: Int = SliderTokens.TrackWidthDp.toInt()
        set(value) { field = value.coerceAtLeast(1); invalidate() }
    var thumbRadiusDp: Int = SliderTokens.ThumbRadiusDp.toInt()
        set(value) { field = value.coerceAtLeast(3); invalidate() }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumHeight = dp(48f).toInt()
    }

    fun setValue(value: Float, fromUser: Boolean = false, animate: Boolean = true) {
        val target = quantize(value.coerceIn(rangeMin, rangeMax))
        valueAnimator?.cancel()
        if (!animate || reducedMotion || !isAttachedToWindow) {
            this.value = target
            invalidate()
        } else if (abs(target - this.value) > .0001f) {
            val spec = WearMotionSpec(WearMotionDurations.Short100, WearMotionEasings.Standard).withReducedMotion(reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context))
            if (spec.durationMillis == 0L) {
                this.value = target
                invalidate()
            } else {
                valueAnimator = ValueAnimator.ofFloat(this.value, target).apply {
                    duration = spec.durationMillis
                    interpolator = spec.interpolator
                    addUpdateListener { this@SliderView.value = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
        }
        if (fromUser) valueListener?.onValueChanged(this, target, true)
        announceForAccessibility(valueString(target))
    }

    fun getValue(): Float = value
    fun setValueRange(min: Float, max: Float) {
        rangeMin = min
        rangeMax = if (max > min) max else min + 1f
        setValue(value.coerceIn(rangeMin, rangeMax), false, false)
    }
    fun setSteps(steps: Int) { stepCount = steps.coerceAtLeast(0); setValue(value, false, false) }
    fun setOnValueChangeListener(listener: WearValueChangeListener?) { valueListener = listener }
    fun setOnValueChangeFinishedListener(listener: (() -> Unit)?) { finishedListener = listener }
    fun setReducedMotionEnabled(enabled: Boolean) { reducedMotion = enabled; if (enabled) valueAnimator?.cancel() }

    private fun quantize(input: Float): Float {
        if (stepCount <= 0) return input
        val step = (rangeMax - rangeMin) / (stepCount + 1).toFloat()
        return (rangeMin + ((input - rangeMin) / step).toInt().coerceAtLeast(0) * step).coerceIn(rangeMin, rangeMax)
    }
    private fun fraction(): Float = if (rangeMax <= rangeMin) 0f else ((value - rangeMin) / (rangeMax - rangeMin)).coerceIn(0f, 1f)
    private fun valueForX(x: Float): Float {
        val start = dp(thumbRadiusDp.toFloat())
        val end = width - start
        if (end <= start) return rangeMin
        return rangeMin + ((x - start) / (end - start)).coerceIn(0f, 1f) * (rangeMax - rangeMin)
    }
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun valueString(number: Float): String = if (number == number.toInt().toFloat()) number.toInt().toString() else "%.2f".format(java.util.Locale.getDefault(), number)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val radius = dp(thumbRadiusDp.toFloat())
        val start = radius
        val end = width - radius
        if (end <= start) return
        val position = start + (end - start) * fraction()
        trackPaint.strokeWidth = dp(trackWidthDp.toFloat())
        trackPaint.color = inactiveColor
        canvas.drawLine(start, centerY, end, centerY, trackPaint)
        activePaint.strokeWidth = dp(trackWidthDp.toFloat())
        activePaint.color = activeColor
        canvas.drawLine(start, centerY, position, centerY, activePaint)
        thumbPaint.color = thumbColor
        canvas.drawCircle(position, centerY, if (dragging) radius * 1.18f else radius, thumbPaint)
        if (isFocused) {
            thumbPaint.style = Paint.Style.STROKE
            thumbPaint.strokeWidth = dp(2f)
            thumbPaint.color = activeColor
            canvas.drawCircle(position, centerY, radius * 1.55f, thumbPaint)
            thumbPaint.style = Paint.Style.FILL
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            dragging = false
            parent?.requestDisallowInterceptTouchEvent(false)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastPointerId = event.getPointerId(0); dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (lastPointerId < 0) return true
                val dx = event.x - downX
                if (!dragging && abs(dx) < dp(4f) && abs(event.y - downY) < dp(10f)) return true
                dragging = true
                setValue(valueForX(event.x), true, false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) setValue(valueForX(event.x), true, true)
                dragging = false
                lastPointerId = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                finishedListener?.invoke()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false; lastPointerId = -1; parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled) return false
        val delta = if (stepCount > 0) (rangeMax - rangeMin) / (stepCount + 1f) else (rangeMax - rangeMin) / 20f
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN -> { setValue(value - delta, true, true); true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> { setValue(value + delta, true, true); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (!isEnabled) return false
        val delta = if (stepCount > 0) (rangeMax - rangeMin) / (stepCount + 1f) else (rangeMax - rangeMin) / 20f
        return when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, ACCESSIBILITY_INCREMENT -> { setValue(value + delta, true, true); true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, ACCESSIBILITY_DECREMENT -> { setValue(value - delta, true, true); true }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        info.isEnabled = isEnabled
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, rangeMin, rangeMax, value)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACCESSIBILITY_INCREMENT, "Increase"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACCESSIBILITY_DECREMENT, "Decrease"))
        if (Build.VERSION.SDK_INT >= 30) info.stateDescription = valueString(value)
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = WearControlSavedState(super.onSaveInstanceState()); state.value = value; return state
    }
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is WearControlSavedState) { super.onRestoreInstanceState(state.superState); value = state.value.coerceIn(rangeMin, rangeMax); invalidate() } else super.onRestoreInstanceState(state)
    }
    override fun onDetachedFromWindow() { valueAnimator?.cancel(); valueAnimator = null; super.onDetachedFromWindow() }
}

/** Slider variant sized for dense inline list rows. */
class InlineSliderView : SliderView {
    // Vendored InlineSlider is 40dp minHeight vs 48dp, track 3dp vs 4dp, thumb 7dp vs 8dp - density-scaled dense row
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        trackWidthDp = 3 // Inline 3dp vs 4dp per Slider inline variant
        thumbRadiusDp = 7 // Inline 7dp vs 8dp
        minimumHeight = dpInline(40f).toInt()
    }
    private fun dpInline(value: Float): Float = value * resources.displayMetrics.density
}

/** A compact native stepper with accessible decrement/increment targets. */
class StepperView : FrameLayout {
    private val minusButton: TextButtonView
    private val plusButton: TextButtonView
    private val valueText: WearTextView
    private var current = 0f
    private var minValue = 0f
    private var maxValue = 10f
    private var step = 1f
    private var listener: WearValueChangeListener? = null
    private var finished: (() -> Unit)? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minusButton = TextButtonView(context).apply { setText("−"); contentDescription = "Decrease" }
        plusButton = TextButtonView(context).apply { setText("+"); contentDescription = "Increase" }
        valueText = WearTextView(context).apply { gravity = android.view.Gravity.CENTER; setText("0") }
        // Vendored Stepper uses 52dp IconButton (Compact 48 but Stepper is Button 52) + TitleLarge for value
        addView(minusButton, LayoutParams(dp(52f), dp(52f), android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL))
        addView(valueText, LayoutParams(dp(64f), dp(52f), android.view.Gravity.CENTER))
        addView(plusButton, LayoutParams(dp(52f), dp(52f), android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL))
        valueText.setTypographyRole(WearTypographyRole.TitleLarge)
        minusButton.setOnClickListener { change(-step, true) }
        plusButton.setOnClickListener { change(step, true) }
        minimumWidth = dp(160f).toInt(); minimumHeight = dp(48f).toInt()
        updateValueText()
    }

    fun setValue(value: Float, fromUser: Boolean = false, animate: Boolean = true) { change(value - current, fromUser, animate) }
    fun getValue(): Float = current
    fun setValueRange(min: Float, max: Float) { minValue = min; maxValue = if (max > min) max else min + 1f; setValue(current.coerceIn(minValue, maxValue), false, false) }
    fun setSteps(steps: Int) { step = if (steps > 0) (maxValue - minValue) / steps else 1f }
    fun setStepSize(size: Float) { step = size.coerceAtLeast(.0001f) }
    fun setOnValueChangeListener(listener: WearValueChangeListener?) { this.listener = listener }
    fun setOnValueChangeFinishedListener(listener: (() -> Unit)?) { finished = listener }
    private fun change(delta: Float, fromUser: Boolean, animate: Boolean = true) {
        val target = (current + delta).coerceIn(minValue, maxValue)
        if (target == current) return
        current = target
        updateValueText()
        if (fromUser) { listener?.onValueChanged(this, current, true); finished?.invoke() }
        announceForAccessibility(current.toString())
    }
    private fun updateValueText() { valueText.text = if (current == current.toInt().toFloat()) current.toInt().toString() else current.toString() }
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(info); info.className = "android.widget.NumberPicker"; info.isEnabled = isEnabled; info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, minValue, maxValue, current); info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACCESSIBILITY_INCREMENT, "Increase")); info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACCESSIBILITY_DECREMENT, "Decrease")); if (Build.VERSION.SDK_INT >= 30) info.stateDescription = current.toString() }
    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean = when (action) { ACCESSIBILITY_INCREMENT, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { change(step, true); true }; ACCESSIBILITY_DECREMENT, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { change(-step, true); true }; else -> super.performAccessibilityAction(action, arguments) }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) { KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN -> { change(-step, true); true }; KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> { change(step, true); true }; else -> super.onKeyDown(keyCode, event) }
    override fun onSaveInstanceState(): Parcelable { val state = WearControlSavedState(super.onSaveInstanceState()); state.value = current; return state }
    override fun onRestoreInstanceState(state: Parcelable?) { if (state is WearControlSavedState) { super.onRestoreInstanceState(state.superState); current = state.value.coerceIn(minValue, maxValue); updateValueText() } else super.onRestoreInstanceState(state) }
}
