package rj.wearui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout

/** ARGB roles used by native button components. Defaults map to [WearColorScheme] roles; live M3 accents are threaded via [WearButtonColors] from TokenBridge. */
data class WearButtonColors(
    val containerColor: Int = WearColorScheme.Dark.primary, // Color.rgb(233, 221, 255) -> WearColorScheme.primary (TokenBridge: M3.primary)
    val contentColor: Int = WearColorScheme.Dark.onPrimary, // Color.rgb(33, 15, 72) -> WearColorScheme.onPrimary (TokenBridge: M3.onPrimary)
    val disabledContainerColor: Int = WearColorScheme.Dark.onSurface, // 0xFFF6EDFF -> WearColorScheme.onSurface / onPrimaryContainer @ WearUiDisabledAlpha.Container (12%)
    val disabledContentColor: Int = 0x61F6EDFF, // WearColorScheme.onSurface @ WearUiDisabledAlpha.Content (38%) — 0x61 = 97/255
    val borderColor: Int = Color.TRANSPARENT,
    val disabledBorderColor: Int = Color.TRANSPARENT,
    val secondaryContentColor: Int = WearColorScheme.Dark.onSurfaceVariant // Color.rgb(202, 196, 208) -> WearColorScheme.onSurfaceVariant (TokenBridge: M3.onSurfaceVariant)
)

class ButtonContent(context: Context) : LinearLayout(context) {
    val icon = WearIconView(context)
    val text = WearTextView(context)
    val secondary = WearTextView(context)
    private val textColumn = LinearLayout(context)
    private var leadingSlot: View = icon
    var content: View? = null
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isBaselineAligned = false
        icon.visibility = GONE
        setIconSize(dp(ButtonTokens.IconSizeDp.toInt()))
        addView(icon)
        textColumn.orientation = VERTICAL
        textColumn.isBaselineAligned = false
        textColumn.gravity = Gravity.CENTER
        // Ensure column content is centered vertically inside the pill — use WRAP_CONTENT height so group is centered via parent gravity, not internal distribution
        text.setTypographyRole(ButtonTokens.LabelTypography)
        text.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        text.includeFontPadding = false
        // Explicitly center lineHeight block: fallbackLineSpacing false already in WearTextView, ensure vertical gravity and no extra top bias
        text.maxLines = 3
        text.ellipsize = android.text.TextUtils.TruncateAt.END
        text.setTextColor(WearColorScheme.Dark.onPrimary) // WearButtonColors.contentColor -> onPrimary (TokenBridge supplies M3.onPrimary)
        text.visibility = GONE
        text.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_VERTICAL }
        textColumn.addView(text)
        secondary.setTypographyRole(ButtonTokens.SecondaryLabelTypography)
        secondary.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        secondary.includeFontPadding = false
        secondary.maxLines = 2
        secondary.ellipsize = android.text.TextUtils.TruncateAt.END
        secondary.setTextColor(WearColorScheme.Dark.onSurfaceVariant) // WearButtonColors.secondaryContentColor -> onSurfaceVariant
        secondary.visibility = GONE
        secondary.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(ButtonTokens.SecondaryLabelTopMarginDp)
            gravity = Gravity.CENTER_VERTICAL
        }
        textColumn.addView(secondary)
        // WRAP_CONTENT height lets FrameLayout center the column as a whole (2.5dp slack distributed outside, not inside)
        textColumn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
        addView(textColumn)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()

    fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    fun setIconSize(sizePx: Int) {
        icon.setIconSize(sizePx)
        icon.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        requestLayout()
    }

    fun setIconView(view: View?, sizePx: Int) {
        val next = view ?: icon
        if (leadingSlot !== next) {
            removeView(leadingSlot)
            leadingSlot = next
            addView(leadingSlot, 0)
        }
        leadingSlot.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        leadingSlot.visibility = if (view == null) GONE else VISIBLE
        icon.visibility = if (leadingSlot === icon && view != null) VISIBLE else GONE
        setTextColumnFlexible(view != null || content != null)
        requestLayout()
    }

    fun setSecondaryLabel(text: CharSequence?) {
        secondary.text = text
        secondary.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
        secondary.maxLines = 2
        requestLayout()
    }

    fun setSecondaryLabelSingleLine(text: CharSequence?) {
        secondary.text = text
        secondary.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
        secondary.maxLines = 1
        requestLayout()
    }

    fun setSecondaryColor(color: Int) {
        secondary.setTextColor(color)
    }

    fun setGap(gap: Int) {
        (textColumn.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.marginStart = gap
            textColumn.layoutParams = lp
        }
    }

    fun setTextColumnFlexible(flexible: Boolean) {
        (textColumn.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = if (flexible) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = if (flexible) 1f else 0f
            textColumn.layoutParams = lp
        }
        text.gravity = Gravity.CENTER_VERTICAL or if (flexible) Gravity.START else Gravity.CENTER
        secondary.gravity = Gravity.CENTER_VERTICAL or if (flexible) Gravity.START else Gravity.CENTER
    }

    fun putContent(view: View?) {
        content?.let { textColumn.removeView(it) }
        content = view
        if (view == null) {
            text.visibility = if (text.text.isNullOrEmpty()) GONE else VISIBLE
            setTextColumnFlexible(leadingSlot.visibility == VISIBLE)
            return
        }
        text.visibility = GONE
        val supplied = view.layoutParams
        val params = when (supplied) {
            is LinearLayout.LayoutParams -> supplied
            else -> LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = if (params.height == ViewGroup.LayoutParams.MATCH_PARENT) params.height else ViewGroup.LayoutParams.WRAP_CONTENT
        view.layoutParams = params
        textColumn.addView(view, 0)
        setTextColumnFlexible(true)
        requestLayout()
    }

    fun drawablePresent(): Boolean = icon.visibility != GONE
}

