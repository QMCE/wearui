package rj.wearui

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Swipe-dismiss host with an API 21 touch-drag back fallback and a reflection-only Android 13+
 * back bridge. No newer back-dispatcher types appear in this class's public ABI.
 */
open class PredictiveBackHostView : SwipeDismissHostView {
    private var platformDispatcher: Any? = null
    private var platformCallback: Any? = null
    private var unregisterMethod: Method? = null
    private var predictiveProgress = 0f

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    /** Enables/disables platform-back reflection; touch dismissal remains available on all APIs. */
    fun setPlatformPredictiveBackEnabled(enabled: Boolean) {
        if (enabled && isAttachedToWindow) registerPlatformBack() else unregisterPlatformBack()
    }

    /** Applies progress from a guarded platform bridge or a host application's own back gesture. */
    fun setPredictiveBackProgress(progress: Float) {
        predictiveProgress = progress.coerceIn(0f, 1f)
        if (reducedMotionForPredictive()) return
        val navigator = getNavigator() ?: return
        if (!navigator.isDismissalPending()) navigator.beginDismissal()
        if (navigator.isDismissalPending()) applyDismissProgress(predictiveProgress)
    }

    fun finishPredictiveBack(commit: Boolean) {
        val navigator = getNavigator()
        if (navigator?.isDismissalPending() != true && commit) {
            if (navigator?.beginDismissal() != true) return
        }
        finishDismissal(commit)
        predictiveProgress = 0f
    }

    private fun reducedMotionForPredictive(): Boolean = isReducedMotionEnabled()

    private fun registerPlatformBack() {
        if (platformCallback != null || Build.VERSION.SDK_INT < 33) return
        try {
            val callbackClass = Class.forName("android.window.OnBackInvokedCallback")
            val dispatcher = context.javaClass.getMethod("getOnBackInvokedDispatcher").invoke(context) ?: return
            val callback = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { _, method, _ ->
                if (method.name == "onBackInvoked") finishPredictiveBack(true)
                null
            }
            val register = dispatcher.javaClass.getMethod(
                "registerOnBackInvokedCallback",
                Int::class.javaPrimitiveType,
                callbackClass
            )
            unregisterMethod = dispatcher.javaClass.getMethod("unregisterOnBackInvokedCallback", callbackClass)
            // Priority default is 0. This is intentionally reflective so minSdk 21 verification
            // never links an Android 13 framework type.
            register.invoke(dispatcher, 0, callback)
            platformDispatcher = dispatcher
            platformCallback = callback
        } catch (_: ReflectiveOperationException) {
            unregisterPlatformBack()
        } catch (_: SecurityException) {
            unregisterPlatformBack()
        } catch (_: RuntimeException) {
            unregisterPlatformBack()
        }
    }

    private fun unregisterPlatformBack() {
        val dispatcher = platformDispatcher
        val callback = platformCallback
        if (dispatcher != null && callback != null) {
            try {
                unregisterMethod?.invoke(dispatcher, callback)
            } catch (_: ReflectiveOperationException) {
                // The platform bridge is optional; the touch fallback remains active.
            } catch (_: SecurityException) {
                // The platform bridge is optional; the touch fallback remains active.
            }
        }
        unregisterMethod = null
        platformCallback = null
        platformDispatcher = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerPlatformBack()
    }

    override fun onDetachedFromWindow() {
        unregisterPlatformBack()
        super.onDetachedFromWindow()
    }
}
