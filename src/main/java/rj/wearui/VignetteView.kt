package rj.wearui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Non-interactive top/bottom contrast vignette for content under watch controls. */
class VignetteView : View {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var topEnabled = true
    private var bottomEnabled = true
    private var vignetteColor = Color.BLACK
    private var strength = 0.50f // vendored Vignette image alpha ~0.5 at top, fading to 0

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = null
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun setTopVignetteEnabled(enabled: Boolean) {
        topEnabled = enabled
        invalidate()
    }

    fun setBottomVignetteEnabled(enabled: Boolean) {
        bottomEnabled = enabled
        invalidate()
    }

    /** Vendored VignettePosition mapping: 0 Top, 1 Bottom, 2 TopAndBottom */
    fun setPosition(position: Int) {
        when (position) {
            0 -> { topEnabled = true; bottomEnabled = false }
            1 -> { topEnabled = false; bottomEnabled = true }
            else -> { topEnabled = true; bottomEnabled = true }
        }
        invalidate()
    }
    fun setPositionTopAndBottom() = setPosition(2)

    fun setVignetteColor(color: Int) {
        vignetteColor = color
        invalidate()
    }

    fun setStrength(value: Float) {
        strength = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || (!topEnabled && !bottomEnabled)) return
        val alpha = (Color.alpha(vignetteColor) * strength).toInt().coerceIn(0, 255)
        val opaque = Color.argb(alpha, Color.red(vignetteColor), Color.green(vignetteColor), Color.blue(vignetteColor))
        val isRound = abs(width - height) <= max(width, height) * 0.14f
        val depth = if (isRound) height * 0.32f else height * 0.20f
        if (topEnabled) {
            paint.shader = LinearGradient(0f, 0f, 0f, depth, opaque, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), depth, paint)
        }
        if (bottomEnabled) {
            paint.shader = LinearGradient(0f, height - depth, 0f, height.toFloat(), Color.TRANSPARENT, opaque, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, height - depth, width.toFloat(), height.toFloat(), paint)
        }
        paint.shader = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}