/**
 * Representative tokenized component.
 *
 * Before: `ButtonView` inlined paddings `14/6`, min height `52dp`, icon `26dp`, gap `6dp`,
 * shape `Large`, and typography `LabelMedium`/`LabelSmall` as literals / stringly-typed
 * `Enum.valueOf("...")`. After: every dimension/shape/typography is sourced from
 * [ButtonTokens], the vendored android-only mirror of `androidx.wear.compose.material3.ButtonTokens`.
 * Colors are threaded through [WearButtonColors] (defaults map to [WearColorScheme] roles,
 * live accents supplied by TokenBridge), so no `Color.rgb` literals remain in component code.
 *
 * This single component demonstrates the tokenization pattern without a massive rewrite;
 * the remaining files are mapped in `WearComponentTokens.kt` and in the audit table
 * returned by this task, to be migrated incrementally.
 */
/** Filled primary Wear button. */
open class ButtonView : WearControlFrame {
    protected val buttonContent: ButtonContent
    protected var buttonColors = WearButtonColors()
    protected var toggleBehavior = false
    protected var checkedState = false

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        buttonContent = ButtonContent(context)
        setButtonContentPaddingDp(
            ButtonTokens.ContentPaddingStartDp.toInt(),
            ButtonTokens.ContentPaddingTopDp.toInt(),
            ButtonTokens.ContentPaddingEndDp.toInt(),
            ButtonTokens.ContentPaddingBottomDp.toInt()
        )
        // Center buttonContent as WRAP_CONTENT block inside the 52dp FrameLayout (padding 6 vertical) — avoids MATCH_PARENT stretch that can bias vertical distribution
        val glp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(buttonContent, glp)
        minimumHeight = dp(ButtonTokens.ContainerHeightDp)
        buttonContent.setIconSize(dp(ButtonTokens.IconSizeDp))
        buttonContent.setGap(dp(ButtonTokens.IconSpacingDp))
        setButtonShape(ButtonTokens.ContainerShape)
        setSurface(buttonColors.containerColor, buttonColors.contentColor)
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        if (toggleBehavior && isEnabled) setCheckedInternal(!checkedState, true)
        return handled
    }

    fun setText(text: CharSequence?) {
        buttonContent.text.text = text
        buttonContent.text.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        buttonContent.putContent(null)
        refreshDrawableState()
    }

    fun setIcon(icon: Drawable?, contentDescription: CharSequence? = null) {
        buttonContent.setIconView(null, dp(ButtonTokens.IconSizeDp))
        buttonContent.icon.setIcon(icon, contentDescription)
        buttonContent.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
        buttonContent.putContent(null)
    }

    /** Set the leading slot to a native child, equivalent to Compose Button's icon lambda. */
    fun setIconView(view: View?, sizeDp: Int = ButtonTokens.SmallIconSizeDp.toInt()) {
        buttonContent.setIconView(view, (sizeDp * resources.displayMetrics.density + .5f).toInt())
        buttonContent.setTextColumnFlexible(view != null || buttonContent.content != null)
    }

    fun setContent(view: View?) {
        buttonContent.putContent(view)
    }

    /** Two-line button: a secondary label under the primary one (vendored Button secondaryLabel slot). */
    fun setSecondaryLabel(text: CharSequence?) {
        buttonContent.setSecondaryLabel(text)
    }

    /** Single-line secondary content for list previews that intentionally truncate at one row. */
    fun setSecondaryLabelSingleLine(text: CharSequence?) {
        buttonContent.setSecondaryLabelSingleLine(text)
    }

    fun setButtonContentPaddingDp(start: Int, top: Int, end: Int, bottom: Int) {
        setPadding(dp(start.toFloat()), dp(top.toFloat()), dp(end.toFloat()), dp(bottom.toFloat()))
    }

    /** Override the leading icon size in dp (e.g. the chat-row avatar at ExtraLargeIconSize 40dp). */
    fun setButtonIconSizeDp(sizeDp: Int) {
        buttonContent.setIconSize((sizeDp * resources.displayMetrics.density + .5f).toInt())
    }

    fun setColors(colors: WearButtonColors) {
        buttonColors = colors
        setSurface(if (isEnabled) colors.containerColor else colors.disabledContainerColor,
            if (isEnabled) colors.contentColor else colors.disabledContentColor,
            colors.borderColor,
            if (colors.borderColor != Color.TRANSPARENT) dp(1f) else 0)
        buttonContent.text.setTextColor(if (isEnabled) colors.contentColor else colors.disabledContentColor)
        buttonContent.setSecondaryColor(
            if (isEnabled) colors.secondaryContentColor
            else withAlpha(colors.secondaryContentColor, WearUiDisabledAlpha.Content)
        )
    }

    fun setButtonShape(shape: WearShape) {
        setShapeInternal(shape)
    }

    fun setButtonMinWidth(widthPx: Int) {
        minimumWidth = widthPx
    }

    fun isChecked(): Boolean = checkedState

    open fun setChecked(checked: Boolean, animate: Boolean = true) {
        setCheckedInternal(checked, animate)
    }

    protected fun setCheckedInternal(checked: Boolean, fromUser: Boolean) {
        if (checkedState == checked) return
        checkedState = checked
        isSelected = checked
        isActivated = checked
        refreshDrawableState()
        if (Build.VERSION.SDK_INT >= 30) stateDescription = if (checked) "On" else "Off"
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> refreshDrawableState()
        }
        return super.onTouchEvent(event)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        buttonContent.text.setTextColor(if (isEnabled) buttonColors.contentColor else buttonColors.disabledContentColor)
        buttonContent.setSecondaryColor(
            if (isEnabled) buttonColors.secondaryContentColor
            else withAlpha(buttonColors.secondaryContentColor, WearUiDisabledAlpha.Content)
        )
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = ButtonView::class.java.name
        info.isClickable = isClickable
        if (toggleBehavior) {
            info.isCheckable = true
            info.isChecked = checkedState
            if (Build.VERSION.SDK_INT >= 30) info.stateDescription = if (checkedState) "On" else "Off"
        }
    }

    override fun onSaveInstanceState(): android.os.Parcelable {
        val state = WearControlSavedState(super.onSaveInstanceState())
        state.checked = checkedState
        return state
    }

    override fun onRestoreInstanceState(state: android.os.Parcelable?) {
        if (state is WearControlSavedState) {
            super.onRestoreInstanceState(state.superState)
            checkedState = state.checked
            isSelected = checkedState
            isActivated = checkedState
        } else super.onRestoreInstanceState(state)
    }
}

