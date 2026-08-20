package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.max

/** Native page position indicator. It accepts IndicatorState while remaining tolerant of its shape. */
class PageIndicatorView : View {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var state: IndicatorState? = null
    private var pageCount = 0
    private var selectedPage = 0
    private var position = 0f
    private var targetPosition = 0f
    private var vertical = false
    private var activeColor = 0xffffffff.toInt()
    private var inactiveColor = 0x66ffffff
    private var dotRadius = 3f
    private var spacing = 12f
    private var animator: ValueAnimator? = null
    private var reducedMotion = false

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) {
            animator?.cancel()
            animator = null
            position = targetPosition
            invalidate()
        }
    }

    fun isReducedMotionEnabled(): Boolean = reducedMotion

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        val density = resources.displayMetrics.density
        dotRadius = 3f * density // 6dp diameter -> radius 3dp matches PageIndicatorItemSize 6dp
        spacing = 4f * density // vendored PageIndicatorSpacing 4dp (not 12)
        contentDescription = "Page indicator"
    }

    fun setState(value: IndicatorState) {
        state = value
        pageCount = readInt(value, "pageCount", "count", "total", "size").coerceAtLeast(0)
        selectedPage = readInt(value, "currentPage", "selectedPage", "index", "position").coerceIn(0, max(0, pageCount - 1))
        val offset = readFloat(value, "offset", "fraction", "positionOffset")
        animateTo(selectedPage + offset)
        contentDescription = if (pageCount > 0) "Page ${selectedPage + 1} of $pageCount" else "Page indicator"
    }

    fun getState(): IndicatorState? = state
    fun setPageCount(count: Int) { pageCount = max(0, count); selectedPage = selectedPage.coerceIn(0, max(0, pageCount - 1)); targetPosition = selectedPage.toFloat(); position = targetPosition; invalidate() }
    fun setSelectedPage(page: Int, animate: Boolean = true) { selectedPage = page.coerceIn(0, max(0, pageCount - 1)); if (animate) animateTo(selectedPage.toFloat()) else { position = selectedPage.toFloat(); targetPosition = position; invalidate() } }
    fun getSelectedPage(): Int = selectedPage
    fun setVertical(enabled: Boolean) { vertical = enabled; requestLayout(); invalidate() }
    fun isVertical(): Boolean = vertical
    fun setOrientation(orientation: PagerOrientation) { vertical = orientation.toString().contains("VERT", true); requestLayout(); invalidate() }
    fun setColors(active: Int, inactive: Int) { activeColor = active; inactiveColor = inactive; invalidate() }
    fun setDotRadiusPx(radius: Float) { dotRadius = max(1f, radius); requestLayout(); invalidate() }

    private fun animateTo(value: Float) {
        targetPosition = value.coerceIn(0f, max(0, pageCount - 1).toFloat())
        animator?.cancel()
        val shouldSnap = reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
            || !isShown || abs(position - targetPosition) < .001f
        val spec = WearMotionSpec(
            durationMillis = WearMotionDurations.Short200,
            interpolator = WearMotionEasings.StandardDecelerate
        ).withReducedMotion(shouldSnap)
        if (spec.durationMillis == 0L) { position = targetPosition; invalidate(); return }
        animator = ValueAnimator.ofFloat(position, targetPosition).apply {
            duration = spec.durationMillis
            interpolator = spec.interpolator
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = if (vertical) dotRadius * 2f else max(dotRadius * 2f, (pageCount - 1) * spacing + dotRadius * 2f)
        val desiredH = if (vertical) max(dotRadius * 2f, (pageCount - 1) * spacing + dotRadius * 2f) else dotRadius * 2f
        setMeasuredDimension(resolveSize(desiredW.toInt(), widthMeasureSpec), resolveSize(desiredH.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 0) return
        val offset = if (vertical) (height - ((pageCount - 1) * spacing + dotRadius * 2f)) / 2f else (width - ((pageCount - 1) * spacing + dotRadius * 2f)) / 2f
        paint.style = Paint.Style.FILL
        for (i in 0 until pageCount) {
            val distance = kotlin.math.abs(i - position).coerceIn(0f, 1f)
            paint.color = blend(inactiveColor, activeColor, 1f - distance)
            val x = if (vertical) width / 2f else offset + dotRadius + i * spacing
            val y = if (vertical) offset + dotRadius + i * spacing else height / 2f
            canvas.drawCircle(x, y, dotRadius * (if (distance < .01f) 1f else 0.66f), paint)
        }
    }

    private fun blend(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun c(v: Int, shift: Int): Int = ((v shr shift) and 255)
        val aa = c(a, 24) + ((c(b, 24) - c(a, 24)) * t).toInt()
        val rr = c(a, 16) + ((c(b, 16) - c(a, 16)) * t).toInt()
        val gg = c(a, 8) + ((c(b, 8) - c(a, 8)) * t).toInt()
        val bb = c(a, 0) + ((c(b, 0) - c(a, 0)) * t).toInt()
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun readInt(value: Any, vararg names: String): Int {
        for (name in names) try {
            val f = value.javaClass.getDeclaredField(name); f.isAccessible = true
            val result = f.get(value)
            if (result is Number) return result.toInt()
        } catch (_: Throwable) { }
        return 0
    }
    private fun readFloat(value: Any, vararg names: String): Float {
        for (name in names) try {
            val f = value.javaClass.getDeclaredField(name); f.isAccessible = true
            val result = f.get(value)
            if (result is Number) return result.toFloat()
        } catch (_: Throwable) { }
        return 0f
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = PageIndicatorView::class.java.name
        info.isFocusable = false
    }

    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        super.onDetachedFromWindow()
    }
}
