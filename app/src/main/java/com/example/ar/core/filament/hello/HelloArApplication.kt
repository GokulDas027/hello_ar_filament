package com.example.ar.core.filament.hello

import android.app.Application
import com.google.android.filament.utils.Utils

class HelloArApplication : Application() {
    companion object {
        lateinit var instance: HelloArApplication private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // init filament utils
        Utils.init()
    }
}