
import Foundation
import Security
import LocalAuthentication

enum ServicioKeychain {

    enum Error: LocalizedError {
        case accesoDenegado
        case sinSesion
        case errorAlGuardar

        var errorDescription: String? {
            switch self {
            case .accesoDenegado:
                return "No se autorizó el acceso biométrico."

            case .sinSesion:
                return "No existe una sesión o refresh token disponible."

            case .errorAlGuardar:
                return "No fue posible almacenar la información segura."
            }
        }
    }

    private static let service = "com.gn.gestion.ios"

    @discardableResult
    static func guardarSesion(_ sesion: TokenSesion) -> Bool {

        guard !sesion.accessToken.isEmpty else {
            print("[Keychain] No se puede guardar una sesión sin access token.")
            return false
        }

        guard guardarEnKeychain(
            token: sesion.accessToken,
            cuenta: "token_acceso"
        ) else {
            return false
        }

        if !sesion.refreshToken.isEmpty {

            guard guardarEnKeychain(
                token: sesion.refreshToken,
                cuenta: "token_refresh"
            ) else {
                return false
            }

        } else {

            guard eliminarTokenEnKeychain(
                cuenta: "token_refresh"
            ) else {
                return false
            }
        }

        let datos = UserDefaults.standard

        datos.set(sesion.usuario, forKey: "usuario")
        datos.set(sesion.nombre, forKey: "nombre")
        datos.set(sesion.nombreCompleto, forKey: "nombre_completo")
        datos.set(sesion.rol, forKey: "rol")
        datos.set(sesion.cargo, forKey: "cargo")
        datos.set(sesion.grado, forKey: "grado")
        datos.set(sesion.area, forKey: "area")
        datos.set(sesion.subdireccion, forKey: "subdireccion")
        datos.set(sesion.correo, forKey: "correo")
        datos.set(
            sesion.permisos.sorted().joined(separator: ","),
            forKey: "permisos"
        )

        return true
    }

    static func tokenAcceso() -> String? {
        leerTokenEnKeychain(cuenta: "token_acceso")
    }

    @discardableResult
    static func guardarAccessToken(_ token: String) -> Bool {
        guardarEnKeychain(
            token: token,
            cuenta: "token_acceso"
        )
    }

    @discardableResult
    static func guardarTokens(
        accessToken: String,
        refreshToken: String
    ) -> Bool {

        guard !accessToken.isEmpty else {
            print("[Keychain] No se recibió un access token válido.")
            return false
        }

        guard guardarEnKeychain(
            token: accessToken,
            cuenta: "token_acceso"
        ) else {
            return false
        }

        if !refreshToken.isEmpty {
            guard guardarEnKeychain(
                token: refreshToken,
                cuenta: "token_refresh"
            ) else {
                return false
            }
        }

        return true
    }

    @discardableResult
    static func cerrarSesion() -> Bool {

        let accessEliminado = eliminarTokenEnKeychain(
            cuenta: "token_acceso"
        )

        let refreshEliminado = eliminarTokenEnKeychain(
            cuenta: "token_refresh"
        )

        let datos = UserDefaults.standard

        for clave in [
            "usuario",
            "nombre",
            "nombre_completo",
            "rol",
            "cargo",
            "grado",
            "area",
            "subdireccion",
            "correo",
            "permisos"
        ] {
            datos.removeObject(forKey: clave)
        }

        return accessEliminado && refreshEliminado
    }

    static func tieneAccesoBiometrico() -> Bool {

        let datos = UserDefaults.standard

        guard datos.bool(forKey: "biometria_activada") else {
            return false
        }

        let tieneRefresh =
            !(leerTokenEnKeychain(cuenta: "token_refresh") ?? "").isEmpty

        let tieneCredenciales =
            !(leerTokenEnKeychain(cuenta: "biometria_user") ?? "").isEmpty &&
            !(leerTokenEnKeychain(cuenta: "biometria_pass") ?? "").isEmpty

        return tieneRefresh || tieneCredenciales
    }

    @discardableResult
    static func activarBiometria(
        usuario: String,
        contrasena: String,
        sesion: TokenSesion
    ) -> Bool {

        guard !usuario.isEmpty,
              !contrasena.isEmpty else {
            return false
        }

        let nombre = sesion.nombre.isEmpty
            ? (
                sesion.nombreCompleto.isEmpty
                    ? usuario
                    : sesion.nombreCompleto
            )
            : sesion.nombre

        if !sesion.refreshToken.isEmpty {

            guard guardarEnKeychain(
                token: sesion.refreshToken,
                cuenta: "token_refresh"
            ) else {
                return false
            }

        } else {

            guard eliminarTokenEnKeychain(
                cuenta: "token_refresh"
            ) else {
                return false
            }
        }

        guard guardarEnKeychain(
            token: contrasena,
            cuenta: "biometria_pass"
        ) else {
            return false
        }

        guard guardarEnKeychain(
            token: usuario,
            cuenta: "biometria_user"
        ) else {
            return false
        }

        let datos = UserDefaults.standard

        datos.set(nombre, forKey: "biometria_nombre_usuario")
        datos.set(usuario, forKey: "biometria_usuario")
        datos.set(true, forKey: "biometria_activada")

        return true
    }

    @discardableResult
    static func desactivarBiometria() -> Bool {

        let datos = UserDefaults.standard

        datos.set(false, forKey: "biometria_activada")

        datos.removeObject(forKey: "biometria_nombre_usuario")
        datos.removeObject(forKey: "biometria_usuario")

        let passEliminada = eliminarTokenEnKeychain(
            cuenta: "biometria_pass"
        )

        let userEliminado = eliminarTokenEnKeychain(
            cuenta: "biometria_user"
        )

        return passEliminada && userEliminado
    }

