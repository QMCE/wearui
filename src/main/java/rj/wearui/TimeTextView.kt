package rj.wearui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A decorative, dynamically-updated clock that follows round-screen geometry. */
class TimeTextView : View {
    private var source: rj.wearui.WearTimeSource? = null
    private var status: CharSequence? = null
    private var maxSweepDegrees = TimeTextTokens.MaxSweepDegrees
    private var clockBackgroundColor = Color.TRANSPARENT
    private var reducedMotion = false
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPath = Path()
    private var attachedReceiver = false
    private var lastText = ""
    private var lastTickMillis = 0L
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTime(true)
        }
    }
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateTime(false)
            if (isAttachedToWindow) scheduleTick()
        }
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        textPaint.color = Color.WHITE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = null
        isFocusable = false
        setWillNotDraw(false)
    }

    fun setTimeSource(source: rj.wearui.WearTimeSource?) {
        this.source = source
        updateTime(true)
    }

    fun setStatusText(text: CharSequence?) {
        status = text
        updateTime(true)
    }

    fun setMaxSweepDegrees(degrees: Float) {
        maxSweepDegrees = degrees.coerceIn(1f, 180f)
        invalidate()
    }

    fun setClockBackgroundColor(color: Int) {
        clockBackgroundColor = color
        invalidate()
    }

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        invalidate()
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    fun getCurrentText(): CharSequence = lastText

    private fun updateTime(force: Boolean) {
        val now = System.currentTimeMillis()
        val text = status ?: source?.getText(now, Locale.getDefault()) ?: defaultTime(now)
        val rendered = stripAmPm(text.toString()).trim()
        if (force || rendered != lastText) {
            lastText = rendered
            invalidate()
        }
        lastTickMillis = now
    }

    private fun defaultTime(now: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val is24 = DateFormat.is24HourFormat(context)
        val base = if (is24) "HH:mm" else "h:mm"
        // Vendored TimeTextDefaults.timeFormat() uses BestDateTimePattern per locale
        val locale = Locale.getDefault()
        val pattern = try {
            android.text.format.DateFormat.getBestDateTimePattern(locale, base).replace("a", "").trim().ifEmpty { base }
        } catch (_: Throwable) { base }
        return android.text.format.DateFormat.format(pattern, calendar).toString()
    }

    private fun stripAmPm(value: String): String {
        // Do not rely on a fixed localized AM/PM token; the formatter keeps numeric time contiguous.
        return value.replace(Regex("(?i)\\s*(a\\.?m\\.?|p\\.?m\\.?)\\s*"), " ").trim()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredWidth = if (availableWidth > 0) availableWidth else dp(120f)
        val desiredHeight = dp(TimeTextTokens.ContainerHeightDp)
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || lastText.isEmpty()) return
        val textSize = sp(15f)
        textPaint.textSize = textSize
        textPaint.alpha = if (isEnabled) 255 else 97
        if (clockBackgroundColor ushr 24 != 0) {
            backgroundPaint.color = clockBackgroundColor
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        }
        val isRound = if (Build.VERSION.SDK_INT >= 23) {
            resources.configuration.isScreenRound
        } else {
            abs(width - height) <= max(width, height) * 0.14f
        }
        if (!isRound) {
            val baseline = (height - (textPaint.descent() + textPaint.ascent())) / 2f
            canvas.drawText(lastText, width / 2f, baseline, textPaint)
            return
        }
        val centerX = width / 2f
        val centerY = max(width, height) / 2f
        val radius = min(width, height) / 2f - dp(TimeTextTokens.ClockRadiusInsetDp)
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val sweep = maxSweepDegrees.coerceAtMost(140f)
        // A tiny upward arc keeps the time at the top of a circular display.
        arcPath.reset()
        arcPath.addArc(rect, 270f - sweep / 2f, sweep)
        val pathLength = approximateArcLength(radius, sweep)
        val textWidth = textPaint.measureText(lastText)
        canvas.drawTextOnPath(lastText, arcPath, (pathLength - textWidth) / 2f, 0f, textPaint)
    }

    private fun approximateArcLength(radius: Float, sweep: Float): Float =
        (2.0 * Math.PI * radius * sweep / 360.0).toFloat()

    private fun scheduleTick() {
        removeCallbacks(tickRunnable)
        val delay = max(250L, 60_000L - (System.currentTimeMillis() % 60_000L) + 25L)
        postDelayed(tickRunnable, delay)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        try {
            context.registerReceiver(timeReceiver, filter)
            attachedReceiver = true
        } catch (_: Throwable) {
            attachedReceiver = false
        }
        updateTime(true)
        scheduleTick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tickRunnable)
        if (attachedReceiver) {
            try { context.unregisterReceiver(timeReceiver) } catch (_: Throwable) { }
        }
        attachedReceiver = false
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
