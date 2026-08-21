package rj.wearui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * Primitive layer audit (P2 Chip/Button tokenization):
 * This file holds WearTextView/WearAnimatedTextView/WearIconView/WearControlFrame only.
 * No chip/button controls live here — all ChipTokens/ButtonTokens wiring is in
 * WearCardViews.kt and WearButtonViews.kt. Colors below are annotated to
 * WearColorScheme roles; live accents are threaded via WearButtonColors/WearCardColors from TokenBridge.
 */

/** A native text view configured from the Wear UI typography roles. */
open class WearTextView : TextView {
    private var typographyRole: WearTypographyRole? = null
    private var lineHeightShiftPx = 0f

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE)
    }

    fun setTypographyRole(role: WearTypographyRole?) {
        typographyRole = role
        if (role == null) return
        // Delegate to the exact token applier so size, weight, variable width, tracking,
        // line height and centered line metrics all come from one source of truth.
        val token = WearUiTypography.Default.token(role)
        WearUiTypography.Default.applyTo(this, role)
        computeComposeLineHeightShift(token.lineHeightSp, token.sizeSp)
    }

    fun getTypographyRole(): WearTypographyRole? = typographyRole

    override fun onDraw(canvas: Canvas) {
        if (lineHeightShiftPx != 0f) {
            canvas.translate(0f, lineHeightShiftPx)
            super.onDraw(canvas)
            canvas.translate(0f, -lineHeightShiftPx)
        } else {
            super.onDraw(canvas)
        }
    }

    /**
     * TextView puts lineHeight expansion below the baseline; Compose's default Center
     * LineHeightStyle splits it above/below. Shift drawing down by half the extra leading.
     */
    private fun computeComposeLineHeightShift(lineHeightSp: Float, textSizeSp: Float) {
        val metrics = paint.fontMetrics
        val naturalLineHeight = metrics.descent - metrics.ascent
        val desiredLineHeight = lineHeightSp * resources.displayMetrics.scaledDensity
        lineHeightShiftPx = ((desiredLineHeight - naturalLineHeight) / 2f).coerceAtLeast(0f)
        if (textSizeSp <= 0f) lineHeightShiftPx = 0f
    }

    fun setTextColorInt(color: Int) {
        setTextColor(color)
    }

    fun setTextAlignmentGravity(gravity: Int) {
        this.gravity = gravity
    }

    fun setMaxLinesCount(maxLines: Int) {
        val count = max(1, maxLines)
        this.maxLines = count
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    override fun getAccessibilityClassName(): CharSequence = TextView::class.java.name
}

/** Text that fades through replacement values and honors reduced-motion requests. */
class WearAnimatedTextView : WearTextView {
    private var replacementAnimator: ObjectAnimator? = null
    private var reducedMotion = false
    private var initialized = false

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialized = true
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) cancelReplacementAnimation()
    }

    private fun shouldSnap(): Boolean =
        reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)

    override fun setText(text: CharSequence?, type: BufferType?) {
        val previous = this.text
        if (!initialized || shouldSnap() || previous == text) {
            cancelReplacementAnimation()
            super.setText(text, type)
            alpha = 1f
            return
        }
        cancelReplacementAnimation()
        // Fade-through: out Short50 + StandardDecelerate, in Short100 + StandardDecelerate
        val outSpec = WearMotionSpec(WearMotionDurations.Short50, WearMotionEasings.StandardDecelerate)
            .withReducedMotion(shouldSnap())
        if (outSpec.durationMillis == 0L) {
            super.setText(text, type)
            alpha = 1f
            return
        }
        val out = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0f)
        out.duration = outSpec.durationMillis
        out.interpolator = outSpec.interpolator
        out.addUpdateListener {
            if (it.animatedFraction >= .95f && this.text != text) super.setText(text, type)
        }
        out.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (this@WearAnimatedTextView.text != text) super@WearAnimatedTextView.setText(text, type)
                if (shouldSnap()) {
                    alpha = 1f
                    replacementAnimator = null
                    return
                }
                val inSpec = WearMotionSpec(WearMotionDurations.Short100, WearMotionEasings.StandardDecelerate)
                    .withReducedMotion(shouldSnap())
                if (inSpec.durationMillis == 0L) {
                    alpha = 1f
                    return
                }
                replacementAnimator = ObjectAnimator.ofFloat(this@WearAnimatedTextView, View.ALPHA, 0f, 1f).also { fadeIn ->
                    fadeIn.duration = inSpec.durationMillis
                    fadeIn.interpolator = inSpec.interpolator
                    fadeIn.start()
                }
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                alpha = 1f
            }
        })
        replacementAnimator = out
        out.start()
    }

    private fun cancelReplacementAnimation() {
        replacementAnimator?.cancel()
        replacementAnimator = null
    }

    override fun onDetachedFromWindow() {
        cancelReplacementAnimation()
        super.onDetachedFromWindow()
    }
}

