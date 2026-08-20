package rj.wearui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.accessibility.AccessibilityEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.ArrayDeque

/** A small, platform-only adapter list used by the advanced Wear UI components. */
abstract class AdapterColumnView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    private var itemAdapter: rj.wearui.WearItemAdapter? = null
    private var spacingPx = 0
    private var leftInset = 0
    private var topInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private var reverse = false
    private var centerContent = false
    private var anchor = 0
    private var touchSlop = 8
    private var dragging = false
    private var lastTouchY = 0f
    private var lastTouchX = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private val scroller = OverScroller(context).apply { setFriction(0.09f) }
    private var lastScroll = 0
    private var oldItemCount = 0
    private var metricsProvider: ScrollMetricsProvider? = null
    private var metricsListener: WearScrollMetricsListener? = null

    // Lazy windowing state
    private val recycledPool = ArrayDeque<View>()
    private val activeViews = LinkedHashMap<Int, View>()
    private val heightCache = HashMap<Int, Int>()
    private var averageHeightPx: Int = -1
    private var viewportHeight: Int = 0

    /**
     * Fisheye transform gate: when null (default), rows render flat (no scale/alpha/translate) on a
     * non-round host and scale like Wear Compose on a round host. True/false force the transform on or
     * off regardless of the host shape. Default auto-detect is [RoundScreenMetrics.isRound].
     */
    var roundScalingOverride: Boolean? = null
        set(value) { field = value; requestLayout(); invalidate() }

    /** Whether the fisheye/edge transforms should apply on the current host. */
    protected fun scalingEnabled(): Boolean = roundScalingOverride ?: RoundScreenMetrics.from(this).isRound

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        isVerticalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        isClickable = false
        isFocusableInTouchMode = false
    }

    fun setAdapter(adapter: rj.wearui.WearItemAdapter?) {
        if (itemAdapter === adapter) {
            refresh()
            return
        }
        itemAdapter = adapter
        refresh()
    }

    fun getAdapter(): rj.wearui.WearItemAdapter? = itemAdapter
    fun setItemAdapter(adapter: rj.wearui.WearItemAdapter?) = setAdapter(adapter)

    fun setItemSpacingPx(spacing: Int) {
        spacingPx = max(0, spacing)
        requestLayout()
    }

    fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        leftInset = max(0, left)
        topInset = max(0, top)
        rightInset = max(0, right)
        bottomInset = max(0, bottom)
        requestLayout()
    }

    fun setReverseLayout(enabled: Boolean) {
        if (reverse != enabled) {
            reverse = enabled
            refresh()
        }
    }

    fun isReverseLayout(): Boolean = reverse

    fun setAutoCenterContent(enabled: Boolean) {
        centerContent = enabled
        requestLayout()
    }

    fun isAutoCenterContent(): Boolean = centerContent

    fun setAnchorItem(position: Int) {
        anchor = max(0, position)
        if (childCount > 0 || (itemAdapter?.getCount() ?: 0) > 0) post { scrollToPosition(anchor, false) }
    }

    fun getAnchorItem(): Int = anchor

    fun refresh() {
        scroller.abortAnimation()
        // Recycle all active views
        for ((_, v) in activeViews) {
            recycledPool.addLast(v)
            removeViewInLayout(v)
        }
        activeViews.clear()
        val count = itemAdapter?.getCount() ?: 0
        if (count < oldItemCount / 2) heightCache.clear()
        // Warm height cache for precise scrollRange: measure off-screen items with recycled views
        // This matches vendored ScalingLazyColumn's pre-measurement for totalContentHeight without
        // keeping all views attached (only windowed views stay in ViewGroup).
        if (count in 1..120 && width > 0) {
            val childWidth = max(0, width - leftInset - rightInset)
            if (childWidth > 0) warmHeightCache(childWidth, count)
        }
        oldItemCount = count
        if (count == 0) scrollTo(0, 0)
        else clampScroll()
        requestLayout()
        invalidate()
        announceForAccessibility("List updated")
    }

    private fun warmHeightCache(childWidth: Int, count: Int) {
        var warmed = 0
        for (i in 0 until count) {
            if (heightCache.containsKey(i)) continue
            if (warmed >= 200) break // allow full warm for 200 items (bottom visibility)
            val recycled = if (recycledPool.isNotEmpty()) recycledPool.removeFirst() else null
            val view = try { itemAdapter?.getView(context, i, recycled, this) } catch (_: Throwable) { null }
            if (view != null) {
                if (view.layoutParams == null) view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                // Ensure MarginLayoutParams for measure
                if (view.layoutParams !is MarginLayoutParams) view.layoutParams = MarginLayoutParams(view.layoutParams)
                view.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val h = view.measuredHeight.coerceAtLeast((52 * resources.displayMetrics.density).toInt())
                heightCache[i] = h
                if (averageHeightPx < 0) averageHeightPx = h else averageHeightPx = (averageHeightPx * 0.9f + h * 0.1f).toInt()
                // recycle immediately - don't keep attached
                recycledPool.addLast(view)
                warmed++
            } else if (recycled != null) recycledPool.addLast(recycled)
        }
    }

    fun getItemCount(): Int = itemAdapter?.getCount() ?: activeViews.size

    fun getFirstVisiblePosition(): Int {
        if (activeViews.isEmpty()) return -1
        // ActiveViews keys are adapter positions (already windowed)
        return activeViews.keys.minOrNull() ?: -1
    }
    fun getLastVisiblePosition(): Int {
        if (activeViews.isEmpty()) return -1
        return activeViews.keys.maxOrNull() ?: -1
    }

    fun scrollToPosition(position: Int, smooth: Boolean = true) {
        val count = itemAdapter?.getCount() ?: 0
        if (count == 0) return
        val targetPos = position.coerceIn(0, count - 1)
        val target = getScrollForPosition(targetPos)
        if (smooth) scroller.startScroll(scrollX, scrollY, 0, target - scrollY, 300) else scrollTo(0, target)
        if (smooth) postInvalidateOnAnimation() else dispatchScrollChanged()
    }

    private fun getScrollForPosition(position: Int): Int {
        // Compute offset of adapter position from top, accounting for spacing and insets
        var y = topInset
        for (i in 0 until position) {
            y += getEstimatedHeight(i) + spacingPx
        }
        if (centerContent && totalContentHeight() < viewportHeight) {
            // Centered content: keep whole list centered, so requested position should still respect centering
            // Instead of scrolling to top, we allow negative? Keep simple: no scroll
            return 0
        }
        // Center the target row vertically if possible (optional). Vendored ScalingLazyColumn uses anchor + center;
        // here we scroll so target is at top inset (like list). For compatibility with ChatList anchor, scroll to show it.
        // If anchor centering is desired, offset by half viewport
        if (centerContent && viewportHeight > 0) {
            val h = getEstimatedHeight(position)
            y -= (viewportHeight - h) / 2
        }
        return max(0, y - topInset)
    }

    private fun getEstimatedHeight(position: Int): Int {
        heightCache[position]?.let { return it }
        if (averageHeightPx > 0) return averageHeightPx
        // Default estimate: 52dp minHeight + small slack
        return (52 * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun totalContentHeight(): Int {
        val count = itemAdapter?.getCount() ?: 0
        if (count == 0) return topInset + bottomInset
        var sum = topInset + bottomInset
        for (i in 0 until count) sum += getEstimatedHeight(i)
        sum += max(0, count - 1) * spacingPx
        // If centered and content smaller than viewport, total is viewport for centering math
        if (centerContent && viewportHeight > sum) return viewportHeight
        return sum
    }

    fun getScrollOffsetPx(): Int = scrollY
    fun getScrollRangePx(): Int = max(0, totalContentHeight() - viewportHeight)
    fun canScrollForward(): Boolean = scrollY < getScrollRangePx()
    fun canScrollBackward(): Boolean = scrollY > 0
    fun getScrollMetricsProvider(): ScrollMetricsProvider {
        val existing = metricsProvider
        if (existing != null) return existing
        return ScrollMetricsProvider.fromView(this).also { provider ->
            metricsProvider = provider
            metricsListener?.let { provider.addListener(it) }
        }
    }
    fun setOnScrollMetricsListener(listener: WearScrollMetricsListener?) {
        val provider = metricsProvider
        metricsListener?.let { provider?.removeListener(it) }
        metricsListener = listener
        listener?.let { getScrollMetricsProvider().addListener(it) }
    }

    private fun displayPosition(index: Int): Int = if (reverse) (itemAdapter?.getCount() ?: childCount) - 1 - index else index
    // For lazy window, child indices are windowed; top is computed via cumulative heights
    private fun windowStartOffset(): Int {
        // Offset of first active view from topInset
        val first = getFirstVisiblePosition()
        if (first <= 0) return topInset
        var y = topInset
        for (i in 0 until first) y += getEstimatedHeight(i) + spacingPx
        return y
    }

    private fun contentHeight(): Int = totalContentHeight()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        viewportHeight = height
        val childWidth = max(0, width - leftInset - rightInset)
        // Warm cache before window fill for exact totalContentHeight
        if (heightCache.size < (itemAdapter?.getCount() ?: 0) && childWidth > 0) {
            val cnt = itemAdapter?.getCount() ?: 0
            if (cnt in 1..200) warmHeightCache(childWidth, cnt)
        }
        // Ensure window is filled for measurement (creates needed children, recycles off-screen)
        fillWindow(childWidth, height)
        for ((_, child) in activeViews) {
            if (child.layoutParams !is MarginLayoutParams) {
                child.layoutParams = MarginLayoutParams(child.layoutParams)
            }
            measureChildWithMargins(child, MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), 0,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0)
            // Update cache with measured height
            val pos = activeViews.entries.find { it.value === child }?.key ?: continue
            val h = child.measuredHeight
            if (h > 0) {
                heightCache[pos] = h
                if (averageHeightPx < 0) averageHeightPx = h
                else averageHeightPx = (averageHeightPx * 0.85f + h * 0.15f).toInt()
            }
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Ensure window reflects current scrollY and viewport
        val childWidth = max(0, width - leftInset - rightInset)
        fillWindow(childWidth, height)
        // Re-measure if any new children added after scroll
        for ((_, child) in activeViews) {
            if (child.measuredWidth != childWidth || child.measuredHeight == 0) {
                measureChildWithMargins(child, MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), 0,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0)
                val pos = activeViews.entries.find { it.value === child }?.key ?: continue
                heightCache[pos] = child.measuredHeight
            }
        }
        // Layout children sequentially starting from window start offset minus scrollY
        var y = windowStartOffset() - scrollY
        // Apply centering if content shorter than viewport
        if (centerContent) {
            val total = totalContentHeight()
            if (total < height) y += (height - total) / 2
            // Also include topInset already in total, but windowStart already includes it; adjust for centering offset
            // The extra centering is the difference between viewport and content
        }
        // Iterate in adapter order sorted
        val sortedPositions = activeViews.keys.sorted()
        val iter = if (reverse) sortedPositions.reversed() else sortedPositions
        for (pos in iter) {
            val child = activeViews[pos] ?: continue
            val w = max(0, width - leftInset - rightInset)
            // Handle reverse visual order: reverse flips adapter order visually
            // We already iterated reversed, but y still increases downwards, so reverse will show last items at top?
            // For reverse layout, we want item 0 at bottom; simplest: compute y as above for forward, then when reverse,
            // layout from bottom. Instead, we keep forward layout but scroll range inverted? For now keep forward semantics;
            // reverse flag mainly affects scroll direction and position mapping, not visual stacking here.
            child.layout(leftInset, y, leftInset + w, y + child.measuredHeight)
            y += child.measuredHeight + spacingPx
        }
        clampScroll()
        applyTransforms()
    }

    private fun fillWindow(childWidth: Int, viewportH: Int) {
        val adapter = itemAdapter ?: run {
            // No adapter: clear
            for ((_, v) in activeViews) {
                recycledPool.addLast(v)
                removeViewInLayout(v)
            }
            activeViews.clear()
            return
        }
        val count = adapter.getCount()
        if (count == 0) {
            for ((_, v) in activeViews) {
                recycledPool.addLast(v)
                removeViewInLayout(v)
            }
            activeViews.clear()
            return
        }
        if (viewportH <= 0) viewportH.let { viewportHeight = it }
        // Estimate visible window expanded by one screen buffer on each side for smoothness
        val visibleTop = scrollY
        val visibleBottom = scrollY + max(viewportH, 1)
        val buffer = max(viewportH * 5, averageHeightPx * 12)
        val windowTop = max(0, visibleTop - buffer)
        val windowBottom = visibleBottom + buffer

        // Find start/end positions whose estimated offsets intersect window
        var y = topInset
        var startPos = 0
        var endPos = count - 1
        var foundStart = false
        for (i in 0 until count) {
            val h = getEstimatedHeight(i)
            val top = y
            val bottom = y + h
            if (!foundStart && bottom >= windowTop) {
                startPos = i
                foundStart = true
            }
            if (bottom >= windowBottom) {
                endPos = i
                break
            }
            y += h + spacingPx
        }
        if (!foundStart) {
            startPos = max(0, count - 1)
            endPos = count - 1
        }
        // Add overdraw of 6 items each side for transform continuity (RecyclerView prefetch style)
        startPos = max(0, startPos - 6)
        endPos = min(count - 1, endPos + 6)

        // Recycle views that fell out of window
        val toRemove = activeViews.keys.filter { it < startPos || it > endPos }
        for (pos in toRemove) {
            val v = activeViews.remove(pos) ?: continue
            recycledPool.addLast(v)
            removeViewInLayout(v)
        }
        // Create views for new positions in window
        for (pos in startPos..endPos) {
            if (activeViews.containsKey(pos)) continue
            val recycled = if (recycledPool.isNotEmpty()) recycledPool.removeFirst() else null
            val child = adapter.getView(context, pos, recycled, this)
            if (child.parent != null && child.parent !== this) (child.parent as ViewGroup).removeView(child)
            // Ensure layout params
            if (child.layoutParams == null) child.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addViewInLayout(child, -1, child.layoutParams, true)
            activeViews[pos] = child
        }
        // Keep child order sorted for ViewGroup ordering (for accessibility and drawing)
        // Detach and re-add in sorted order if needed
        val sorted = activeViews.keys.sorted()
        for (i in sorted.indices) {
            val pos = sorted[i]
            val v = activeViews[pos] ?: continue
            val currentIndex = indexOfChild(v)
            if (currentIndex != i) {
                detachViewFromParent(v)
                attachViewToParent(v, i, v.layoutParams)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewportHeight = h
        clampScroll()
        applyTransforms()
    }

    private fun clampScroll() {
        val range = getScrollRangePx()
        if (scrollY > range || scrollY < 0) scrollTo(0, scrollY.coerceIn(0, range))
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(0, y.coerceIn(0, getScrollRangePx()))
        if (lastScroll != scrollY) {
            lastScroll = scrollY
            // Window may need to shift
            if (width > 0 && height > 0) {
                val childWidth = max(0, width - leftInset - rightInset)
                fillWindow(childWidth, height)
                // Re-layout after window shift (without full requestLayout to keep scroll smooth)
                // Force re-layout of children positions
                var curY = windowStartOffset() - scrollY
                if (centerContent && totalContentHeight() < height) curY += (height - totalContentHeight()) / 2
                for (pos in activeViews.keys.sorted()) {
                    val child = activeViews[pos] ?: continue
                    val w = max(0, width - leftInset - rightInset)
                    child.layout(leftInset, curY, leftInset + w, curY + child.measuredHeight)
                    curY += child.measuredHeight + spacingPx
                }
            }
            applyTransforms()
            dispatchScrollChanged()
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    private fun dispatchScrollChanged() {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        metricsProvider?.refresh()
        invalidate()
    }

    protected abstract fun transformChild(child: View, distanceFromCenter: Float, viewportCenter: Float)
    private fun applyTransforms() {
        if (height <= 0) return
        val center = scrollY + height / 2f
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childCenter = child.top + child.measuredHeight / 2f
            transformChild(child, childCenter - center, height / 2f)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                lastTouchY = event.y
                lastTouchX = event.x
                initialTouchX = event.x
                initialTouchY = event.y
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                scroller.abortAnimation()
                // Let child handle down
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - initialTouchY
                val dx = event.x - initialTouchX
                if (!dragging && abs(dy) > touchSlop * 1.8f && abs(dy) > abs(dx) * 1.5f) dragging = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.pointerCount > 1) return true
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                lastTouchX = event.x
                initialTouchX = event.x
                initialTouchY = event.y
                scroller.abortAnimation()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (abs(dy) > 0) {
                    // Only start dragging after passing slop and predominantly vertical
                    val totalDy = event.y - initialTouchY
                    val totalDx = event.x - initialTouchX
                    if (!dragging && abs(totalDy) > touchSlop * 1.8f && abs(totalDy) > abs(totalDx) * 1.5f) dragging = true
                    if (dragging) scrollBy(0, dy.toInt())
                }
                lastTouchY = event.y
                lastTouchX = event.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && dragging) {
                    velocityTracker?.computeCurrentVelocity(1000, 8000f)
                    var vy = velocityTracker?.yVelocity ?: 0f
                    // Watch-tuned: 0.08x and 700 cap + high friction (0.09) for 1:1 drag feel, mimics RecyclerView snap decay
                    vy *= 0.08f
                    vy = vy.coerceIn(-700f, 700f)
                    if (abs(vy) > 60f) scroller.fling(0, scrollY, 0, -vy.toInt(), 0, 0, 0, getScrollRangePx())
                }
                dragging = false
                velocityTracker?.recycle(); velocityTracker = null
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        postInvalidateOnAnimation()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> { smoothScrollBy(0, max(1, height * 3 / 4)); true }
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> { smoothScrollBy(0, -max(1, height * 3 / 4)); true }
            AccessibilityNodeInfoCompat.ACTION_SCROLL_UP -> { smoothScrollBy(0, -max(1, height * 3 / 4)); true }
            AccessibilityNodeInfoCompat.ACTION_SCROLL_DOWN -> { smoothScrollBy(0, max(1, height * 3 / 4)); true }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    private fun smoothScrollBy(dx: Int, dy: Int) {
        scroller.startScroll(scrollX, scrollY, dx, dy, 300)
        postInvalidateOnAnimation()
    }

    override fun computeVerticalScrollRange(): Int = max(height, contentHeight())
    override fun computeVerticalScrollOffset(): Int = scrollY
    override fun computeVerticalScrollExtent(): Int = height.coerceAtLeast(0)

    override fun onDetachedFromWindow() {
        scroller.abortAnimation()
        velocityTracker?.recycle(); velocityTracker = null
        metricsProvider?.dispose()
        metricsProvider = null
        for (i in 0 until childCount) getChildAt(i).animate().cancel()
        super.onDetachedFromWindow()
    }
}

private object AccessibilityNodeInfoCompat {
    const val ACTION_SCROLL_FORWARD = 4096
    const val ACTION_SCROLL_BACKWARD = 8192
    const val ACTION_SCROLL_UP = 16908344
    const val ACTION_SCROLL_DOWN = 16908346
}


class ScalingLazyColumnView : AdapterColumnView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Vendored ResponsiveTransformationSpec.smallScreen/largeScreen: scale 0.70/0.60,
    // alpha 0.50, easing CubicBezier 0.3,0,0.7,1, transition zones
    // derived from scrollProgress/TransitionAreaProgress. Native port
    // approximates the same curve via distance-fraction -> eased lerp.
    var minChildScale: Float = 0.70f
        set(value) { field = value.coerceIn(0.35f, 1f); invalidate() }
    var minChildAlpha: Float = 0.50f
        set(value) { field = value.coerceIn(0.1f, 1f); invalidate() }

    private val transformEasing = PathInterpolator(0.3f, 0f, 0.7f, 1f)

    override fun transformChild(child: View, distanceFromCenter: Float, viewportCenter: Float) {
        if (!scalingEnabled()) {
            // Non-round hosts render a flat list (no visible fisheye).
            child.pivotX = child.measuredWidth / 2f
            child.pivotY = child.measuredHeight / 2f
            child.scaleX = 1f
            child.scaleY = 1f
            child.alpha = 1f
            child.translationY = 0f
            child.visibility = VISIBLE
            return
        }
        val rawFraction = (abs(distanceFromCenter) / max(1f, viewportCenter)).coerceIn(0f, 1f)
        val eased = transformEasing.getInterpolation(rawFraction)
        val scale = 1f - (1f - minChildScale) * eased
        child.pivotX = child.measuredWidth / 2f
        child.pivotY = child.measuredHeight / 2f
        child.scaleX = scale
        child.scaleY = scale
        // Vendored container+content alpha both to 0.5 edge; unified here as single alpha.
        child.alpha = 1f - (1f - minChildAlpha) * eased
        // Matches vendored applyContainerTransformation:
        // translationY = -height*(1-scale)/2 to keep scaled item anchored.
        child.translationY = -child.measuredHeight * (1f - scale) / 2f
        child.visibility = if (child.alpha < 0.02f) INVISIBLE else VISIBLE
    }
}

/** Lazy column with a less aggressive transform suitable for text-heavy screens. */
class TransformingLazyColumnView : AdapterColumnView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Vendored ResponsiveTransformationSpec for Transforming: same family as
    // Scaling (small 0.70, large 0.60 with 0.50 alpha, 0.3/0,0.7/1 easing).
    // Previous native default 0.82/0.65 linear was too shallow; align to
    // vendored 0.70 edge so both lazy variants share the spec source.
    var edgeScale: Float = 0.70f
        set(value) { field = value.coerceIn(0.5f, 1f); invalidate() }
    var edgeAlpha: Float = 0.50f
        set(value) { field = value.coerceIn(0.1f, 1f); invalidate() }

    private val transformEasing = PathInterpolator(0.3f, 0f, 0.7f, 1f)

    override fun transformChild(child: View, distanceFromCenter: Float, viewportCenter: Float) {
        if (!scalingEnabled()) {
            // Non-round hosts render a flat list (no visible transform).
            child.pivotX = child.measuredWidth / 2f
            child.pivotY = child.measuredHeight / 2f
            child.scaleX = 1f
            child.scaleY = 1f
            child.alpha = 1f
            child.translationY = 0f
            child.visibility = VISIBLE
            return
        }
        val rawFraction = (abs(distanceFromCenter) / max(1f, viewportCenter)).coerceIn(0f, 1f)
        val eased = transformEasing.getInterpolation(rawFraction)
        val scale = 1f - (1f - edgeScale) * eased
        // Vendored transformOrigin is center; native pivot centered matches
        // GraphicsLayerScope default so scale shrinks symmetrically.
        child.pivotX = child.measuredWidth / 2f
        child.pivotY = child.measuredHeight / 2f
        child.scaleX = scale
        child.scaleY = scale
        child.alpha = 1f - (1f - edgeAlpha) * eased
        // Vendored container translation: -height*(1-scale)/2 (see
        // ResponsiveTransformationSpecImpl.applyContainerTransformation).
        // Replaces the prior -distance*0.035 approximation.
        child.translationY = -child.measuredHeight * (1f - scale) / 2f
        child.visibility = if (child.alpha < 0.02f) INVISIBLE else VISIBLE
    }
}

/** Standard adapter-backed list with no visual transformation. */
class WearListView : AdapterColumnView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun transformChild(child: View, distanceFromCenter: Float, viewportCenter: Float) {
        child.alpha = 1f
        child.scaleX = 1f
        child.scaleY = 1f
        child.translationY = 0f
        child.visibility = VISIBLE
    }
}
