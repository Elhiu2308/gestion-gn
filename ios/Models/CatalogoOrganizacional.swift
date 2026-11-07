import Foundation

enum CatalogoOrganizacional {

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

    static func subdirecciones(area: String) -> [String] {
        let lista = subdireccionesPorArea[area] ?? []
        let actual = UserDefaults.standard.string(forKey: "subdireccion") ?? ""
        if lista.isEmpty, !actual.isEmpty {
            return [actual]
        }
        return lista
    }

    static func personas(subdireccion: String) -> [String] {
        let lista = personasPorSubdireccion[subdireccion] ?? []
        let actual = UserDefaults.standard.string(forKey: "nombre_completo") ?? ""
        if lista.isEmpty, !actual.isEmpty {
            return [actual]
        }
        return lista
    }

    static let clasificaciones: [String] = [
        "Público",
        "Interno",
        "Confidencial",
        "Reservado"
    ]

    static let nivelesUrgencia: [String] = [
        "Ordinario",
        "Urgente",
        "Extraurgente"
    ]

    static func urgenciaBackend(_ etiqueta: String) -> String {
        switch etiqueta {
        case "Urgente": return "URGENTE"
        case "Extraurgente": return "EXTRAURGENTE"
        default: return "ORDINARIO"
        }
    }

    static func clasificacionBackend(_ etiqueta: String) -> String {
        etiqueta.uppercased()
    }
}
