package rj.wearui

import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/**
 * Typography token table — single source of truth for sp/weight/tracking dimensions.
 * Component role assignments (which [WearTypographyRole] each component uses) live in
 * [WearComponentTokens.kt]. Consumers such as [WearTextView.setTypographyRole] (see
 * WearTextIconChipViews.kt) must dispatch on [WearTypographyRole] enum directly
 * (when(role)) and delegate to [WearUiTypography.token]/[defaultTokens] — no
 * String.contains checks. If string matching is ever reintroduced, add:
 * TODO(WearTypographyRole): replace string match with enum when.
 */

/** The complete native typography role set. */
enum class WearTypographyRole {
    ArcLarge,
    ArcMedium,
    ArcSmall,
    DisplayLarge,
    DisplayMedium,
    DisplaySmall,
    TitleLarge,
    TitleMedium,
    TitleSmall,
    LabelLarge,
    LabelMedium,
    LabelSmall,
    BodyLarge,
    BodyMedium,
    BodySmall,
    BodyExtraSmall,
    NumeralExtraLarge,
    NumeralLarge,
    NumeralMedium,
    NumeralSmall,
    NumeralExtraSmall
}

/** One typography role expressed in scalable pixels and variable-font-friendly values. */
data class WearTypographyToken(
    val sizeSp: Float,
    val lineHeightSp: Float,
    val weight: Int,
    val widthPercent: Int,
    val trackingSp: Float,
    val arcTopPaddingSp: Float = 0f,
    val arcBottomPaddingSp: Float = 0f,
    val prominentWeight: Int = weight
)

