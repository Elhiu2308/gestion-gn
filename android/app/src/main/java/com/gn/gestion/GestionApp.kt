package com.gn.gestion

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Configuración global de la aplicación.
 *
 * La identidad institucional está diseñada únicamente en tema claro. Se fuerza
 * este modo incluso cuando el dispositivo tiene activado el modo oscuro para
 * mantener la misma apariencia en Android e iOS.
 */
class GestionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
