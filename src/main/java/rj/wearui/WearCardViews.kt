package rj.wearui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout

/** Surface roles for cards and chips. Tokens map to [WearColorScheme] roles. */
data class WearCardColors(
    val containerColor: Int = WearColorScheme.Dark.surfaceContainer, // WearColorScheme.surfaceContainer
    val contentColor: Int = WearColorScheme.Dark.onSurface, // WearColorScheme.onSurface
    val secondaryContentColor: Int = WearColorScheme.Dark.onSurfaceVariant, // WearColorScheme.onSurfaceVariant
    val borderColor: Int = Color.TRANSPARENT,
    val disabledContainerColor: Int = WearColorScheme.Dark.surfaceContainer // WearColorScheme.surfaceContainer (disabled alpha applied in WearControlFrame)
)

class CardBody(context: Context, chip: Boolean = false) : LinearLayout(context) {
    val icon = WearIconView(context)
    val image = WearIconView(context)
    val textColumn = LinearLayout(context)
    val title = WearTextView(context)
    val secondary = WearTextView(context)
    private var customView: View? = null
    private val isChip = chip

    init {
        orientation = if (chip) HORIZONTAL else VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        // Children are decorative; container is the single accessible node (title fallback handled in host).
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        image.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        title.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        secondary.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        textColumn.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (chip) {
            // ChipTokens: 14dp/6dp per Chip.kt contentPadding
            setPadding(
                dp(ChipTokens.ContentPaddingHorizontalDp),
                dp(ChipTokens.ContentPaddingVerticalDp),
                dp(ChipTokens.ContentPaddingHorizontalDp),
                dp(ChipTokens.ContentPaddingVerticalDp)
            )
        } else {
            // CardTokens: 12dp per Card.kt contentPadding
            val p = dp(CardTokens.ContentPaddingDp)
            setPadding(p, p, p, p)
        }
        val iconSize = dp(if (chip) ChipTokens.IconSizeDp else CardTokens.IconSizeDp)
        icon.layoutParams = LayoutParams(iconSize, iconSize)
        icon.visibility = View.GONE
        addView(icon)
        val imageSize = dp(if (chip) ChipTokens.ImageSizeDp else CardTokens.ImageSizeDp)
        image.layoutParams = LayoutParams(imageSize, imageSize).apply { marginStart = dp(CardTokens.ImageMarginStartDp) }
        image.visibility = View.GONE
        addView(image)
        textColumn.orientation = VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        textColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(if (chip) ChipTokens.TextColumnMarginStartDp else CardTokens.TextColumnMarginStartDp)
        }
        title.setTypographyRole(if (chip) ChipTokens.LabelTypography else CardTokens.TitleTypography)
        title.setTextColor(Color.rgb(246, 237, 255))
        title.maxLines = 2
        title.visibility = View.GONE
        textColumn.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        secondary.setTypographyRole(if (chip) ChipTokens.SecondaryTypography else CardTokens.SecondaryTypography)
        secondary.setTextColor(Color.rgb(202, 196, 208))
        secondary.maxLines = 3
        secondary.visibility = View.GONE
        textColumn.addView(secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(if (chip) ChipTokens.SecondaryLabelTopMarginDp else CardTokens.SecondaryLabelTopMarginDp)
        })
        addView(textColumn)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()

    fun setCustom(view: View?) {
        customView?.let { textColumn.removeView(it) }
        customView = view
        view?.let {
            textColumn.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(if (isChip) ChipTokens.CustomContentTopMarginDp else CardTokens.CustomContentTopMarginDp)
            })
        }
    }

    fun setTextVisibility() {
        title.visibility = if (title.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        secondary.visibility = if (secondary.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}

private fun enumRole(name: String): WearTypographyRole? = try {
    java.lang.Enum.valueOf(WearTypographyRole::class.java, name)
} catch (_: Throwable) { null }

open class CardView : WearControlFrame {
    protected val cardBody: CardBody
    protected var cardColors = WearCardColors()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        cardBody = CardBody(context)
        addView(cardBody, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        minimumHeight = dp(CardTokens.ContainerMinHeightDp)
        setSurface(cardColors.containerColor, cardColors.contentColor)
        setShapeInternal(CardTokens.ContainerShape)
    }

    fun setTitle(title: CharSequence?) {
        cardBody.title.text = title
        cardBody.setTextVisibility()
        cardBody.title.setTextColor(if (isEnabled) cardColors.contentColor else cardColors.secondaryContentColor)
        if (contentDescription.isNullOrEmpty() && !title.isNullOrEmpty()) contentDescription = title
    }

    fun setSecondaryText(text: CharSequence?) {
        cardBody.secondary.text = text
        cardBody.setTextVisibility()
    }

    fun setIcon(icon: Drawable?, contentDescription: CharSequence? = null) {
        cardBody.icon.setIcon(icon, null)
        cardBody.icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        cardBody.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
        if (cardBody.title.text.isNullOrEmpty() && !contentDescription.isNullOrEmpty()) this.contentDescription = contentDescription
    }

    fun setImage(image: Drawable?, contentDescription: CharSequence? = null) {
        cardBody.image.setIcon(image, null)
        cardBody.image.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        cardBody.image.visibility = if (image == null) View.GONE else View.VISIBLE
        if (cardBody.title.text.isNullOrEmpty() && !contentDescription.isNullOrEmpty()) this.contentDescription = contentDescription
    }

    fun setContent(view: View?) {
        cardBody.setCustom(view)
    }

    fun setColors(colors: WearCardColors) {
        cardColors = colors
        setSurface(if (isEnabled) colors.containerColor else colors.disabledContainerColor, colors.contentColor, colors.borderColor, if (colors.borderColor != Color.TRANSPARENT) dp(1f) else 0)
        cardBody.title.setTextColor(if (isEnabled) colors.contentColor else colors.secondaryContentColor)
        cardBody.secondary.setTextColor(colors.secondaryContentColor)
    }

    fun setCardShape(shape: WearShape) { setShapeInternal(shape) }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.isClickable = isClickable
        if (contentDescription.isNullOrEmpty()) info.text = cardBody.title.text
    }
}

open class OutlinedCardView : CardView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearCardColors(Color.TRANSPARENT, Color.rgb(246, 237, 255), Color.rgb(202, 196, 208), Color.rgb(148, 143, 154)))
    }
}

