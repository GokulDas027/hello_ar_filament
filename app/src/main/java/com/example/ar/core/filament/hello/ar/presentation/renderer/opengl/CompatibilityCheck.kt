    package com.example.ar.core.filament.hello.ar.presentation.renderer.opengl

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.ar.core.filament.hello.R
import com.example.ar.core.filament.hello.ar.presentation.renderer.utils.parse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


val minOpenGlVersion = Version(3, 0, 0, null, null)

/**
 * Check if the platform OpenGL version is supported
 * at least 3.0.0
 */
fun Context.checkIfOpenGlVersionSupported(minOpenGlVersion: Version): Boolean =
    versionComparator.compare(
        minOpenGlVersion,
        ContextCompat
            .getSystemService(this, ActivityManager::class.java)!!
            .deviceConfigurationInfo
            .glEsVersion
            .let { parserVersion.parse(it) }
    ) <= 0

/**
 * Show Alert Dialog on OpenGL not suppored
 */
suspend fun showOpenGlNotSupportedDialog(
    activity: Activity,
) = suspendCancellableCoroutine<Unit> { continuation ->
    val alertDialog = AlertDialog
        .Builder(activity)
        .setTitle(R.string.opengl_required_title)
        .setMessage(activity.getString(R.string.opengl_required_message, minOpenGlVersion.print()))
        .setPositiveButton(android.R.string.ok) {
            _, _ -> continuation.resume(Unit)
        }
        .setCancelable(false)
        .show()

    continuation.invokeOnCancellation { alertDialog.dismiss() }
}
