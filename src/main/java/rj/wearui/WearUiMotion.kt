package rj.wearui

import android.animation.TimeInterpolator
import android.graphics.Path
import android.view.animation.PathInterpolator

/** Exact duration tokens used by WearUI transitions. */
object WearMotionDurations {
    const val Short50: Long = 50L
    const val Short100: Long = 100L
    const val Short150: Long = 150L
    const val Short200: Long = 200L
    const val Medium250: Long = 250L
    const val Medium300: Long = 300L
    const val Medium350: Long = 350L
    const val Medium400: Long = 400L
    const val Long450: Long = 450L
    const val Long500: Long = 500L
    const val Long550: Long = 550L
    const val Long600: Long = 600L
    const val ExtraLong700: Long = 700L
    const val ExtraLong800: Long = 800L
    const val ExtraLong900: Long = 900L
    const val ExtraLong1000: Long = 1000L

    // Readable aliases for callers that select one duration per family.
    const val Short: Long = Short200
    const val Medium: Long = Medium400
    const val Long: Long = Long600
    const val ExtraLong: Long = ExtraLong1000
}

/** Cubic-bezier easing tokens. */
object WearMotionEasings {
    @JvmField val EmphasizedAccelerate: TimeInterpolator = PathInterpolator(.3f, 0f, .8f, .15f)
    @JvmField val EmphasizedDecelerate: TimeInterpolator = PathInterpolator(.05f, .7f, .1f, 1f)
    @JvmField val EmphasizedStandard: TimeInterpolator = PathInterpolator(Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133f, 0.06f, 0.166f, 0.4f)
        cubicTo(0.208f, 0.82f, 0.25f, 1f, 1f, 1f)
    })
    @JvmField val LegacyAccelerate: TimeInterpolator = PathInterpolator(.4f, 0f, 1f, 1f)
    @JvmField val LegacyDecelerate: TimeInterpolator = PathInterpolator(0f, 0f, .2f, 1f)
    @JvmField val LegacyStandard: TimeInterpolator = PathInterpolator(.4f, 0f, .2f, 1f)
    @JvmField val Standard: TimeInterpolator = PathInterpolator(.2f, 0f, 0f, 1f)
    @JvmField val StandardAccelerate: TimeInterpolator = PathInterpolator(.3f, 0f, 1f, 1f)
    @JvmField val StandardDecelerate: TimeInterpolator = PathInterpolator(0f, 0f, 0f, 1f)
}

data class WearSpringSpec(
    val dampingRatio: Float,
    val stiffness: Float,
    val bouncy: Boolean = false
)

/** Named spring families; callers may use these values to drive a ValueAnimator manually. */
object WearMotionSprings {
    @JvmField val StandardSpatialSmall = WearSpringSpec(1f, 500f)
    @JvmField val StandardSpatialMedium = WearSpringSpec(1f, 1400f)
    @JvmField val StandardSpatialLarge = WearSpringSpec(1f, 260f)

    @JvmField val ExpressiveSpatialSmall = WearSpringSpec(.75f, 350f, true)
    @JvmField val ExpressiveSpatialMedium = WearSpringSpec(.7f, 800f, true)
    @JvmField val ExpressiveSpatialLarge = WearSpringSpec(.8f, 200f, true)

    @JvmField val EffectsSmall = WearSpringSpec(1f, 500f)
    @JvmField val EffectsMedium = WearSpringSpec(1f, 1400f)
    @JvmField val EffectsLarge = WearSpringSpec(1f, 260f)
}

/** Serializable-ish motion description shared by animation-capable native views. */
data class WearMotionSpec(
    val durationMillis: Long = WearMotionDurations.Medium,
    val interpolator: TimeInterpolator = WearMotionEasings.Standard,
    val spring: WearSpringSpec? = null
) {
    val durationMs: Long get() = durationMillis

    fun withReducedMotion(reduced: Boolean): WearMotionSpec {
        return if (reduced) copy(durationMillis = 0L, spring = null) else this
    }

    companion object {
        @JvmField val Standard = WearMotionSpec(WearMotionDurations.Medium, WearMotionEasings.Standard)
        @JvmField val StandardDecelerate = WearMotionSpec(WearMotionDurations.Long500, WearMotionEasings.StandardDecelerate)
        @JvmField val Emphasized = WearMotionSpec(WearMotionDurations.Medium, WearMotionEasings.EmphasizedDecelerate)
        @JvmField val UnbouncyMediumSpring = WearMotionSpec(
            WearMotionDurations.Medium,
            WearMotionEasings.StandardDecelerate,
            WearMotionSprings.EffectsMedium
        )
    }
}

/**
 * Central MotionScheme unifying rj.wearui effect springs.
 *
 * - [fastEffectsSpec] — spring(damping=1, stiffness=1400), no-bouncy, for selection progress.
 * - [slowEffectsSpec] — spring(damping=1, stiffness=260), no-bouncy, for colors.
 *
 * Both are android-only (no Compose runtime) and use [WearMotionSpec.withReducedMotion] to snap
 * when reduced motion is enabled. Stiffness values map to [WearMotionSprings.EffectsMedium]
 * and [WearMotionSprings.EffectsLarge] respectively.
 */
object MotionScheme {
    @JvmField val fastEffectsSpec: WearMotionSpec = WearMotionSpec(
        durationMillis = WearMotionDurations.Short200,
        interpolator = WearMotionEasings.StandardDecelerate,
        spring = WearMotionSprings.EffectsMedium // damping 1, stiffness 1400
    )

    @JvmField val slowEffectsSpec: WearMotionSpec = WearMotionSpec(
        durationMillis = WearMotionDurations.Medium400,
        interpolator = WearMotionEasings.Standard,
        spring = WearMotionSprings.EffectsLarge // damping 1, stiffness 260
    )

    // Explicit aliases matching the Compose token naming used in the spec.
    @JvmField val FastEffectsSpec: WearMotionSpec = fastEffectsSpec
    @JvmField val SlowEffectsSpec: WearMotionSpec = slowEffectsSpec
}

/**
 * Resolve whether reduced motion is requested on this device.
 * Android-only: checks the global animator duration scale.
 */
fun isReducedMotionRequested(context: android.content.Context): Boolean {
    return try {
        val scale = android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        scale == 0f || !android.animation.ValueAnimator.areAnimatorsEnabled()
    } catch (_: Exception) {
        !android.animation.ValueAnimator.areAnimatorsEnabled()
    }
}
