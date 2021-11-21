package com.example.ar.core.filament.hello.ar

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.example.ar.core.filament.hello.databinding.ActivityArBinding

class ArActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // view binding
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Full Screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(android.R.id.content)!!.windowInsetsController!!
                .also { windowInsetsController ->
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    windowInsetsController.hide(WindowInsets.Type.systemBars())
                }
        } else @Suppress("DEPRECATION") run {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                .or(View.SYSTEM_UI_FLAG_FULLSCREEN)       // hide status bar
                .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)  // hide navigation bar
                .or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) // hide stat/nav bar after interaction timeout
        }


    }
}