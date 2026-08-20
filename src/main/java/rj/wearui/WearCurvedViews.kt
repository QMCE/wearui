package rj.wearui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.sin

private fun curvedDp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

/** A measured-bounds arc container. On a square host it behaves as a horizontal row. */
enum class WearCurvedRadialAlignment { Inner, Center, Outer }

open class CurvedLayoutView : ViewGroup {
    private var sweep = 180f // vendored CurvedRow default 270 for full, 180 for half - keep 180 for row
    private var curveRadius = 0f
    private var clockwise = true
    private var reverse = false
    private var clipArc = false
    private var radialAlignment: WearCurvedRadialAlignment = WearCurvedRadialAlignment.Center
    private var anchorDegrees: Float = 0f

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSweepDegrees(degrees: Float) { sweep = degrees.coerceIn(1f, 359f); requestLayout(); invalidate() }
    fun getSweepDegrees(): Float = sweep
    fun setRadiusPx(radius: Float) { curveRadius = max(0f, radius); requestLayout(); invalidate() }
    fun setRadiusDp(radius: Float) { setRadiusPx(curvedDp(context, radius)) }
    fun getRadiusPx(): Float = curveRadius
    fun setClockwise(enabled: Boolean) { clockwise = enabled; requestLayout(); invalidate() }
    fun isClockwise(): Boolean = clockwise
    fun setReverseDirection(enabled: Boolean) { reverse = enabled; requestLayout(); invalidate() }
    fun isReverseDirection(): Boolean = reverse
    fun setClipToArc(enabled: Boolean) { clipArc = enabled; invalidate() }
    fun isClipToArc(): Boolean = clipArc
    fun setRadialAlignment(alignment: WearCurvedRadialAlignment) { radialAlignment = alignment; requestLayout(); invalidate() }
    fun getRadialAlignment(): WearCurvedRadialAlignment = radialAlignment
    fun setAnchorDegrees(degrees: Float) { anchorDegrees = degrees; requestLayout(); invalidate() }
    fun getAnchorDegrees(): Float = anchorDegrees

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        for (i in 0 until childCount) measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    private fun measuredRound(): Boolean = width > 0 && height > 0 && abs(width - height) < min(width, height) * 0.12f

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount == 0) return
        val square = !measuredRound()
        if (square) {
            var x = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val y = (height - child.measuredHeight) / 2
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth
            }
            return
        }
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = if (curveRadius > 0f) curveRadius else min(width, height) / 2f - max(1f, curvedDp(context, 2f))
        val direction = if (clockwise xor reverse) 1f else -1f
        // Vendored anchor rotates the whole arc around center; anchorDegrees offsets start
        val start = -90f - direction * sweep / 2f + anchorDegrees
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val fraction = if (childCount == 1) .5f else i.toFloat() / (childCount - 1).toFloat()
            val angle = Math.toRadians((start + direction * sweep * fraction).toDouble())
            // Radial alignment shifts child radially by half height (inner/outer) per vendored CurvedAlignment logic
            val radial = when (radialAlignment) {
                WearCurvedRadialAlignment.Inner -> baseRadius - child.measuredHeight / 2f
                WearCurvedRadialAlignment.Outer -> baseRadius + child.measuredHeight / 2f
                else -> baseRadius
            }
            val x = cx + cos(angle).toFloat() * radial - child.measuredWidth / 2f
            val y = cy + sin(angle).toFloat() * radial - child.measuredHeight / 2f
            child.layout(x.toInt(), y.toInt(), (x + child.measuredWidth).toInt(), (y + child.measuredHeight).toInt())
            child.pivotX = child.measuredWidth / 2f
            child.pivotY = child.measuredHeight / 2f
            child.rotation = (start + direction * sweep * fraction + if (direction > 0) 90f else -90f)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!clipArc || !measuredRound()) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        val inset = curvedDp(context, 1f)
        canvas.clipPath(Path().apply { addCircle(width / 2f, height / 2f, min(width, height) / 2f - inset, Path.Direction.CW) })
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() {
        for (i in 0 until childCount) getChildAt(i).animate().cancel()
        super.onDetachedFromWindow()
    }
}

