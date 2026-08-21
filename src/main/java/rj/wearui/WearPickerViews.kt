package rj.wearui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Parcelable
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Native wheel picker backed by the core WearItemAdapter contract. */
open class PickerView : ViewGroup {
    private var adapter: WearItemAdapter? = null
    private val itemViews = ArrayList<View>()
    private var selected = 0
    private var visibleItems = PickerTokens.VisibleItems // vendored Picker defaults to 3 visible (center + above/below)
    private var wrap = true
    private var selectionListener: WearSelectionListener? = null
    private var dragging = false
    private var downY = 0f
    private var lastY = 0f
    private var accumulatedDy = 0f
    private var activePointer = -1
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(64, 208, 188, 255) }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(148, 143, 154); strokeWidth = 1f }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setWillNotDraw(false)
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumHeight = dp(PickerTokens.ContainerHeightDp.toInt())
    }

    fun setAdapter(value: WearItemAdapter?) {
        adapter = value
        selected = normalize(selected)
        rebuildChildren()
        requestLayout()
        invalidate()
    }
    fun getAdapter(): WearItemAdapter? = adapter
    fun setSelectedIndex(index: Int, fromUser: Boolean = false, animate: Boolean = true) {
        val normalized = normalize(index)
        if (selected == normalized && itemViews.isNotEmpty()) return
        selected = normalized
        rebuildChildren()
        requestLayout()
        invalidate()
        if (fromUser) selectionListener?.onSelectionChanged(this, selected, true)
        announceForAccessibility(selectedValueText())
    }
    fun getSelectedIndex(): Int = selected
    fun getSelectedItemId(): Long = adapter?.getItemId(selected) ?: View.NO_ID.toLong()
    fun setWrapAround(enabled: Boolean) { wrap = enabled; setSelectedIndex(selected, false, false) }
    fun isWrapAround(): Boolean = wrap
    fun setVisibleItemCount(count: Int) { visibleItems = max(3, if (count % 2 == 0) count + 1 else count); rebuildChildren(); requestLayout() }
    fun getVisibleItemCount(): Int = visibleItems
    fun setOnSelectionListener(listener: WearSelectionListener?) { selectionListener = listener }

    private fun normalize(index: Int): Int {
        val count = adapter?.getCount() ?: 0
        if (count <= 0) return 0
        return if (wrap) ((index % count) + count) % count else index.coerceIn(0, count - 1)
    }
    private fun offsetForChild(childIndex: Int): Int = childIndex - visibleItems / 2
    private fun childPosition(childIndex: Int): Int = normalize(selected + offsetForChild(childIndex))
    private fun rebuildChildren() {
        val localAdapter = adapter
        if (localAdapter == null || localAdapter.getCount() <= 0) { removeAllViews(); itemViews.clear(); return }
        val recycled = ArrayList<View>(itemViews)
        removeAllViews(); itemViews.clear()
        for (i in 0 until visibleItems) {
            val position = childPosition(i)
            val reuse = recycled.getOrNull(i)
            val child = localAdapter.getView(context, position, reuse, this)
            if (child.parent is ViewGroup && child.parent !== this) (child.parent as ViewGroup).removeView(child)
            child.isClickable = false
            child.isFocusable = false
            child.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(child)
            itemViews.add(child)
        }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private fun itemHeight(): Int = max(dp(PickerTokens.ItemHeightDp.toInt()), height / visibleItems)
    private fun selectedValueText(): CharSequence {
        val child = itemViews.getOrNull(visibleItems / 2)
        return if (child is TextView) child.text else "Item ${selected + 1}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(PickerTokens.ItemHeightDp.toInt()) * visibleItems + dp(24)
        val width = resolveSize(suggestedMinimumWidth.coerceAtLeast(dp(96)), widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val childHeight = max(1, height / visibleItems)
        for (child in itemViews) child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    private val pickerEasing = android.view.animation.PathInterpolator(0.3f, 0f, 0.7f, 1f)
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val childHeight = itemHeight()
        itemViews.forEachIndexed { index, child ->
            val y = index * childHeight
            child.layout(0, y, width, y + childHeight)
            // Vendored ScalingLazyColumn spec: scale 0.70 / alpha 0.50 / cubic 0.3,0,0.7,1
            val raw = abs(index - visibleItems / 2).toFloat() / (visibleItems / 2f).coerceAtLeast(1f)
            val eased = pickerEasing.getInterpolation(raw.coerceIn(0f,1f))
            val scale = 1f - (1f - PickerTokens.MinScale) * eased
            val alpha = 1f - (1f - PickerTokens.MinAlpha) * eased
            child.pivotX = child.measuredWidth / 2f
            child.pivotY = child.measuredHeight / 2f
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = alpha
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val selectionHeight = itemHeight()
        val top = (height - selectionHeight) / 2f
        canvas.drawRoundRect(RectF(0f, top, width.toFloat(), top + selectionHeight), dp(PickerTokens.SelectionRadiusDp.toInt()).toFloat(), dp(PickerTokens.SelectionRadiusDp.toInt()).toFloat(), selectionPaint)
        canvas.drawLine(0f, top, width.toFloat(), top, dividerPaint)
        canvas.drawLine(0f, top + selectionHeight, width.toFloat(), top + selectionHeight, dividerPaint)
        super.dispatchDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || adapter == null) return false
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) { dragging = false; parent?.requestDisallowInterceptTouchEvent(false); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = event.y; lastY = event.y; accumulatedDy = 0f; activePointer = event.getPointerId(0); parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.y - lastY
                lastY = event.y
                accumulatedDy += delta
                if (abs(event.y - downY) > dp(4)) dragging = true
                val amount = (accumulatedDy / itemHeight()).toInt()
                if (amount != 0) {
                    setSelectedIndex(selected - amount, true, false)
                    accumulatedDy -= amount * itemHeight()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    val offset = ((event.y / itemHeight()).toInt() - visibleItems / 2)
                    if (offset != 0) setSelectedIndex(selected + offset, true, false)
                }
                activePointer = -1; dragging = false; parent?.requestDisallowInterceptTouchEvent(false); performClick(); return true
            }
            MotionEvent.ACTION_CANCEL -> { activePointer = -1; dragging = false; parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { setSelectedIndex(selected + 1, true, false); true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { setSelectedIndex(selected - 1, true, false); true }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.NumberPicker::class.java.name
        info.isScrollable = (adapter?.getCount() ?: 0) > 1
        info.isEnabled = isEnabled
        info.text = selectedValueText()
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (android.os.Build.VERSION.SDK_INT >= 30) info.stateDescription = selectedValueText()
    }
    override fun onSaveInstanceState(): Parcelable { val state = WearControlSavedState(super.onSaveInstanceState()); state.index = selected; return state }
    override fun onRestoreInstanceState(state: Parcelable?) { if (state is WearControlSavedState) { super.onRestoreInstanceState(state.superState); selected = normalize(state.index); rebuildChildren() } else super.onRestoreInstanceState(state) }
}

/** Picker preconfigured with localized time-of-day values and Calendar-safe APIs. */
class TimePickerView : PickerView {
    private var calendar: Calendar = Calendar.getInstance()
    private val timeAdapter = object : WearItemAdapter {
        override fun getCount(): Int = 24 * 12
        override fun getView(context: Context, position: Int, recycled: View?, parent: ViewGroup): View {
            val text = (recycled as? WearTextView) ?: WearTextView(context).apply { setTypographyRole(WearTypographyRole.BodyLarge); gravity = android.view.Gravity.CENTER }
            val minute = position * 5
            val local = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60) }
            val base = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
            val pattern = try { android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), base).replace("a","").trim().ifEmpty{ base } } catch (_: Throwable) { base }
            text.text = java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(local.time)
            text.gravity = android.view.Gravity.CENTER
            return text
        }
        override fun getItemId(position: Int): Long = position.toLong()
    }
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setAdapter(timeAdapter)
        setTime(calendar, false)
        setOnSelectionListener(object : WearSelectionListener { override fun onSelectionChanged(view: View, index: Int, fromUser: Boolean) { calendar.set(Calendar.HOUR_OF_DAY, index / 12); calendar.set(Calendar.MINUTE, (index % 12) * 5) } })
    }
    fun setTime(value: Calendar, fromUser: Boolean = false) { calendar = value.clone() as Calendar; setSelectedIndex(calendar.get(Calendar.HOUR_OF_DAY) * 12 + calendar.get(Calendar.MINUTE) / 5, fromUser, false) }
    fun getTime(): Calendar = calendar.clone() as Calendar
}

