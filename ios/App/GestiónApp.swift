
import SwiftUI
import UIKit

// MARK: - Delegado de la aplicación

/// Delegado de la aplicación para manejar el registro de APNs.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Entrega el token APNs a Firebase Messaging.
        GestorMensajesFirebase.shared.registrarDeviceToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[APNs] No se pudo registrar: \(error.localizedDescription)")
    }
}

// MARK: - Punto de entrada

/// Punto de entrada de GestióN (iOS).
@main
struct GestiónApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var vistaSesion = VistaSesion()

    var body: some Scene {
        WindowGroup("Gestión Documental") {
            RootView(vista: vistaSesion)
                // La identidad institucional se diseñó en tema claro.
                // Se ignora el modo oscuro del sistema para mantener la
                // misma apariencia homologada con Android.
                .preferredColorScheme(.light)
        }
    }
}

// MARK: - Control de sesión

/// Controla el ciclo de vida de la sesión del usuario.
@MainActor
final class VistaSesion: ObservableObject {

    @Published private(set) var activa =
        ServicioKeychain.tokenAcceso() != nil

    @Published private(set) var cerrandoSesion = false

    /// Conserva la tarea de registro para poder cancelarla
    /// y esperar a que termine antes de eliminar el token FCM.
    private var tareaRegistroFCM: Task<Void, Never>?

    /// Evita reanudar dos veces los servicios de una misma sesión.
    private var serviciosIniciados = false

    /// Reanuda Socket.IO y FCM después del primer frame de la aplicación.
    ///
    /// Antes se hacía desde `init`, lo que alargaba el arranque en frío porque
    /// Firebase y la conexión realtime competían con la construcción de la
    /// interfaz. Ahora la vista raíz lo invoca cuando ya está visible.
    func reanudarServicios() {

        guard activa, !cerrandoSesion, !serviciosIniciados else {
            return
        }

        serviciosIniciados = true

        GestorMensajesFirebase.shared.habilitarRegistroAutomatico()
        conectarSocket()
        programarRegistroFCM()
    }

    // MARK: - Inicio de sesión

    /// Se ejecuta después de iniciar sesión correctamente.
    func iniciar() {

        guard !activa, !cerrandoSesion else {
            return
        }

        // El JWT debe estar guardado antes de ejecutar este método.
        guard ServicioKeychain.tokenAcceso() != nil else {
            print("[Sesión] No se puede iniciar sin un access token.")
            return
        }

        activa = true
        serviciosIniciados = true

        // Si el usuario inicia sesión antes de que la tarea de arranque
        // configure Firebase, se configura aquí de forma idempotente.
        GestorMensajesFirebase.shared.configurar()

        // Habilita el registro automático de FCM para esta sesión.
        GestorMensajesFirebase.shared.habilitarRegistroAutomatico()

        conectarSocket()
        programarRegistroFCM()
    }

    // MARK: - Registro FCM

    /// Programa el registro del token FCM.
    private func programarRegistroFCM() {

        guard activa, !cerrandoSesion else {
            return
        }

        // Evita tareas de registro simultáneas.
        tareaRegistroFCM?.cancel()

        tareaRegistroFCM = Task { [weak self] in

            guard let self else {
                return
            }

            await registrarTokenFCM()
        }
    }

    /// Registra el token FCM utilizando la sesión autenticada.
    private func registrarTokenFCM() async {

        guard activa,
              !cerrandoSesion,
              !Task.isCancelled,
              ServicioKeychain.tokenAcceso() != nil else {
            return
        }

        do {

            try await GestorMensajesFirebase.shared
                .registrarTokenFCMEnBackend()

            if !Task.isCancelled && !cerrandoSesion {
                print("[FCM] Token registrado correctamente.")
            }

        } catch {

            if !Task.isCancelled {
                print(
                    "[FCM] No se pudo registrar el token: " +
                    error.localizedDescription
                )
            }
        }
    }

    // MARK: - Cierre de sesión

    /// Cierra la sesión e intenta desactivar el token FCM.
    func cerrar() {

        guard !cerrandoSesion else {
            return
        }

        cerrandoSesion = true

        // Impide que se programen nuevos registros FCM.
        tareaRegistroFCM?.cancel()

        Task { [weak self] in

            guard let self else {
                return
            }

            // Espera a que termine cualquier registro FCM pendiente.
            if let tarea = tareaRegistroFCM {
                await tarea.value
            }

            tareaRegistroFCM = nil

            // Primero se elimina el token FCM mientras el JWT
            // todavía está disponible en Keychain.
            do {

                try await GestorMensajesFirebase.shared
                    .eliminarDeviceToken()

                print("[FCM] Token eliminado del backend.")

            } catch {

                print(
                    "[FCM] No se pudo eliminar el token: " +
                    error.localizedDescription
                )
            }

            // Desconecta Socket.IO antes de eliminar el JWT.
            ServicioNotificaciones().desconectar()

            // Elimina los tokens locales y los datos del perfil.
            let sesionEliminada = ServicioKeychain.cerrarSesion()

            if !sesionEliminada {
                print(
                    "[Sesión] No se pudo completar la limpieza " +
                    "de todos los datos de sesión en Keychain."
                )
            }

            // Limpia cualquier navegación pendiente.
            CentroNavegacion.shared.cerrar()

            // La interfaz vuelve al login.
            activa = false
            cerrandoSesion = false
            serviciosIniciados = false
        }
    }

