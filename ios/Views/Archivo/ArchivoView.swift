import SwiftUI

/// Categorías del archivo documental.
///
/// "Archivo" no es sólo el histórico: reúne los documentos que me asignaron,
/// los que respondí, los vencidos y los que ya están archivados.
enum CategoriaArchivo: String, CaseIterable, Identifiable {
    case todos
    case asignados
    case creados
    case respondidos
    case vencidos
    case porVencer
    case finalizados
    case archivados

    var id: String { rawValue }

    var titulo: String {
        switch self {
        case .todos: return "Todos"
        case .asignados: return "Asignados a mí"
        case .creados: return "Creados por mí"
        case .respondidos: return "Respondidos"
        case .vencidos: return "Vencidos"
        case .porVencer: return "Por vencer"
        case .finalizados: return "Finalizados"
        case .archivados: return "Archivados"
        }
    }

    var icono: String {
        switch self {
        case .todos: return "tray.full.fill"
        case .asignados: return "person.crop.circle.badge.checkmark"
        case .creados: return "square.and.pencil"
        case .respondidos: return "arrowshape.turn.up.left.fill"
        case .vencidos: return "exclamationmark.triangle.fill"
        case .porVencer: return "clock.badge.exclamationmark"
        case .finalizados: return "checkmark.seal.fill"
        case .archivados: return "archivebox.fill"
        }
    }

    func incluye(_ documento: DocumentoArchivo) -> Bool {
        let estado = documento.estado.uppercased()

        switch self {
        case .todos:
            return true
        case .asignados:
            return documento.asignadoAMi
        case .creados:
            return documento.creadoPorMi
        case .respondidos:
            return GrupoDocumentos.estaRespondido(estado)
        case .vencidos:
            return documento.estaVencido
        case .porVencer:
            guard let dias = documento.diasParaVencer else { return false }
            return (0...3).contains(dias)
        case .finalizados:
            return GrupoDocumentos.estaFinalizado(estado) || documento.archivado
        case .archivados:
            return documento.archivado
                || estado.contains("ARCHIVADO")
                || estado.contains("HISTORICO")
                || estado.contains("HISTÓRICO")
        }
    }
}

/// Pantalla de archivo documental (`GET /api/archivo` + documentos propios).
///
/// Muestra todos los documentos del usuario agrupados por categoría:
/// asignados, creados, respondidos, vencidos, por vencer, finalizados y
/// archivados. También conserva los filtros por año y estado.
struct ArchivoView: View {

    var alRegresar: (() -> Void)? = nil

    @StateObject private var vistaModelo = ArchivoViewModel()
    @State private var anioSeleccionado: Int?
    @State private var estadoSeleccionado: String?

    private var aniosDisponibles: [Int] {
        let anios = vistaModelo.documentos
            .filter { vistaModelo.categoria.incluye($0) }
            .map { Calendar.current.component(.year, from: $0.creadoEn) }
            .filter { $0 > 1970 }
        return Array(Set(anios)).sorted(by: >)
    }

    private var estadosDisponibles: [String] {
        Array(
            Set(
                vistaModelo.documentos
                    .filter { vistaModelo.categoria.incluye($0) }
                    .map { $0.estado.uppercased() }
                    .filter { !$0.isEmpty }
            )
        ).sorted()
    }

    private var documentosFiltrados: [DocumentoArchivo] {
        vistaModelo.documentos.filter { documento in
            let anioOk = anioSeleccionado == nil
                || Calendar.current.component(.year, from: documento.creadoEn) == anioSeleccionado
            let estadoOk = estadoSeleccionado == nil
                || documento.estado.uppercased() == estadoSeleccionado
            return vistaModelo.categoria.incluye(documento) && anioOk && estadoOk
        }
    }

