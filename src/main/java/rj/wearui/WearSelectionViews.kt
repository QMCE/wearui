package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Colors for checkable tracks, thumbs, and marks. */
data class WearControlColors(
    val checkedTrackColor: Int = Color.rgb(208, 188, 255),
    val uncheckedTrackColor: Int = Color.rgb(97, 93, 103),
    val checkedThumbColor: Int = Color.rgb(33, 15, 72),
    val uncheckedThumbColor: Int = Color.rgb(246, 237, 255),
    val disabledTrackColor: Int = Color.rgb(51, 46, 60),
    val disabledThumbColor: Int = Color.rgb(148, 143, 154)
)

enum class WearMarkKind { Checkbox, Radio, Switch }

/** A standalone drawn check/radio/switch mark, shared by native row controls and usable alone. */
open class ToggleMarkView(context: Context, private val kind: WearMarkKind) : View(context) {
    constructor(context: Context) : this(context, WearMarkKind.Switch)

    var checked = false
        private set
    private var checkedListener: ((Boolean) -> Unit)? = null
    var colors = WearControlColors()
    private var visual = 0f
    // Color progress driven separately so selection geometry (fast 1/1400) and colors (slow 1/260)
    // each follow their canonical MotionScheme spring while staying android-only.
    private var colorVisual = 0f
    private var animator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Default size for bare embeds (e.g. as a row-trailing mark): same 42x32dp as the row usage.
        // Without an explicit measure, a plain View measures 0x0 and the mark is invisible.
        val width = dp(42f).toInt()
        val height = dp(32f).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    fun setChecked(value: Boolean, animate: Boolean) {
        val target = if (value) 1f else 0f
        if (checked == value && (!animate || (visual == target && colorVisual == target))) return
        checked = value
        animator?.cancel()
        colorAnimator?.cancel()
        val reduced = !isAttachedToWindow || isReducedMotionRequested(context) || !animate
        if (reduced) {
            visual = target
            colorVisual = target
            invalidate()
            return
        }
        // Selection progress uses MotionScheme.fastEffectsSpec: spring(damping=1, stiffness=1400).
        val fastSpec = MotionScheme.fastEffectsSpec.withReducedMotion(false)
        animator = ValueAnimator.ofFloat(visual, target).apply {
            duration = fastSpec.durationMillis
            interpolator = fastSpec.interpolator
            addUpdateListener { visual = it.animatedValue as Float; invalidate() }
            start()
        }
        // Colors use MotionScheme.slowEffectsSpec: spring(damping=1, stiffness=260).
        val slowSpec = MotionScheme.slowEffectsSpec.withReducedMotion(false)
        colorAnimator = ValueAnimator.ofFloat(colorVisual, target).apply {
            duration = slowSpec.durationMillis
            interpolator = slowSpec.interpolator
            addUpdateListener { colorVisual = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun isChecked(): Boolean = checked

    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        checkedListener = listener
    }

    /** Flips the mark and notifies the listener; external seeding should use setChecked(..) instead. */
    fun toggle() {
        setChecked(!checked, true)
        checkedListener?.invoke(checked)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Role mapping: Switch -> android.widget.Switch (Role.Switch), others -> CompoundButton.
        // Checkbox/Radio could use CheckBox/RadioButton but spec mandates CompoundButton for toggles,
        // so Switch is the only specialization needed for Role.Switch semantics.
        info.className = when (kind) {
            WearMarkKind.Switch -> android.widget.Switch::class.java.name
            WearMarkKind.Checkbox -> android.widget.CheckBox::class.java.name
            WearMarkKind.Radio -> android.widget.RadioButton::class.java.name
        }
        info.isCheckable = true
        info.isChecked = checked
        info.isEnabled = isEnabled
        if (contentDescription.isNullOrEmpty() && Build.VERSION.SDK_INT >= 30) {
            // No titleView here; caller-provided contentDescription is the label.
        }
        if (Build.VERSION.SDK_INT >= 30) info.stateDescription = if (checked) "On" else "Off"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val enabled = isEnabled
        val checkedTrack = if (enabled) colors.checkedTrackColor else colors.disabledTrackColor
        val uncheckedTrack = if (enabled) colors.uncheckedTrackColor else colors.disabledTrackColor
        val checkedThumb = if (enabled) colors.checkedThumbColor else colors.disabledThumbColor
        val uncheckedThumb = if (enabled) colors.uncheckedThumbColor else colors.disabledThumbColor
        val centerY = height / 2f
        when (kind) {
            WearMarkKind.Switch -> {
                // Align to vendored SwitchButton.kt: 32x24 allocation, 32x22 visible track, 2dp border,
                // thumb 6dp off → 9dp on, centers at 6dp from track edge → 23dp.
                // Geometry uses visual (fastEffectsSpec 1/1400), colors use colorVisual (slowEffectsSpec 1/260).
                // Vendored SwitchButton.kt exact geometry:
                // SWITCH_WIDTH 32dp, SWITCH_INNER_HEIGHT 22dp, SWITCH_OUTER_HEIGHT 24dp, TRACK 2dp,
                // THUMB 6dp → 9dp, padding = INNER/2 - thumbRadius.
                val trackW = dp(SwitchTokens.TrackWidthDp)
                val visibleH = dp(SwitchTokens.TrackInnerHeightDp)
                val trackTop = centerY - visibleH / 2f
                val trackLeft = (width - trackW) / 2f
                val trackColor = blend(uncheckedTrack, checkedTrack, colorVisual)
                fillPaint.color = trackColor
                canvas.drawRoundRect(RectF(trackLeft, trackTop, trackLeft + trackW, trackTop + visibleH), visibleH / 2f, visibleH / 2f, fillPaint)
                // Border like Compose Modifier.border inset by half stroke (centered stroke).
                val needBorder = checkedTrack != uncheckedTrack
                if (needBorder) {
                    strokePaint.color = blend(uncheckedTrack, checkedTrack, colorVisual)
                    strokePaint.strokeWidth = dp(2f)
                    val inset = dp(1f)
                    canvas.drawRoundRect(
                        RectF(trackLeft + inset, trackTop + inset, trackLeft + trackW - inset, trackTop + visibleH - inset),
                        (visibleH - dp(2f)) / 2f, (visibleH - dp(2f)) / 2f, strokePaint
                    )
                }
                // Thumb: radius lerp 6→9, position lerp(radius+paddingUnchecked → trackW-radius-paddingChecked)
                val rUnchecked = dp(SwitchTokens.ThumbRadiusUncheckedDp)
                val rChecked = dp(SwitchTokens.ThumbRadiusCheckedDp)
                var r = rUnchecked + (rChecked - rUnchecked) * visual
                // Press shrink: when pressed the thumb contracts slightly (≈15%), matching the
                // expected “only on press it shrinks” behavior while keeping checked/unchecked
                // radii distinct per vendored 6→9 spec. Without this the two states appear to have
                // inconsistent ball sizes with no press feedback.
                if (isPressed) r *= 0.85f
                val paddingUnchecked = dp(SwitchTokens.TrackInnerHeightDp/2f) - rUnchecked // INNER/2 - 6 = 5dp
                val paddingChecked = dp(SwitchTokens.TrackInnerHeightDp/2f) - rChecked // INNER/2 - 9 = 2dp
                val xOff = trackLeft + rUnchecked + paddingUnchecked // 11dp from trackLeft
                val xOn = trackLeft + trackW - rChecked - paddingChecked // 21dp from trackLeft (trackW -11)
                val x = xOff + (xOn - xOff) * visual
                val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
                val drawX = if (rtl) trackLeft + trackW - (x - trackLeft) else x
                fillPaint.color = blend(uncheckedThumb, checkedThumb, colorVisual)
                canvas.drawCircle(drawX, centerY, r, fillPaint)
                // Vendored tick: Path 24dp container, base 2.5 + stick 6, design center 12,12,
                // stroke 2dp, scale eased cubicOut 1-(1-p)^3 around pivot, matching AnimateTick.kt
                if (visual > 0f && colorVisual > 0f) {
                    val tickAlpha = (255 * colorVisual.coerceIn(0f,1f)).toInt()
                    if (tickAlpha > 5) {
                        // Vendored thumbIconColor = Primary (checked) - tick is primary on light thumb
                        val tickColor = if (enabled) checkedTrack else colors.disabledTrackColor
                        strokePaint.color = tickColor
                        strokePaint.alpha = tickAlpha
                        strokePaint.strokeWidth = dp(2f)
                        strokePaint.style = Paint.Style.STROKE
                        strokePaint.strokeCap = Paint.Cap.ROUND
                        strokePaint.strokeJoin = Paint.Join.ROUND
                        val scale = 1f - (1f - visual.coerceIn(0f,1f)).let { it * it * it }
                        if (scale > 0.01f) {
                            val save = canvas.save()
                            // Translate canvas so tick design center (12,12) aligns with thumb center (drawX, centerY)
                            canvas.translate(drawX - dp(12f), centerY - dp(12f))
                            canvas.scale(scale, scale, dp(12f), dp(12f))
                            val path = Path().apply {
                                val baseStartX = dp(7.4f); val baseStartY = dp(12.6f)
                                val baseComp = dp(2.5f)
                                val stickStartX = dp(10.5f); val stickStartY = dp(15.1f)
                                val stickComp = dp(6f)
                                moveTo(baseStartX, baseStartY)
                                lineTo(baseStartX + baseComp, baseStartY + baseComp)
                                moveTo(stickStartX, stickStartY)
                                lineTo(stickStartX + stickComp, stickStartY - stickComp)
                            }
                            canvas.drawPath(path, strokePaint)
                            canvas.restoreToCount(save)
                        }
                        strokePaint.alpha = 255
                        strokePaint.style = Paint.Style.STROKE
                    }
                }
            }
            WearMarkKind.Checkbox -> {
                val side = dp(18f)
                val radius = dp(2f)
                val left = (width - side) / 2f
                val top = (height - side) / 2f
                val rect = RectF(left, top, left + side, top + side)
                fillPaint.color = blend(uncheckedTrack, checkedTrack, colorVisual)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
                if (visual > .05f) {
                    strokePaint.color = checkedThumb
                    strokePaint.strokeWidth = dp(2f)
                    strokePaint.alpha = (255 * colorVisual).toInt()
                    val x1 = left + side * .22f
                    val y1 = top + side * .52f
                    canvas.drawLine(x1, y1, left + side * .43f, top + side * .72f, strokePaint)
                    canvas.drawLine(left + side * .43f, top + side * .72f, left + side * .79f, top + side * .30f, strokePaint)
                    strokePaint.alpha = 255
                }
            }
            WearMarkKind.Radio -> {
                val r = dp(9f)
                val cx = width / 2f
                val cy = centerY
                val trackBlend = blend(uncheckedTrack, checkedTrack, colorVisual)
                strokePaint.color = trackBlend
                strokePaint.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, r, strokePaint)
                if (visual > 0f) {
                    fillPaint.color = trackBlend
                    canvas.drawCircle(cx, cy, r * visual, fillPaint)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        colorAnimator?.cancel()
        colorAnimator = null
        super.onDetachedFromWindow()
    }

    private fun blend(from: Int, to: Int, fraction: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
    )
}

/** Base native control used by checkbox, radio, and switch rows. */
open class ToggleControlView : WearControlFrame {
    private val row = LinearLayout(context)
    protected val titleView = WearTextView(context)
    protected val secondaryView = WearTextView(context)
    private val textColumn = LinearLayout(context)
    private val markView: ToggleMarkView
    private var customContent: View? = null
    private var iconView: WearIconView? = null
    private var checkedState = false
    private var checkedListener: WearCheckedChangeListener? = null
    private var markKind = WearMarkKind.Checkbox
    private var radioGroup: WearRadioGroup? = null
    private var radioId = View.NO_ID
    private var secondaryClickListener: View.OnClickListener? = null
    protected var controlColors = WearControlColors()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        markView = ToggleMarkView(context, markKind)
        minimumHeight = dp(52f)
        setPadding(dp(4f), 0, dp(4f), 0)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14f), dp(8f), dp(10f), dp(8f))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        textColumn.orientation = LinearLayout.VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        titleView.applyWearTypography(WearTypographyRole.BodyLarge, useProminentWeight = true)
        secondaryView.applyWearTypography(WearTypographyRole.BodySmall)
        titleView.maxLines = 2
        secondaryView.maxLines = 2
        secondaryView.visibility = GONE
        textColumn.addView(titleView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        textColumn.addView(secondaryView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        row.addView(textColumn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(markView, LinearLayout.LayoutParams(dp(42f), dp(32f)).apply { marginStart = dp(8f) })
        titleView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        secondaryView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        markView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setSurface(Color.rgb(51, 46, 60), Color.rgb(246, 237, 255))
        applyControlColors()
    }

    internal fun configureMark(value: WearMarkKind) {
        if (markKind == value) return
        // The visual class is fixed in this base constructor; subclasses declare the desired type
        // through the constructor-specific replacement so checked animations stay localized.
        markKind = value
        val index = row.indexOfChild(markView)
        row.removeView(markView)
        val replacement = ToggleMarkView(context, value)
        replacement.colors = controlColors
        replacement.isEnabled = isEnabled
        replacement.setChecked(checkedState, false)
        row.addView(replacement, index, LinearLayout.LayoutParams(dp(42f), dp(32f)).apply { marginStart = dp(8f) })
        markReference = replacement
    }

    private var markReference: ToggleMarkView? = null
    private fun activeMark(): ToggleMarkView = markReference ?: markView

    fun setText(text: CharSequence?) { titleView.text = text; if (contentDescription.isNullOrEmpty()) contentDescription = text }
    fun setTitle(text: CharSequence?) = setText(text)
    fun setSecondaryText(text: CharSequence?) { secondaryView.text = text; secondaryView.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE }
    fun setHasSecondary(has: Boolean) { secondaryView.visibility = if (has) VISIBLE else GONE }
    fun setControlShape(shape: WearShape) { setShapeInternal(shape) }
    fun setIcon(icon: Drawable?, contentDescription: CharSequence? = null) {
        var target = iconView
        if (target == null) {
            target = WearIconView(context)
            target.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            iconView = target
            row.addView(target, 0, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { marginEnd = dp(10f) })
        }
        target.setIcon(icon, null)
        target.visibility = if (icon == null) GONE else VISIBLE
        if (titleView.text.isNullOrEmpty()) this.contentDescription = contentDescription
    }
    fun setContent(view: View?) {
        customContent?.let { textColumn.removeView(it) }
        customContent = view
        view?.let { textColumn.addView(it, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    }
    fun setColors(colors: WearControlColors) { controlColors = colors; applyControlColors() }
    private fun applyControlColors() {
        activeMark().colors = controlColors
        activeMark().setChecked(checkedState, false)
        titleView.setTextColor(if (isEnabled) Color.rgb(246, 237, 255) else Color.rgb(148, 143, 154))
        secondaryView.setTextColor(if (isEnabled) Color.rgb(202, 196, 208) else Color.rgb(148, 143, 154))
    }

    fun isChecked(): Boolean = checkedState
    open fun setChecked(checked: Boolean, animate: Boolean = true) { changeChecked(checked, false, animate) }
    fun setOnCheckedChangeListener(listener: WearCheckedChangeListener?) { checkedListener = listener }
    fun setRadioGroup(group: WearRadioGroup?, id: Int = this.id) {
        radioGroup?.removeRadio(this)
        radioGroup = group
        radioId = id
        if (group != null && id != View.NO_ID) group.addRadio(this, id)
    }
    fun setOnSecondaryClickListener(listener: View.OnClickListener?) { secondaryClickListener = listener }
    fun performSecondaryClick(): Boolean { if (!isEnabled) return false; secondaryClickListener?.onClick(this); return true }

    protected fun changeChecked(checked: Boolean, fromUser: Boolean, animate: Boolean) {
        if (checkedState == checked) return
        checkedState = checked
        isSelected = checked
        isActivated = checked
        activeMark().setChecked(checked, animate && isEnabled)
        refreshDrawableState()
        if (radioGroup != null && checked && radioId != View.NO_ID) radioGroup?.select(radioId, fromUser)
        checkedListener?.onCheckedChanged(this, checked, fromUser)
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
    }

    override fun performClick(): Boolean {
        if (isEnabled) changeChecked(!checkedState, true, true)
        return super.performClick()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        activeMark().isEnabled = enabled
        applyControlColors()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && event.x >= width - dp(54f)) performSecondaryClick()
        return super.onTouchEvent(event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = if (markKind == WearMarkKind.Switch) android.widget.Switch::class.java.name else android.widget.CompoundButton::class.java.name
        info.isCheckable = true
        info.isChecked = checkedState
        info.isEnabled = isEnabled
        if (contentDescription.isNullOrEmpty()) info.text = titleView.text
        if (Build.VERSION.SDK_INT >= 30) info.stateDescription = if (checkedState) "On" else "Off"
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = WearControlSavedState(super.onSaveInstanceState())
        state.checked = checkedState
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is WearControlSavedState) {
            super.onRestoreInstanceState(state.superState)
            changeChecked(state.checked, false, false)
        } else super.onRestoreInstanceState(state)
    }
}

open class CheckboxButtonView : ToggleControlView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { configureMark(WearMarkKind.Checkbox) }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.CheckBox::class.java.name
    }
}

class SplitCheckboxButtonView : CheckboxButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}

open class RadioButtonView : ToggleControlView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { configureMark(WearMarkKind.Radio) }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.RadioButton::class.java.name
    }
    override fun performClick(): Boolean {
        if (isEnabled && !isChecked()) changeChecked(true, true, true)
        return super.performClick()
    }
}

class SplitRadioButtonView : RadioButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}

open class SwitchButtonView : ToggleControlView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { configureMark(WearMarkKind.Switch) }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Role.Switch semantics: must surface as Switch, not generic CompoundButton.
        info.className = android.widget.Switch::class.java.name
        // super already set isCheckable/isChecked/stateDescription/title fallback; ensure Switch role keeps On/Off.
        if (Build.VERSION.SDK_INT >= 30) info.stateDescription = if (isChecked()) "On" else "Off"
    }
}

class SplitSwitchButtonView : SwitchButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}
