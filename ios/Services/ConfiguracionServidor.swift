
import Foundation

/// Configuración local del servidor y de la sincronización.
enum ConfiguracionServidor {

    private static var diccionario: NSDictionary {
        let almacen = UserDefaults.standard

        if let objeto = almacen.object(forKey: "configuracion") as? NSDictionary {
            return objeto
        }

        return [
            "url": "https://thermos-provider-populace.ngrok-free.dev",
            "automatica": true
        ] as NSDictionary
    }

    /// URL base del servidor.
    static var url: String {
        let guardada = (
            diccionario["url"] as? String ?? ""
        ).trimmingCharacters(in: .whitespacesAndNewlines)

        if !guardada.isEmpty,
           let direccion = URL(string: guardada),
           let esquema = direccion.scheme,
           let host = direccion.host,
           !esquema.isEmpty,
           !host.isEmpty {

            return guardada.hasSuffix("/")
                ? String(guardada.dropLast())
                : guardada
        }

        return "https://thermos-provider-populace.ngrok-free.dev"
    }

    /// Construye una URL incluyendo el prefijo /api.
    static func urlCon(ruta: String) -> URL {
        let baseOriginal = url.trimmingCharacters(in: .whitespacesAndNewlines)

        let baseSinBarra = baseOriginal.hasSuffix("/")
            ? String(baseOriginal.dropLast())
            : baseOriginal

        let baseNormalizada: String

        if baseSinBarra.hasSuffix("/api") {
            baseNormalizada = baseSinBarra
        } else {
            baseNormalizada = "\(baseSinBarra)/api"
        }

        let rutaLimpia = ruta.trimmingCharacters(in: .whitespacesAndNewlines)

        let rutaNormalizada = rutaLimpia.hasPrefix("/")
            ? String(rutaLimpia.dropFirst())
            : rutaLimpia

        let completa = rutaNormalizada.isEmpty
            ? baseNormalizada
            : "\(baseNormalizada)/\(rutaNormalizada)"

        guard let resultado = URL(string: completa) else {
            fatalError("[ConfiguracionServidor] URL inválida: \(completa)")
        }

        return resultado
    }

    static var baseURL: URL {
        urlCon(ruta: "")
    }

    /// Indica si la sincronización automática está habilitada.
    static var automatica: Bool {
        diccionario["automatica"] as? Bool ?? true
    }

    /// Fecha de la última sincronización.
    static var ultimaSincronizacion: Date {
        let texto = diccionario["ultima"] as? String ?? ""

        return texto.isEmpty
            ? .distantPast
            : Date(timeIntervalSince1970: Double(texto) ?? 0)
    }

    /// Fecha de la próxima sincronización.
    static var proxima: Date {
        let texto = diccionario["proxima"] as? String ?? ""

        return texto.isEmpty
            ? .distantPast
            : Date(timeIntervalSince1970: Double(texto) ?? 0)
    }

    /// Guarda la URL y la configuración de sincronización.
    static func guardar(url: String, automatica: Bool) {
        let copia = diccionario.mutableCopy() as! NSMutableDictionary

        copia["url"] = url
        copia["automatica"] = automatica

        UserDefaults.standard.set(copia, forKey: "configuracion")
    }
}