    // MARK: - Socket.IO

    /// Conecta Socket.IO usando el token de acceso guardado.
    private func conectarSocket() {

        guard activa,
              !cerrandoSesion,
              ServicioKeychain.tokenAcceso() != nil else {
            return
        }

        ServicioNotificaciones().conectarConTokenGuardado()
    }
}

// MARK: - Arranque diferido

/// Tareas que no deben bloquear el primer frame de la aplicación.
@MainActor
final class ArranqueAplicacion {

    static let shared = ArranqueAplicacion()

    private var preparado = false

    private init() {}

    func preparar(vista: VistaSesion) async {

        guard !preparado else {
            vista.reanudarServicios()
            return
        }

        preparado = true

        // Deja que SwiftUI pinte la pantalla inicial antes de configurar
        // Firebase, APNs y Socket.IO.
        await Task.yield()

        GestorMensajesFirebase.shared.configurar()
        _ = AvisosNotificaciones.shared
        vista.reanudarServicios()
    }
}

// MARK: - Vista raíz

private enum PestanaPrincipal: Hashable {
    case inicio
    case expedientes
    case archivo
    case recepcion
    case perfil
}

/// Raíz de la aplicación: muestra las pestañas según la sesión.
struct RootView: View {

    @ObservedObject var vista: VistaSesion
    @ObservedObject var navegador = CentroNavegacion.shared
    @StateObject private var avisos = AvisosNotificaciones.shared

    @State private var avisoParaDatos: AvisoNotificacion?
    @State private var pestanaSeleccionada: PestanaPrincipal = .inicio

    var body: some View {

        ZStack(alignment: .top) {

            contenido

            if let aviso = avisos.aviso {

                BannerAvisoNotificacion(
                    aviso: aviso,
                    onVerDatos: { verDatos(aviso) },
                    onAbrir: { abrir(aviso) },
                    onCerrar: { avisos.descartar(aviso) }
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .transition(
                    .move(edge: .top)
                    .combined(with: .opacity)
                )
                .zIndex(20)
            }
        }
        .animation(
            .spring(response: 0.35, dampingFraction: 0.86),
            value: avisos.aviso?.id
        )
        .sheet(item: $avisoParaDatos) { aviso in

            if let documentoId = aviso.documentoId {
                DatosDocumentoAvisoView(documentoId: documentoId)
            }
        }
        .task {
            await ArranqueAplicacion.shared.preparar(vista: vista)
        }
    }

    @ViewBuilder
    private var contenido: some View {

        if vista.activa {

            TabView(selection: $pestanaSeleccionada) {

                DashboardView(onCerrarSesion: vista.cerrar)
                    .tag(PestanaPrincipal.inicio)
                    .tabItem {
                        Label("Inicio", systemImage: "house.fill")
                    }

                NavigationStack {
                    ExpedientesView(alRegresar: { pestanaSeleccionada = .inicio })
                }
                .tag(PestanaPrincipal.expedientes)
                .tabItem {
                    Label("Expedientes", systemImage: "folder.fill")
                }

                NavigationStack {
                    ArchivoView(alRegresar: { pestanaSeleccionada = .inicio })
                }
                .tag(PestanaPrincipal.archivo)
                .tabItem {
                    Label("Archivo", systemImage: "archivebox.fill")
                }

                NavigationStack {
                    RecepcionQrView(alRegresar: { pestanaSeleccionada = .inicio })
                }
                .tag(PestanaPrincipal.recepcion)
                .tabItem {
                    Label("Recepción", systemImage: "qrcode.viewfinder")
                }

                PerfilView(alRegresar: { pestanaSeleccionada = .inicio })
                    .tag(PestanaPrincipal.perfil)
                    .tabItem {
                        Label("Perfil", systemImage: "person.crop.circle.fill")
                    }
            }
            .tint(EstiloInstitucional.verdePrincipal)
            .fullScreenCover(
                isPresented: Binding(
                    get: {
                        navegador.documentoPendiente != nil
                    },
                    set: {
                        if !$0 {
                            navegador.cerrar()
                        }
                    }
                )
            ) {

                if let id = navegador.documentoPendiente {
                    DetalleDocumentoView(documentoId: id)
                }
            }

        } else {

            NavigationStack {
                LoginView(onSesionIniciada: vista.iniciar)
            }
            .tint(EstiloInstitucional.verdePrincipal)
        }
    }

    private func abrir(_ aviso: AvisoNotificacion) {

        marcarLeida(aviso)

        if let documentoId = aviso.documentoId {
            CentroNavegacion.shared.abrir(
                documentoId: documentoId,
                notificacionId: aviso.notificacionId
            )
        }

        avisos.descartar(aviso)
    }

    private func verDatos(_ aviso: AvisoNotificacion) {

        marcarLeida(aviso)

        avisoParaDatos = aviso
        avisos.descartar(aviso)
    }

    private func marcarLeida(_ aviso: AvisoNotificacion) {

        guard aviso.notificacionId > 0 else {
            return
        }

        Task {
            try? await ServicioNotificaciones()
                .marcarLeida(id: aviso.notificacionId)
        }
    }
}
