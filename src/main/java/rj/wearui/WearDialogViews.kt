package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Native Dialog - vendored from Dialog.kt / AlertDialog.kt / ConfirmationDialog.kt
 * Full-screen scrim + Large shape container (26dp), surfaceContainer, onSurface content,
 * TitleMedium + BodyLarge, edge button at bottom. Motion: EmphasizedStandard 350ms.
 */
open class WearDialogView : FrameLayout {
    protected val scrimView: View
    protected val container: LinearLayout
    protected var dismissListener: (() -> Unit)? = null
    private var fadeAnimator: ValueAnimator? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        isClickable = true
        isFocusable = true
        scrimView = View(context).apply {
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            isClickable = true
            setOnClickListener { dismissListener?.invoke() }
        }
        addView(scrimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipToOutline = true
            // Large shape 26dp, surfaceContainer
            val radius = WearUiShapes.Default.cornerRadiusPx(WearShape.Large, 0f, 0f, resources.displayMetrics.density)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(WearColorScheme.Dark.surfaceContainer)
            }
            elevation = dp(8f).toFloat()
            // ContentPadding from AlertDialog: 14dp horizontal, 12dp vertical
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }
        val contLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        contLp.marginStart = dp(16f)
        contLp.marginEnd = dp(16f)
        addView(container, contLp)

        alpha = 0f
        visibility = GONE
    }

    fun setOnDismissListener(listener: (() -> Unit)?) { dismissListener = listener; scrimView.setOnClickListener { listener?.invoke() } }

    // Optional background view that scales to BackgroundMinScale (0.92) when dialog shows — mirrors vendored Dialog's SwipeToDismissBoxState background scaling
    var backgroundContent: View? = null
    private var backgroundAnimator: ValueAnimator? = null
    companion object {
        const val BackgroundMinScale: Float = 0.92f
    }

    open fun show(animated: Boolean = true) {
        visibility = VISIBLE
        fadeAnimator?.cancel()
        backgroundAnimator?.cancel()
        val shouldSnap = !animated || isReducedMotionRequested(context) || !isAttachedToWindow
        if (shouldSnap) {
            alpha = 1f
            backgroundContent?.let { it.scaleX = BackgroundMinScale; it.scaleY = BackgroundMinScale }
            return
        }
        val spec = WearMotionSpec(WearMotionDurations.Medium350, WearMotionEasings.EmphasizedStandard).withReducedMotion(false)
        fadeAnimator = ValueAnimator.ofFloat(alpha, 1f).apply {
            duration = spec.durationMillis
            interpolator = spec.interpolator
            addUpdateListener { alpha = it.animatedValue as Float }
            start()
        }
        backgroundContent?.let { bg ->
            bg.pivotX = bg.width / 2f
            bg.pivotY = bg.height / 2f
            val bgSpec = WearMotionSpec(WearMotionDurations.Medium350, WearMotionEasings.EmphasizedDecelerate).withReducedMotion(false)
            backgroundAnimator = ValueAnimator.ofFloat(bg.scaleX, BackgroundMinScale).apply {
                duration = bgSpec.durationMillis
                interpolator = bgSpec.interpolator
                addUpdateListener { v -> bg.scaleX = v.animatedValue as Float; bg.scaleY = v.animatedValue as Float }
                start()
            }
        }
    }

    open fun dismiss(animated: Boolean = true, onEnd: (() -> Unit)? = null) {
        fadeAnimator?.cancel()
        backgroundAnimator?.cancel()
        val shouldSnap = !animated || isReducedMotionRequested(context) || !isAttachedToWindow
        if (shouldSnap) {
            alpha = 0f
            visibility = GONE
            backgroundContent?.let { it.scaleX = 1f; it.scaleY = 1f }
            onEnd?.invoke()
            return
        }
        val spec = WearMotionSpec(WearMotionDurations.Short200, WearMotionEasings.EmphasizedAccelerate).withReducedMotion(false)
        fadeAnimator = ValueAnimator.ofFloat(alpha, 0f).apply {
            duration = spec.durationMillis
            interpolator = spec.interpolator
            addUpdateListener { alpha = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = GONE
                    onEnd?.invoke()
                }
            })
            start()
        }
        backgroundContent?.let { bg ->
            val bgSpec = WearMotionSpec(WearMotionDurations.Short200, WearMotionEasings.EmphasizedAccelerate).withReducedMotion(false)
            backgroundAnimator = ValueAnimator.ofFloat(bg.scaleX, 1f).apply {
                duration = bgSpec.durationMillis
                interpolator = bgSpec.interpolator
                addUpdateListener { v -> bg.scaleX = v.animatedValue as Float; bg.scaleY = v.animatedValue as Float }
                start()
            }
        }
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}