/** Filled tonal button with a secondary container tone. */
class FilledTonalButtonView : ButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        buttonContent.setIconSize(dp(ButtonTokens.SmallIconSizeDp))
        setColors(WearButtonColors(
            containerColor = WearColorScheme.Dark.surfaceContainer, // Color.rgb(51, 46, 60) -> surfaceContainer
            contentColor = WearColorScheme.Dark.onSurface // Color.rgb(246, 237, 255) -> onSurface
        ))
    }
}

/** Outlined button with a 1dp semantic border. */
class OutlinedButtonView : ButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        buttonContent.setIconSize(dp(ButtonTokens.SmallIconSizeDp))
        setColors(WearButtonColors(
            containerColor = Color.TRANSPARENT,
            contentColor = WearColorScheme.Dark.onSurface, // Color.rgb(246, 237, 255) -> onSurface
            disabledContainerColor = Color.TRANSPARENT,
            disabledContentColor = WearColorScheme.Dark.surfaceContainer, // Color.rgb(51, 46, 60) -> surfaceContainer (disabled outline)
            borderColor = WearColorScheme.Dark.outline // Color.rgb(148, 143, 154) -> outline (TokenBridge: M3.outline)
        ))
    }
}

/** Button whose primary content is an arbitrary native child view. */
class ChildButtonView : ButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        buttonContent.setIconSize(dp(ButtonTokens.SmallIconSizeDp))
    }
}

