package com.example.ar.core.filament.hello.ar.presentation.arcore

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ar.core.filament.hello.ar.helpers.CameraPermissionHelper
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException

const val TAG = "ArCoreSession"

class ArCoreSession(
    private val activity: Activity,
    private val features: Set<Session.Feature> = setOf()
) : DefaultLifecycleObserver {
    /**
     * ArCore Session
     */
    var session: Session? = null
        private set

    /**
     * Creating a session may fail. In this case, session will remain null, and this function will be
     * called with an exception.
     */
    var exceptionCallback: ((Exception) -> Unit)? = null

    /**
     * Before `Session.resume()` is called, a session must be configured.
     * Call this from MainActivity with Config
     */
    var beforeSessionResume: ((Session) -> Unit)? = null

    var installRequested = false

    private fun tryCreateSession(): Session? {
        // The app must have been given the CAMERA permission. If we don't have it yet, request it.
        if (!CameraPermissionHelper.hasCameraPermission(activity)) {
            CameraPermissionHelper.requestCameraPermission(activity)
            return null
        }

        return try {
            // Request installation if necessary.
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)!!) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    // tryCreateSession will be called again, so we return null for now.
                    return null
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    // Left empty; nothing needs to be done.
                }
            }

            // Create a session if Google Play Services for AR is installed and up to date.
            Session(activity, features)
        } catch (e: Exception) {
            exceptionCallback?.invoke(e)
            null
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        val session = this.session ?: tryCreateSession() ?: return
        try {
            beforeSessionResume?.invoke(session)
            session.resume()
            this.session = session
        } catch (e: CameraNotAvailableException) {
            exceptionCallback?.invoke(e)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        session?.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // Explicitly close the ARCore session to release native resources.
        // Review the API reference for important considerations before calling close() in apps with
        // more complicated lifecycle requirements:
        // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
        session?.close()
        session = null
    }
}
