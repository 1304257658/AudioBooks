package com.example.audiobooks

import android.app.Application
import android.os.StrictMode

class AudioBooksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyDeath()
                    .build()
            )
        }
    }
}