class AppCardView : CardView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearCardColors(Color.rgb(51, 46, 60), Color.rgb(246, 237, 255), Color.rgb(202, 196, 208)))
    }
}

class TitleCardView : CardView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        cardBody.title.setTypographyRole(CardTokens.TitleLargeTypography)
    }
}

class OutlinedAppCardView : OutlinedCardView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearCardColors(Color.TRANSPARENT, Color.rgb(246, 237, 255), Color.rgb(202, 196, 208), Color.rgb(148, 143, 154)))
    }
}

class OutlinedTitleCardView : OutlinedCardView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        cardBody.title.setTypographyRole(CardTokens.TitleLargeTypography)
    }
}

open class ChipView : WearControlFrame {
    protected val chipBody: CardBody
    protected var chipColors = WearCardColors(Color.rgb(51, 46, 60), Color.rgb(246, 237, 255), Color.rgb(202, 196, 208))

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        chipBody = CardBody(context, chip = true)
        addView(chipBody, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ChipTokens.ContainerHeightDp)))
        minimumHeight = dp(ChipTokens.ContainerHeightDp)
        setSurface(chipColors.containerColor, chipColors.contentColor)
        setShapeInternal(ChipTokens.ContainerShape)
    }

    fun setTitle(title: CharSequence?) {
        chipBody.title.text = title
        chipBody.setTextVisibility()
        if (contentDescription.isNullOrEmpty() && !title.isNullOrEmpty()) contentDescription = title
    }

    fun setSecondaryText(text: CharSequence?) {
        chipBody.secondary.text = text
        chipBody.setTextVisibility()
    }

    fun setIcon(icon: Drawable?, contentDescription: CharSequence? = null) {
        chipBody.icon.setIcon(icon, null)
        chipBody.icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        chipBody.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
        if (chipBody.title.text.isNullOrEmpty() && !contentDescription.isNullOrEmpty()) this.contentDescription = contentDescription
    }

    fun setImage(image: Drawable?, contentDescription: CharSequence? = null) {
        chipBody.image.setIcon(image, null)
        chipBody.image.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        chipBody.image.visibility = if (image == null) View.GONE else View.VISIBLE
        if (chipBody.title.text.isNullOrEmpty() && !contentDescription.isNullOrEmpty()) this.contentDescription = contentDescription
    }

    fun setContent(view: View?) { chipBody.setCustom(view) }

    fun setColors(colors: WearCardColors) {
        chipColors = colors
        setSurface(if (isEnabled) colors.containerColor else colors.disabledContainerColor, colors.contentColor, colors.borderColor, if (colors.borderColor != Color.TRANSPARENT) dp(1f) else 0)
        chipBody.title.setTextColor(colors.contentColor)
        chipBody.secondary.setTextColor(colors.secondaryContentColor)
    }

    fun setChipShape(shape: WearShape) { setShapeInternal(shape) }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.isClickable = isClickable
        if (contentDescription.isNullOrEmpty()) info.text = chipBody.title.text
    }
}

