package rj.wearui

import android.view.View
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.ScrollView
import java.lang.reflect.Method

/**
 * Framework-neutral vertical scroll metrics. Implementations are intentionally independent of
 * RecyclerView so a scaffold can consume platform lists and RecyclerView-like views alike.
 */
interface ScrollMetricsProvider {
    val view: View?
    val scrollOffsetPx: Int
    val viewportExtentPx: Int
    val contentExtentPx: Int
    val canScrollBackward: Boolean
    val canScrollForward: Boolean
    val isScrollable: Boolean

    fun refresh()
    fun addListener(listener: WearScrollMetricsListener)
    fun removeListener(listener: WearScrollMetricsListener)
    fun dispose()

    fun toIndicatorState(): IndicatorState = IndicatorState(
        scrollOffsetPx = scrollOffsetPx,
        viewportExtentPx = viewportExtentPx,
        contentExtentPx = contentExtentPx
    )

    companion object {
        /**
         * Creates a provider for ScrollView, AbsListView, or a RecyclerView-like View discovered
         * through its scroll metric methods. Unknown views receive a stable non-scrollable provider.
         */
        @JvmStatic
        fun fromView(view: View): ScrollMetricsProvider {
            return when (view) {
                is ScrollView -> ScrollViewMetricsProvider(view)
                is AbsListView -> AbsListViewMetricsProvider(view)
                else -> {
                    if (ReflectiveScrollMetricsProvider.supports(view)) {
                        ReflectiveScrollMetricsProvider(view)
                    } else {
                        StaticScrollMetricsProvider(view)
                    }
                }
            }
        }

        @JvmStatic
        fun empty(): ScrollMetricsProvider = StaticScrollMetricsProvider(null)
    }
}

private data class ScrollMetricSnapshot(
    val offset: Int = 0,
    val extent: Int = 0,
    val range: Int = 0,
    val backward: Boolean = false,
    val forward: Boolean = false
) {
    val scrollable: Boolean get() = range > extent || backward || forward
}

private abstract class BaseScrollMetricsProvider(final override val view: View?) : ScrollMetricsProvider {
    private val listeners = ArrayList<WearScrollMetricsListener>()
    private var snapshot = ScrollMetricSnapshot()
    private var observing = false
    private var observer: ViewTreeObserver? = null

    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener { refresh() }
    private val attachStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (listeners.isNotEmpty()) startObserving()
        }

        override fun onViewDetachedFromWindow(v: View) {
            stopObserving()
        }
    }

    init {
        view?.addOnAttachStateChangeListener(attachStateListener)
        snapshot = readMetrics().normalized()
    }

    override val scrollOffsetPx: Int get() = snapshot.offset
    override val viewportExtentPx: Int get() = snapshot.extent
    override val contentExtentPx: Int get() = snapshot.range
    override val canScrollBackward: Boolean get() = snapshot.backward
    override val canScrollForward: Boolean get() = snapshot.forward
    override val isScrollable: Boolean get() = snapshot.scrollable

    override fun refresh() {
        val next = readMetrics().normalized()
        if (next == snapshot) return
        snapshot = next
        val copy = ArrayList(listeners)
        for (listener in copy) listener.onScrollMetricsChanged(this)
    }

    override fun addListener(listener: WearScrollMetricsListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        startObserving()
        refresh()
    }

    override fun removeListener(listener: WearScrollMetricsListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) stopObserving()
    }

    override fun dispose() {
        stopObserving()
        listeners.clear()
        view?.removeOnAttachStateChangeListener(attachStateListener)
    }

    private fun startObserving() {
        if (observing || view == null) return
        val tree = view.viewTreeObserver
        if (!tree.isAlive) return
        tree.addOnScrollChangedListener(scrollChangedListener)
        observer = tree
        observing = true
    }

    private fun stopObserving() {
        if (!observing) return
        val tree = observer
        if (tree != null && tree.isAlive) tree.removeOnScrollChangedListener(scrollChangedListener)
        observer = null
        observing = false
    }

    protected abstract fun readMetrics(): ScrollMetricSnapshot
}

private class ScrollViewMetricsProvider(private val scrollView: ScrollView) : BaseScrollMetricsProvider(scrollView) {
    override fun readMetrics(): ScrollMetricSnapshot {
        val extent = (scrollView.height - scrollView.paddingTop - scrollView.paddingBottom).coerceAtLeast(0)
        val child = if (scrollView.childCount > 0) scrollView.getChildAt(0) else null
        val childHeight = child?.height ?: child?.measuredHeight ?: 0
        val range = (childHeight + scrollView.paddingTop + scrollView.paddingBottom).coerceAtLeast(extent)
        val offset = scrollView.scrollY.coerceIn(0, (range - extent).coerceAtLeast(0))
        return ScrollMetricSnapshot(
            offset = offset,
            extent = extent,
            range = range,
            backward = scrollView.canScrollVertically(-1),
            forward = scrollView.canScrollVertically(1)
        )
    }
}