/** Compact icon-only button. */
open class IconButtonView : ButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setPadding(dp(ButtonTokens.IconButtonContentPaddingHorizontalDp), 0, dp(ButtonTokens.IconButtonContentPaddingHorizontalDp), 0)
        minimumWidth = dp(ButtonTokens.CompactIconButtonContainerSizeDp)
        buttonContent.text.visibility = View.GONE
        buttonContent.setGap(dp(ButtonTokens.IconButtonGapDp))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(ButtonTokens.CompactIconButtonContainerSizeDp)
        val w = resolveSize(maxOf(size, suggestedMinimumWidth), widthMeasureSpec)
        val h = resolveSize(maxOf(size, suggestedMinimumHeight), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}

class FilledIconButtonView : IconButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearButtonColors(
            containerColor = WearColorScheme.Dark.primary, // Color.rgb(233, 221, 255) -> primary (TokenBridge: M3.primary)
            contentColor = WearColorScheme.Dark.onPrimary // Color.rgb(33, 15, 72) -> onPrimary
        ))
    }
}

class FilledTonalIconButtonView : IconButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearButtonColors(
            containerColor = WearColorScheme.Dark.surfaceContainer, // Color.rgb(51, 46, 60) -> surfaceContainer
            contentColor = WearColorScheme.Dark.onSurface // Color.rgb(246, 237, 255) -> onSurface
        ))
    }
}

class OutlinedIconButtonView : IconButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearButtonColors(
            containerColor = Color.TRANSPARENT,
            contentColor = WearColorScheme.Dark.onSurface, // Color.rgb(246, 237, 255) -> onSurface
            disabledContainerColor = Color.TRANSPARENT,
            disabledContentColor = WearColorScheme.Dark.surfaceContainer, // Color.rgb(51, 46, 60)
            borderColor = WearColorScheme.Dark.outline // Color.rgb(148, 143, 154) -> outline
        ))
    }
}