/** Drawable-only icon view with native tinting and decorative accessibility mode. */
class WearIconView : View {
    enum class ScaleType { FIT_CENTER, CENTER, CENTER_CROP, FIT_XY }

    private var icon: Drawable? = null
    private var tint: Int? = null
    private var requestedSize = 0
    private var scaleType = ScaleType.FIT_CENTER
    private var iconContentDescription: CharSequence? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setIcon(drawable: Drawable?, contentDescription: CharSequence? = null) {
        icon?.callback = null
        icon = drawable?.mutate()
        icon?.callback = this
        iconContentDescription = contentDescription
        this.contentDescription = contentDescription
        importantForAccessibility = if (contentDescription.isNullOrEmpty()) {
            IMPORTANT_FOR_ACCESSIBILITY_NO
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        invalidate()
    }

    fun setTint(color: Int?) {
        tint = color
        // Vendored Icon uses LocalContentColor with ColorSchemeKeyTokens mapping via contentColorFor
        // Native maps via WearColorScheme role, tint as ColorStateList to preserve alpha
        if (Build.VERSION.SDK_INT >= 21) {
            icon?.setTintList(color?.let { ColorStateList.valueOf(it) })
            icon?.setTintMode(android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            icon?.setColorFilter(color ?: Color.TRANSPARENT, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        invalidate()
    }

    fun setIconSize(sizePx: Int) {
        requestedSize = max(0, sizePx)
        requestLayout()
    }

    fun setScaleType(type: ScaleType) {
        scaleType = type
        invalidate()
    }

    fun setDecorative(decorative: Boolean) {
        if (decorative) {
            iconContentDescription = null
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        } else if (!iconContentDescription.isNullOrEmpty()) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val intrinsicW = icon?.intrinsicWidth?.takeIf { it > 0 } ?: dp(24)
        val intrinsicH = icon?.intrinsicHeight?.takeIf { it > 0 } ?: dp(24)
        val desiredW = requestedSize.takeIf { it > 0 } ?: intrinsicW
        val desiredH = requestedSize.takeIf { it > 0 } ?: intrinsicH
        setMeasuredDimension(resolveSize(desiredW + paddingLeft + paddingRight, widthMeasureSpec), resolveSize(desiredH + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = icon ?: return
        tint?.let { if (Build.VERSION.SDK_INT < 21) drawable.setColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN) }
        val content = Rect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        val iw = max(1, drawable.intrinsicWidth)
        val ih = max(1, drawable.intrinsicHeight)
        val target = when (scaleType) {
            ScaleType.FIT_XY -> content
            ScaleType.CENTER -> {
                val w = min(iw, content.width()); val h = min(ih, content.height())
                Rect(content.centerX() - w / 2, content.centerY() - h / 2, content.centerX() + (w + 1) / 2, content.centerY() + (h + 1) / 2)
            }
            ScaleType.CENTER_CROP -> {
                val scale = max(content.width().toFloat() / iw, content.height().toFloat() / ih)
                val w = (iw * scale).toInt(); val h = (ih * scale).toInt()
                Rect(content.centerX() - w / 2, content.centerY() - h / 2, content.centerX() + (w + 1) / 2, content.centerY() + (h + 1) / 2)
            }
            ScaleType.FIT_CENTER -> {
                val scale = min(content.width().toFloat() / iw, content.height().toFloat() / ih)
                val w = (iw * scale).toInt(); val h = (ih * scale).toInt()
                Rect(content.centerX() - w / 2, content.centerY() - h / 2, content.centerX() + (w + 1) / 2, content.centerY() + (h + 1) / 2)
            }
        }
        drawable.bounds = target
        drawable.draw(canvas)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        icon?.state = drawableState
        invalidate()
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === icon || super.verifyDrawable(who)

    override fun invalidateDrawable(drawable: Drawable) {
        if (drawable === icon) invalidate() else super.invalidateDrawable(drawable)
    }

    override fun getAccessibilityClassName(): CharSequence = ImageView::class.java.name

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}

/** Shared View state for controls with user-visible checked/value/index state. */
internal class WearControlSavedState : View.BaseSavedState {
    var checked = false
    var value = 0f
    var index = 0
    var extra: String? = null

    constructor(superState: Parcelable?) : super(superState)
    private constructor(source: Parcel) : super(source) {
        checked = source.readInt() != 0
        value = source.readFloat()
        index = source.readInt()
        extra = source.readString()
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        super.writeToParcel(out, flags)
        out.writeInt(if (checked) 1 else 0)
        out.writeFloat(value)
        out.writeInt(index)
        out.writeString(extra)
    }

    companion object CREATOR : Parcelable.Creator<WearControlSavedState> {
        override fun createFromParcel(source: Parcel): WearControlSavedState = WearControlSavedState(source)
        override fun newArray(size: Int): Array<WearControlSavedState?> = arrayOfNulls(size)
    }
}

/** Internal native container used by the public control implementations. */
open class WearControlFrame(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : FrameLayout(context, attrs, defStyleAttr) {
    // Defaults annotated to WearColorScheme roles; subclasses override via WearButtonColors/WearCardColors from TokenBridge.
    protected var containerColor = WearColorScheme.Dark.surfaceContainer // Color.rgb(51, 46, 60) -> WearColorScheme.surfaceContainer
    protected var contentColor = WearColorScheme.Dark.onSurface // Color.rgb(246, 237, 255) -> WearColorScheme.onSurface
    protected var borderColor = Color.TRANSPARENT
    protected var borderWidthPx = 0
    protected var shape: WearShape? = null
    private var stateAnimator: ValueAnimator? = null
    private var reducedMotion = false

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) stopControlAnimations()
    }

    fun isReducedMotionEnabled(): Boolean = reducedMotion

    init {
        isClickable = true
        isFocusable = true
        clipToPadding = false
        updateSurface()
    }

    protected fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()

    protected fun setSurface(fill: Int, content: Int, border: Int = Color.TRANSPARENT, borderWidth: Int = 0) {
        containerColor = fill
        contentColor = content
        borderColor = border
        borderWidthPx = borderWidth
        updateSurface()
    }

    protected fun setShapeInternal(value: WearShape?) {
        shape = value
        updateSurface()
    }

    protected fun updateSurface() {
        val base = when {
            !isEnabled -> withAlpha(containerColor, .12f)
            isPressed -> blend(containerColor, Color.BLACK, .16f)
            isActivated || isSelected -> blend(containerColor, Color.WHITE, .08f)
            else -> containerColor
        }
        val radius = shapeRadius()
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(base)
            if (borderWidthPx > 0) setStroke(borderWidthPx, if (isEnabled) borderColor else withAlpha(borderColor, .20f))
        }
        background = drawable
        alpha = if (isEnabled) 1f else .92f
    }

    private fun shapeRadius(): Float {
        // Direct enum switch — no String.contains. Single source of truth for
        // corner radii is WearUiShapes (dp tokens) and WearComponentTokens.kt
        // (shape assignments). See WearShape.
        val resolved = shape ?: WearShape.Medium
        return WearUiShapes.Default.cornerRadiusPx(
            resolved, width.toFloat(), height.toFloat(), resources.displayMetrics.density
        )
    }

    protected fun animatePressed(pressed: Boolean) {
        if (!isEnabled) return
        stateAnimator?.cancel()
        val target = if (pressed) .97f else 1f
        val shouldSnap = reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
        val baseSpec = WearMotionSpec(
            durationMillis = if (pressed) WearMotionDurations.Short50 else WearMotionDurations.Short100,
            interpolator = WearMotionEasings.StandardDecelerate
        ).withReducedMotion(shouldSnap)
        if (baseSpec.durationMillis == 0L) {
            scaleX = target
            scaleY = target
            return
        }
        stateAnimator = ValueAnimator.ofFloat(scaleX, target).apply {
            duration = baseSpec.durationMillis
            interpolator = baseSpec.interpolator
            addUpdateListener { value ->
                val scale = value.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
            start()
        }
    }

    protected fun stopControlAnimations() {
        stateAnimator?.cancel()
        stateAnimator = null
        scaleX = 1f
        scaleY = 1f
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) stopControlAnimations()
        updateSurface()
        refreshDrawableState()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateSurface()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSurface()
    }

    override fun onDetachedFromWindow() {
        stopControlAnimations()
        super.onDetachedFromWindow()
    }

    protected fun withAlpha(color: Int, alpha: Float): Int = Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    protected fun blend(a: Int, b: Int, amount: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * amount).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * amount).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * amount).toInt()
    )

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isEnabled = isEnabled
    }
}
