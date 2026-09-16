package com.pubpeb.compass.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * This build's version, as a person would quote it in a bug report.
 *
 * Read from [PackageManager] rather than `BuildConfig`: this module does not
 * enable `buildFeatures.buildConfig`, so no `BuildConfig` class is generated at
 * all, and turning it on to reach two strings the platform already holds would
 * add a generated source set for nothing.
 *
 * Returns null rather than a placeholder if the lookup fails — an app that
 * cannot find its own package is not a state to paper over with "unknown", and
 * the caller simply omits the line.
 */
fun Context.appVersionLabel(): String? = runCatching {
    @Suppress("DEPRECATION")
    val info = packageManager.getPackageInfo(packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
    info.versionName?.let { "$it ($code)" }
}.getOrNull()

/**
 * Opens [url] in whatever the user browses with.
 *
 * `FLAG_ACTIVITY_NEW_TASK` and the `runCatching` are here for the same reasons
 * they are on [openAppSettings]: the call site is a composable that may hold a
 * non-Activity context, and a device with no browser at all should leave the
 * button inert rather than take the app down on the way to a policy page.
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
