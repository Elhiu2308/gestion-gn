# GestióN GN — Gestión Documental

Repositorio de las aplicaciones móviles nativas de **GestióN GN (Gestión Documental)** para
la Guardia Nacional: cliente **Android** (Kotlin + XML) y cliente **iOS** (Swift + SwiftUI).

El sistema permite consultar, recibir, turnar, dar seguimiento y archivar documentación
oficial, con autenticación institucional, control de acceso por roles, persistencia local,
notificaciones push (FCM y APNs) y notificaciones en tiempo real mediante Socket.IO.

> Contenido alineado con la **Memoria Técnica y Ficha del Sistema GestióN GN**, versión 1.0
> (6 de noviembre de 2026 — Dirección General Científica, Innovación Tecnológica).

---

## Contenido del repositorio

```text
.
├── android/     Cliente Android (Kotlin, XML, Room, FCM, Socket.IO)
├── ios/         Cliente iOS (Swift, SwiftUI, Keychain, APNs/FCM, Socket.IO)
└── README.md
```

| Cliente | Documentación | Stack |
|---|---|---|
| Android | [`android/README.md`](android/README.md) | Kotlin 2.2, XML, Room, Retrofit, CameraX, ML Kit, FCM, Socket.IO 2.1.1 |
| iOS | [`ios/README.md`](ios/README.md) | Swift 5.9, SwiftUI, XcodeGen, Keychain/CryptoKit, APNs/FCM, Socket.IO 16 |

## Arquitectura general

Las dos aplicaciones son clientes nativos del mismo backend **Python/Flask**, que expone una
**API REST** autenticada con **JWT** y un canal **Socket.IO** para eventos en tiempo real.
La base de datos central es **SQLite**; Android añade persistencia local con **Room**.

```text
┌──────────────────┐        HTTPS / REST + JWT        ┌────────────────────┐
│  App Android     │ ───────────────────────────────► │  Backend Flask     │
│  Kotlin + XML    │ ◄─────────────────────────────── │  API REST          │
│  Room + FCM      │        Socket.IO  /rt             │  Socket.IO         │
└──────────────────┘                                   │  SQLite            │
┌──────────────────┐        HTTPS / REST + JWT        │  FCM + APNs        │
│  App iOS         │ ───────────────────────────────► │                    │
│  Swift + SwiftUI │ ◄─────────────────────────────── │                    │
│  Keychain + APNs │        Socket.IO  /rt             └────────────────────┘
└──────────────────┘
```

## Notificaciones

Ambos clientes implementan dos canales complementarios y deduplican por `notificacion_id`:

1. **Push**
   - Android: Firebase Cloud Messaging.
   - iOS: APNs como transporte del sistema, con FCM como capa de entrega.
   - Registro del token en `POST /api/fcm/token` (con `plataforma: android` o `ios`) y baja
     en `DELETE /api/fcm/token` al cerrar sesión.
2. **Tiempo real (Socket.IO)**
   - Namespace `/rt`, autenticación con el JWT de la sesión.
   - `join` → `joined` (sala privada `user:<id>` y contador de no leídas).
   - `notification` como evento oficial de avisos.
   - `leave` al cerrar sesión.

El backend es la fuente oficial del contador de no leídas. Los detalles de cada
implementación están en los README de [`android`](android/README.md#5-notificaciones) e
[`ios`](ios/README.md#5-notificaciones).

## Requisitos rápidos

### Android

- JDK 17 y Android SDK 36.
- Android 8.0 (API 26) o superior.
- `android/app/google-services.json`.

```bash
cd android
./gradlew assembleDebug
```

### iOS

- macOS con Xcode 15+ (recomendado 16), Swift 5.9+.
- iOS 17.0 o superior para la configuración actual del proyecto.
- `ios/GoogleService-Info.plist` y perfil de aprovisionamiento con Push Notifications.

```bash
cd ios
xcodegen
open GestiónIOS.xcodeproj
```

## Configuración del backend

Las dos apps apuntan por defecto al backend de demostración configurado en el proyecto:

```text
https://thermos-provider-populace.ngrok-free.dev
```

- Android permite sobreescribir la URL en tiempo de compilación
  (`-Pgn.serverUrl`, `GN_SERVER_URL` o `gn.properties`) y desde la pantalla de servidor.
- iOS permite cambiarla desde la pantalla **Configuración**.

En producción debe utilizarse un dominio con HTTPS y credenciales institucionales propias.

## Credenciales

Este repositorio **no publica** usuarios ni contraseñas. La cuenta de demostración definida
en la memoria técnica se solicita al área de desarrollo y debe cambiarse antes de un
despliegue productivo.

## Estado de verificación

| Comprobación | Android | iOS |
|---|---|---|
| Autenticación REST contra el backend desplegado | Verificada | Verificada |
| Conexión Socket.IO `/rt` y `join` a la sala privada | Verificada (cliente de prueba) | Verificada (cliente de prueba) |
| Revisión estática del registro de token FCM | Verificada | Verificada |
| Recepción de push en dispositivo físico | Pendiente: el token Android almacenado está obsoleto; requiere abrir la app con sesión activa | Requiere iPhone con perfil APNs |
| Recepción de notificación en tiempo real | Requiere evento generado por el backend | Requiere evento generado por el backend |

## Seguridad

- Autenticación JWT y almacenamiento seguro de la sesión (Android Keystore / iOS Keychain).
- Autenticación biométrica opcional (huella/rostro en Android; Face ID/Touch ID en iOS).
- Cierre de sesión con limpieza local y baja del token de notificaciones.
- No se versionan cuentas de servicio de Firebase, claves privadas de APNs, keystores,
  perfiles de aprovisionamiento, archivos `.env`, bases de datos ni artefactos de compilación.

## Equipo y control documental

- **Proyecto:** GestióN GN (Gestión Documental)
- **Área responsable:** Dirección General Científica
- **Área de desarrollo:** Innovación Tecnológica
- **Equipo:** Espíndola Muñoz Christian Elhiu, Peach Reyes Aura Nicte-Ha, Durán Ponce
  Cristian Ignacio, López Canseco Rogelio
- **Documento base:** Memoria Técnica y Ficha del Sistema GestióN GN, versión 1.0
  (6 de noviembre de 2026)

---

*GestióN GN — Menos papel. Más control.*