/** Picker preconfigured with a rolling Calendar date range without java.time dependencies. */
class DatePickerView : PickerView {
    private var baseDate: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, -183) }
    private var selectedDate: Calendar = Calendar.getInstance()
    private val dateAdapter = object : WearItemAdapter {
        override fun getCount(): Int = 367
        override fun getView(context: Context, position: Int, recycled: View?, parent: ViewGroup): View {
            val text = (recycled as? WearTextView) ?: WearTextView(context)
            val date = baseDate.clone() as Calendar
            date.add(Calendar.DAY_OF_YEAR, position)
            text.text = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault()).format(date.time)
            text.gravity = android.view.Gravity.CENTER
            return text
        }
        override fun getItemId(position: Int): Long { val date = baseDate.clone() as Calendar; date.add(Calendar.DAY_OF_YEAR, position); return date.timeInMillis }
    }
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setAdapter(dateAdapter)
        setWrapAround(false)
        setDate(selectedDate, false)
        setOnSelectionListener(object : WearSelectionListener { override fun onSelectionChanged(view: View, index: Int, fromUser: Boolean) { selectedDate = baseDate.clone() as Calendar; selectedDate.add(Calendar.DAY_OF_YEAR, index) } })
    }
    fun setDate(value: Calendar, fromUser: Boolean = false) {
        selectedDate = value.clone() as Calendar
        val days = ((selectedDate.timeInMillis - baseDate.timeInMillis) / (24L * 60L * 60L * 1000L)).toInt()
        setSelectedIndex(days, fromUser, false)
    }
    fun getDate(): Calendar = selectedDate.clone() as Calendar
}

/** Native PickerGroup - vendored from PickerGroup.kt, horizontal row of Pickers with 8dp spacing */
class PickerGroupView : ViewGroup {
    private var spacingPx = 0
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        spacingPx = (8f * resources.displayMetrics.density + 0.5f).toInt() // PickerGroup spacing 8dp
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 0) { setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec)); return }
        val availableW = MeasureSpec.getSize(widthMeasureSpec)
        val availableH = MeasureSpec.getSize(heightMeasureSpec)
        val childW = (availableW - spacingPx * (childCount - 1)) / childCount.coerceAtLeast(1)
        var maxH = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(availableH, MeasureSpec.AT_MOST))
            maxH = maxOf(maxH, child.measuredHeight)
        }
        setMeasuredDimension(resolveSize(availableW, widthMeasureSpec), resolveSize(maxH, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = 0
        val h = height
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val cw = child.measuredWidth
            val y = (h - child.measuredHeight) / 2
            child.layout(x, y, x + cw, y + child.measuredHeight)
            x += cw + spacingPx
        }
    }
}