class CompactChipView : ChipView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        chipBody.setPadding(dp(ChipTokens.CompactContentPaddingHorizontalDp), 0, dp(ChipTokens.CompactContentPaddingHorizontalDp), 0)
        minimumHeight = dp(ChipTokens.CompactContainerHeightDp)
        chipBody.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ChipTokens.CompactContainerHeightDp))
        setShapeInternal(ChipTokens.CompactContainerShape)
    }
}

class AppChipView : ChipView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setColors(WearCardColors(Color.rgb(51, 46, 60), Color.rgb(246, 237, 255), Color.rgb(202, 196, 208)))
    }
}

class TitleChipView : ChipView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        chipBody.title.setTypographyRole(ChipTokens.LabelTypography)
    }
}

open class ToggleChipView : ChipView {
    private var checkedState = false
    private var checkedListener: WearCheckedChangeListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        super.setOnClickListener { setChecked(!checkedState, true, true) }
    }

    fun isChecked(): Boolean = checkedState

    fun setChecked(checked: Boolean, animate: Boolean = true) { setChecked(checked, animate, false) }

    private fun setChecked(checked: Boolean, animate: Boolean, fromUser: Boolean) {
        if (checkedState == checked) return
        checkedState = checked
        isSelected = checked
        isActivated = checked
        refreshDrawableState()
        if (Build.VERSION.SDK_INT >= 30) stateDescription = if (checked) "On" else "Off"
        checkedListener?.onCheckedChanged(this, checked, fromUser)
    }

    fun setOnCheckedChangeListener(listener: WearCheckedChangeListener?) { checkedListener = listener }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.CompoundButton::class.java.name
        info.isCheckable = true
        info.isChecked = checkedState
        info.isEnabled = isEnabled
        if (contentDescription.isNullOrEmpty()) info.text = chipBody.title.text
        if (Build.VERSION.SDK_INT >= 30) info.stateDescription = if (checkedState) "On" else "Off"
    }

    override fun onSaveInstanceState(): android.os.Parcelable {
        val state = WearControlSavedState(super.onSaveInstanceState()); state.checked = checkedState; return state
    }

    override fun onRestoreInstanceState(state: android.os.Parcelable?) {
        if (state is WearControlSavedState) {
            super.onRestoreInstanceState(state.superState); checkedState = state.checked; isSelected = checkedState; isActivated = checkedState
        } else super.onRestoreInstanceState(state)
    }
}

class SplitToggleChipView : ToggleChipView {
    private var secondary: View? = null
    private var secondaryListener: OnClickListener? = null
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
