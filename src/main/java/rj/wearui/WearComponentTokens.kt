package rj.wearui

/**
 * Vendored design tokens — android-only mirror of Wear Compose Material3 *Tokens.
 *
 * These objects make the source of truth explicit so component implementations
 * reference tokens instead of inline literals. Values are normative per
 * `docs/rj-wearui/token-table.md` and the Wear M3 spec; shape/typography are
 * framework-native ([WearShape]/[WearTypographyRole]), paddings/heights are
 * dp floats to be converted with `resources.displayMetrics.density` (or
 * `scaledDensity` for typography, which is owned by [WearUiTypography]).
 * This file is the single source of truth for component dimensions (heights,
 * paddings, icon sizes, margins) — views must reference these constants, not
 * duplicate literals. Shape dp values are owned by [WearUiShapes] and
 * typography sp/weight values by [WearUiTypography.defaultTokens]; this file
 * only assigns which [WearShape]/[WearTypographyRole] each component uses.
 *
 * Scope: android-only. No Compose `Dp`/`Sp` types, no `androidx` dependency.
 * Tokens live in `rj.wearui` so `momoi.mod.qqpro.lib.wearuiadapter.TokenBridge`
 * can map QQ live `M3.*` accents onto them without polluting `rj.wearui`.
 *
 * Vendored sources (semantic, not imported):
 * - `androidx.wear.compose.material3.ButtonTokens`
 * - `androidx.wear.compose.material3.CardTokens`
 * - `androidx.wear.compose.material3.ChipTokens`
 * - `androidx.wear.compose.material3.ListHeaderTokens`
 */

// ---------------------------------------------------------------------------
// ButtonTokens — vendored from ButtonTokens
// ---------------------------------------------------------------------------
object ButtonTokens {
    /** Container height for the standard filled button (52dp). */
    const val ContainerHeightDp: Float = 52f

    /** Content padding inside the button container. */
    const val ContentPaddingStartDp: Float = 14f
    const val ContentPaddingExtraLargeIconStartDp: Float = 8f
    const val ContentPaddingTopDp: Float = 6f
    const val ContentPaddingEndDp: Float = 14f
    const val ContentPaddingBottomDp: Float = 6f

    /** Leading icon size. 26dp for primary Button, 24dp for tonal/text/child variants. */
    const val IconSizeDp: Float = 26f
    const val SmallIconSizeDp: Float = 24f

    /** Gap between the leading icon slot and the text column. */
    const val IconSpacingDp: Float = 6f

    /** Container shape for Button / FilledTonal / Outlined (Large = 26dp radius). */
    val ContainerShape: WearShape = WearShape.Large

    /** TextButton uses the fully-rounded shape. */
    val TextButtonContainerShape: WearShape = WearShape.Full

    /** Primary label typography. */
    val LabelTypography: WearTypographyRole = WearTypographyRole.LabelMedium

    /** Secondary label typography (two-line button slot). */
    val SecondaryLabelTypography: WearTypographyRole = WearTypographyRole.LabelSmall

    /** Top offset of the secondary label inside the text column. */
    const val SecondaryLabelTopMarginDp: Float = 1f

    // IconButton sub-tokens — vendored from IconButtonTokens (compact sibling)
    const val IconButtonContainerSizeDp: Float = 52f
    /** Compact 48dp icon button used by [IconButtonView] measured default. */
    const val CompactIconButtonContainerSizeDp: Float = 48f
    const val IconButtonContentPaddingHorizontalDp: Float = 12f
    const val IconButtonGapDp: Float = 0f
}

// ---------------------------------------------------------------------------
// CardTokens — vendored from CardTokens
// ---------------------------------------------------------------------------
object CardTokens {
    /** Minimum container height. */
    const val ContainerMinHeightDp: Float = 64f

    /** Inner body padding (CardBody). */
    const val ContentPaddingDp: Float = 12f

    /** Leading icon size inside a Card. */
    const val IconSizeDp: Float = 32f

    /** Trailing image size inside a Card. */
    const val ImageSizeDp: Float = 64f

    /** Start margin of the text column from the icon/image slot. */
    const val TextColumnMarginStartDp: Float = 10f

    /** Top margin of the secondary line below the title. */
    const val SecondaryLabelTopMarginDp: Float = 3f

    /** Top margin of arbitrary custom content slotted under the labels. */
    const val CustomContentTopMarginDp: Float = 4f