class WearAlertDialogView : WearDialogView {
    private val iconView: WearIconView
    private val titleView: WearTextView
    private val messageView: WearTextView
    private val buttonContainer: LinearLayout

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        iconView = WearIconView(context).apply { visibility = GONE; setIconSize(dp(32f)) }
        titleView = WearTextView(context).apply {
            setTypographyRole(WearTypographyRole.TitleMedium)
            gravity = Gravity.CENTER
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WearColorScheme.Dark.onSurface)
            visibility = GONE
        }
        messageView = WearTextView(context).apply {
            setTypographyRole(WearTypographyRole.BodyLarge)
            gravity = Gravity.CENTER
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WearColorScheme.Dark.onSurfaceVariant)
            visibility = GONE
        }
        buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = GONE
        }
        container.addView(iconView, LinearLayout.LayoutParams(dp(32f), dp(32f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8f) })
        container.addView(titleView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4f) })
        container.addView(messageView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12f) })
        container.addView(buttonContainer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setIcon(drawable: android.graphics.drawable.Drawable?, contentDescription: CharSequence? = null) {
        iconView.setIcon(drawable, contentDescription)
        iconView.visibility = if (drawable == null) GONE else VISIBLE
    }
    fun setTitle(text: CharSequence?) { titleView.text = text; titleView.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE }
    fun setMessage(text: CharSequence?) { messageView.text = text; messageView.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE }
    fun setPositiveButton(text: CharSequence, onClick: (() -> Unit)?) { addButton(text, true, onClick) }
    fun setNegativeButton(text: CharSequence, onClick: (() -> Unit)?) { addButton(text, false, onClick) }
    private fun addButton(text: CharSequence, primary: Boolean, onClick: (() -> Unit)?) {
        buttonContainer.visibility = VISIBLE
        val btn = if (primary) ButtonView(context) else TextButtonView(context)
        btn.setText(text)
        if (primary) btn.setColors(WearButtonColors(containerColor = WearColorScheme.Dark.primary, contentColor = WearColorScheme.Dark.onPrimary))
        btn.setOnClickListener { onClick?.invoke(); dismiss() }
        val lp = LinearLayout.LayoutParams(0, dp(52f), 1f)
        if (buttonContainer.childCount > 0) lp.marginStart = dp(8f)
        buttonContainer.addView(btn, lp)
    }
    fun setButtons(buttons: List<Pair<CharSequence, (() -> Unit)?>>) {
        buttonContainer.removeAllViews()
        if (buttons.isEmpty()) { buttonContainer.visibility = GONE; return }
        buttons.forEachIndexed { index, (label, action) ->
            val primary = index == buttons.size - 1
            addButton(label, primary, action)
        }
    }
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}

class WearConfirmationDialogView : WearDialogView {
    private val iconView: WearIconView
    private val curvedTextView: WearTextView
    private var autoDismissRunnable: Runnable? = null
    var durationMillis: Long = 2000L

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        // Confirmation is centered icon + optional curved text at bottom edge
        container.gravity = Gravity.CENTER
        container.setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
        iconView = WearIconView(context).apply { setIconSize(dp(48f)) }
        curvedTextView = WearTextView(context).apply {
            setTypographyRole(WearTypographyRole.BodySmall)
            gravity = Gravity.CENTER
            maxLines = 3
            setTextColor(WearColorScheme.Dark.onSurfaceVariant)
            visibility = GONE
        }
        container.addView(iconView, LinearLayout.LayoutParams(dp(48f), dp(48f)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        container.addView(curvedTextView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f); gravity = Gravity.CENTER_HORIZONTAL })
        // Confirmation scrim is more transparent, no click to dismiss
        scrimView.isClickable = false
    }

    fun setIcon(drawable: android.graphics.drawable.Drawable?) { iconView.setIcon(drawable, null) }
    fun setText(text: CharSequence?) { curvedTextView.text = text; curvedTextView.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE }
    override fun show(animated: Boolean) {
        super.show(animated)
        autoDismissRunnable?.let { removeCallbacks(it) }
        autoDismissRunnable = Runnable { dismiss() }
        postDelayed(autoDismissRunnable, durationMillis)
    }
    override fun dismiss(animated: Boolean, onEnd: (() -> Unit)?) {
        autoDismissRunnable?.let { removeCallbacks(it) }
        autoDismissRunnable = null
        super.dismiss(animated, onEnd)
    }
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