    var body: some View {
        ZStack {
            EstiloInstitucional.fondo.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    encabezado
                    resumen
                    categorias
                    filtros
                    contador
                    listado

                    Text("GestióN · Gestión Documental")
                        .font(.system(size: 11))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
            }
        }
        .navigationTitle("GestióN Documental")
        .navigationBarTitleDisplayMode(.inline)
        .barraInstitucional()
        .toolbar {
            if let alRegresar {
                ToolbarItem(placement: .topBarLeading) {
                    BotonRegresarInicio(accion: alRegresar)
                }
            }
        }
        .task {
            await vistaModelo.cargar()
        }
        .refreshable {
            await vistaModelo.cargar()
        }
    }

    // MARK: - Encabezado

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "archivebox.fill")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }
            Text("Archivo")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
            Text("Documentos asignados, respondidos, vencidos y archivados.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
        }
    }

    // MARK: - Resumen

    private var resumen: some View {
        HStack(spacing: 10) {
            resumenItem(
                valor: vistaModelo.documentos.count,
                titulo: "Documentos",
                color: EstiloInstitucional.verdePrincipal
            )
            resumenItem(
                valor: vistaModelo.conteo(para: .asignados),
                titulo: "Asignados",
                color: EstiloInstitucional.dorado
            )
            resumenItem(
                valor: vistaModelo.conteo(para: .vencidos),
                titulo: "Vencidos",
                color: EstiloInstitucional.rojoUrgencia
            )
        }
    }

    private func resumenItem(valor: Int, titulo: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(valor)")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(color)
            Text(titulo)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
    }

    // MARK: - Categorías

    private var categorias: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(CategoriaArchivo.allCases) { categoria in
                    let activa = vistaModelo.categoria == categoria
                    Button {
                        vistaModelo.categoria = categoria
                        anioSeleccionado = nil
                        estadoSeleccionado = nil
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: categoria.icono)
                                .font(.system(size: 11, weight: .semibold))
                            Text(categoria.titulo)
                                .font(.system(size: 12, weight: .semibold))
                            let cuenta = vistaModelo.conteo(para: categoria)
                            if cuenta > 0 {
                                Text("\(cuenta)")
                                    .font(.system(size: 10, weight: .bold))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 1)
                                    .background(activa ? Color.white.opacity(0.25) : EstiloInstitucional.verdeSuave)
                                    .clipShape(Capsule())
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(activa ? EstiloInstitucional.verdePrincipal : Color.white)
                        .foregroundColor(activa ? .white : EstiloInstitucional.verdePrincipal)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(
                                EstiloInstitucional.verdePrincipal.opacity(activa ? 0 : 0.3),
                                lineWidth: 1
                            )
                        )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }

    // MARK: - Filtros

    private var filtros: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ChipFiltro(
                    titulo: "Año",
                    valor: anioSeleccionado.map(String.init),
                    opciones: aniosDisponibles.map(String.init)
                ) { valor in
                    anioSeleccionado = valor.flatMap(Int.init)
                }

                ChipFiltro(
                    titulo: "Estado",
                    valor: estadoSeleccionado,
                    opciones: estadosDisponibles
                ) { valor in
                    estadoSeleccionado = valor
                }

                if anioSeleccionado != nil || estadoSeleccionado != nil {
                    Button {
                        anioSeleccionado = nil
                        estadoSeleccionado = nil
                    } label: {
                        Label("Limpiar", systemImage: "xmark.circle.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(EstiloInstitucional.rojoUrgencia.opacity(0.1))
                            .foregroundColor(EstiloInstitucional.rojoUrgencia)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }

    private var contador: some View {
        Text("\(documentosFiltrados.count) documento(s)")
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(EstiloInstitucional.textoSecundario)
    }

    // MARK: - Listado

    private var listado: some View {
        Group {
            if vistaModelo.estaCargando {
                HStack {
                    Spacer()
                    ProgressView().padding(20)
                    Spacer()
                }
            } else if let error = vistaModelo.errorTexto, vistaModelo.documentos.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 42))
                        .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.6))
                    Text(error)
                        .font(.system(size: 14))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                        .multilineTextAlignment(.center)
                    Button("Reintentar") {
                        Task { await vistaModelo.cargar() }
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
            } else if documentosFiltrados.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "archivebox")
                        .font(.system(size: 42))
                        .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.6))
                    Text("No hay documentos en esta categoría.")
                        .font(.system(size: 14))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
            } else {
                ForEach(documentosFiltrados) { documento in
                    NavigationLink {
                        DetalleDocumentoView(documentoId: documento.id)
                    } label: {
                        FilaArchivo(documento: documento)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }
}

/// Tarjeta de un documento del archivo.
private struct FilaArchivo: View {

    let documento: DocumentoArchivo

    private var colorEstado: Color {
        let estado = documento.estado.uppercased()
        if documento.estaVencido { return EstiloInstitucional.rojoUrgencia }
        if estado.contains("ARCHIVADO") || estado.contains("HISTORICO") || estado.contains("HISTÓRICO") {
            return EstiloInstitucional.dorado
        }
        if GrupoDocumentos.estaFinalizado(estado) { return EstiloInstitucional.verdeClaro }
        return EstiloInstitucional.verdePrincipal
    }

    private var estadoMostrado: String {
        documento.estado.isEmpty ? "SIN ESTADO" : documento.estado.uppercased()
    }

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(colorEstado)
                .frame(width: 6)
                .padding(.vertical, 2)

            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text(documento.folio.isEmpty ? "Sin folio" : documento.folio)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.5))
                }

                Text(documento.asunto.isEmpty ? "Sin asunto" : documento.asunto)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                    .lineLimit(2)

                HStack(spacing: 6) {
                    Text(estadoMostrado)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(colorEstado)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(colorEstado.opacity(0.12))
                        .clipShape(Capsule())

                    if documento.asignadoAMi {
                        etiqueta("ASIGNADO", color: EstiloInstitucional.verdePrincipal)
                    }
                    if documento.creadoPorMi {
                        etiqueta("CREADO", color: EstiloInstitucional.dorado)
                    }

                    Spacer()

                    if documento.estaVencido {
                        Text("VENCIDO")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(EstiloInstitucional.rojoUrgencia)
                    } else if let dias = documento.diasParaVencer, (0...3).contains(dias) {
                        Text(dias == 0 ? "VENCE HOY" : "\(dias) día(s)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(red: 0.96, green: 0.65, blue: 0.14))
                    }
                }

                HStack(spacing: 8) {
                    if !documento.areaActual.isEmpty {
                        Text(documento.areaActual)
                            .lineLimit(1)
                    }
                    Spacer()
                    Text(documento.creadoEn == .distantPast
                         ? "—"
                         : documento.creadoEn.formatted(date: .numeric, time: .omitted))
                }
                .font(.system(size: 11))
                .foregroundColor(EstiloInstitucional.textoSecundario)
            }
        }
        .padding(14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 3, y: 2)
    }

    private func etiqueta(_ texto: String, color: Color) -> some View {
        Text(texto)
            .font(.system(size: 9, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(color.opacity(0.10))
            .clipShape(Capsule())
    }
}

/// Chip de filtro con menú desplegable.
private struct ChipFiltro: View {

    let titulo: String
    let valor: String?
    let opciones: [String]
    let onSeleccionar: (String?) -> Void

    var body: some View {
        Menu {
            if valor != nil {
                Button("Todos") { onSeleccionar(nil) }
            }
            ForEach(opciones, id: \.self) { opcion in
                Button(opcion) { onSeleccionar(opcion) }
            }
        } label: {
            HStack(spacing: 4) {
                Text(valor == nil ? titulo : "\(titulo) · \(valor!)")
                    .font(.system(size: 13, weight: .semibold))
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .bold))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(valor == nil ? Color.white : EstiloInstitucional.verdeSuave)
            .foregroundColor(EstiloInstitucional.verdePrincipal)
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(EstiloInstitucional.verdePrincipal.opacity(0.4), lineWidth: 1)
            )
        }
    }
}