private class AbsListViewMetricsProvider(private val listView: AbsListView) : BaseScrollMetricsProvider(listView) {
    override fun readMetrics(): ScrollMetricSnapshot {
        val extent = invokeScrollMetric(listView, "computeVerticalScrollExtent").coerceAtLeast(0)
            .takeIf { it > 0 } ?: (listView.height - listView.paddingTop - listView.paddingBottom).coerceAtLeast(0)
        var range = invokeScrollMetric(listView, "computeVerticalScrollRange").coerceAtLeast(0)
        var offset = invokeScrollMetric(listView, "computeVerticalScrollOffset").coerceAtLeast(0)
        if (range <= 0 && listView.count > 0) {
            val first = listView.getChildAt(0)
            val itemHeight = first?.height?.takeIf { it > 0 } ?: 0
            range = if (itemHeight > 0) itemHeight * listView.count else extent
            offset = if (itemHeight > 0) {
                (listView.firstVisiblePosition * itemHeight - (first?.top ?: 0)).coerceAtLeast(0)
            } else 0
        }
        range = range.coerceAtLeast(extent)
        return ScrollMetricSnapshot(
            offset = offset.coerceIn(0, (range - extent).coerceAtLeast(0)),
            extent = extent,
            range = range,
            backward = listView.canScrollVertically(-1),
            forward = listView.canScrollVertically(1)
        )
    }
}

/** Handles RecyclerView and equivalent widgets without linking against their classes. */
private class ReflectiveScrollMetricsProvider(private val target: View) : BaseScrollMetricsProvider(target) {
    override fun readMetrics(): ScrollMetricSnapshot {
        val extent = invokeScrollMetric(target, "computeVerticalScrollExtent").coerceAtLeast(0)
        val range = invokeScrollMetric(target, "computeVerticalScrollRange").coerceAtLeast(extent)
        val offset = invokeScrollMetric(target, "computeVerticalScrollOffset")
            .coerceIn(0, (range - extent).coerceAtLeast(0))
        return ScrollMetricSnapshot(
            offset = offset,
            extent = extent,
            range = range,
            backward = target.canScrollVertically(-1),
            forward = target.canScrollVertically(1)
        )
    }

    companion object {
        fun supports(view: View): Boolean {
            val name = view.javaClass.name
            if (name.contains("RecyclerView")) return true
            return findMethod(view, "computeVerticalScrollRange") != null &&
                findMethod(view, "computeVerticalScrollExtent") != null &&
                findMethod(view, "computeVerticalScrollOffset") != null
        }
    }
}

private class StaticScrollMetricsProvider(override val view: View?) : ScrollMetricsProvider {
    override val scrollOffsetPx: Int = 0
    override val viewportExtentPx: Int = 0
    override val contentExtentPx: Int = 0
    override val canScrollBackward: Boolean = false
    override val canScrollForward: Boolean = false
    override val isScrollable: Boolean = false
    override fun refresh() = Unit
    override fun addListener(listener: WearScrollMetricsListener) = Unit
    override fun removeListener(listener: WearScrollMetricsListener) = Unit
    override fun dispose() = Unit
}

private fun ScrollMetricSnapshot.normalized(): ScrollMetricSnapshot {
    val safeExtent = extent.coerceAtLeast(0)
    val safeRange = range.coerceAtLeast(safeExtent)
    return copy(
        offset = offset.coerceIn(0, (safeRange - safeExtent).coerceAtLeast(0)),
        extent = safeExtent,
        range = safeRange
    )
}

private fun invokeScrollMetric(view: View, name: String): Int {
    val method = findMethod(view, name) ?: return 0
    return try {
        (method.invoke(view) as? Number)?.toInt() ?: 0
    } catch (_: ReflectiveOperationException) {
        0
    } catch (_: SecurityException) {
        0
    }
}

private fun findMethod(view: View, name: String): Method? {
    return try {
        view.javaClass.getMethod(name)
    } catch (_: NoSuchMethodException) {
        try {
            view.javaClass.getDeclaredMethod(name).also { it.isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: SecurityException) {
            null
        }
    } catch (_: SecurityException) {
        null
    }
}
