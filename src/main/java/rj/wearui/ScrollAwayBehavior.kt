package rj.wearui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.animation.PathInterpolator
import java.util.WeakHashMap
import kotlin.math.max

/** Applies the standard watch scroll-away transform without changing layout bounds. */
class ScrollAwayBehavior {
    private data class Binding(
        val provider: ScrollMetricsProvider,
        val stageProvider: ScreenStageProvider,
        val attachListener: View.OnAttachStateChangeListener,
        val layoutListener: View.OnLayoutChangeListener
    )

    private val bindings = WeakHashMap<View, Binding>()

    fun attach(view: View, provider: ScrollMetricsProvider, stageProvider: ScreenStageProvider) {
        detach(view)
        view.pivotX = view.width / 2f
        view.pivotY = 0f
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = update(v)
            override fun onViewDetachedFromWindow(v: View) = reset(v)
        }
        val layoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.pivotX = v.width / 2f
            v.pivotY = 0f
            update(v)
        }
        val binding = Binding(provider, stageProvider, attachListener, layoutListener)
        bindings[view] = binding
        view.addOnAttachStateChangeListener(attachListener)
        view.addOnLayoutChangeListener(layoutListener)
        update(view)
    }

    fun detach(view: View) {
        bindings.remove(view)?.let { binding ->
            view.removeOnAttachStateChangeListener(binding.attachListener)
            view.removeOnLayoutChangeListener(binding.layoutListener)
        }
        reset(view)
    }

    fun update(view: View) {
        val binding = bindings[view] ?: return
        if (!view.isAttachedToWindow) return
        val stage = stageName(binding.stageProvider)
        if (stage == "NEW" || stage == "IDLE") {
            reset(view)
            return
        }
        val rawOffset = metric(binding.provider, "anchorOffset", "anchorScrollOffset", "scrollOffset", "offset", "position")
        val scrollable = boolean(binding.provider, "isScrollable", "scrollable", "canScroll")
        if (!scrollable && rawOffset <= 0f) {
            reset(view)
            return
        }
        val root = view.rootView
        val rect = Rect()
        val visible = view.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0 &&
            (root == null || rect.bottom > 0 && rect.top < root.height)
        if (!visible) {
            view.alpha = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        val maxScrollOut = dp(view.context, 36f)
        val fullyHidden = rawOffset.isNaN() || rawOffset > maxScrollOut
        // A NaN offset and any offset beyond maxScrollOut both mean "scrolled out": fully hide.
        val offset = if (rawOffset.isNaN()) maxScrollOut else rawOffset
        val progress = (offset / maxScrollOut).coerceIn(0f, 1f)
        view.pivotX = view.width / 2f
        view.pivotY = 0f
        view.translationY = -dp(view.context, 24f) * progress
        val scale = 1f - .5f * progress
        view.scaleX = scale
        view.scaleY = scale
        view.alpha = if (fullyHidden) 0f else 1f - .5f * progress
    }

    private fun reset(view: View) {
        view.animate().cancel()
        val targetAlpha = if (view.isEnabled) 1f else .38f
        view.pivotX = view.width / 2f
        view.pivotY = 0f
        // Re-display tween instead of an instant jump: modest EasingStandard (0.2,0,0,1).
        // ViewPropertyAnimator shares one duration across all targets, so progress and alpha both
        // ride a short 200ms EasingStandard tween.
        view.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(targetAlpha)
            .setDuration(200L)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .start()
    }

    private fun metric(source: Any, vararg names: String): Float {
        for (name in names) {
            try {
                val field = source.javaClass.getDeclaredField(name)
                field.isAccessible = true
                (field.get(source) as? Number)?.let { return it.toFloat() }
            } catch (_: Throwable) { }
            try {
                val getter = "get" + name.substring(0, 1).uppercase() + name.substring(1)
                val result = source.javaClass.methods.firstOrNull { it.name == getter && it.parameterTypes.isEmpty() }?.invoke(source)
                if (result is Number) return result.toFloat()
            } catch (_: Throwable) { }
        }
        return 0f
    }

    private fun boolean(source: Any, vararg names: String): Boolean {
        for (name in names) {
            try {
                val field = source.javaClass.getDeclaredField(name)
                field.isAccessible = true
                (field.get(source) as? Boolean)?.let { return it }
            } catch (_: Throwable) { }
            try {
                val suffix = name.substring(0, 1).uppercase() + name.substring(1)
                val getter = source.javaClass.methods.firstOrNull {
                    it.parameterTypes.isEmpty() && (it.name == "is$suffix" || it.name == "get$suffix")
                }?.invoke(source)
                if (getter is Boolean) return getter
            } catch (_: Throwable) { }
        }
        return false
    }

    private fun stageName(source: Any): String {
        val candidates = arrayOf("getStage", "stage", "getScreenStage", "screenStage")
        for (candidate in candidates) {
            try {
                val method = source.javaClass.methods.firstOrNull { it.name == candidate && it.parameterTypes.isEmpty() }
                val value = method?.invoke(source) ?: continue
                return value.toString().uppercase()
            } catch (_: Throwable) { }
        }
        return "SCROLLING"
    }

    private fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density
}