    /** Image margin from the leading icon slot. */
    const val ImageMarginStartDp: Float = 8f

    /** Container shape for Card / OutlinedCard (Large). */
    val ContainerShape: WearShape = WearShape.Large

    /** Title typography (standard card). */
    val TitleTypography: WearTypographyRole = WearTypographyRole.TitleMedium

    /** Title typography for the title-led variant. */
    val TitleLargeTypography: WearTypographyRole = WearTypographyRole.TitleLarge

    /** Secondary text typography. */
    val SecondaryTypography: WearTypographyRole = WearTypographyRole.BodySmall
}

// ---------------------------------------------------------------------------
// ChipTokens — vendored from ChipTokens
// ---------------------------------------------------------------------------
object ChipTokens {
    /** Container height for the standard chip row. */
    const val ContainerHeightDp: Float = 52f

    /** Compact chip height (CompactChip). */
    const val CompactContainerHeightDp: Float = 48f

    /** Content padding for the standard chip (horizontal 14dp, vertical 6dp). */
    const val ContentPaddingHorizontalDp: Float = 14f
    const val ContentPaddingVerticalDp: Float = 6f

    /** Compact chip horizontal padding (12dp, vertical managed by fixed height). */
    const val CompactContentPaddingHorizontalDp: Float = 12f

    /** Leading icon size inside a chip. */
    const val IconSizeDp: Float = 24f

    /** Image size for app chips. */
    const val ImageSizeDp: Float = 48f

    /** Same text-column start margin as Card for visual rhythm. */
    const val TextColumnMarginStartDp: Float = 10f
    const val SecondaryLabelTopMarginDp: Float = 3f
    const val CustomContentTopMarginDp: Float = 4f

    /** Standard chip shape (Large). Compact chip is Full (pill). */
    val ContainerShape: WearShape = WearShape.Large
    val CompactContainerShape: WearShape = WearShape.Full

    /** Title typography — standard chip and title-chip both resolve to TitleMedium. */
    val LabelTypography: WearTypographyRole = WearTypographyRole.TitleMedium
    val SecondaryTypography: WearTypographyRole = WearTypographyRole.BodySmall
}

// ---------------------------------------------------------------------------
// ListHeaderTokens — vendored from ListHeaderTokens
// ---------------------------------------------------------------------------
/**
 * Header row placed above a [ScalingLazyColumnView]/[WearListView] section.
 * Not yet a dedicated View in `rj.wearui`; the token set documents the
 * paddings/shapes/typography that header-like rows (e.g. settings section
 * titles built from [CardBody]/[ToggleControlView] titles) must converge on
 * as they are extracted into a native `ListHeaderView`.
 */
object ListHeaderTokens {
    /** Intrinsic header height before insets. */
    const val ContainerHeightDp: Float = 32f

    /** Horizontal padding matching Button/Chip rhythm. */
    const val ContentPaddingHorizontalDp: Float = 14f

    /** Vertical padding around the header label. */
    const val ContentPaddingTopDp: Float = 4f
    const val ContentPaddingBottomDp: Float = 4f

    /** Header has no container shape of its own (inherits list background). */
    val ContainerShape: WearShape = WearShape.None

    /** Header label typography. */
    val LabelTypography: WearTypographyRole = WearTypographyRole.TitleSmall

    /** Optional supporting text below a header label. */
    val SupportingTypography: WearTypographyRole = WearTypographyRole.BodySmall
}
// ---------------------------------------------------------------------------
// SliderTokens — dimensional mirror of vendored Slider/SliderDefaults (4dp/8dp)
// ---------------------------------------------------------------------------
/**
 * Slider geometry. Vendored `SliderTokens` exposes only colors; dimensions live in
 * `SliderView` defaults (`track 4dp, thumb 8dp`) and in wear's `SliderDefaults` helpers.
 * These values are normalized here so `SliderView/InlineSlider` don't duplicate literals.
 */
object SliderTokens {
    /** Track thickness (height of the bar). */
    const val TrackWidthDp: Float = 4f
    /** Thumb radius (half diameter). Vendored thumb is 8dp radius → 16dp diameter. */
    const val ThumbRadiusDp: Float = 8f
    /** Inline slider collapsed height 40dp, thumb 7dp per vendored Inline. */
    const val InlineTrackWidthDp: Float = 3f
    const val InlineThumbRadiusDp: Float = 7f
    const val InlineContainerHeightDp: Float = 40f

