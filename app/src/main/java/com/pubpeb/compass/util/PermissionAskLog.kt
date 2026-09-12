package com.pubpeb.compass.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.pubpeb.compass.Constants

/**
 * Records that a runtime permission has been requested at least once.
 *
 * Android exposes no "permanently denied" state. It has to be inferred from
 * three facts: the permission is not granted, the system says no rationale is
 * warranted, and we have asked before. Only the third is ours to remember —
 * and it has to outlive the process. A `rememberSaveable` marker is lost on a
 * cold start, which puts a permanently denied user back on the first-run copy
 * behind a button the system will silently refuse. That is the dead end this
 * exists to close.
 *
 * Deliberately a plain [android.content.SharedPreferences] object rather than
 * ViewModel state: the AR overlay reads it and holds no ViewModel, and the
 * value is a single boolean written once in the lifetime of an install.
 */
object PermissionAskLog {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.Prefs.NAME, Context.MODE_PRIVATE)

    private fun key(permission: String) = Constants.Prefs.KEY_ASKED_PREFIX + permission

    /** True once [recordAsked] has been called for [permission] on this install. */
    fun hasAsked(context: Context, permission: String): Boolean =
        prefs(context).getBoolean(key(permission), false)

    /** Call immediately before launching the system request for [permission]. */
    fun recordAsked(context: Context, permission: String) {
        prefs(context).edit().putBoolean(key(permission), true).apply()
    }
}

/**
 * Opens this app's entry in system Settings, where a permanently denied
 * permission can still be granted.
 *
 * `FLAG_ACTIVITY_NEW_TASK` because the call site may be a composable holding a
 * non-Activity context. Wrapped because a small number of OEM builds ship no
 * activity for this action, and a crash on the way to fixing a permission is a
 * worse outcome than the button doing nothing.
 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