/** Text painted directly on a measured arc path, with linear square-screen fallback. */
class CurvedTextView : TextView {
    private val path = Path()
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var arcSweep = 70f
    private var arcRadius = 0f
    private var arcClockwise = true
    private var arcReverse = false
    private var arcRole: rj.wearui.WearTypographyRole? = null
    private var decorative = false

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        includeFontPadding = false
        arcPaint.typeface = paint.typeface
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setSweepDegrees(degrees: Float) { arcSweep = degrees.coerceIn(1f, 359f); invalidate() }
    fun getSweepDegrees(): Float = arcSweep
    fun setRadiusPx(radius: Float) { arcRadius = max(0f, radius); invalidate() }
    fun setRadiusDp(radius: Float) { setRadiusPx(curvedDp(context, radius)) }
    fun setClockwise(enabled: Boolean) { arcClockwise = enabled; invalidate() }
    fun setReverseDirection(enabled: Boolean) { arcReverse = enabled; invalidate() }
    fun setTypographyRole(role: rj.wearui.WearTypographyRole?) { arcRole = role; applyRoleSize(); invalidate() }
    fun setDecorative(decorative: Boolean) {
        this.decorative = decorative
        importantForAccessibility = if (decorative) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun applyRoleSize() {
        val role = arcRole ?: return
        // Direct enum dispatch - single source is WearUiTypography.defaultTokens()
        val token = WearUiTypography.Default.token(role)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, token.sizeSp)
        letterSpacing = if (token.sizeSp == 0f) 0f else token.trackingSp / token.sizeSp
        // Apply weight via fallback typeface (variable font only on API26+ handled via WearUiTypography)
        // For curved, use sans-serif with weight
        val w = token.weight
        typeface = android.graphics.Typeface.create("sans-serif", if (w >= 650) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        // Line spacing not needed for curved path, but keep for fallback linear
        val extra = max(0f, (token.lineHeightSp - token.sizeSp) * resources.displayMetrics.scaledDensity)
        setLineSpacing(extra, 1f)
    }

    private fun isRoundMeasured(): Boolean = width > 0 && height > 0 && abs(width - height) < min(width, height) * .12f

    override fun onDraw(canvas: Canvas) {
        if (!isRoundMeasured()) {
            super.onDraw(canvas)
            return
        }
        val value = text?.toString() ?: return
        if (value.isEmpty()) return
        arcPaint.set(paint)
        arcPaint.color = currentTextColor
        arcPaint.textSize = textSize
        arcPaint.textAlign = Paint.Align.CENTER
        val radius = if (arcRadius > 0f) arcRadius else min(width, height) / 2f - curvedDp(context, 8f)
        val direction = if (arcClockwise xor arcReverse) 1f else -1f
        val start = -90f - direction * arcSweep / 2f
        val rect = RectF(width / 2f - radius, height / 2f - radius, width / 2f + radius, height / 2f + radius)
        path.reset()
        path.addArc(rect, start, direction * arcSweep)
        // Vendored BasicCurvedText uses text center baseline = -(ascent+descent)/2 + stroke cap offset
        val baselineOffset = -(arcPaint.ascent() + arcPaint.descent()) / 2f
        // Apply arcRole's lineHeight centering like WearTypography for curved
        val role = arcRole
        if (role != null) {
            val token = WearUiTypography.Default.token(role)
            val desiredH = token.lineHeightSp * resources.displayMetrics.scaledDensity
            val textH = arcPaint.descent() - arcPaint.ascent()
            // Center text height within lineHeight
            val extra = (desiredH - textH).coerceAtLeast(0f) / 2f
            canvas.save()
            canvas.translate(0f, baselineOffset + extra)
            canvas.drawTextOnPath(value, path, 0f, 0f, arcPaint)
            canvas.restore()
        } else {
            canvas.save()
            canvas.translate(0f, baselineOffset)
            canvas.drawTextOnPath(value, path, 0f, 0f, arcPaint)
            canvas.restore()
        }
    }

    override fun onInitializeAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = CurvedTextView::class.java.name
    }
}

/** Curved horizontal row; its API is intentionally the same as CurvedLayoutView. */
class CurvedRowView : CurvedLayoutView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}
