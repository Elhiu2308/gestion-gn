# GestióN GN — Aplicación iOS

Aplicación móvil nativa de **GestióN GN (Gestión Documental)** para la Guardia Nacional,
desarrollada en **Swift** con **SwiftUI**. Permite consultar, recibir, turnar, dar
seguimiento y archivar documentación oficial, con autenticación institucional, biometría y
notificaciones push (APNs/FCM) y en tiempo real (Socket.IO).

Esta carpeta contiene el cliente **iOS** del repositorio de GestióN GN. El cliente Android
está en [`../android`](../android); el backend Flask/Socket.IO forma parte del mismo sistema.

> Documentación alineada con la **Memoria Técnica y Ficha del Sistema GestióN GN**
> (versión 1.0, 6 de noviembre de 2026 — Dirección General Científica, Innovación Tecnológica).

---

## 1. Descripción general

GestióN GN es un sistema institucional para la gestión, consulta, distribución y seguimiento
de documentación oficial. La app iOS es nativa, usa **SwiftUI** con una arquitectura
**MVVM** y consume la API REST y el canal **Socket.IO** del backend Flask.

Capacidades principales:

- Inicio de sesión con credenciales institucionales y **JWT**, con **Face ID / Touch ID**.
- Panel de inicio con métricas y atención prioritaria.
- Documentos: listado, creación, detalle, turnado, respuesta, cambio de estado, archivo,
  anotaciones, versiones, PDF y OCR.
- Expedientes y archivo documental.
- Recepción de documentos mediante **código QR**.
- Notificaciones **push (APNs + FCM)** y **en tiempo real (Socket.IO)**, con contador de
  no leídas y navegación al documento relacionado.
- Perfil, configuración del servidor y ajustes de seguridad.
- Asistente de consulta con IA (servicio configurable).

## 2. Módulos funcionales

| Módulo | Descripción |
|---|---|
| Inicio de sesión | Credenciales institucionales, JWT y biometría Face ID / Touch ID. |
| Inicio (Dashboard) | Métricas, pendientes y actividad reciente. |
| Documentos | Listado, búsqueda, alta, detalle, adjuntos, PDF, OCR y flujo documental. |
| Expedientes | Creación, consulta y administración de expedientes. |
| Archivo | Documentos archivados o en histórico. |
| Notificaciones | Avisos push y en tiempo real, contador y marcado de leídas. |
| Recepción QR | Escaneo de códigos QR y confirmación de recepción. |
| Perfil | Identidad institucional, permisos y sincronización del perfil. |
| Configuración | Dirección del backend y sincronización periódica. |
| Seguridad | Keychain, biometría y cierre de sesión seguro. |

## 3. Arquitectura

Arquitectura **MVVM** por capas, con inyección de dependencias simple:

- **App (`App/`)**: punto de entrada (`@main`), `AppDelegate` para APNs, `Info.plist`,
  `GestionIOS.entitlements` (APNs), `LaunchScreen` y `DIContainer`.
- **Vistas (`Views/`)**: pantallas SwiftUI agrupadas por módulo.
- **ViewModels (`ViewModels/`)**: estado y lógica de presentación.
- **Repositorios (`Repositories/`)**: acceso tipado a la API REST.
- **Servicios (`Services/`)**: autenticación, documentos, expedientes, archivo, notificaciones,
  QR, biometría, Keychain, Socket.IO y FCM.
- **Modelos (`Models/`)**: entidades y DTOs.
- **Componentes (`Components/`)**: UI reutilizable y estilo institucional.
- **Utilidades (`Utilities/`)**: Keychain, navegación y soporte transversal.

```text
ios/
├── App/              Entrada, AppDelegate, Info.plist, DI, LaunchScreen
├── Models/           Entidades y DTOs
├── Services/         REST, notificaciones, FCM, Socket.IO, QR, biometría
├── Repositories/     Llamadas HTTP tipadas
├── ViewModels/       Estado de la UI
├── Components/       Componentes visuales reutilizables
├── Views/            Pantallas por módulo
├── Utilities/        Keychain, navegación y utilidades
├── Tests/            Pruebas unitarias
├── UITests/          Pruebas de interfaz
├── Assets.xcassets/  Recursos e iconos
├── GoogleService-Info.plist
└── project.yml       Especificación de XcodeGen
```