    static func nombreUsuarioBiometrico() -> String {

        let datos = UserDefaults.standard

        let nombre = datos.string(
            forKey: "biometria_nombre_usuario"
        ) ?? ""

        if !nombre.isEmpty {
            return nombre
        }

        let usuario = datos.string(
            forKey: "biometria_usuario"
        ) ?? ""

        return usuario.isEmpty ? "Usuario" : usuario
    }

    static func credencialesBiometricas() -> (
        usuario: String,
        contrasena: String
    )? {

        guard
            let pass = leerTokenEnKeychain(cuenta: "biometria_pass"),
            let user = leerTokenEnKeychain(cuenta: "biometria_user"),
            !user.isEmpty,
            !pass.isEmpty
        else {
            return nil
        }

        return (user, pass)
    }

    static func tipoBiometria() -> (
        disponible: Bool,
        nombre: String,
        icono: String
    ) {

        let contexto = LAContext()

        var error: NSError?

        let disponible = contexto.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            error: &error
        )

        switch contexto.biometryType {

        case .faceID:
            return (disponible, "Face ID", "faceid")

        case .touchID:
            return (disponible, "Touch ID", "touchid")

        case .opticID:
            return (disponible, "Optic ID", "opticid")

        default:
            return (
                disponible,
                "Biometría",
                "lock.shield.fill"
            )
        }
    }

    static func refreshTokenBiometrico() async throws -> String {

        let contexto = LAContext()

        let concedido = try await contexto.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Confirma tu identidad para acceder a GestióN"
        )

        guard concedido else {
            throw Error.accesoDenegado
        }

        guard
            let token = leerTokenEnKeychain(cuenta: "token_refresh"),
            !token.isEmpty
        else {
            throw Error.sinSesion
        }

        return token
    }

    static func tokenRefreshGuardado() -> String? {
        leerTokenEnKeychain(cuenta: "token_refresh")
    }

    static func permisos() -> Set<String> {

        let datos = UserDefaults.standard

        let texto = datos.string(forKey: "permisos") ?? ""

        return Set(
            texto.split(separator: ",").map(String.init)
        )
    }

    static func nombreUsuario() -> String {

        let datos = UserDefaults.standard

        let nombre = datos.string(forKey: "nombre") ?? ""

        if !nombre.isEmpty {
            return nombre
        }

        let usuario = datos.string(forKey: "usuario") ?? ""

        return usuario.isEmpty ? "Usuario" : usuario
    }

    static func areaUsuario() -> String {

        let datos = UserDefaults.standard

        let area = datos.string(forKey: "area") ?? ""

        return area.isEmpty ? "GN Sede Central" : area
    }

    static func nombreCompletoUsuario() -> String {

        let datos = UserDefaults.standard

        let completo = datos.string(
            forKey: "nombre_completo"
        ) ?? ""

        if !completo.isEmpty {
            return completo
        }

        return nombreUsuario()
    }

    static func subdireccionUsuario() -> String {

        let datos = UserDefaults.standard

        let subdireccion = datos.string(
            forKey: "subdireccion"
        ) ?? ""

        return subdireccion.isEmpty
            ? areaUsuario()
            : subdireccion
    }

    @discardableResult
    private static func guardarEnKeychain(
        token: String,
        cuenta: String
    ) -> Bool {

        guard
            !token.isEmpty,
            let data = token.data(using: .utf8)
        else {
            print("[Keychain] No se guardó un valor vacío: \(cuenta)")
            return false
        }

        let consulta: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: cuenta
        ]

        let atributos: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String:
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]

        let estadoActualizacion = SecItemUpdate(
            consulta as CFDictionary,
            atributos as CFDictionary
        )

        if estadoActualizacion == errSecSuccess {
            return true
        }

        guard estadoActualizacion == errSecItemNotFound else {

            print(
                "[Keychain] Error al actualizar \(cuenta): " +
                "\(estadoActualizacion)"
            )

            return false
        }

        var nuevaConsulta = consulta

        nuevaConsulta[kSecValueData as String] = data

        nuevaConsulta[kSecAttrAccessible as String] =
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly

        let estadoInsercion = SecItemAdd(
            nuevaConsulta as CFDictionary,
            nil
        )

        guard estadoInsercion == errSecSuccess else {

            print(
                "[Keychain] Error al guardar \(cuenta): " +
                "\(estadoInsercion)"
            )

            return false
        }

        return true
    }

    private static func leerTokenEnKeychain(
        cuenta: String
    ) -> String? {

        let consulta: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: cuenta,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var elemento: CFTypeRef?

        let estado = SecItemCopyMatching(
            consulta as CFDictionary,
            &elemento
        )

        guard
            estado == errSecSuccess,
            let data = elemento as? Data,
            let token = String(data: data, encoding: .utf8)
        else {
            return nil
        }

        return token
    }

    @discardableResult
    private static func eliminarTokenEnKeychain(
        cuenta: String
    ) -> Bool {

        let consulta: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: cuenta
        ]

        let estado = SecItemDelete(
            consulta as CFDictionary
        )

        guard
            estado == errSecSuccess ||
            estado == errSecItemNotFound
        else {

            print(
                "[Keychain] Error al eliminar \(cuenta): " +
                "\(estado)"
            )

            return false
        }

        return true
    }
}
