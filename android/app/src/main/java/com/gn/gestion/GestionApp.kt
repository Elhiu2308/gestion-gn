package com.gn.gestion

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class GestionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
