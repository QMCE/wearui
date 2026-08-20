package rj.wearui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import java.util.Locale

/** Receives checked-state changes from native selection controls. */
interface WearCheckedChangeListener {
    fun onCheckedChanged(view: View, checked: Boolean, fromUser: Boolean)
}

/** Receives scalar value changes from sliders and steppers. */
interface WearValueChangeListener {
    fun onValueChanged(view: View, value: Float, fromUser: Boolean)
}

/** Receives picker and selection-control index changes. */
interface WearSelectionListener {
    fun onSelectionChanged(view: View, index: Int, fromUser: Boolean)
}

/** Receives refreshed scroll metrics. */
interface WearScrollMetricsListener {
    fun onScrollMetricsChanged(provider: ScrollMetricsProvider)
}

/** Supplies locale-aware time/status text to [TimeTextView]. */
interface WearTimeSource {
    fun getText(nowMillis: Long, locale: Locale): CharSequence
}

/** Adapter used by native picker and list views. */
interface WearItemAdapter {
    fun getCount(): Int
    fun getView(context: Context, position: Int, recycled: View?, parent: ViewGroup): View
    fun getItemId(position: Int): Long
}

/** Adapter used by native pager views. */
interface WearPagerAdapter {
    fun getCount(): Int
    fun getView(context: Context, position: Int, recycled: View?, parent: ViewGroup): View
    fun getItemId(position: Int): Long
}

/** Creates a route view for a screen entry. */
interface WearScreenFactory {
    fun create(context: Context, entry: ScreenEntry): View
}

/** Observes navigation route changes. */
interface WearScreenListener {
    fun onScreenChanged(current: ScreenEntry?, previous: ScreenEntry?)
}

/** Provides the current scaffold stage to scroll-away behaviors. */
interface ScreenStageProvider {
    fun getScreenStage(): ScreenStage
}

/** Observes page changes. */
interface WearPageChangeListener {
    fun onPageChanged(view: View, page: Int, fromUser: Boolean)
}

/** Observes radio-group selection changes without requiring AndroidX. */
interface WearRadioGroupListener {
    fun onRadioSelectionChanged(group: WearRadioGroup, selectedId: Int)
}

/** Core-owned contract implemented by radio control groups. */
interface WearRadioGroup {
    fun addRadio(view: View, id: Int)
    fun removeRadio(view: View)
    fun clearSelection(fromUser: Boolean = false)
    fun select(id: Int, fromUser: Boolean = false)
    fun getSelectedId(): Int
    fun setOnSelectionChangedListener(listener: WearRadioGroupListener?)
}

/** Callback for a completed user value gesture. */
interface WearValueChangeFinishedListener {
    fun onValueChangeFinished(view: View)
}

/** Stable identity and factory information for one navigator route. */
data class ScreenEntry(
    val id: String,
    val title: CharSequence? = null,
    val screenToken: String = id,
    val arguments: android.os.Bundle? = null
)

/** Declarative information used when a route is activated. */
data class ScreenSpec(
    val factory: WearScreenFactory? = null,
    val contentDescription: CharSequence? = null,
    val keepState: Boolean = true
)

/** State consumed by scroll indicators. Values are fractions unless noted. */
data class IndicatorState(
    val firstVisibleItem: Int = 0,
    val visibleItemCount: Int = 0,
    val totalItemCount: Int = 0,
    val scrollOffsetPx: Int = 0,
    val viewportExtentPx: Int = 0,
    val contentExtentPx: Int = 0,
    val overscrollFraction: Float = 0f
) {
    val isValid: Boolean
        get() = totalItemCount > 0 && viewportExtentPx > 0 && contentExtentPx >= viewportExtentPx

    val positionFraction: Float
        get() {
            val range = (contentExtentPx - viewportExtentPx).coerceAtLeast(0)
            return if (range == 0) 0f else (scrollOffsetPx.toFloat() / range).coerceIn(0f, 1f)
        }
}

enum class ScreenStage { New, Scrolling, Idle }
enum class EdgeButtonSize { ExtraSmall, Small, Medium, Large }
enum class PagerOrientation { Horizontal, Vertical }
