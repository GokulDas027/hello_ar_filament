package com.example.ar.core.filament.hello.ar.helpers

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Full Screen Config Helper
 */
object FullScreenHelper {
    /**
     * Toggle this activity to run full screen
     * */
    fun configFullScreen(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.windowInsetsController
                .also { windowInsetsController ->
                    windowInsetsController?.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    windowInsetsController?.hide(WindowInsets.Type.systemBars())
                }
        } else @Suppress("DEPRECATION") run {
            activity.window.decorView.systemUiVisibility =
                activity.window.decorView.systemUiVisibility
                    .or(View.SYSTEM_UI_FLAG_FULLSCREEN)       // hide status bar
                    .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)  // hide navigation bar
                    .or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) // hide stat/nav bar after interaction timeout
        }
    }
}
