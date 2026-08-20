package rj.wearui

import android.graphics.RectF

/**
 * Shape token table — single source of truth for dp corner radii.
 * Component shape assignments (which [WearShape] each component uses) live in
 * [WearComponentTokens.kt]. Consumers such as [WearControlFrame.shapeRadius] (see
 * WearTextIconChipViews.kt) must dispatch on [WearShape] enum directly
 * (when(shape) / [WearUiShapes.radiusDp]/[WearUiShapes.cornerRadiusPx]) — no
 * String.contains checks. If string matching is ever reintroduced, add:
 * TODO(WearShape): replace string match with enum when.
 */

/** Named corner treatments understood by native WearUI components. */
enum class WearShape {
    None,
    ExtraSmall,
    Small,
    Medium,
    Large,
    ExtraLarge,
    Full
}

/** Density-independent corner-radius tokens and measured-bounds conversion helpers. */
data class WearUiShapes(
    val noneDp: Float = 0f,
    val extraSmallDp: Float = 4f,
    val smallDp: Float = 8f,
    val mediumDp: Float = 18f,
    val largeDp: Float = 26f,
    val extraLargeDp: Float = 36f
) {
    fun radiusDp(shape: WearShape): Float = when (shape) {
        WearShape.None -> noneDp
        WearShape.ExtraSmall -> extraSmallDp
        WearShape.Small -> smallDp
        WearShape.Medium -> mediumDp
        WearShape.Large -> largeDp
        WearShape.ExtraLarge -> extraLargeDp
        WearShape.Full -> 0f
    }

    /**
     * Resolves a shape against the measured bounds. Full is always exactly half of the shortest
     * measured side, so it remains circular even in a non-square parent.
     */
    fun cornerRadiusPx(shape: WearShape, widthPx: Float, heightPx: Float, density: Float): Float {
        return if (shape == WearShape.Full) {
            (minOf(widthPx, heightPx) / 2f).coerceAtLeast(0f)
        } else {
            (radiusDp(shape) * density).coerceAtLeast(0f)
        }
    }

    fun cornerRadiusPx(shape: WearShape, bounds: RectF, density: Float): Float {
        return cornerRadiusPx(shape, bounds.width(), bounds.height(), density)
    }

    companion object {
        @JvmField
        val Default = WearUiShapes()
    }
}

fun WearShape.radiusPx(widthPx: Float, heightPx: Float, density: Float, shapes: WearUiShapes = WearUiShapes.Default): Float {
    return shapes.cornerRadiusPx(this, widthPx, heightPx, density)
}
