package rj.wearui

/**
 * ARGB colors used by WearUI.  The default is intentionally dark-only; applications can supply
 * a different instance instead of relying on an undocumented light palette.
 */
data class WearColorScheme(
    val primary: Int,
    val primaryDim: Int,
    val primaryContainer: Int,
    val onPrimary: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val secondaryDim: Int,
    val secondaryContainer: Int,
    val onSecondary: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryDim: Int,
    val tertiaryContainer: Int,
    val onTertiary: Int,
    val onTertiaryContainer: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val background: Int,
    val onBackground: Int,
    val error: Int,
    val errorDim: Int,
    val errorContainer: Int,
    val onError: Int,
    val onErrorContainer: Int
) {
    /** Returns the paired foreground for a defined color role, or null for arbitrary colors. */
    fun contentColorFor(backgroundColor: Int): Int? = when (backgroundColor) {
        primary, primaryDim -> onPrimary
        primaryContainer -> onPrimaryContainer
        secondary, secondaryDim -> onSecondary
        secondaryContainer -> onSecondaryContainer
        tertiary, tertiaryDim -> onTertiary
        tertiaryContainer -> onTertiaryContainer
        surfaceContainerLow, surfaceContainer, surfaceContainerHigh -> onSurface
        background -> onBackground
        error, errorDim -> onError
        errorContainer -> onErrorContainer
        else -> null
    }

    companion object {
        /** Default Wear Material dark color scheme expressed as opaque ARGB integers. */
        @JvmField
        val Dark: WearColorScheme = WearColorScheme(
            primary = 0xFFE9DDFF.toInt(),
            primaryDim = 0xFFD0BCFF.toInt(),
            primaryContainer = 0xFF4D3D76.toInt(),
            onPrimary = 0xFF210F48.toInt(),
            onPrimaryContainer = 0xFFF6EDFF.toInt(),
            secondary = 0xFFDEE0FF.toInt(),
            secondaryDim = 0xFFBAC3FF.toInt(),
            secondaryContainer = 0xFF3A4376.toInt(),
            onSecondary = 0xFF0C1649.toInt(),
            onSecondaryContainer = 0xFFF0EFFF.toInt(),
            tertiary = 0xFFFFDCC2.toInt(),
            tertiaryDim = 0xFFFFB77A.toInt(),
            tertiaryContainer = 0xFF6C3A03.toInt(),
            onTertiary = 0xFF2E1500.toInt(),
            onTertiaryContainer = 0xFFFFEEE2.toInt(),
            surfaceContainerLow = 0xFF272430.toInt(),
            surfaceContainer = 0xFF332E3C.toInt(),
            surfaceContainerHigh = 0xFF494453.toInt(),
            onSurface = 0xFFF6EDFF.toInt(),
            onSurfaceVariant = 0xFFCAC4D0.toInt(),
            outline = 0xFF948F9A.toInt(),
            outlineVariant = 0xFF615D67.toInt(),
            background = 0xFF000000.toInt(),
            onBackground = 0xFFFFFFFF.toInt(),
            error = 0xFFF2B8B5.toInt(),
            errorDim = 0xFFEC928E.toInt(),
            errorContainer = 0xFF8C1D18.toInt(),
            onError = 0xFF410E0B.toInt(),
            onErrorContainer = 0xFFFCEEEE.toInt()
        )

        @JvmStatic
        fun defaultDark(): WearColorScheme = Dark
    }
}

/** Standard disabled alpha values. Apply them to the relevant role, not to an entire hierarchy. */
object WearUiDisabledAlpha {
    const val Content: Float = 0.38f
    const val Container: Float = 0.12f
    const val Border: Float = 0.20f
}
