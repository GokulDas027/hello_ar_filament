package com.example.ar.core.filament.hello.ar.presentation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.ar.core.filament.hello.ar.helpers.CameraPermissionHelper
import com.example.ar.core.filament.hello.ar.helpers.FullScreenHelper
import com.example.ar.core.filament.hello.databinding.ActivityArBinding

class ArActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // view binding
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Full Screen
        FullScreenHelper.configFullScreen(this@ArActivity)
    }

    override fun onResume() {
        super.onResume()

        // The app must have been given the CAMERA permission. If we don't have it yet, request it.
        if (!CameraPermissionHelper.hasCameraPermission(this@ArActivity)) {
            CameraPermissionHelper.requestCameraPermission(this@ArActivity)
            return
        }
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
