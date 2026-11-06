# GestióN GN — Aplicación Android

Aplicación móvil nativa de **GestióN GN (Gestión Documental)** para la Guardia Nacional.
Permite consultar, recibir, turnar, dar seguimiento y archivar documentación oficial, con
autenticación institucional, operación con persistencia local y notificaciones push y en
tiempo real.

Esta carpeta contiene el cliente **Android** del repositorio de GestióN GN. El cliente iOS
está en [`../ios`](../ios); el backend Flask/Socket.IO forma parte del mismo sistema.

> Documentación alineada con la **Memoria Técnica y Ficha del Sistema GestióN GN**
> (versión 1.0, 6 de noviembre de 2026 — Dirección General Científica, Innovación Tecnológica).

---

## 1. Descripción general

GestióN GN es un sistema institucional para la gestión, consulta, distribución y seguimiento
de documentación oficial. La app Android es nativa, está escrita en **Kotlin** con **vistas
XML** y consume la API REST y el canal **Socket.IO** del backend Flask.

Capacidades principales:

- Autenticación con credenciales institucionales y **JWT**, con acceso **biométrico** opcional.
- Panel de inicio con indicadores de documentos pendientes, en proceso, por vencer y vencidos.
- Gestión de documentos: alta, digitalización, escaneo, OCR, detalle, turnado, respuesta,
  cambio de estado, archivo e historial de trazabilidad.
- Expedientes y archivo documental.
- Recepción de documentos mediante **código QR**.
- Notificaciones **push (FCM)** y **en tiempo real (Socket.IO)** con contador de no leídas.
- Persistencia local con **Room** y sincronización en segundo plano con **WorkManager**.
- Configuración de la dirección del servidor desde la propia aplicación.

## 2. Módulos funcionales

| Módulo | Descripción |
|---|---|
| Inicio de sesión | Credenciales institucionales, JWT y biometría (huella/rostro). |
| Inicio | Resumen de documentos y movimientos recientes. |
| Documentos | Listado, búsqueda, filtros, detalle, adjuntos, OCR y PDF. |
| Escanear / Nuevo documento | Captura con cámara, galería o archivo; procesamiento y OCR. |
| Expedientes | Consulta y administración de expedientes. |
| Archivo | Documentos asignados, respondidos, vencidos y archivados. |
| Notificaciones | Avisos push y en tiempo real, contador y marcado de leídas. |
| Recepción QR | Lectura de QR y confirmación de recepción documental. |
| Perfil | Identidad institucional, firma digital QR y seguridad del dispositivo. |
| Configuración | Dirección del servidor, prueba de conexión y preferencias. |
| Seguridad | Sesión, permisos por rol, Keystore y autenticación biométrica. |

## 3. Arquitectura

La app sigue una separación por capas dentro de un esquema **MVVM** apoyado en
`Fragment` + `ViewModel` + repositorios:

- **Vistas (`views/`)**: una `MainActivity` con Navigation Component y un `Fragment` por
  pantalla (login, dashboard, documentos, detalle, expedientes, archivo, escáner, QR,
  notificaciones, perfil y configuración del servidor).
- **ViewModel (`viewmodels/`)**: estado de la UI y coordinación de casos de uso.
- **Servicios (`services/`)**: autenticación, repositorios de documentos y flujo, sesión,
  servidor, sincronización, biometría y notificaciones.
- **Datos (`data/`)**:
  - `local/`: base **Room** (`AppDatabase`, DAOs de documentos, movimientos y cola de syncing).
  - `remote/`: API REST con **Retrofit/OkHttp**.
  - `sync/`: `SyncWorker` con **WorkManager**.
  - `notif/`: utilidades de notificaciones.
- **Socket.IO (`socketio/`)**: conexión al namespace `/rt`, servicio en primer plano y
  recepción de cambios de red.
- **Escáner (`scanner/`)**: detección de documento, OCR, procesamiento de imagen y
  generación de PDF.

```text
app/src/main/java/com/gn/gestion/
├── adapters/     Adaptadores de listas (RecyclerView)
├── data/         Room, API remota, sincronización y utilidades de notificaciones
├── models/       Entidades y DTOs
├── scanner/      Captura, OCR, OpenCV, PDF
├── services/     Autenticación, repositorios, sesión, FCM, biometría
├── socketio/     Socket.IO /rt, servicio en primer plano, red
├── ui/           Fragmentos y widgets compartidos
├── util/         Diálogos, colores, selectores
├── viewmodels/   Estado de la UI
└── views/        Pantallas (login, dashboard, documentos, perfil, etc.)
```

