package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.min

/** Persistent application route host with one shared native time overlay. */
class AppScaffoldView : ViewGroup {
    private data class RouteState(val entry: ScreenEntry, val spec: ScreenSpec, var content: View? = null)

    private val routes = LinkedHashMap<String, RouteState>()
    private val routeScaffold: ScreenScaffoldView = ScreenScaffoldView(context)
    private var sharedTimeText: TimeTextView? = null
    private var activeToken: String? = null
    private var transition: ValueAnimator? = null
    private val clipPath = Path()
    private var reducedMotion = false

    fun setReducedMotionEnabled(enabled: Boolean) {
        reducedMotion = enabled
        if (enabled) {
            transition?.cancel()
            transition = null
        }
    }

    fun isReducedMotionEnabled(): Boolean = reducedMotion

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        addView(routeScaffold)
        contentDescription = "Application screen"
    }

    /** Exposes the persistent scaffold for native overlay configuration. */
    fun getScreenScaffold(): ScreenScaffoldView = routeScaffold

    fun setTimeTextView(view: TimeTextView?) {
        if (sharedTimeText === view) return
        sharedTimeText = view
        routeScaffold.setTimeTextView(view)
    }

    fun setScrollIndicatorView(view: ScrollIndicatorView?) = routeScaffold.setScrollIndicatorView(view)
    fun setScrollMetricsProvider(provider: ScrollMetricsProvider?) = routeScaffold.setScrollMetricsProvider(provider)
    fun setEdgeButton(view: EdgeButtonView?) = routeScaffold.setEdgeButton(view)
    fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) =
        routeScaffold.setContentPadding(left, top, right, bottom)

    /** Activates a declared route. Its factory is invoked lazily and retained when [ScreenSpec.keepState] is true. */
    fun activateScreen(screenToken: String, spec: ScreenSpec) {
        activateScreen(ScreenEntry(id = screenToken, screenToken = screenToken), spec)
    }

    fun activateScreen(entry: ScreenEntry, spec: ScreenSpec) {
        require(entry.screenToken.isNotEmpty()) { "screenToken must not be empty" }
        val token = entry.screenToken
        val prior = routes[token]
        val state = if (prior != null && prior.spec == spec) prior else RouteState(entry, spec)
        routes[token] = state
        val changing = activeToken != token
        activeToken = token
        val content = if (spec.keepState) {
            state.content ?: spec.factory?.create(context, entry)?.also { state.content = it }
        } else {
            spec.factory?.create(context, entry)?.also { state.content = it }
        }
        routeScaffold.setContent(content)
        routeScaffold.contentDescription = spec.contentDescription ?: entry.title ?: "Application screen"
        if (changing) animateRouteIn()
        requestLayout()
    }

    /** Activates a direct native view without requiring a factory. */
    fun activateScreen(screenToken: String, content: View, contentDescription: CharSequence? = null) {
        activateScreen(
            ScreenEntry(id = screenToken, screenToken = screenToken),
            ScreenSpec(factory = object : WearScreenFactory {
                override fun create(context: Context, entry: ScreenEntry): View = content
            }, contentDescription = contentDescription)
        )
    }

    fun deactivateScreen(screenToken: String) {
        routes.remove(screenToken)
        if (activeToken == screenToken) {
            activeToken = null
            routeScaffold.setScrollMetricsProvider(null)
            routeScaffold.setContent(null)
            routeScaffold.setScrollIndicatorView(null)
            routeScaffold.setEdgeButton(null)
            routeScaffold.setTimeTextView(sharedTimeText)
        }
    }

    fun getActiveScreenToken(): String? = activeToken

    private fun animateRouteIn() {
        transition?.cancel()
        val target = routeScaffold.getContentView() ?: return
        val shouldSnap = reducedMotion || !isAttachedToWindow || isReducedMotionRequested(context)
        val spec = WearMotionSpec(
            durationMillis = WearMotionDurations.Medium400,
            interpolator = WearMotionEasings.LegacyStandard
        ).withReducedMotion(shouldSnap)
        if (spec.durationMillis == 0L) {
            target.scaleX = 1f
            target.scaleY = 1f
            target.alpha = 1f
            invalidate()
            return
        }
        target.scaleX = .75f
        target.scaleY = .75f
        target.alpha = .10f
        transition = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = spec.durationMillis
            interpolator = spec.interpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                target.scaleX = .75f + .25f * fraction
                target.scaleY = .75f + .25f * fraction
                target.alpha = .10f + .90f * fraction
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
        routeScaffold.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        routeScaffold.layout(0, 0, width, height)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val round = abs(width - height) <= maxOf(width, height) * .14f
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

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        routeScaffold.isEnabled = enabled
        if (!enabled) transition?.cancel()
    }

    override fun onDetachedFromWindow() {
        transition?.cancel()
        transition = null
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("super", super.onSaveInstanceState())
            putString("active", activeToken)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            @Suppress("DEPRECATION")
            super.onRestoreInstanceState(state.getParcelable("super"))
            state.getString("active")?.let { token -> routes[token]?.let { activateScreen(it.entry, it.spec) } }
        } else super.onRestoreInstanceState(state)
    }
}
