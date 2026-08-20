package rj.wearui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import java.util.Collections
import java.util.WeakHashMap

/** Display-density conversion tied to the resources actually used by a native View. */
class WearUiDensity(context: Context) {
    val density: Float = context.resources.displayMetrics.density
    val scaledDensity: Float = context.resources.displayMetrics.scaledDensity

    fun dp(value: Float): Int = (value * density + .5f).toInt()
    fun dpFloat(value: Float): Float = value * density
    fun sp(value: Float): Float = value * scaledDensity
}

/** Complete theme value consumed by WearUI native components. */
data class WearUiTheme(
    val colors: WearColorScheme = WearColorScheme.Dark,
    val typography: WearUiTypography = WearUiTypography.Default,
    val shapes: WearUiShapes = WearUiShapes.Default,
    val reducedMotionEnabled: Boolean = false
) {
    fun contentColorFor(backgroundColor: Int): Int? = colors.contentColorFor(backgroundColor)

    fun disabledColor(color: Int, alpha: Float): Int {
        val clamped = alpha.coerceIn(0f, 1f)
        return Color.argb((Color.alpha(color) * clamped + .5f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }

    companion object {
        @JvmField val Default = WearUiTheme()

        private val installedThemes: MutableMap<Context, WearUiTheme> = Collections.synchronizedMap(WeakHashMap())

        /** Associates a theme with a Context without retaining that Context. */
        @JvmStatic
        fun install(context: Context, theme: WearUiTheme) {
            installedThemes[context] = theme
        }

        @JvmStatic
        fun clear(context: Context) {
            installedThemes.remove(context)
        }

        /** Returns the nearest Context-associated theme or the documented dark default. */
        @JvmStatic
        fun from(context: Context?): WearUiTheme {
            if (context == null) return Default
            synchronized(installedThemes) {
                var cursor: Context? = context
                while (cursor != null) {
                    installedThemes[cursor]?.let { return it }
                    val base = if (cursor is android.content.ContextWrapper) cursor.baseContext else null
                    if (base === cursor) break
                    cursor = base
                }
            }
            return Default
        }

        @JvmStatic
        fun fromView(view: View?): WearUiTheme = if (view == null) Default else from(view.context)
    }
}

/** Small measured-unit and interpolation helpers shared by native components. */
object WearUiMath {
    @JvmStatic fun dp(view: View, value: Float): Int = WearUiDensity(view.context).dp(value)
    @JvmStatic fun dp(context: Context, value: Float): Int = WearUiDensity(context).dp(value)
    @JvmStatic fun sp(context: Context, value: Float): Float = WearUiDensity(context).sp(value)
    @JvmStatic fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)
    @JvmStatic fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
    @JvmStatic fun quantize(value: Float, step: Float): Float = if (step <= 0f) value else (value / step).toInt() * step
    @JvmStatic fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color) * alpha.coerceIn(0f, 1f) + .5f).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}

/** Allocation-conscious drawing helpers that always resolve shapes from measured bounds. */
object WearUiDraw {
    @JvmStatic
    fun roundRectPath(bounds: RectF, shape: WearShape, density: Float, shapes: WearUiShapes = WearUiShapes.Default): Path {
        val radius = shapes.cornerRadiusPx(shape, bounds, density)
        return Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
    }

    @JvmStatic
    fun drawRoundRect(canvas: Canvas, paint: Paint, bounds: RectF, shape: WearShape, density: Float, shapes: WearUiShapes = WearUiShapes.Default) {
        val radius = shapes.cornerRadiusPx(shape, bounds, density)
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}

/** Runtime animation policy with API-safe system animator checks. */
object WearUiRuntime {
    @JvmStatic
    fun animationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled() else true
    }

    @JvmStatic
    fun shouldAnimate(view: View?, reducedMotionEnabled: Boolean = false): Boolean {
        return view != null && view.isAttachedToWindow && !reducedMotionEnabled && animationsEnabled()
    }
}

/** Accessibility helpers shared by native controls without AndroidX delegates. */
object WearUiAccessibility {
    @JvmStatic
    fun markDecorative(view: View) {
        view.contentDescription = null
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @JvmStatic
    fun markInteractive(view: View, description: CharSequence?) {
        view.contentDescription = description
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    @JvmStatic
    fun setStateDescription(view: View, description: CharSequence?) {
        if (Build.VERSION.SDK_INT >= 30) view.stateDescription = description
    }

    @JvmStatic
    fun completeClick(view: View): Boolean = view.performClick()
}
