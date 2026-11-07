
import SwiftUI
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {

        GestorMensajesFirebase.shared.registrarDeviceToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[APNs] No se pudo registrar: \(error.localizedDescription)")
    }
}

@main
struct GestiónApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var vistaSesion = VistaSesion()

    var body: some Scene {
        WindowGroup("Gestión Documental") {
            RootView(vista: vistaSesion)

                .preferredColorScheme(.light)
        }
    }
}

@MainActor
final class VistaSesion: ObservableObject {

    @Published private(set) var activa =
        ServicioKeychain.tokenAcceso() != nil

    @Published private(set) var cerrandoSesion = false

    private var tareaRegistroFCM: Task<Void, Never>?

    private var serviciosIniciados = false

    func reanudarServicios() {

        guard activa, !cerrandoSesion, !serviciosIniciados else {
            return
        }

        serviciosIniciados = true

        GestorMensajesFirebase.shared.habilitarRegistroAutomatico()
        conectarSocket()
        programarRegistroFCM()
    }

    func iniciar() {

        guard !activa, !cerrandoSesion else {
            return
        }

        guard ServicioKeychain.tokenAcceso() != nil else {
            print("[Sesión] No se puede iniciar sin un access token.")
            return
        }

        activa = true
        serviciosIniciados = true

        GestorMensajesFirebase.shared.configurar()

        GestorMensajesFirebase.shared.habilitarRegistroAutomatico()

        conectarSocket()
        programarRegistroFCM()
    }

    private func programarRegistroFCM() {

        guard activa, !cerrandoSesion else {
            return
        }

        tareaRegistroFCM?.cancel()

        tareaRegistroFCM = Task { [weak self] in

            guard let self else {
                return
            }

            await registrarTokenFCM()
        }
    }

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

    func cerrar() {

        guard !cerrandoSesion else {
            return
        }

        cerrandoSesion = true

        tareaRegistroFCM?.cancel()

        Task { [weak self] in

            guard let self else {
                return
            }

            if let tarea = tareaRegistroFCM {
                await tarea.value
            }

            tareaRegistroFCM = nil

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

            ServicioNotificaciones().desconectar()

            let sesionEliminada = ServicioKeychain.cerrarSesion()

            if !sesionEliminada {
                print(
                    "[Sesión] No se pudo completar la limpieza " +
                    "de todos los datos de sesión en Keychain."
                )
            }

            CentroNavegacion.shared.cerrar()

            activa = false
            cerrandoSesion = false
            serviciosIniciados = false
        }
    }

    private func conectarSocket() {

        guard activa,
              !cerrandoSesion,
              ServicioKeychain.tokenAcceso() != nil else {
            return
        }

        ServicioNotificaciones().conectarConTokenGuardado()
    }
}

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

        await Task.yield()

        GestorMensajesFirebase.shared.configurar()
        _ = AvisosNotificaciones.shared
        vista.reanudarServicios()
    }
}

private enum PestanaPrincipal: Hashable {
    case inicio
    case expedientes
    case archivo
    case recepcion
    case perfil
}

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
