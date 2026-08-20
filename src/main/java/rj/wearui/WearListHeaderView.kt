package rj.wearui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout

/**
 * Native ListHeader - vendored from ListHeaderTokens / ListHeader.kt
 * Height 32dp, horizontal 14dp / vertical 4dp, TitleSmall, heading semantics, centered.
 * Used above ScalingLazyColumn sections (e.g. settings groups).
 */
open class ListHeaderView : FrameLayout {
    private val labelView: WearTextView

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // heading() semantics -> set as heading via className? Use TextView heading is via labelView
        labelView = WearTextView(context).apply {
            setTypographyRole(ListHeaderTokens.LabelTypography)
            gravity = Gravity.CENTER
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WearColorScheme.Dark.onSurfaceVariant)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val hp = dp(ListHeaderTokens.ContentPaddingHorizontalDp)
        val tp = dp(ListHeaderTokens.ContentPaddingTopDp)
        val bp = dp(ListHeaderTokens.ContentPaddingBottomDp)
        setPadding(hp, tp, hp, bp)
        minimumHeight = dp(ListHeaderTokens.ContainerHeightDp)
        addView(labelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        // heading semantics
        labelView.contentDescription = null
    }

    fun setText(text: CharSequence?) {
        labelView.text = text
        contentDescription = text
    }

    fun setTextColor(color: Int) {
        labelView.setTextColor(color)
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    override fun getAccessibilityClassName(): CharSequence = "android.widget.TextView"

    fun setTypographyRole(role: WearTypographyRole) {
        labelView.setTypographyRole(role)
    }
}

class ListSubHeaderView : FrameLayout {
    private val iconView: WearIconView
    private val labelView: WearTextView
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        iconView = WearIconView(context).apply { visibility = GONE }
        labelView = WearTextView(context).apply {
            setTypographyRole(ListHeaderTokens.LabelTypography)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 2
            setTextColor(WearColorScheme.Dark.onSurface)
        }
        val hp = dp(ListHeaderTokens.ContentPaddingHorizontalDp)
        val tp = dp(ListHeaderTokens.ContentPaddingTopDp)
        val bp = dp(ListHeaderTokens.ContentPaddingBottomDp)
        setPadding(hp, tp, hp, bp)
        minimumHeight = dp(ListHeaderTokens.ContainerHeightDp)
        addView(iconView, LayoutParams(dp(18f), dp(18f), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(labelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL).apply { marginStart = dp(8f) })
    }
    fun setIcon(drawable: android.graphics.drawable.Drawable?) {
        iconView.setIcon(drawable, null)
        iconView.visibility = if (drawable == null) GONE else VISIBLE
    }
    fun setText(text: CharSequence?) { labelView.text = text; contentDescription = text }
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