    val ContainerShape: WearShape = WearShape.Large
    val LabelTypography: WearTypographyRole = WearTypographyRole.LabelMedium
}

// ---------------------------------------------------------------------------
// ProgressIndicatorTokens — vendored from CircularProgressIndicatorDefaults
// ---------------------------------------------------------------------------
object ProgressIndicatorTokens {
    /** Large stroke for circular (12dp on large screen, 8dp on small → native 12dp base). */
    const val CircularLargeStrokeWidthDp: Float = 12f
    /** Small stroke for circular (8dp large, 5dp small → native 8dp base). */
    const val CircularSmallStrokeWidthDp: Float = 8f
    const val CircularIndeterminateStrokeWidthDp: Float = 3f
    const val CircularGapFactor: Float = 1f / 3f // gap = stroke/3
    const val StartAngle: Float = 270f
    const val FullScreenPaddingDp: Float = 12f // PaddingDefaults.edgePadding
    /** Linear track height 12dp per LinearProgressIndicatorDefaults. */
    const val LinearTrackHeightDp: Float = 12f
    /** Level indicator 120° sweep, 4dp stroke, 5 levels (vendored LevelIndicator). */
    const val LevelSweepDegrees: Float = 120f
    const val LevelStrokeWidthDp: Float = 4f
    const val LevelCount: Int = 5
    /** Arc progress 65° start, 280° sweep, 3dp default stroke. */
    const val ArcStartAngle: Float = 65f
    const val ArcSweepDegrees: Float = 280f
    const val ArcStrokeWidthDp: Float = 3f
}

// ---------------------------------------------------------------------------
// PickerTokens — vendored from Picker.kt (3 visible, 0.70/0.50 cubic 0.3,0,0.7,1)
// ---------------------------------------------------------------------------
object PickerTokens {
    const val VisibleItems: Int = 3
    const val ItemHeightDp: Float = 32f
    const val ContainerHeightDp: Float = 120f
    const val SelectionRadiusDp: Float = 8f
    const val MinScale: Float = 0.70f
    const val MinAlpha: Float = 0.50f
}

// ---------------------------------------------------------------------------
// PageIndicatorTokens — vendored from PageIndicatorSpacing
// ---------------------------------------------------------------------------
object PageIndicatorTokens {
    const val IndicatorSizeDp: Float = 6f
    const val SpacingDp: Float = 4f
    const val SelectedAlpha: Float = 1f
    const val UnselectedAlpha: Float = 0.66f
    val LabelTypography: WearTypographyRole = WearTypographyRole.LabelSmall
}

// ---------------------------------------------------------------------------
// VignetteTokens — vendored from VignettePosition
// ---------------------------------------------------------------------------
object VignetteTokens {
    const val TopAlpha: Float = 0.50f
    const val BottomAlpha: Float = 0.32f
    const val BothAlpha: Float = 0.20f
}

// ---------------------------------------------------------------------------
// DialogTokens — vendored from Dialog.kt (Alert/Confirmation)
// ---------------------------------------------------------------------------
object DialogTokens {
    const val ContainerMaxWidthDp: Float = 280f
    const val ButtonHeightDp: Float = 52f
    val TitleTypographyRole: WearTypographyRole = WearTypographyRole.TitleMedium
    val ContentTypographyRole: WearTypographyRole = WearTypographyRole.BodyMedium
}

// ---------------------------------------------------------------------------
// TimeTextTokens — vendored from TimeText.kt
// ---------------------------------------------------------------------------
object TimeTextTokens {
    const val ContainerHeightDp: Float = 30f
    const val ClockRadiusInsetDp: Float = 9f
    const val MaxSweepDegrees: Float = 70f
    const val EdgePaddingDp: Float = 12f // PaddingDefaults.edgePadding mirrored for TimeText
}

// ---------------------------------------------------------------------------
// SwitchTokens — vendored from SwitchButton.kt (52dp row, 32x22 track, 6→9dp thumb)
// ---------------------------------------------------------------------------
object SwitchTokens {
    const val ContainerHeightDp: Float = 52f
    const val TrackWidthDp: Float = 32f
    const val TrackOuterHeightDp: Float = 24f
    const val TrackInnerHeightDp: Float = 22f
    const val ThumbRadiusUncheckedDp: Float = 6f
    const val ThumbRadiusCheckedDp: Float = 9f
    const val BorderWidthDp: Float = 2f
    const val AnimationDurationMillis: Long = 160L
}
