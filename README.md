# wearui — rj.wearui native Wear Material3 View system

Pure Android `View` port of Wear Compose Material3, zero `androidx` / `compose` / `momoi` deps.

- Package `rj.wearui` — only `android.* + java/javax/kotlin` (API 21+ loadable)
- Design tokens mirrored from `~/qmce/app-new/src/main/java/androidx/wear/compose/material3/tokens`
- Components: Button/Card/Chip/ListHeader/Slider/Stepper/Progress/Circular/Level/Arc/Picker/CurvedText/Vignette/PageIndicator/Dialog/Stepper/Scaffold/TimeText/ScrollIndicator/SwipeDismiss etc.
- Used via `momoi.mod.qqpro.lib.wearuiadapter.WearUI` bridge (M3 accent → `WearColorScheme`)

## Module
`wearui` is an Android Library (`com.android.library`, `compileSdk 34`, `minSdk 21`).

Consumer: `implementation(project(":wearui"))` — `app` module.

## Constraints
- No `import androidx.* / kotlinx.coroutines / momoi / com.*` inside `rj.wearui` — verify with `rg -n "^import (androidx|kotlinx|momoi|com\\.)" wearui/src/main/java`
- ApkMixin helpers must be `public`; View needs `(Context)` ctor.

## Build
```
./gradlew :wearui:compileReleaseKotlin --offline
./gradlew :app:compileReleaseKotlin --offline
```

## Source of truth
Vendored: `~/qmce/app-new/src/main/java/androidx/wear/compose/{foundation/material/material3}` + `rj/qmce/lite/ui/screens`
