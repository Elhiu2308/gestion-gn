import SwiftUI

@MainActor
final class PerfilViewModel: ObservableObject {

    @Published private(set) var perfil: PerfilServicio?
    @Published private(set) var resumen: ResumenPanel?
    @Published private(set) var asignadosAMi: [Documento] = []
    @Published private(set) var asignadosPorMi: [Documento] = []

    @Published private(set) var qrTexto: String?
    @Published private(set) var estaCargando = false
    @Published var errorTexto: String?

    private let repositorio = RepositorioPerfil.shared

    func cargar() async {
        estaCargando = true
        qrTexto = nil
        defer { estaCargando = false }
        do {
            async let p = repositorio.me()
            async let r = repositorio.panelResumen()
            async let ami = repositorio.asignadosAMi()
            async let porMi = repositorio.asignadosPorMi()
            let perfilCargado = try await p
            perfil = perfilCargado
            resumen = try await r
            asignadosAMi = try await ami
            asignadosPorMi = try await porMi

            if perfilCargado.id > 0 {
                qrTexto = (try? await repositorio.qrUsuario(id: perfilCargado.id))?.texto
            } else {
                qrTexto = nil
            }

            if let perfil {
                let datos = UserDefaults.standard
                datos.set(perfil.usuario, forKey: "usuario")
                datos.set(perfil.nombre, forKey: "nombre")
                datos.set(perfil.nombreCompleto, forKey: "nombre_completo")
                datos.set(perfil.rol, forKey: "rol")
                datos.set(perfil.cargo, forKey: "cargo")
                datos.set(perfil.grado, forKey: "grado")
                datos.set(perfil.area, forKey: "area")
                datos.set(perfil.subdireccion, forKey: "subdireccion")
                datos.set(perfil.correo, forKey: "correo")
                datos.set(perfil.permisos.sorted().joined(separator: ","), forKey: "permisos")
            }
        } catch {
            errorTexto = "No se pudo cargar el perfil."
        }
    }

    func cambiarContrasena(actual: String, nueva: String) async throws {
        try await repositorio.cambiarContrasena(actual: actual, nueva: nueva)
    }
}
