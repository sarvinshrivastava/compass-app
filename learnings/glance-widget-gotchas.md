# Glance app-widget gotchas

## `actionStartActivity` exists in two packages, and only one is reified
- `androidx.glance.action.actionStartActivity` — has the reified
  `<T : Activity>()` overload plus `ComponentName` / `Class<T>` overloads.
  **This is the one you want for a launcher tile.**
- `androidx.glance.appwidget.action.actionStartActivity` — only `Intent`
  overloads. Importing this one makes `actionStartActivity<MainActivity>()`
  fail to resolve, with an error that points at the type argument rather than
  the import.
- Verified by `javap` on `glance-1.2.0-rc01.aar` / `glance-appwidget-1.2.0-rc01.aar`.

## `ColorProvider(Int)` is an `@ColorRes` id, not an ARGB value
- `ColorProvider(0xFFE63946.toInt())` compiles, then crashes the launcher's
  RemoteViews inflater with `Resources$NotFoundException` — the int is passed
  to `Resources.getColor()`.
- Correct call for a literal: `ColorProvider(androidx.compose.ui.graphics.Color(0xFFE63946))`.
- Raw ARGB ints are only valid on the `android.graphics.Paint` path (canvas
  drawing), which is a different code path entirely.

## `remember {}` is not supported inside `provideContent { }`
- Compute anything stateful in `provideGlance` *before* the call and pass it in.

## Theme the tile, don't hardcode the app palette
- `GlanceTheme.colors.surface` / `.onSurface` follow the system light/dark
  setting. Home screens are light at least as often as dark, so a tile painted
  with an app's night palette reads as a hole punched in a light wallpaper.
- `GlanceModifier.cornerRadius(dp)` is applied only on API 31+; below that
  Glance logs a warning and no-ops, so the tile is square there.

## Widget picker preview
- `android:previewLayout` (API 31+) is a *static XML layout* — the picker cannot
  run Glance, so the layout duplicates the composable by hand and has to be kept
  in step with it.
- `android:previewImage` is still needed for API 24–30.
- Use `?android:attr/colorBackground` / `?android:attr/textColorPrimary` in the
  preview layout so it themes with the picker.
- `android:paddingHorizontal` / `paddingVertical` are API 26+; on `minSdk 24`
  use `android:padding` or the four per-edge attributes.

## Sizing
- `targetCellWidth`/`targetCellHeight` (API 31+) set the picker's default
  placement; `minWidth`/`minHeight` are the pre-31 equivalent and must be set to
  match, or older devices get a different default tier.
- `SizeMode.Responsive` snaps `LocalSize.current` to one of the *declared*
  sizes, not the real one — declare only tiers whose content actually differs.
- Two cells is roughly 140dp minus launcher margins. An icon + wordmark row has
  to be budgeted against that or the launcher clips the text.

## A widget cannot hold a sensor open
- Any heading a compass widget shows is one reading taken in whatever pose the
  phone was in when the job ran (face-down on a desk, in a pocket), then shown
  as fact for up to the refresh interval. Route taken here: the tile shows no
  data at all and is a pure launcher. `updatePeriodMillis="0"` and no worker.