/** Typography tokens plus safe application to a framework [TextView]. */
data class WearUiTypography(
    private val tokens: Map<WearTypographyRole, WearTypographyToken> = defaultTokens()
) {
    fun token(role: WearTypographyRole): WearTypographyToken {
        return tokens[role] ?: throw IllegalArgumentException("Missing typography token: $role")
    }

    fun copyWith(role: WearTypographyRole, token: WearTypographyToken): WearUiTypography {
        return WearUiTypography(tokens + (role to token))
    }

    /**
     * Applies size, tracking, line height and the best available sans-serif weight treatment.
     * API 21–25 uses a stable Roboto/sans-serif fallback. Variable-font settings are only used on
     * API 26 and later and failures are intentionally non-fatal for vendor font stacks.
     */
    fun applyTo(
        textView: TextView,
        role: WearTypographyRole,
        useProminentWeight: Boolean = false,
        centerLineHeight: Boolean = true
    ) {
        val token = token(role)
        val metrics = textView.resources.displayMetrics
        textView.includeFontPadding = false
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, token.sizeSp)
        textView.letterSpacing = if (token.sizeSp > 0f) token.trackingSp / token.sizeSp else 0f
        textView.textScaleX = token.widthPercent / 100f

        val desiredWeight = if (useProminentWeight) token.prominentWeight else token.weight
        if (Build.VERSION.SDK_INT >= 26) {
            val family = runCatching { Typeface.create("roboto-flex", Typeface.NORMAL) }
                .getOrDefault(Typeface.create("sans-serif", Typeface.NORMAL))
            textView.typeface = family
            val applied = try {
                textView.fontFeatureSettings = null
                textView.setTypeface(family)
                textView.setFontVariationSettings("'wght' $desiredWeight, 'wdth' ${token.widthPercent}")
            } catch (_: RuntimeException) {
                false
            }
            if (applied) {
                // A successful variable-font request already carries the width axis. Adding
                // textScaleX would widen the glyphs twice.
                textView.textScaleX = 1f
            } else {
                textView.typeface = fallbackTypeface(desiredWeight)
                textView.textScaleX = token.widthPercent / 100f
            }
        } else {
            textView.typeface = fallbackTypeface(desiredWeight)
            textView.textScaleX = token.widthPercent / 100f
        }

        // Compose uses lineHeightStyle centered (extra distributed top+bottom). TextView's
        // fallbackLineSpacing (API 28) must be disabled to get the same centering; otherwise
        // setLineSpacing adds all extra below baseline and text appears upward-biased.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                textView.isFallbackLineSpacing = false
                textView.lineHeight = (token.lineHeightSp * metrics.scaledDensity + 0.5f).toInt()
            } catch (_: Throwable) {
                val lineHeightPx = token.lineHeightSp * metrics.scaledDensity
                val textSizePx = token.sizeSp * metrics.scaledDensity
                textView.setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
            }
        } else {
            val lineHeightPx = token.lineHeightSp * metrics.scaledDensity
            val textSizePx = token.sizeSp * metrics.scaledDensity
            textView.setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
        }
        if (centerLineHeight) {
            textView.gravity = (textView.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) or Gravity.CENTER_VERTICAL
        }
    }

    private fun fallbackTypeface(weight: Int): Typeface {
        return when {
            weight >= 650 -> Typeface.create("sans-serif", Typeface.BOLD)
            weight >= 450 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    companion object {
        @JvmField
        val Default = WearUiTypography()

        @JvmStatic
        fun defaultTokens(): Map<WearTypographyRole, WearTypographyToken> = mapOf(
            WearTypographyRole.ArcLarge to WearTypographyToken(18f, 22f, 599, 100, 0f, .4f, 1.6f),
            WearTypographyRole.ArcMedium to WearTypographyToken(15f, 18f, 599, 100, 0f, .6f, 1.4f),
            WearTypographyRole.ArcSmall to WearTypographyToken(14f, 16f, 560, 100, 0f, .6f, 1.4f),
            WearTypographyRole.DisplayLarge to WearTypographyToken(40f, 44f, 500, 110, .2f),
            WearTypographyRole.DisplayMedium to WearTypographyToken(30f, 34f, 520, 110, .2f),
            WearTypographyRole.DisplaySmall to WearTypographyToken(24f, 26f, 550, 110, .2f),
            WearTypographyRole.TitleLarge to WearTypographyToken(18f, 20f, 500, 110, .2f),
            WearTypographyRole.TitleMedium to WearTypographyToken(16f, 18f, 550, 110, .4f),
            WearTypographyRole.TitleSmall to WearTypographyToken(14f, 16f, 550, 110, .4f),
            WearTypographyRole.LabelLarge to WearTypographyToken(20f, 22f, 500, 110, .4f),
            WearTypographyRole.LabelMedium to WearTypographyToken(15f, 18f, 500, 110, .4f),
            WearTypographyRole.LabelSmall to WearTypographyToken(13f, 16f, 500, 110, .4f),
            WearTypographyRole.BodyLarge to WearTypographyToken(16f, 18f, 450, 110, .4f, prominentWeight = 650),
            WearTypographyRole.BodyMedium to WearTypographyToken(14f, 16f, 450, 110, .4f, prominentWeight = 650),
            WearTypographyRole.BodySmall to WearTypographyToken(12f, 14f, 500, 110, .4f, prominentWeight = 700),
            WearTypographyRole.BodyExtraSmall to WearTypographyToken(10f, 12f, 500, 104, .2f, prominentWeight = 700),
            WearTypographyRole.NumeralExtraLarge to WearTypographyToken(60f, 60f, 560, 110, 0f, prominentWeight = 760),
            WearTypographyRole.NumeralLarge to WearTypographyToken(50f, 50f, 580, 110, 0f, prominentWeight = 780),
            WearTypographyRole.NumeralMedium to WearTypographyToken(40f, 40f, 580, 100, 0f, prominentWeight = 780),
            WearTypographyRole.NumeralSmall to WearTypographyToken(30f, 30f, 550, 100, 0f, prominentWeight = 750),
            WearTypographyRole.NumeralExtraSmall to WearTypographyToken(24f, 24f, 550, 100, 0f, prominentWeight = 750)
        )
    }
}

typealias WearTypography = WearUiTypography

fun TextView.applyWearTypography(
    role: WearTypographyRole,
    typography: WearUiTypography = WearUiTypography.Default,
    useProminentWeight: Boolean = false,
    centerLineHeight: Boolean = true
) {
    typography.applyTo(this, role, useProminentWeight, centerLineHeight)
}
