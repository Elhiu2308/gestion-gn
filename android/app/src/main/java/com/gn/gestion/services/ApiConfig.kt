package com.gn.gestion.services;

import com.gn.gestion.BuildConfig

/**
 * Configuración del servidor web de Gestión de Documentos.
 *
 * - En el EMULADOR de Android, "10.0.2.2" apunta al localhost de la PC
 *   donde corre el servidor.
 * - En un EQUIPO FÍSICO conectado a la misma red, pon la IP de la computadora
 *   que ejecuta "web/server.py" (p.ej. "http://192.168.1.10:5000").
 */
object ApiConfig {

    val BASE_URL: String = BuildConfig.SERVER_URL

    /** Si es false, la app funciona solo con almacenamiento local. */
    const val SYNC_ENABLED: Boolean = true

    /** Activa logs de red en el cliente HTTP (desarrollo). */
    const val LOGS: Boolean = false

    // Las credenciales NO se guardan aquí: cada usuario inicia sesión
    // desde la pantalla de Login y el token se almacena cifrado (Keystore).
}