open class TextButtonView : ButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setButtonShape(ButtonTokens.TextButtonContainerShape)
        setPadding(dp(ButtonTokens.ContentPaddingStartDp), dp(ButtonTokens.ContentPaddingTopDp), dp(ButtonTokens.ContentPaddingEndDp), dp(ButtonTokens.ContentPaddingBottomDp))
        buttonContent.setIconSize(dp(ButtonTokens.SmallIconSizeDp))
        setColors(WearButtonColors(
            containerColor = Color.TRANSPARENT,
            contentColor = WearColorScheme.Dark.onSurface // Color.rgb(246, 237, 255) -> onSurface (TokenBridge: M3.primary for wearButtonText)
        ))
    }
}

class FilledTextButtonView : TextButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearButtonColors(
            containerColor = WearColorScheme.Dark.primary, // Color.rgb(233, 221, 255) -> primary
            contentColor = WearColorScheme.Dark.onPrimary // Color.rgb(33, 15, 72) -> onPrimary
        ))
    }
}

class FilledTonalTextButtonView : TextButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearButtonColors(
            containerColor = WearColorScheme.Dark.surfaceContainer, // Color.rgb(51, 46, 60)
            contentColor = WearColorScheme.Dark.onSurface // Color.rgb(246, 237, 255)
        ))
    }
}

class OutlinedTextButtonView : TextButtonView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearButtonColors(
            containerColor = Color.TRANSPARENT,
            contentColor = WearColorScheme.Dark.onSurface, // Color.rgb(246, 237, 255)
            disabledContainerColor = Color.TRANSPARENT,
            disabledContentColor = WearColorScheme.Dark.surfaceContainer, // Color.rgb(51, 46, 60)
            borderColor = WearColorScheme.Dark.outline // Color.rgb(148, 143, 154)
        ))
    }
}

open class IconToggleButtonView : IconButtonView {
    private var checkedListener: WearCheckedChangeListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        toggleBehavior = true
    }

    fun setOnCheckedChangeListener(listener: WearCheckedChangeListener?) {
        checkedListener = listener
    }

    override fun setChecked(checked: Boolean, animate: Boolean) {
        val old = isChecked()
        super.setChecked(checked, animate)
        if (old != checked) checkedListener?.onCheckedChanged(this, checked, false)
    }

    protected fun setCheckedFromUser(value: Boolean) {
        val old = isChecked()
        super.setChecked(value, true)
        if (old != value) checkedListener?.onCheckedChanged(this, value, true)
    }
}

open class TextToggleButtonView : TextButtonView {
    private var checkedListener: WearCheckedChangeListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        toggleBehavior = true
    }

    fun setOnCheckedChangeListener(listener: WearCheckedChangeListener?) { checkedListener = listener }

    override fun setChecked(checked: Boolean, animate: Boolean) {
        val old = isChecked()
        super.setChecked(checked, animate)
        if (old != checked) checkedListener?.onCheckedChanged(this, checked, false)
    }
}

open class ToggleButtonView : ButtonView {
    private var checkedListener: WearCheckedChangeListener? = null
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        toggleBehavior = true
    }
    fun setOnCheckedChangeListener(listener: WearCheckedChangeListener?) { checkedListener = listener }
    override fun setChecked(checked: Boolean, animate: Boolean) {
        val old = isChecked(); super.setChecked(checked, animate)
        if (old != checked) checkedListener?.onCheckedChanged(this, checked, false)
    }
}

/** Two-target toggle button: the secondary child remains independently clickable. */
class SplitToggleButtonView : ToggleButtonView {
    private var secondaryListener: OnClickListener? = null
    private var secondary: View? = null
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSecondaryContent(view: View?, listener: OnClickListener? = null) {
        secondary?.let { removeView(it) }
        secondary = view
        secondaryListener = listener
        view?.let {
            addView(it, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL))
            it.setOnClickListener { secondaryListener?.onClick(it) }
        }
    }
}