## 4. Tecnologías y dependencias

| Área | Tecnología / versión |
|---|---|
| Lenguaje | Kotlin 2.2.0 |
| SDK | `compileSdk` 36, `targetSdk` 36, `minSdk` 26 (Android 8.0+) |
| Build | Android Gradle Plugin 9.0.0, Gradle Wrapper, JDK 17 |
| UI | AppCompat 1.7.0, Material 1.12.0, ConstraintLayout 2.2.1, RecyclerView 1.4.0, Navigation 2.9.0 |
| Ciclo de vida | Lifecycle 2.9.1, Activity 1.10.1, Fragment 1.8.9 |
| Persistencia local | Room 2.7.2 (SQLite) + KSP |
| Red | Retrofit 2.11.0, OkHttp 4.12.0, Gson |
| Cámara / escaneo | CameraX 1.4.2, ML Kit Text Recognition 16.0.1, ML Kit Document Scanner 16.0.0 |
| Imagen / QR | OpenCV 4.9.0, ZXing Core 3.5.3 |
| Segundo plano | WorkManager 2.10.0, corrutinas 1.10.2 |
| Seguridad | Security Crypto 1.1.0-alpha06, Biometric 1.1.0 |
| Tiempo real | `io.socket:socket.io-client:2.1.1` |
| Push | Firebase BOM 33.13.0, Firebase Cloud Messaging |
| Splash | AndroidX Core SplashScreen 1.0.1 |

La lista completa y centralizada está en `gradle/libs.versions.toml`.

## 5. Notificaciones

La app implementa **dos canales complementarios** con deduplicación por `notificacion_id`:

### 5.1 Push — Firebase Cloud Messaging

- `GestionMessagingService` recibe `onMessageReceived` y `onNewToken`.
- `FcmRegistrador` obtiene el token FCM y lo registra en el backend:

```http
POST   /api/fcm/token
DELETE /api/fcm/token
Authorization: Bearer <JWT>

{
  "token": "<token FCM>",
  "dispositivo": "<fabricante modelo>",
  "plataforma": "android"
}
```

- El token se envía al iniciar sesión y se da de baja **antes** de cerrar sesión.
- Requiere `app/google-services.json` y el plugin `com.google.gms.google-services`.
- En Android 13+ se solicita el permiso `POST_NOTIFICATIONS`.
- Las notificaciones se publican en el canal `gestion_documental` (importancia alta).
- Con la app en primer plano no se muestra el banner del sistema: se emite un aviso
  interno con acciones (Abrir / Ver datos).

### 5.2 Tiempo real — Socket.IO

- Namespace: **`/rt`**.
- Autenticación: `auth.token` y query `token` con el JWT de la sesión.
- El servicio `SocketIOBackground` mantiene la conexión como servicio en primer plano
  (`dataSync`), y `NetworkChangeReceiver` la restablece al recuperar conectividad.

| Evento | Dirección | Uso |
|---|---|---|
| `join` | app → servidor | Incorporarse a la sala privada del usuario autenticado. |
| `joined` | servidor → app | Confirmación con `room` y `no_leidas`. |
| `notification` | servidor → app | Nueva notificación persistida (`notificacion_id`, `documento_id`, `documento_uuid`, `tipo`, `mensaje`, `no_leidas`). |
| `contador_actualizado` | servidor → app | Actualización del contador de no leídas. |
| `error_sala` | servidor → app | Rechazo de acceso a la sala privada. |
| `leave` | app → servidor | Salida al cerrar sesión. |

### 5.3 Flujo de una notificación

1. El backend genera la notificación y la persiste.
2. La envía por Socket.IO a la sala `user:<id>` y, si corresponde, por FCM al dispositivo.
3. La app valida que exista sesión activa y que `notificacion_id` sea válido.
4. Deduplica: si Socket.IO y FCM entregan el mismo aviso, solo se publica una vez.
5. Publica la notificación (o el aviso interno si la app está visible) y actualiza el contador
   con el valor enviado por el servidor.
6. Al tocar el aviso se abre el documento o el centro de notificaciones.

## 6. Requisitos

