# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ── Glance app widget ────────────────────────────────────────────────────────
# The launcher instantiates the receiver by the class name recorded in the
# manifest, and Glance resolves generated layout resources by name at runtime
# for SizeMode.Responsive. R8 cannot see either path, so without these keeps it
# renames or strips them — and a broken widget does not crash, it just renders
# blank or disappears from the picker, which ships unnoticed.
-keep class com.pubpeb.compass.widget.** { *; }
-keep class androidx.glance.** { *; }
-keep class androidx.glance.appwidget.** { *; }
-keepclassmembers class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends android.appwidget.AppWidgetProvider

# Kept so the AR overlay's Camera2 interop reflection keeps resolving.
-keep class androidx.camera.camera2.interop.** { *; }

# ── Firebase Crashlytics ─────────────────────────────────────────────────────
# The android.os.Profiling* -dontwarn rules that used to live here are gone.
# They existed because Crashlytics references those classes and compileSdk 35
# could not resolve them; compileSdk is 36 now and resolves them directly. The
# old comment claimed 36 broke this build — it does not, and that claim cost a
# release cycle. Do not reinstate either the rules or the claim.

# Keep line numbers and source file so Crashlytics stack traces stay readable
# after obfuscation; the mapping file is uploaded by the Crashlytics plugin.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
