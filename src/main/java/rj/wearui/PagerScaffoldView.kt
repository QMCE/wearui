package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Paint
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Native page host supporting horizontal and vertical watch paging. */
class PagerScaffoldView : ViewGroup {
    private var adapter: rj.wearui.WearPagerAdapter? = null
    private var orientation: rj.wearui.PagerOrientation? = null
    private var page = 0
    private var pageOffset = 0f
    private var swipeEnabled = true
    private var reducedMotion = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var dragStart = 0f
    private var pageAnimator: ValueAnimator? = null
    private var pageChangedListener: ((Int) -> Unit)? = null
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        clipChildren = false
        setWillNotDraw(false)
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Pages"
        indicatorPaint.style = Paint.Style.FILL
    }

    fun setAdapter(adapter: rj.wearui.WearPagerAdapter?) {
        pageAnimator?.cancel()
        this.adapter = adapter
        removeAllViews()
        page = 0
        pageOffset = 0f
        if (adapter != null) {
            for (index in 0 until adapter.getCount().coerceAtLeast(0)) {
                addView(adapter.getView(context, index, null, this))
            }
        }
        requestLayout()
        invalidate()
    }

    fun setOrientation(orientation: rj.wearui.PagerOrientation) {
        this.orientation = orientation
        requestLayout()
    }

    fun setCurrentPage(index: Int, animate: Boolean = true) {
        val count = childCount
        if (count == 0) return
        val target = index.coerceIn(0, count - 1)
        pageAnimator?.cancel()
        if (!animate || reducedMotion) {
            pageOffset = (target - page).toFloat()
            page = target
            pageOffset = 0f
            requestLayout()
            pageChangedListener?.invoke(page)
            invalidate()
            return
        }
        val startPage = page
        val distance = target - startPage
        pageAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                pageOffset = distance * fraction
                requestLayout()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    page = target
                    pageOffset = 0f
                    requestLayout()
                    pageChangedListener?.invoke(page)
                }
            })
            start()
        }
    }

    fun getCurrentPage(): Int = page
    fun setSwipeEnabled(enabled: Boolean) { swipeEnabled = enabled }
    fun setOnPageChangedListener(listener: ((Int) -> Unit)?) { pageChangedListener = listener }
    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) {
            pageAnimator?.cancel()
            pageOffset = 0f
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
            )
        }
    }

    private fun vertical(): Boolean = orientation?.toString()?.contains("VERT", true) == true

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val distance = if (vertical()) height else width
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val relative = index - page - pageOffset
            val offset = (relative * distance).toInt()
            if (vertical()) child.layout(0, offset, width, offset + height)
            else child.layout(offset, 0, offset + width, height)
            val leading = if (relative >= 0f) 1f else 0f
            val amount = abs(relative).coerceAtMost(1f)
            val scale = if (reducedMotion) 1f else 1f - .45f * amount
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = if (reducedMotion) 1f else 1f - .5f * amount
            if (!reducedMotion) {
                child.pivotX = if (relative >= 0f) 0f else width.toFloat()
                child.pivotY = if (relative >= 0f) 0f else height.toFloat()
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled || childCount < 2) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragStart = if (vertical()) event.y else event.x
                dragging = false
                pageAnimator?.cancel()
            }
            MotionEvent.ACTION_POINTER_DOWN -> return false
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1) return false
                val dx = event.x - downX
                val dy = event.y - downY
                val primary = if (vertical()) dy else dx
                val cross = if (vertical()) dx else dy
                if (abs(primary) > dp(8f) && abs(primary) > abs(cross) * 1.2f) {
                    dragging = true
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> dragging = false
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled || childCount < 2) return false
        val distance = if (vertical()) height else width
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStart = if (vertical()) event.y else event.x
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && dragging) {
                val current = if (vertical()) event.y else event.x
                pageOffset = ((dragStart - current) / max(1, distance).toFloat()).coerceIn(-1f, 1f)
                requestLayout()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val target = when {
                        pageOffset > .25f -> page + 1
                        pageOffset < -.25f -> page - 1
                        else -> page
                    }
                    pageOffset = 0f
                    dragging = false
                    setCurrentPage(target, animate = true)
                    performClick()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                setCurrentPage(page, animate = true)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (childCount <= 1) return
        val count = childCount
        val active = page + pageOffset
        indicatorPaint.color = 0xFFFFFFFF.toInt()
        val dot = dp(4f).toFloat()
        val gap = dp(5f).toFloat()
        if (!vertical()) {
            val total = count * dot + (count - 1) * gap
            val start = (width - total) / 2f
            val y = height - dp(10f).toFloat()
            for (i in 0 until count) {
                val x = start + i * (dot + gap)
                indicatorPaint.alpha = if (abs(active - i) < .5f) 255 else 100
                canvas.drawCircle(x + dot / 2f, y, dot / 2f, indicatorPaint)
            }
        } else {
            val total = count * dot + (count - 1) * gap
            val x = width - dp(10f).toFloat()
            val start = (height - total) / 2f
            for (i in 0 until count) {
                val y = start + i * (dot + gap)
                indicatorPaint.alpha = if (abs(active - i) < .5f) 255 else 100
                canvas.drawCircle(x, y + dot / 2f, dot / 2f, indicatorPaint)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val round = abs(width - height) <= max(width, height) * .14f
        if (!round) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        clipPath.reset()
        clipPath.addCircle(width / 2f, height / 2f, min(width, height) / 2f, Path.Direction.CW)
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() {
        pageAnimator?.cancel()
        pageAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable("super", super.onSaveInstanceState())
        state.putInt("page", page)
        state.putBoolean("swipe", swipeEnabled)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("super"))
            page = state.getInt("page", 0).coerceIn(0, max(0, childCount - 1))
            swipeEnabled = state.getBoolean("swipe", true)
            requestLayout()
        } else super.onRestoreInstanceState(state)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