- Android Studio con JDK 17 (o JDK 17 configurado para Gradle).
- Android SDK Platform 36 y Build Tools 36.0.0.
- Dispositivo o emulador con **Android 8.0 (API 26)** o superior.
- Conexión de red hacia el backend institucional.
- Servicios de Google Play habilitados en el dispositivo (para FCM y ML Kit).
- `app/google-services.json` del proyecto Firebase correspondiente.

## 7. Configuración del servidor

La URL del backend se resuelve en tiempo de compilación, en este orden de prioridad:

1. Propiedad de Gradle: `-Pgn.serverUrl="https://mi-servidor"`
2. Variable de entorno: `GN_SERVER_URL`
3. Archivo `gn.properties` en la raíz del proyecto:
   `gestion.serverUrl=https://mi-servidor`
4. Valor por defecto del proyecto:
   `https://thermos-provider-populace.ngrok-free.dev`

Ejemplo:

```bash
./gradlew assembleDebug -Pgn.serverUrl="https://thermos-provider-populace.ngrok-free.dev"
```

La URL también puede cambiarse en la pantalla **Servidor de conexión** de la app.
El tráfico HTTP sin TLS solo se permite en depuración o hacia hosts de intranet; en
producción debe utilizarse HTTPS.

## 8. Compilación y ejecución

```bash
# Depuración
./gradlew assembleDebug

# Instalar en un dispositivo conectado
./gradlew installDebug

# Pruebas unitarias
./gradlew testDebugUnitTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/`.

Para una compilación de release es necesario configurar un keystore propio y **no**
versionarlo en el repositorio.

## 9. Credenciales de prueba

La memoria técnica (§7) define una cuenta de demostración con rol de Administrador y la URL
del backend de pruebas. Por seguridad, este repositorio **no publica** el usuario ni la
contraseña; se solicitan al área de desarrollo.

> La cuenta de demostración debe cambiarse o deshabilitarse antes de cualquier despliegue
> productivo.

## 10. Seguridad

- Sesión basada en **JWT**; el token se almacena cifrado con **Android Keystore**
  (`EncryptedSharedPreferences`).
- Autenticación biométrica opcional mediante AndroidX Biometric.
- Cierre de sesión con limpieza de datos locales y baja del token FCM.
- Las notificaciones solo se procesan si existe una sesión activa.
- No se versionan keystores, `local.properties`, `gn.properties`, `.env` ni cuentas de
  servicio de Firebase.

## 11. Estructura del repositorio

```text
android/
├── app/
│   ├── google-services.json        Configuración Firebase (cliente)
│   └── src/main/
│       ├── java/com/gn/gestion/    Código Kotlin
│       ├── res/                    Layouts, drawables, valores
│       └── AndroidManifest.xml
├── gradle/
│   ├── libs.versions.toml          Catálogo de versiones
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── README.md
```

## 12. Estado de verificación

| Comprobación | Resultado |
|---|---|
| Revisión estática de registro FCM Android (`/api/fcm/token`) | Correcta |
| Revisión estática del cliente Socket.IO (`/rt`, `join`, `notification`) | Correcta |
| Autenticación contra el backend desplegado (cuenta de prueba) | Correcta (HTTP 200) |
| Conexión Socket.IO y `join` a la sala privada (cliente de prueba) | Correcta (`room: user:3`) |
| Validación de credenciales de Firebase (cuenta de servicio) | Correcta: FCM autenticó la petición y respondió `UNREGISTERED` al token almacenado, por lo que el token está obsoleto |
| Recepción de push en dispositivo físico | Pendiente: debe abrirse la app con sesión activa para renovar el token y repetir la prueba |
| Recepción de notificación en tiempo real | Requiere que el backend genere un evento para el usuario |

El detalle del protocolo y del flujo está en la sección 5.

## 13. Equipo y control documental

- **Proyecto:** GestióN GN (Gestión Documental)
- **Área responsable:** Dirección General Científica
- **Área de desarrollo:** Innovación Tecnológica
- **Equipo:** Espíndola Muñoz Christian Elhiu, Peach Reyes Aura Nicte-Ha, Durán Ponce
  Cristian Ignacio, López Canseco Rogelio
- **Documento base:** Memoria Técnica y Ficha del Sistema GestióN GN, versión 1.0
  (6 de noviembre de 2026)

---

*GestióN GN — Menos papel. Más control.*