## 4. Tecnologías y dependencias

| Área | Tecnología / versión |
|---|---|
| Lenguaje | Swift 5.9 |
| Interfaz | SwiftUI + componentes nativos |
| Sistema mínimo | iOS 17.0 (`project.yml`) · la ficha técnica indica iOS 16 como mínimo |
| Proyecto | XcodeGen (`project.yml`) + Xcode 15/16 |
| Bundle | `com.gn.gestion.ios` |
| Tiempo real | [Socket.IO-Client-Swift](https://github.com/socketio/Socket.IO-Client-Swift) 16.0.0 |
| Push | FirebaseCore + FirebaseMessaging (APNs/FCM) |
| Biometría | LocalAuthentication (Face ID / Touch ID) |
| Escaneo / OCR | VisionKit, Vision, AVFoundation |
| Documentos | PDFKit, QuickLook |
| Seguridad | Keychain + CryptoKit (AES-GCM) |

## 5. Notificaciones

La app usa **APNs como transporte del sistema** y **FCM como capa de entrega**, además del
canal **Socket.IO** para eventos en tiempo real. Los avisos se deduplican por
`notificacion_id`.

### 5.1 Push — APNs + Firebase Cloud Messaging

- `AppDelegate` recibe el token APNs y lo entrega a Firebase Messaging.
- `GestorMensajesFirebase` obtiene el token FCM, solicita permisos
  (`.alert`, `.sound`, `.badge`) y registra el token en el backend:

```http
POST   /api/fcm/token
DELETE /api/fcm/token
Authorization: Bearer <JWT>

{
  "token": "<token FCM>",
  "plataforma": "ios",
  "dispositivo": "iPhone"
}
```

- El registro se habilita después de iniciar sesión y se bloquea al cerrar sesión.
- Requiere `GoogleService-Info.plist`, el entitlement `aps-environment`
  (`App/GestionIOS.entitlements`, declarado como `CODE_SIGN_ENTITLEMENTS`) y el modo de fondo
  `remote-notification` en `Info.plist`.
- Con la app abierta se muestra un aviso propio con acciones; al tocar una notificación del
  sistema se abre el documento relacionado.

### 5.2 Tiempo real — Socket.IO

- Namespace: **`/rt`**.
- Autenticación: JWT en los parámetros de conexión (`token`).
- Eventos:

| Evento | Dirección | Uso |
|---|---|---|
| `join` | app → servidor | Incorporarse a la sala privada del usuario autenticado. |
| `joined` | servidor → app | Confirmación con `room` y `no_leidas`. |
| `notification` | servidor → app | Nueva notificación persistida (`notificacion_id`, `documento_id`, `documento_uuid`, `tipo`, `mensaje`, `no_leidas`, `ts`). |
| `error_sala` | servidor → app | Rechazo de acceso a la sala privada. |
| `leave` | app → servidor | Salida al cerrar sesión. |

El contador de no leídas se toma de `joined` (`no_leidas`) y del campo `no_leidas` de cada
evento `notification`. El cliente iOS no consume el evento `contador_actualizado` del
backend; el contador se refresca al recibir un nuevo aviso o al reconectar.

### 5.3 Flujo de una notificación

1. El backend genera la notificación y la persiste.
2. La envía por Socket.IO a la sala `user:<id>` y por FCM/APNs al dispositivo.
3. La app valida la sesión y el `notificacion_id`.
4. Deduplica los avisos recibidos por ambos canales.
5. Actualiza el contador con el valor enviado por el servidor y muestra el aviso.
6. Al tocar el aviso se abre el documento o el centro de notificaciones.

## 6. Requisitos

- macOS con **Xcode 15 o superior** (recomendado Xcode 16).
- **XcodeGen** para regenerar el proyecto (opcional si se usa el `.xcodeproj` incluido).
- Swift 5.9+.
- Dispositivo o simulador con **iOS 17.0** o superior para esta configuración; la ficha
  técnica establece iOS 16 como mínimo funcional.
- Cuenta de desarrollador Apple, App ID con la capacidad **Push Notifications** habilitada y
  perfil de aprovisionamiento que incluya `aps-environment` para probar APNs en dispositivo
  físico.
- `GoogleService-Info.plist` del proyecto Firebase correspondiente.
- Conexión de red hacia el backend institucional.

## 7. Configuración del servidor

La URL se guarda en `UserDefaults` y puede modificarse desde la pantalla **Configuración**.
Valor por defecto (`Services/ConfiguracionServidor.swift`):

```text
https://thermos-provider-populace.ngrok-free.dev
```

Las peticiones REST se construyen con el prefijo `/api`. Para producción debe utilizarse
siempre HTTPS.

## 8. Compilación y ejecución

### Opción A — XcodeGen (recomendada)

```bash
brew install xcodegen
xcodegen
open GestiónIOS.xcodeproj
```

### Opción B — Proyecto incluido

Abrir `GestiónIOS.xcodeproj` en Xcode, seleccionar un dispositivo o simulador y compilar
con **⌘B**.

### Línea de comandos

```bash
xcodebuild -project GestiónIOS.xcodeproj -scheme GestiónIOS -configuration Debug build

# Pruebas
xcodebuild test -project GestiónIOS.xcodeproj -scheme GestiónIOS
```

## 9. Credenciales de prueba

La memoria técnica (§7) define una cuenta de demostración con rol de Administrador y la URL
del backend de pruebas. Por seguridad, este repositorio **no publica** el usuario ni la
contraseña; se solicitan al área de desarrollo.

> La cuenta de demostración debe cambiarse o deshabilitarse antes de cualquier despliegue
> productivo.

## 10. Seguridad

- Sesión basada en **JWT** almacenada en el **Keychain**.
- Refresh token biométrico cifrado con **CryptoKit (AES-GCM)** y protegido por Face ID /
  Touch ID.
- Cierre de sesión con baja del token FCM y limpieza de datos locales.
- Las notificaciones solo se procesan con una sesión activa.
- No se versionan claves privadas de APNs (`.p8`), perfiles de aprovisionamiento,
  `firebase-service-account.json` ni archivos `.env`.

## 11. Estructura del repositorio

```text
ios/
├── App/                        Entrada, DI, Info.plist, entitlements, LaunchScreen
├── Models/                     Entidades y DTOs
├── Services/                   REST, FCM, Socket.IO, QR, biometría
├── Repositories/               Acceso HTTP tipado
├── ViewModels/                 Estado de la UI
├── Components/                 UI reutilizable
├── Views/                      Pantallas por módulo
├── Utilities/                  Keychain y navegación
├── Tests/                      Pruebas unitarias
├── UITests/                    Pruebas de interfaz
├── Assets.xcassets/            Iconos y recursos
├── GoogleService-Info.plist    Configuración Firebase (cliente)
├── project.yml                 Especificación de XcodeGen
└── README.md
```

## 12. Estado de verificación

| Comprobación | Resultado |
|---|---|
| Revisión estática del registro FCM iOS (`/api/fcm/token`, `plataforma: ios`) | Correcta |
| Revisión estática del cliente Socket.IO (`/rt`, `join`, `notification`) | Correcta |
| Configuración APNs/FCM (`Info.plist`, `aps-environment` en `GestionIOS.entitlements` y `GoogleService-Info.plist`) | Presente |
| Autenticación contra el backend desplegado (cuenta de prueba) | Correcta (HTTP 200) |
| Conexión Socket.IO y `join` a la sala privada (cliente de prueba) | Correcta (`room: user:3`) |
| Recepción de push en dispositivo físico | Requiere iPhone con la app instalada, sesión iniciada y perfil APNs válido |
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
