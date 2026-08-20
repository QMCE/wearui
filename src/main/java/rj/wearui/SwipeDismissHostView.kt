package rj.wearui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A platform FrameLayout that renders the navigator's immediate previous route as a noninteractive
 * swipe background. It retains route instances by [ScreenEntry.id] so a promotion preserves state.
 */
open class SwipeDismissHostView : FrameLayout {
    private var navigator: SwipeDismissNavigator? = null
    private var factory: rj.wearui.WearScreenFactory? = null
    private val routeViews = LinkedHashMap<String, View>()
    private var foreground: View? = null
    private var background: View? = null
    private var lastCurrentId: String? = null
    private var userSwipeEnabled = true
    private var reducedMotion = false
    private var activePointer = -1
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracking = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var transitionAnimator: ValueAnimator? = null
    private var scrimAmount = 0f
    private var flashAmount = 0f

    private val navigationListener = object : rj.wearui.WearScreenListener {
        override fun onScreenChanged(current: rj.wearui.ScreenEntry?, previous: rj.wearui.ScreenEntry?) {
            val isPopPromotion = previous != null && current?.id == backgroundEntryId()
            renderStack(animateForward = !isPopPromotion && previous != null)
        }
    }

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        isClickable = true
        isFocusable = true
        clipChildren = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Navigation"
    }

    fun setNavigator(value: SwipeDismissNavigator?) {
        if (navigator === value) return
        navigator?.removeListener(navigationListener)
        navigator = value
        value?.addListener(navigationListener)
        cancelTransition()
        renderStack(false)
    }

    fun getNavigator(): SwipeDismissNavigator? = navigator

    fun setScreenFactory(value: rj.wearui.WearScreenFactory?) {
        if (factory === value) return
        factory = value
        routeViews.clear()
        removeAllViews()
        foreground = null
        background = null
        lastCurrentId = null
        renderStack(false)
    }

    fun setUserSwipeEnabled(enabled: Boolean) {
        userSwipeEnabled = enabled
        navigator?.setUserSwipeEnabled(enabled)
        if (!enabled) cancelInteractiveDismissal()
    }

    fun isUserSwipeEnabled(): Boolean = userSwipeEnabled

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) cancelTransition()
    }

    fun isReducedMotionEnabled(): Boolean = reducedMotion

    /** Forces creation and composition of the current/previous navigator entries. */
    fun renderCurrent() = renderStack(false)

    /** Programmatic equivalent of a completed user dismissal. */
    fun dismissCurrent(): Boolean {
        val nav = navigator ?: return false
        if (!userSwipeEnabled || !nav.beginDismissal()) return false
        completeDismissal(animated = !reducedMotion)
        return true
    }

    private fun ensureView(entry: rj.wearui.ScreenEntry?): View? {
        if (entry == null) return null
        val cached = routeViews[entry.id]
        if (cached != null) return cached
        val created = factory?.create(context, entry) ?: return null
        routeViews[entry.id] = created
        return created
    }

    private fun backgroundEntryId(): String? {
        val entry = navigator?.previous ?: return null
        return entry.id
    }

    private fun renderStack(animateForward: Boolean) {
        val nav = navigator
        val currentEntry = nav?.current
        val previousEntry = nav?.previous
        val activeEntry = currentEntry ?: run {
            removeAllViews()
            foreground = null
            background = null
            lastCurrentId = null
            return
        }
        val currentView = ensureView(activeEntry)
        val previousView = ensureView(previousEntry)
        if (currentView == null) {
            removeAllViews()
            foreground = null
            background = null
            lastCurrentId = null
            return
        }

        cancelTransition()
        if (previousView !== currentView && previousView != null) {
            attachAt(previousView, 0)
            prepareBackground(previousView)
            background = previousView
        } else {
            background?.let { if (it.parent === this) removeView(it) }
            background = null
        }
        attachAt(currentView, if (background == null) 0 else 1)
        prepareForeground(currentView)
        foreground = currentView

        // Keep only retained stack screens. This removes stale entries after a completed pop while
        // retaining the immediate background until the transition that promotes it is over.
        val retained = HashSet<String>()
        activeEntry.let { retained.add(it.id) }
        previousEntry?.let { retained.add(it.id) }
        val iterator = routeViews.entries.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (!retained.contains(candidate.key)) {
                val view = candidate.value
                if (view.parent === this) removeView(view)
                iterator.remove()
            }
        }

        contentDescription = activeEntry.title ?: activeEntry.id
        val changed = lastCurrentId != activeEntry.id
        lastCurrentId = activeEntry.id
        if (changed && animateForward && !reducedMotion) runForwardTransition(currentView)
        invalidate()
    }

    private fun attachAt(view: View, index: Int) {
        val parent = view.parent
        if (parent !== this) {
            if (parent is ViewGroup) parent.removeView(view)
            addView(view, min(index, childCount), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            val target = min(index, childCount - 1)
            if (indexOfChild(view) != target) {
                removeView(view)
                addView(view, target, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
        }
    }

    private fun prepareForeground(view: View) {
        view.visibility = VISIBLE
        view.isEnabled = true
        view.isClickable = true
        view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
    }

    private fun prepareBackground(view: View) {
        view.visibility = VISIBLE
        view.isEnabled = false
        view.isClickable = false
        view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.alpha = 1f
        view.scaleX = .92f
        view.scaleY = .92f
        view.translationX = -width * .20f
    }

    private fun runForwardTransition(view: View) {
        view.scaleX = .75f
        view.scaleY = .75f
        view.alpha = .10f
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = PathInterpolator(.4f, 0f, .2f, 1f)
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                view.scaleX = .75f + .25f * fraction
                view.scaleY = .75f + .25f * fraction
                view.alpha = .10f + .90f * fraction
                val flashProgress = ((fraction - .25f) / .75f).coerceIn(0f, 1f)
                val flashEase = PathInterpolator(.4f, 0f, .2f, 1f).getInterpolation(flashProgress)
                flashAmount = .07f * (1f - flashEase)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (transitionAnimator === animation) {
                        transitionAnimator = null
                        scrimAmount = 0f
                        flashAmount = 0f
                        prepareForeground(view)
                    }
                }
            })
            start()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !userSwipeEnabled || navigator?.isUserSwipeEnabled() != true) return false
        if (event.pointerCount > 1) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                downX = event.x
                downY = event.y
                dragging = false
                tracking = navigator?.canPop() == true && downX <= max(width * .16f, densityDp(24f))
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = event.x - downX
                val dy = event.y - downY
                if (dx > touchSlop && dx > abs(dy) * 1.25f) {
                    if (navigator?.beginDismissal() == true) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    tracking = false
                } else if (abs(dy) > touchSlop || dx < -touchSlop) {
                    tracking = false
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> tracking = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging && event.actionMasked != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    cancelInteractiveDismissal()
                    return false
                }
                val offset = (event.x - downX).coerceIn(0f, width.toFloat())
                applyDismissProgress(if (width > 0) offset / width else 0f)
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val velocity = velocityTracker?.xVelocity ?: 0f
                val progress = if (width > 0) (event.x - downX).coerceIn(0f, width.toFloat()) / width else 0f
                if (progress >= .5f || velocity > 800f * resources.displayMetrics.density) completeDismissal(!reducedMotion) else cancelInteractiveDismissal()
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelInteractiveDismissal()
                recycleVelocity()
            }
        }
        return true
    }

    /** Applies a seekable back progress; PredictiveBackHostView uses this for platform callbacks. */
    protected fun applyDismissProgress(progress: Float) {
        val value = progress.coerceIn(0f, 1f)
        val front = foreground ?: return
        front.scaleX = 1f - .3f * value
        front.scaleY = 1f - .3f * value
        front.translationX = (1f - front.scaleX) * width / 2f
        background?.let {
            it.translationX = -width * .20f * (1f - value)
            it.scaleX = .92f + .08f * value
            it.scaleY = .92f + .08f * value
        }
        scrimAmount = .50f * (1f - value)
        invalidate()
    }

    /** Completes or cancels a predictive/touch dismiss. */
    protected fun finishDismissal(commit: Boolean) {
        if (commit) completeDismissal(!reducedMotion) else cancelInteractiveDismissal()
    }

    private fun completeDismissal(animated: Boolean) {
        val front = foreground
        if (front == null || !animated) {
            navigator?.completeDismissal()
            renderStack(false)
            return
        }
        cancelTransition()
        val start = (front.translationX / max(1, width)).coerceIn(0f, 1f)
        transitionAnimator = ValueAnimator.ofFloat(start, 1f).apply {
            duration = 200L
            interpolator = PathInterpolator(0f, 0f, .2f, 1f)
            addUpdateListener { applyDismissProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (transitionAnimator === animation) {
                        transitionAnimator = null
                        navigator?.completeDismissal()
                        renderStack(false)
                    }
                }
            })
            start()
        }
    }

    private fun cancelInteractiveDismissal() {
        if (navigator?.isDismissalPending() != true) return
        val front = foreground
        if (front == null || reducedMotion) {
            navigator?.cancelDismissal()
            prepareForeground(front ?: return)
            background?.let(::prepareBackground)
            scrimAmount = 0f
            invalidate()
            return
        }
        cancelTransition()
        val start = (front.translationX / max(1, width)).coerceIn(0f, 1f)
        transitionAnimator = ValueAnimator.ofFloat(start, 0f).apply {
            duration = 200L
            interpolator = PathInterpolator(0f, 0f, .2f, 1f)
            addUpdateListener { applyDismissProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (transitionAnimator === animation) {
                        transitionAnimator = null
                        navigator?.cancelDismissal()
                        prepareForeground(front)
                        background?.let(::prepareBackground)
                        scrimAmount = 0f
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
        dragging = false
        tracking = false
        activePointer = -1
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelTransition() {
        transitionAnimator?.cancel()
        transitionAnimator = null
    }

    private fun densityDp(value: Float): Float = value * resources.displayMetrics.density

    override fun dispatchDraw(canvas: Canvas) {
        if (!isRoundHost()) {
            super.dispatchDraw(canvas)
        } else {
            val save = canvas.save()
            val radius = min(width, height) / 2f
            canvas.clipPath(Path().apply { addCircle(width / 2f, height / 2f, radius, Path.Direction.CW) })
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        }
        if (scrimAmount > 0f) canvas.drawColor((scrimAmount.coerceIn(0f, 1f) * 255).toInt() shl 24)
        if (flashAmount > 0f) {
            val flashAlpha = (flashAmount.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawColor(flashAlpha shl 24 or 0xFFFFFF)
        }
    }

    private fun isRoundHost(): Boolean = width > 0 && height > 0 && abs(width - height) <= min(width, height) * .12f

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == 8192 && dismissCurrent()) return true
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        cancelTransition()
        recycleVelocity()
        navigator?.cancelDismissal()
        for (view in routeViews.values) view.animate().cancel()
        super.onDetachedFromWindow()
    }
}
