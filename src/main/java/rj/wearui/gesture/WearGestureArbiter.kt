package rj.wearui.gesture

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import kotlin.math.abs

/** Shared pointer arbitration for edge swipes and page gestures. */
class WearGestureArbiter {
    private var root: ViewGroup? = null
    private var downX = 0f
    private var downY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var claimed = false
    private var pagerDragging = false
    private var multiTouch = false
    private var swipeEnabled = true
    private var touchSlop = 8f

    constructor() {
    }
    constructor(context: Context) {
        touchSlop = 8f * context.resources.displayMetrics.density
    }
    constructor(context: Context, attrs: AttributeSet?) {
        touchSlop = 8f * context.resources.displayMetrics.density
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        touchSlop = 8f * context.resources.displayMetrics.density
    }

    fun attach(view: ViewGroup?) {
        root = view
        view?.let { touchSlop = 8f * it.resources.displayMetrics.density }
        reset()
    }

    fun detach() {
        root = null
        reset()
    }

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
        if (!enabled) reset()
    }

    fun setPagerDragging(dragging: Boolean) {
        pagerDragging = dragging
        if (dragging) claimed = true
    }

    fun isPointerClaimed(): Boolean = claimed
    fun isMultiTouch(): Boolean = multiTouch

    /** Call from the host ViewGroup's onInterceptTouchEvent. */
    fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                activePointerId = event.getPointerId(0)
                claimed = false
                multiTouch = false
                val target = root?.findTouchTargetAt(event.x.toInt(), event.y.toInt())
                if (isExcluded(target)) claimed = true
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                claimed = true
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeEnabled || claimed || multiTouch || pagerDragging) return false
                val index = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
                val dx = event.getX(index) - downX
                val dy = event.getY(index) - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                    // The arbiter only identifies an eligible edge gesture. The navigator/host
                    // owns the actual transition and can coordinate with SwipeBackLayout.
                    val target = root?.findTouchTargetAt(downX.toInt(), downY.toInt())
                    if (!isExcluded(target) && !hasSwipeBackAncestor(target)) {
                        claimed = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> multiTouch = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
        return false
    }

    /** Call from the host while it owns an eligible gesture. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                activePointerId = event.getPointerId(0)
                return claimed
            }
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
        return claimed && !multiTouch
    }

    fun reset() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        claimed = false
        multiTouch = false
        pagerDragging = false
    }

    private fun isExcluded(view: View?): Boolean {
        var current = view
        while (current != null) {
            if (current is HorizontalScrollView || current is SeekBar) return true
            val name = current.javaClass.name
            if (name.contains("Slider", true) || name.contains("SeekBar", true) ||
                name.contains("Pager", true) || name.contains("Zoom", true) ||
                name.contains("Image", true) || name.endsWith("SwipeBackLayout")) return true
            if (current.isClickable || current.isLongClickable || current.isFocusable) return true
            current = current.parent as? View
        }
        return false
    }

    private fun hasSwipeBackAncestor(view: View?): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.javaClass.name.endsWith("SwipeBackLayout")) return true
            current = current.parent as? View
        }
        return false
    }

    private fun ViewGroup.findTouchTargetAt(x: Int, y: Int): View? {
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (x >= child.left && x < child.right && y >= child.top && y < child.bottom) {
                if (child is ViewGroup) return child.findTouchTargetAt(x - child.left, y - child.top) ?: child
                return child
            }
        }
        return this
    }
}
