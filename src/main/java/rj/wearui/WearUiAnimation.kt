package rj.wearui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.View

/** Small ownership wrapper that keeps one animation cancellable per native component. */
class WearAnimationController(
    var reducedMotion: Boolean = false
) {
    private var current: ValueAnimator? = null

    fun cancel() {
        current?.cancel()
        current = null
    }

    fun isRunning(): Boolean = current?.isRunning == true

    fun animateFloat(
        from: Float,
        to: Float,
        spec: WearMotionSpec = WearMotionSpec.Standard,
        onUpdate: (Float) -> Unit,
        onEnd: (() -> Unit)? = null
    ) {
        // Tokenized snap via withReducedMotion; duration 0 => snap (duration 0 handled via spec).
        val resolved = spec.withReducedMotion(reducedMotion)
        if (resolved.durationMillis <= 0L || from == to) {
            cancel()
            onUpdate(to)
            onEnd?.invoke()
            return
        }
        cancel()
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = resolved.durationMillis
        animator.interpolator = resolved.interpolator
        animator.addUpdateListener { onUpdate((it.animatedValue as Number).toFloat()) }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd?.invoke()
                if (current === animator) current = null
            }
        })
        current = animator
        animator.start()
    }

    /** Host-aware variant checks isAttachedToWindow and isReducedMotionRequested via withReducedMotion. */
    fun animateFloat(
        host: View,
        from: Float,
        to: Float,
        spec: WearMotionSpec = WearMotionSpec.Standard,
        onUpdate: (Float) -> Unit,
        onEnd: (() -> Unit)? = null
    ) {
        val shouldSnap = reducedMotion || !host.isAttachedToWindow || isReducedMotionRequested(host.context)
        animateFloat(from, to, spec.withReducedMotion(shouldSnap), onUpdate, onEnd)
    }

    fun animateInt(
        from: Int,
        to: Int,
        spec: WearMotionSpec = WearMotionSpec.Standard,
        onUpdate: (Int) -> Unit,
        onEnd: (() -> Unit)? = null
    ) {
        animateFloat(from.toFloat(), to.toFloat(), spec, { onUpdate(it.toInt()) }, onEnd)
    }

    fun animateColor(
        from: Int,
        to: Int,
        spec: WearMotionSpec = WearMotionSpec.Standard,
        onUpdate: (Int) -> Unit,
        onEnd: (() -> Unit)? = null
    ) {
        animateFloat(0f, 1f, spec, {
            val t = it.coerceIn(0f, 1f)
            onUpdate(Color.argb(
                (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
                (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
                (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
                (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
            ))
        }, onEnd)
    }
}

/** Function-oriented helpers for views that do not need to retain a controller. */
object WearAnimation {
    @JvmStatic
    fun cancel(animator: ValueAnimator?) {
        animator?.cancel()
    }

    @JvmStatic
    fun snapFloat(value: Float, onUpdate: (Float) -> Unit) {
        onUpdate(value)
    }

    @JvmStatic
    fun interpolate(
        from: Float,
        to: Float,
        durationMillis: Long = WearMotionDurations.Medium,
        interpolator: TimeInterpolator = WearMotionEasings.Standard,
        onUpdate: (Float) -> Unit,
        onEnd: (() -> Unit)? = null
    ): ValueAnimator? {
        val spec = WearMotionSpec(durationMillis, interpolator).withReducedMotion(durationMillis <= 0L)
        if (spec.durationMillis <= 0L || from == to) {
            onUpdate(to)
            onEnd?.invoke()
            return null
        }
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = spec.durationMillis
        animator.interpolator = spec.interpolator
        animator.addUpdateListener { onUpdate((it.animatedValue as Number).toFloat()) }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) { if (!cancelled) onEnd?.invoke() }
        })
        animator.start()
        return animator
    }

    /** Host-aware interpolate checks isAttachedToWindow and isReducedMotionRequested via withReducedMotion. */
    @JvmStatic
    fun interpolate(
        host: View,
        from: Float,
        to: Float,
        spec: WearMotionSpec = WearMotionSpec.Standard,
        onUpdate: (Float) -> Unit,
        onEnd: (() -> Unit)? = null
    ): ValueAnimator? {
        val shouldSnap = !host.isAttachedToWindow || isReducedMotionRequested(host.context)
        val resolved = spec.withReducedMotion(shouldSnap)
        if (resolved.durationMillis <= 0L || from == to) {
            onUpdate(to)
            onEnd?.invoke()
            return null
        }
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = resolved.durationMillis
        animator.interpolator = resolved.interpolator
        animator.addUpdateListener { onUpdate((it.animatedValue as Number).toFloat()) }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) { if (!cancelled) onEnd?.invoke() }
        })
        animator.start()
        return animator
    }
}

/** Common base contract for components that expose an explicit reduced-motion setting. */
interface WearReducedMotionHost {
    fun setReducedMotionEnabled(enabled: Boolean)
    fun isReducedMotionEnabled(): Boolean
}

fun View.cancelWearAnimation(animator: ValueAnimator?) {
    animator?.cancel()
}
