package com.example.ar.core.filament.hello.ar.presentation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.ar.core.filament.hello.ar.helpers.CameraPermissionHelper
import com.example.ar.core.filament.hello.ar.helpers.FullScreenHelper
import com.example.ar.core.filament.hello.ar.helpers.SnackbarHelper
import com.example.ar.core.filament.hello.ar.presentation.arcore.ArCoreSession
import com.example.ar.core.filament.hello.databinding.ActivityArBinding
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*

const val TAG = "ArActivity"

class ArActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArBinding
    private lateinit var arSceneSession: ArCoreSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // view binding
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Full Screen
        FullScreenHelper.configFullScreen(this@ArActivity)

        arSceneSession = ArCoreSession(this@ArActivity)

        arSceneSession.exceptionCallback =
            { exception ->
                val message =
                    when (exception) {
                        is UnavailableUserDeclinedInstallationException ->
                            "Please install Google Play Services for AR"
                        is UnavailableApkTooOldException -> "Please update ARCore"
                        is UnavailableSdkTooOldException -> "Please update this app"
                        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
                        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
                        else -> "Failed to create AR session: $exception"
                    }
                Log.e(TAG, "ARCore threw an exception", exception)
                // todo change
                SnackbarHelper().showError(this, message)
            }

        arSceneSession.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arSceneSession)
    }

    override fun onResume() {
        super.onResume()

        // The app must have been given the CAMERA permission. If we don't have it yet, request it.
        if (!CameraPermissionHelper.hasCameraPermission(this@ArActivity)) {
            CameraPermissionHelper.requestCameraPermission(this@ArActivity)
            return
        }
    }

    // Configure the session, using Lighting Estimation, and Depth mode.
    private fun configureSession(session: Session) {
        session.configure(
            session.config.apply {
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

                // Depth API is used if it is configured in Hello AR's settings.
                depthMode = Config.DepthMode.DISABLED // todo check depth rendering issue with latest filament
//                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
//                        Config.DepthMode.AUTOMATIC
//                    } else {
//                        Config.DepthMode.DISABLED
//                    }

                // Instant Placement is enabled(LOCAL_Y_UP) or not
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(this@ArActivity)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(
                this,
                "Camera permission is required to run this application",
                Toast.LENGTH_LONG
            ).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this@ArActivity)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this@ArActivity)
            }
            // Finish activity if camera permission is not provided
            finish()
        }
    }
}
