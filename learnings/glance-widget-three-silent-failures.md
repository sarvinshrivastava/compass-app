# The Glance widget had three independent ways to show "Can't load widget"

> **Rule:** never `exclude(group = "androidx.work")` from Glance, never put `<Space>` in a RemoteViews layout, and never ship an `<appwidget-provider>` without `android:initialLayout`.
> **Applies to:** `app/build.gradle.kts`, `app/src/main/java/com/pubpeb/compass/widget/CompassWidget.kt`, `app/src/main/java/com/pubpeb/compass/widget/CompassWidgetReceiver.kt`, `app/src/main/res/xml/compass_appwidget_info.xml`

Found 2026-09-12 while capturing a Play-listing screenshot of the widget on a
Pixel_9a emulator. All three shipped in the branch and none was visible from
unit tests, `lint`, or the app itself — the widget only fails when the
**launcher** touches it.

## 1. Excluding `androidx.work` crashes the app process

`app/build.gradle.kts` carried:

```kotlin
implementation(libs.androidx.glance.appwidget) { exclude(group = "androidx.work") }
```

with a comment claiming Glance 1.1.1's compiled code "contains zero references"
to WorkManager. It does reference it — in the constructor:

```
java.lang.ClassNotFoundException: Didn't find class "androidx.work.CoroutineWorker"
  at androidx.glance.appwidget.GlanceAppWidget.<init>(GlanceAppWidget.kt:57)
  at com.pubpeb.compass.widget.CompassWidget.<init>(CompassWidget.kt:71)
  at com.pubpeb.compass.widget.CompassWidgetReceiver.<init>(CompassWidgetReceiver.kt:15)
```

Glance runs its composition in a `SessionWorker`. Constructing the receiver
resolves the class, so **opening the widget picker killed the app process** —
`ActivityManager: Process com.pubpeb.compass has crashed too many times`.

The POM dependency is not vestigial. Verify a claim like that by instantiating
the receiver, not by grepping the sources.

## 2. `<Space>` is not on the RemoteViews allowlist

`previewLayout` is inflated by the launcher through RemoteViews, which accepts
only an allowlist of view classes:

```
InflateException: Binary XML file line #33 in layout/widget_preview:
  Class not allowed to be inflated android.widget.Space
```

Use a `layout_margin` instead. The same restriction rules out most custom and
AppCompat views; if a layout is reachable from a widget, keep it to
`LinearLayout`/`FrameLayout`/`RelativeLayout`/`GridLayout`, `TextView`,
`ImageView`, `Button`, `ProgressBar`, `Chronometer`, `ViewFlipper` and the other
documented RemoteViews types.

## 3. A missing `android:initialLayout` shows the error tile forever

`res/xml/compass_appwidget_info.xml` had `previewLayout` and `previewImage` but
no `initialLayout`. The host inflates `initialLayout` the moment the widget is
bound, before Glance has produced anything:

```
Resources$NotFoundException: Resource ID #0x0
  at android.appwidget.AppWidgetHostView.getDefaultView(AppWidgetHostView.java:856)
```

A view that failed to inflate is never replaced, so the tile reads "Can't load
widget" permanently. Glance ships the layout to point at:

```xml
android:initialLayout="@layout/glance_default_loading_layout"
```

## How to actually test a widget

Nothing above reproduces in the app. The check is three steps on a device or
emulator, in this order, because each failure masks the next:

1. Open the launcher's widget picker → the preview tile must render (catches 1 and 2).
2. Drag the tile onto the home screen → it must resolve past Glance's spinner (catches 3).
3. `adb logcat | grep -E "AppWidgetHostView|ClassNotFound|Glance"` — every one of
   these failures logs at **W**, not E, and never surfaces in the UI beyond the
   generic "Can't load widget" string.

One more trap while testing: after the crash loop in (1), Android force-stops
the package, and a force-stopped app receives no `APPWIDGET_UPDATE` broadcast.
The tile then sits on Glance's loading spinner even once the bug is fixed —
launch the app once by hand before concluding the fix did not work.