// MARK: - ViewModel

@MainActor
final class ArchivoViewModel: ObservableObject {

    @Published private(set) var documentos: [DocumentoArchivo] = []
    @Published private(set) var estaCargando = false
    @Published var categoria: CategoriaArchivo = .todos
    @Published private(set) var errorTexto: String?

    private let servicioArchivo = ServicioArchivo()
    private let servicioDocumentos = ServicioDocumentos()

    func conteo(para categoria: CategoriaArchivo) -> Int {
        documentos.filter { categoria.incluye($0) }.count
    }

    func cargar() async {
        estaCargando = true
        defer { estaCargando = false }

        async let asignados = capturar {
            try await self.servicioDocumentos.listar(
                filtros: RepositorioDocumentos.Filtros(bandeja: "asignados_a_mi")
            )
        }
        async let creados = capturar {
            try await self.servicioDocumentos.listar(
                filtros: RepositorioDocumentos.Filtros(bandeja: "creados_por_mi")
            )
        }
        async let archivados = capturar {
            try await self.servicioArchivo.listar()
        }
        async let visibles = capturar {
            try await self.servicioDocumentos.listar()
        }

        let (listaAsignados, listaCreados, listaArchivados, listaVisibles) =
            await (asignados, creados, archivados, visibles)

        var mapa: [Int: DocumentoArchivo] = [:]

        // Documentos visibles para el usuario (incluye los de su área y los que
        // le turnaron). Se muestran en "Todos", pero sin las etiquetas de
        // asignado/creado.
        for documento in listaVisibles ?? [] {
            mapa[documento.id] = DocumentoArchivo(documento: documento)
        }

        for documento in listaAsignados ?? [] {
            var item = DocumentoArchivo(documento: documento, asignadoAMi: true)
            if let previo = mapa[documento.id] {
                item.creadoPorMi = previo.creadoPorMi
                item.archivado = previo.archivado
            }
            mapa[documento.id] = item
        }

        for documento in listaCreados ?? [] {
            if var previo = mapa[documento.id] {
                previo.creadoPorMi = true
                mapa[documento.id] = previo
            } else {
                mapa[documento.id] = DocumentoArchivo(documento: documento, creadoPorMi: true)
            }
        }

        for archivado in listaArchivados ?? [] {
            if var previo = mapa[archivado.id] {
                previo.archivado = true
                if previo.estado.isEmpty { previo.estado = archivado.estado }
                if previo.creadoEn == .distantPast { previo.creadoEn = archivado.creadoEn }
                if previo.areaActual.isEmpty { previo.areaActual = archivado.areaActual }
                mapa[archivado.id] = previo
            } else {
                mapa[archivado.id] = archivado
            }
        }

        documentos = mapa.values.sorted { $0.creadoEn > $1.creadoEn }

        if documentos.isEmpty,
           listaAsignados == nil,
           listaCreados == nil,
           listaArchivados == nil,
           listaVisibles == nil {
            errorTexto = "No se pudo consultar el archivo.\nRevisa tu conexión e intenta de nuevo."
        } else {
            errorTexto = nil
        }
    }

    /// Ejecuta una operación y devuelve `nil` si falla, para que un endpoint
    /// caído no impida mostrar el resto del archivo.
    private func capturar<T>(_ operacion: @escaping () async throws -> T) async -> T? {
        try? await operacion()
    }
}
