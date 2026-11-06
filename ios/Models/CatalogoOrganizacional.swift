import Foundation

/// Estructura organizacional usada por el selector de turnado
/// ("1. Área → 2. Subdirección → 3. Persona").
///
/// La app Android toma esta estructura del backend. Mientras se expone el
/// endpoint correspondiente, aquí se mantiene un catálogo local con las áreas
/// institucionales y se enriquece con los datos del usuario en sesión.
enum CatalogoOrganizacional {

    /// Áreas institucionales disponibles para turnar un documento.
    static let areas: [String] = [
        "Innovación Tecnológica",
        "GN Carreteras",
        "Recursos Humanos",
        "Administración",
        "Operaciones",
        "Jurídico",
        "Comunicación Social",
        "Inspección Fiscal"
    ]

    /// Subdirecciones por área. Si un área no tiene subdirecciones declaradas
    /// se usa la subdirección del propio usuario como valor por defecto.
    private static let subdireccionesPorArea: [String: [String]] = [
        "Innovación Tecnológica": [
            "Subdirección de Desarrollo de Aplicaciones Móviles",
            "Subdirección de Infraestructura y Redes",
            "Subdirección de Sistemas Institucionales"
        ],
        "GN Carreteras": [
            "Subdirección de Operación Carretera",
            "Subdirección de Seguridad Vial"
        ],
        "Recursos Humanos": [
            "Subdirección de Personal",
            "Subdirección de Nómina y Prestaciones"
        ],
        "Administración": [
            "Subdirección de Recursos Materiales",
            "Subdirección de Finanzas"
        ],
        "Operaciones": [
            "Subdirección de Operaciones Regionales"
        ],
        "Jurídico": [
            "Subdirección de Asuntos Jurídicos"
        ],
        "Comunicación Social": [
            "Subdirección de Difusión"
        ],
        "Inspección Fiscal": [
            "Subdirección de Auditoría"
        ]
    ]

    /// Personas por subdirección (responsables a los que se puede turnar).
    private static let personasPorSubdireccion: [String: [String]] = [
        "Subdirección de Desarrollo de Aplicaciones Móviles": [
            "Christian Elhiu Espindola Muñoz",
            "Encargado de Desarrollo Móvil"
        ],
        "Subdirección de Infraestructura y Redes": [
            "Responsable de Infraestructura"
        ],
        "Subdirección de Sistemas Institucionales": [
            "Responsable de Sistemas"
        ],
        "Subdirección de Operación Carretera": [
            "Responsable de Operación"
        ],
        "Subdirección de Personal": [
            "Responsable de Personal"
        ]
    ]

    /// Subdirecciones disponibles para un área dada.
    static func subdirecciones(area: String) -> [String] {
        let lista = subdireccionesPorArea[area] ?? []
        let actual = UserDefaults.standard.string(forKey: "subdireccion") ?? ""
        if lista.isEmpty, !actual.isEmpty {
            return [actual]
        }
        return lista
    }

    /// Personas disponibles para una subdirección dada.
    static func personas(subdireccion: String) -> [String] {
        let lista = personasPorSubdireccion[subdireccion] ?? []
        let actual = UserDefaults.standard.string(forKey: "nombre_completo") ?? ""
        if lista.isEmpty, !actual.isEmpty {
            return [actual]
        }
        return lista
    }

    /// Opciones de clasificación de la información.
    static let clasificaciones: [String] = [
        "Público",
        "Interno",
        "Confidencial",
        "Reservado"
    ]

    /// Niveles de urgencia (coinciden con los del backend Android).
    static let nivelesUrgencia: [String] = [
        "Ordinario",
        "Urgente",
        "Extraurgente"
    ]

    /// Traduce el nivel de urgencia mostrado al valor que espera el backend.
    static func urgenciaBackend(_ etiqueta: String) -> String {
        switch etiqueta {
        case "Urgente": return "URGENTE"
        case "Extraurgente": return "EXTRAURGENTE"
        default: return "ORDINARIO"
        }
    }

    /// Traduce la clasificación mostrada al valor que espera el backend.
    static func clasificacionBackend(_ etiqueta: String) -> String {
        etiqueta.uppercased()
    }
}
