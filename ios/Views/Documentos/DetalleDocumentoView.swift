import SwiftUI
import UIKit

struct DetalleDocumentoView: View {

    let documentoId: Int

    @StateObject private var modelo = DetalleDocumentoViewModel()
    @Environment(\.dismiss) private var dismiss

    @State private var pestaña: Pestaña = .datos
    @State private var mostrandoEditar = false
    @State private var mostrandoTurnar = false
    @State private var mostrandoCompartir = false
    @State private var mostrandoResponder = false
    @State private var mostrandoConsolidar = false
    @State private var mostrandoRevisarValidar = false
    @State private var revisarAccion = false
    @State private var mostrandoAnotar = false
    @State private var mostrandoQR = false
    @State private var mostrandoConfirmacionEliminar = false
    @State private var mostrandoArchivar = false
    @State private var mostrandoVistaPDF = false

    enum Pestaña: String, CaseIterable, Identifiable {
        case datos = "Datos"
        case pdf = "PDF"
        case ocr = "OCR"
        case tramite = "Trámite"
        case anotaciones = "Anotaciones"
        case historial = "Historial"
        var id: String { rawValue }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()
                if modelo.estaCargando && modelo.documento == nil {
                    ProgressView().scaleEffect(1.2)
                } else if let doc = modelo.documento {
                    VStack(spacing: 0) {
                        cabecera(doc: doc)
                        pestañas
                        ScrollView(showsIndicators: false) {
                            contenido(doc: doc)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                        }
                    }
                } else {
                    Text("No se pudo cargar el documento.")
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                }
            }
            .navigationTitle("GestióN Documental")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    if let doc = modelo.documento {
                        Menu {
                            if !doc.estado.esTerminal {
                                if ServicioKeychain.permisos().contains("DOCUMENTO_EDITAR") {
                                    Button("Editar") { mostrandoEditar = true }
                                }
                                if ServicioKeychain.permisos().contains("DOCUMENTO_SUBIR_PDF") {
                                    Button("Subir PDF") { subirPDF() }
                                }
                            }
                            Button("Ver QR") { mostrandoQR = true }
                            if ServicioKeychain.permisos().contains("DOCUMENTO_ELIMINAR") {
                                Button("Eliminar", role: .destructive) {
                                    mostrandoConfirmacionEliminar = true
                                }
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                    }
                }
            }
            .task { await modelo.cargar(id: documentoId) }
            .refreshable { await modelo.cargar(id: documentoId) }
            .sheet(isPresented: $mostrandoEditar) {
                if let documento = modelo.documento {
                    CrearDocumentoView(origen: .ninguno, documento: documento) { _ in
                        Task { await modelo.cargar(id: documentoId) }
                    }
                }
            }
            .sheet(isPresented: $mostrandoTurnar) {
                TurnarSheet(modelo: modelo) { Task { await modelo.cargar(id: documentoId) } }
            }
            .sheet(isPresented: $mostrandoCompartir) {
                CompartirSheet(modelo: modelo) { Task { await modelo.cargar(id: documentoId) } }
            }
            .sheet(isPresented: $mostrandoResponder) {
                ResponderSheet(modelo: modelo) { Task { await modelo.cargar(id: documentoId) } }
            }
            .sheet(isPresented: $mostrandoConsolidar) {
                ConsolidarSheet(modelo: modelo) { Task { await modelo.cargar(id: documentoId) } }
            }
            .sheet(isPresented: $mostrandoRevisarValidar) {
                DecisionSheet(modelo: modelo, esRevisar: revisarAccion) {
                    Task { await modelo.cargar(id: documentoId) }
                }
            }
            .sheet(isPresented: $mostrandoAnotar) {
                AnotarSheet(modelo: modelo) { Task { await modelo.cargar(id: documentoId) } }
            }
            .sheet(isPresented: $mostrandoQR) {
                QRSimpleSheet(texto: modelo.qrTexto, matriz: modelo.qrMatriz)
            }
            .sheet(isPresented: $mostrandoArchivar) {
                ArchivarSheet(modelo: modelo) { Task { await modelo.cargar(id: documentoId) } }
            }
            .confirmationDialog(
                "Eliminar documento",
                isPresented: $mostrandoConfirmacionEliminar,
                titleVisibility: .visible
            ) {
                Button("Eliminar documento", role: .destructive) {
                    eliminar()
                }
                Button("Cancelar", role: .cancel) {}
            } message: {
                Text("Esta acción eliminará el documento de forma permanente. ¿Deseas continuar?")
            }
            .alert(modelo.errorTexto ?? "", isPresented: Binding(
                get: { modelo.errorTexto != nil },
                set: { if !$0 { modelo.errorTexto = nil } }
            )) {
                Button("Entendido", role: .cancel) {}
            }
        }
    }

    private func cabecera(doc: Documento) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text("\(doc.tipo.etiqueta) · recibido \(fechaHora(doc.fechaRecepcion))")
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            Text(doc.asunto.isEmpty ? "—" : doc.asunto)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
                .lineLimit(2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    BadgeEstado(estado: doc.estado)
                    BadgeUrgencia(urgencia: doc.urgencia)
                    if !doc.vencimiento.isEmpty { BadgeVencimiento(texto: doc.vencimiento) }
                    if doc.tieneArchivo {
                        Text("PDF v\(doc.pdfVersion)")
                            .font(.system(size: 11, weight: .semibold))
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(EstiloInstitucional.verdeSuave)
                            .foregroundColor(EstiloInstitucional.verdePrincipal)
                            .clipShape(Capsule())
                    }
                    Text(doc.clasificacion.etiqueta)
                        .font(.system(size: 11, weight: .semibold))
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(EstiloInstitucional.fondo)
                        .clipShape(Capsule())
                    if doc.compartido {
                        Text("Compartido A/B")
                            .font(.system(size: 11, weight: .semibold))
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(EstiloInstitucional.fondo)
                            .clipShape(Capsule())
                    }
                    if !doc.areaActual.isEmpty {
                        Text("📍 \(doc.areaActual)")
                            .font(.system(size: 11, weight: .semibold))
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(EstiloInstitucional.fondo)
                            .clipShape(Capsule())
                    }
                }
            }

            if !doc.estado.esTerminal {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        accionesRapidas(doc: doc)
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white)
    }

    @ViewBuilder
    private func accionesRapidas(doc: Documento) -> some View {
        let permisos = ServicioKeychain.permisos()
        if permisos.contains("DOCUMENTO_CREAR") && doc.estado == .recibido {
            BotonAccion(titulo: "Recibir", icono: "tray.and.arrow.down.fill") { recibir() }
        }
        if permisos.contains("DOCUMENTO_ASIGNAR") && doc.estado.estaVivoEnFlujo {
            BotonAccion(titulo: "Turnar", icono: "arrow.turn.down.right") { mostrandoTurnar = true }
        }
        if permisos.contains("DOCUMENTO_COMPARTIR") && [
            EstadoDocumento.pendienteTurno, .turnado, .enDistribucion,
            .pendienteAsignacion, .designado
        ].contains(doc.estado) {
            BotonAccion(titulo: "Compartir", icono: "arrow.triangle.branch") { mostrandoCompartir = true }
        }
        if permisos.contains("DOCUMENTO_ASIGNAR") && [
            EstadoDocumento.turnado, .enDistribucion, .asignado, .designado
        ].contains(doc.estado) && !doc.compartido {
            BotonAccion(titulo: "Atender", icono: "play.fill") { atender() }
        }
        if permisos.contains("DOCUMENTO_RESPONDER") && doc.estado == .enAtencion {
            BotonAccion(titulo: "Acuse", icono: "checkmark.seal.fill") { acusar() }
        }
        if permisos.contains("DOCUMENTO_RESPONDER") && [
            EstadoDocumento.enAtencion, .enDistribucion, .atencionParcial,
            .respuestasCompletas, .respondido, .consolidando, .consolidado
        ].contains(doc.estado) {
            BotonAccion(titulo: "Responder", icono: "envelope.fill") { mostrandoResponder = true }
        }
        if permisos.contains("DOCUMENTO_CONSOLIDAR") && [
            EstadoDocumento.consolidando, .respuestasCompletas
        ].contains(doc.estado) {
            BotonAccion(titulo: "Consolidar", icono: "square.stack.3d.up.fill") { mostrandoConsolidar = true }
        }
        if permisos.contains("DOCUMENTO_REVISAR") && [
            EstadoDocumento.respuestasCompletas, .consolidado, .respondido,
            .consolidando, .enRevision
        ].contains(doc.estado) {
            BotonAccion(titulo: "Revisar", icono: "magnifyingglass") {
                revisarAccion = true; mostrandoRevisarValidar = true
            }
        }
        if permisos.contains("DOCUMENTO_VALIDAR") && [
            EstadoDocumento.enRevision, .enValidacion, .respondido,
            .consolidando, .consolidado
        ].contains(doc.estado) {
            BotonAccion(titulo: "Validar", icono: "checkmark.circle.fill") {
                revisarAccion = false; mostrandoRevisarValidar = true
            }
        }
        if permisos.contains("DOCUMENTO_ARCHIVAR") && [
            EstadoDocumento.validado, .enValidacion
        ].contains(doc.estado) {
            BotonAccion(titulo: "Archivar", icono: "archivebox.fill") { mostrandoArchivar = true }
        }
        if permisos.contains("DOCUMENTO_ARCHIVAR") && doc.estado == .archivado {
            BotonAccion(titulo: "Histórico", icono: "clock.arrow.circlepath") { historico() }
        }
        if permisos.contains("DOCUMENTO_ANOTAR") && !doc.estado.esTerminal {
            BotonAccion(titulo: "Anotar", icono: "note.text") { mostrandoAnotar = true }
        }
    }

    private var pestañas: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(Pestaña.allCases) { tab in
                    Button {
                        pestaña = tab
                    } label: {
                        Text(tab.rawValue)
                            .font(.system(size: 13, weight: pestaña == tab ? .bold : .regular))
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(pestaña == tab ? EstiloInstitucional.verdePrincipal : Color.white)
                            .foregroundColor(pestaña == tab ? .white : EstiloInstitucional.textoSecundario)
                            .clipShape(Capsule())
                    }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .background(EstiloInstitucional.fondo)
    }

    @ViewBuilder
    private func contenido(doc: Documento) -> some View {
        switch pestaña {
        case .datos: datosTab(doc: doc)
        case .pdf: pdfTab(doc: doc)
        case .ocr: ocrTab(doc: doc)
        case .tramite: tramiteTab(doc: doc)
        case .anotaciones: anotacionesTab(doc: doc)
        case .historial: historialTab
        }
    }

    private func datosTab(doc: Documento) -> some View {
        VStack(spacing: 12) {
            SeccionCard(titulo: "Información del documento") {
                Fila("Folio", doc.folio)
                Fila("Asunto", doc.asunto)
                Fila("Tipo", doc.tipo.etiqueta)
                Fila("Remitente", doc.remitente)
                Fila("Destinatario", doc.destinatario)
                Fila("Área de origen", doc.areaOrigen)
                Fila("Área destinataria", doc.areaDestino)
                Fila("Área actual", doc.areaActual)
                Fila("Clasificación", doc.clasificacion.etiqueta)
                Fila("Referencia", doc.referencia)
                Fila("Anexos", "\(doc.numAnexos)")
                Fila("Fecha documento", doc.fechaDocumento)
                Fila("Fecha recepción", fechaHora(doc.fechaRecepcion))
                Fila("Fecha límite", doc.fechaLimite + (doc.fechaLimiteHora.isEmpty ? "" : " \(doc.fechaLimiteHora) h"))
                Fila("Estado", doc.estado.etiqueta)
                Fila("Prioridad", doc.urgencia.etiqueta)
                Fila("Notas", doc.notas)
                if !doc.observaciones.isEmpty { Fila("Observaciones", doc.observaciones) }
                Text("UUID: \(doc.uuid)")
                    .font(.system(size: 11))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            SeccionCard(titulo: "Línea de tiempo") {
                if modelo.historial.isEmpty {
                    Text("Sin movimientos registrados.")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                } else {
                    ForEach(modelo.historial) { paso in
                        PasoHistorialRow(paso: paso)
                    }
                }
            }
        }
    }

    private func pdfTab(doc: Documento) -> some View {
        VStack(spacing: 12) {
            if !doc.tieneArchivo {
                Text("Este documento no tiene PDF asociado.")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                if ServicioKeychain.permisos().contains("DOCUMENTO_SUBIR_PDF") {
                    Button("Subir PDF") { subirPDF() }
                        .buttonStyle(.borderedProminent)
                        .tint(EstiloInstitucional.verdePrincipal)
                }
            } else {
                Text(doc.pdfNombre)
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)

                if modelo.pdfCargando {
                    ProgressView().scaleEffect(1.2)
                } else if let datos = modelo.pdfDatos {
                    VisorPDF(datos: datos)
                        .frame(height: 420)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(EstiloInstitucional.bordeSuave, lineWidth: 1))
                } else {
                    Button("Cargar PDF") {
                        Task { await modelo.cargarPDF(documentoId: doc.id) }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(EstiloInstitucional.verdePrincipal)
                }

                HStack(spacing: 12) {
                    Button("Recargar") {
                        Task { await modelo.cargarPDF(documentoId: doc.id) }
                    }
                    .buttonStyle(.bordered)
                    .tint(EstiloInstitucional.verdePrincipal)

                    if let datos = modelo.pdfDatos {
                        ShareLink(item: datos, preview: SharePreview(doc.pdfNombre)) {
                            Label("Compartir", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.bordered)
                        .tint(EstiloInstitucional.verdePrincipal)
                    }
                }
            }
        }
        .task {
            if doc.tieneArchivo, modelo.pdfDatos == nil, !modelo.pdfCargando {
                await modelo.cargarPDF(documentoId: doc.id)
            }
        }
    }

    private func ocrTab(doc: Documento) -> some View {
        VStack(spacing: 12) {
            SeccionCard(titulo: "Texto reconocido (OCR)") {
                if doc.textoOCR.isEmpty && modelo.textoOCR.isEmpty {
                    Text("Sin texto OCR disponible.")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                } else {
                    TextEditor(text: $modelo.textoOCR)
                        .frame(minHeight: 140)
                        .padding(8)
                        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    if ServicioKeychain.permisos().contains("DOCUMENTO_OCR") {
                        Button("Guardar corrección") {
                            Task {
                                do {
                                    try await ServicioDocumentos().guardarOCR(documentoId: doc.id, texto: modelo.textoOCR)
                                    modelo.documento?.textoOCR = modelo.textoOCR
                                } catch {
                                    modelo.errorTexto = "No se pudo guardar el OCR."
                                }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(EstiloInstitucional.verdePrincipal)
                    }
                }
            }
        }
        .onAppear {
            if modelo.textoOCR.isEmpty { modelo.textoOCR = doc.textoOCR }
        }
    }

    private func tramiteTab(doc: Documento) -> some View {
        VStack(spacing: 12) {
            SeccionCard(titulo: "Ruta del trámite") {
                FlujoPasosView(estado: doc.estado)
            }
            SeccionCard(titulo: "Árbol de asignación") {
                if modelo.asignaciones.isEmpty {
                    Text("Sin asignaciones registradas.")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                } else {
                    ForEach(modelo.asignaciones) { a in
                        AsignacionRow(a: a)
                    }
                }
            }
            SeccionCard(titulo: "Respuestas oficiales") {
                if modelo.respuestas.isEmpty {
                    Text("Sin respuestas registradas.")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                } else {
                    ForEach(modelo.respuestas) { r in
                        RespuestaRow(r: r)
                    }
                }
                if !doc.respuestaConsolidada.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Respuesta consolidada final:")
                            .font(.system(size: 13, weight: .bold))
                        Text(doc.respuestaConsolidada)
                            .font(.system(size: 13))
                    }
                    .padding(10)
                    .background(Color(red: 0.94, green: 0.98, blue: 0.97))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            SeccionCard(titulo: "Documentos relacionados") {
                if modelo.relaciones.isEmpty {
                    Text("Sin documentos relacionados.")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                } else {
                    ForEach(modelo.relaciones) { r in
                        HStack {
                            Text("#\(r.documentoRelacionadoId)")
                                .font(.system(size: 13, weight: .bold))
                            Text(r.etiquetaTipo)
                                .font(.system(size: 12))
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                            Spacer()
                            if ServicioKeychain.permisos().contains("DOCUMENTO_CONSULTAR") {
                                Button {
                                    Task { await modelo.desvincular(relacionId: r.id) }
                                } label: {
                                    Image(systemName: "xmark.circle")
                                        .foregroundColor(.red)
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                    }
                }
                if !doc.estado.esTerminal && ServicioKeychain.permisos().contains("DOCUMENTO_CONSULTAR") {
                    VincularForm(modelo: modelo)
                }
            }
        }
    }

    private func anotacionesTab(doc: Documento) -> some View {
        VStack(spacing: 12) {
            if ServicioKeychain.permisos().contains("DOCUMENTO_ANOTAR") && !doc.estado.esTerminal {
                Button("＋ Nueva anotación") { mostrandoAnotar = true }
                    .buttonStyle(.borderedProminent)
                    .tint(EstiloInstitucional.verdePrincipal)
            }
            if modelo.anotaciones.isEmpty {
                Text("Sin anotaciones.")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            } else {
                ForEach(modelo.anotaciones) { a in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(a.creadoPor.isEmpty ? "—" : a.creadoPor)
                                .font(.system(size: 13, weight: .bold))
                            Text("· \(a.etiquetaTipo)")
                                .font(.system(size: 12))
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                            Spacer()
                            if ServicioKeychain.permisos().contains("AUDITORIA_CONSULTAR") {
                                Button {
                                    Task { await modelo.eliminarAnotacion(anotacionId: a.id) }
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        Text(a.texto)
                            .font(.system(size: 13))
                        Text("\(fechaHora(a.creadoEn)) · \(a.area)")
                            .font(.system(size: 11))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                    }
                    .padding(10)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }

    private var historialTab: some View {
        VStack(spacing: 8) {
            if modelo.historial.isEmpty {
                Text("Sin movimientos registrados.")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            } else {
                ForEach(modelo.historial) { paso in
                    PasoHistorialRow(paso: paso)
                }
            }
        }
    }

    private func recibir() {
        Task {
            do {
                _ = try await ServicioDocumentos().recibir(documentoId: documentoId)
                await modelo.cargar(id: documentoId)
            } catch { modelo.errorTexto = "No se pudo registrar la recepción." }
        }
    }

    private func atender() {
        Task {
            do {
                _ = try await ServicioDocumentos().atender(documentoId: documentoId)
                await modelo.cargar(id: documentoId)
            } catch { modelo.errorTexto = "No se pudo atender." }
        }
    }

    private func acusar() {
        Task {
            do {
                _ = try await ServicioDocumentos().leer(documentoId: documentoId, acuse: true)
                await modelo.cargar(id: documentoId)
            } catch { modelo.errorTexto = "No se pudo registrar el acuse." }
        }
    }

    private func historico() {
        Task {
            do {
                _ = try await ServicioDocumentos().historico(documentoId: documentoId)
                await modelo.cargar(id: documentoId)
            } catch { modelo.errorTexto = "No se pudo pasar a histórico." }
        }
    }

    private func eliminar() {
        Task {
            do {
                try await ServicioDocumentos().eliminar(id: documentoId)
                dismiss()
            } catch { modelo.errorTexto = "No se pudo eliminar." }
        }
    }

    private func subirPDF() {

    }

    private func fechaHora(_ fecha: Date?) -> String {
        guard let fecha else { return "—" }
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: fecha)
    }

    private func Fila(_ etiqueta: String, _ valor: String) -> some View {
        HStack(alignment: .top) {
            Text(etiqueta)
                .font(.system(size: 13))
                .foregroundColor(EstiloInstitucional.textoSecundario)
            Spacer(minLength: 16)
            Text(valor.isEmpty ? "—" : valor)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
                .multilineTextAlignment(.trailing)
        }
    }
}

@MainActor
final class DetalleDocumentoViewModel: ObservableObject {
    @Published var documento: Documento?
    @Published var historial: [PasoHistorial] = []
    @Published var asignaciones: [AsignacionDocumento] = []
    @Published var respuestas: [RespuestaOficial] = []
    @Published var relaciones: [RelacionDocumento] = []
    @Published var anotaciones: [Anotacion] = []
    @Published var textoOCR: String = ""
    @Published var pdfDatos: Data?
    @Published var pdfCargando = false
    @Published var qrTexto: String = ""
    @Published var qrMatriz: [[Bool]] = []
    @Published var estaCargando = false
    @Published var errorTexto: String?

    private let servicio = ServicioDocumentos()
    private let repoPerfil = RepositorioPerfil.shared

    func cargar(id: Int) async {
        estaCargando = true
        defer { estaCargando = false }
        do {
            async let doc = servicio.obtener(id: id)
            async let hist = servicio.historial(id: id)
            async let asg = servicio.asignaciones(id: id)
            async let res = servicio.respuestas(id: id)
            async let rel = servicio.relaciones(id: id)
            async let ano = servicio.anotaciones(id: id)
            documento = try await doc
            historial = try await hist
            asignaciones = try await asg
            respuestas = try await res
            relaciones = try await rel
            anotaciones = try await ano
            textoOCR = documento?.textoOCR ?? ""

            let (t, m) = try await servicio.qr(documentoId: id)
            qrTexto = t
            qrMatriz = m
        } catch {
            errorTexto = "No se pudo cargar el documento."
        }
    }

    func cargarPDF(documentoId: Int) async {
        pdfCargando = true
        defer { pdfCargando = false }
        do {
            let (datos, _) = try await ServicioDocumentos().descargarArchivo(documentoId: documentoId)
            pdfDatos = datos
        } catch {
            errorTexto = "No se pudo cargar el PDF."
        }
    }

    func desvincular(relacionId: Int) async {
        guard let doc = documento else { return }
        do {
            try await servicio.eliminarRelacion(documentoId: doc.id, relacionId: relacionId)
            await cargar(id: doc.id)
        } catch { errorTexto = "No se pudo desvincular." }
    }

    func eliminarAnotacion(anotacionId: Int) async {
        guard let doc = documento else { return }
        do {
            try await servicio.eliminarAnotacion(documentoId: doc.id, anotacionId: anotacionId)
            await cargar(id: doc.id)
        } catch { errorTexto = "No se pudo eliminar la anotación." }
    }
}

private struct TurnarSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var direcciones: [NivelOrganizacional] = []
    @State private var subdirecciones: [NivelOrganizacional] = []
    @State private var departamentos: [NivelOrganizacional] = []
    @State private var personas: [PersonaCatalogo] = []

    @State private var direccionSeleccionada: Int?
    @State private var subdireccionSeleccionada: Int?
    @State private var departamentoSeleccionado: Int?
    @State private var personaSeleccionada: Int?
    @State private var comentario = ""
    @State private var cargando = false
    @State private var enviando = false
    @State private var errorTexto: String?

    private var personasFiltradas: [PersonaCatalogo] {
        guard let departamentoSeleccionado else { return personas }
        return personas.filter { $0.idDepartamento == departamentoSeleccionado }
    }

    private var persona: PersonaCatalogo? {
        personas.first { $0.id == personaSeleccionada }
    }

    private var destino: (tipo: String, id: Int)? {
        guard let p = persona else { return nil }
        if let d = p.idDepartamento { return ("departamento", d) }
        if let s = p.idSubdireccion { return ("subdireccion", s) }
        if let d = p.idDireccion { return ("direccion", d) }
        let uid = p.idUsuario ?? p.id
        return uid > 0 ? ("usuario", uid) : nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if cargando {
                        HStack {
                            ProgressView().scaleEffect(0.85)
                            Text("Cargando estructura organizacional…")
                                .font(.footnote)
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                        }
                    }

                    Picker("1. Área", selection: $direccionSeleccionada) {
                        Text("Seleccionar Área").tag(Int?.none)
                        ForEach(direcciones) { nivel in
                            Text(nivel.nombre).tag(Optional(nivel.id))
                        }
                    }

                    Picker("2. Subdirección", selection: $subdireccionSeleccionada) {
                        Text(direccionSeleccionada == nil ? "Primero seleccione el Área" : "Seleccionar Subdirección")
                            .tag(Int?.none)
                        ForEach(subdirecciones) { nivel in
                            Text(nivel.nombre).tag(Optional(nivel.id))
                        }
                    }
                    .disabled(direccionSeleccionada == nil)

                    if !departamentos.isEmpty {
                        Picker("3. Departamento", selection: $departamentoSeleccionado) {
                            Text("Todos los departamentos").tag(Int?.none)
                            ForEach(departamentos) { nivel in
                                Text(nivel.nombre).tag(Optional(nivel.id))
                            }
                        }
                        .disabled(subdireccionSeleccionada == nil)
                    }

                    Picker("Persona", selection: $personaSeleccionada) {
                        Text(direccionSeleccionada == nil ? "Primero seleccione el Área" : "Seleccionar Persona")
                            .tag(Int?.none)
                        ForEach(personasFiltradas) { p in
                            Text(p.nombre).tag(Optional(p.id))
                        }
                    }
                    .disabled(direccionSeleccionada == nil || personasFiltradas.isEmpty)
                } header: {
                    Text("Turnar a (estructura institucional)")
                } footer: {
                    Text("Selecciona Área, Subdirección y la persona responsable. El servidor valida la jerarquía y registra la trazabilidad.")
                }

                if let persona {
                    Section("Destinatario") {
                        Text(persona.nombre).font(.system(size: 15, weight: .semibold))
                        if !persona.cargo.isEmpty {
                            Text(persona.cargo)
                                .font(.footnote)
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                        }
                        if let destino {
                            Text("Nivel de turno: \(destino.tipo.capitalized) #\(destino.id)")
                                .font(.caption)
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                        }
                    }
                }

                Section("Comentario") {
                    TextField("Comentario (opcional)", text: $comentario, axis: .vertical)
                        .lineLimit(2...4)
                }

                if let errorTexto {
                    Section {
                        Text(errorTexto)
                            .font(.footnote)
                            .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Turnar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                        .disabled(enviando)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await turnar() }
                    } label: {
                        if enviando {
                            ProgressView()
                        } else {
                            Text("Turnar")
                        }
                    }
                    .disabled(persona == nil || destino == nil || enviando)
                }
            }
            .task { await cargarEstructura() }
            .onChange(of: direccionSeleccionada) { _, nuevo in
                subdireccionSeleccionada = nil
                departamentoSeleccionado = nil
                personaSeleccionada = nil
                subdirecciones = []
                departamentos = []
                personas = []
                guard let nuevo else { return }
                Task { await cargarSubdirecciones(direccionId: nuevo) }
            }
            .onChange(of: subdireccionSeleccionada) { _, nuevo in
                departamentoSeleccionado = nil
                personaSeleccionada = nil
                departamentos = []
                personas = []
                guard let nuevo else { return }
                Task { await cargarDepartamentosYPersonas(subdireccionId: nuevo) }
            }
            .onChange(of: departamentoSeleccionado) { _, _ in
                personaSeleccionada = nil
            }
        }
    }

    @MainActor
    private func cargarEstructura() async {
        cargando = true
        errorTexto = nil
        defer { cargando = false }
        do {
            let repo = RepositorioOrganigrama.shared
            let dgs = try await repo.direccionesGeneralesPadre()
            guard let dg = dgs.first else {
                errorTexto = "No se encontró la Dirección General."
                return
            }
            direcciones = try await repo.hijos(.direccion, padreId: dg.id)
            if direcciones.isEmpty {
                errorTexto = "No hay Áreas disponibles para el turno."
            }
        } catch {
            errorTexto = "No se pudo cargar la estructura organizacional."
        }
    }

    @MainActor
    private func cargarSubdirecciones(direccionId: Int) async {
        cargando = true
        defer { cargando = false }
        do {
            subdirecciones = try await RepositorioOrganigrama.shared.hijos(.subdireccion, padreId: direccionId)
        } catch {
            errorTexto = "No se pudieron cargar las subdirecciones."
        }
    }

    @MainActor
    private func cargarDepartamentosYPersonas(subdireccionId: Int) async {
        cargando = true
        defer { cargando = false }
        do {
            let repo = RepositorioOrganigrama.shared
            async let deptos = repo.hijos(.departamento, padreId: subdireccionId)
            async let lista = repo.personas(filtros: ["subdireccion": "\(subdireccionId)"])
            async let cuentas = repo.usuarios()
            departamentos = (try? await deptos) ?? []

            let delCatalogo = (try? await lista) ?? []
            let deUsuarios = ((try? await cuentas) ?? []).filter {
                $0.idSubdireccion == subdireccionId
            }
            var vistos = Set<Int>()
            personas = (delCatalogo + deUsuarios).filter { persona in
                let clave = persona.idUsuario ?? persona.id
                return vistos.insert(clave).inserted
            }
            if personas.isEmpty {
                errorTexto = "No hay personas disponibles en esta subdirección."
            }
        }
    }

    @MainActor
    private func turnar() async {
        guard let doc = modelo.documento,
              let persona,
              let destino else { return }
        enviando = true
        errorTexto = nil
        defer { enviando = false }
        do {
            _ = try await ServicioDocumentos().turnar(
                documentoId: doc.id,
                opcionTipo: destino.tipo,
                opcionId: destino.id,
                usuarioDestinoId: persona.idUsuario ?? persona.id,
                comentario: comentario
            )
            onCerrar()
            dismiss()
        } catch {
            errorTexto = "No se pudo turnar: \(error.localizedDescription)"
        }
    }
}

private struct CompartirSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var areaA = ""
    @State private var areaB = ""
    @State private var comentario = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Dirección A", text: $areaA)
                TextField("Dirección B", text: $areaB)
                TextField("Comentario", text: $comentario, axis: .vertical)
            }
            .navigationTitle("Compartir A/B")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Compartir") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                _ = try await ServicioDocumentos().compartir(
                                    documentoId: doc.id, areaA: areaA, areaB: areaB, comentario: comentario
                                )
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo compartir." }
                        }
                    }
                }
            }
        }
    }
}

private struct ResponderSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var texto = ""
    @State private var observaciones = ""
    @State private var rama = 0

    var body: some View {
        NavigationStack {
            Form {
                TextEditor(text: $texto).frame(minHeight: 120)
                TextField("Observaciones", text: $observaciones, axis: .vertical)
                if modelo.documento?.compartido == true {
                    Picker("Rama", selection: $rama) {
                        Text("Rama A").tag(1)
                        Text("Rama B").tag(2)
                    }
                }
            }
            .navigationTitle("Responder")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Registrar") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                _ = try await ServicioDocumentos().responder(
                                    documentoId: doc.id, texto: texto,
                                    observaciones: observaciones, rama: rama
                                )
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo responder." }
                        }
                    }
                }
            }
        }
    }
}

private struct ConsolidarSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var texto = ""
    @State private var comentario = ""

    var body: some View {
        NavigationStack {
            Form {
                TextEditor(text: $texto).frame(minHeight: 120)
                TextField("Comentario", text: $comentario, axis: .vertical)
            }
            .navigationTitle("Consolidar")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Consolidar") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                _ = try await ServicioDocumentos().consolidar(
                                    documentoId: doc.id, texto: texto, comentario: comentario
                                )
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo consolidar." }
                        }
                    }
                }
            }
        }
    }
}

private struct DecisionSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    let esRevisar: Bool
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var comentario = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Comentario", text: $comentario, axis: .vertical)
                HStack {
                    Button(esRevisar ? "Aceptar" : "Validar") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                if esRevisar {
                                    _ = try await ServicioDocumentos().revisar(
                                        documentoId: doc.id, aceptar: true, comentario: comentario
                                    )
                                } else {
                                    _ = try await ServicioDocumentos().validar(
                                        documentoId: doc.id, aceptar: true, comentario: comentario
                                    )
                                }
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo procesar." }
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(EstiloInstitucional.verdePrincipal)
                    Button("Rechazar") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                if esRevisar {
                                    _ = try await ServicioDocumentos().revisar(
                                        documentoId: doc.id, aceptar: false, comentario: comentario
                                    )
                                } else {
                                    _ = try await ServicioDocumentos().validar(
                                        documentoId: doc.id, aceptar: false, comentario: comentario
                                    )
                                }
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo procesar." }
                        }
                    }
                    .buttonStyle(.bordered)
                    .tint(.red)
                }
            }
            .navigationTitle(esRevisar ? "Revisar" : "Validar")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
            }
        }
    }
}

private struct AnotarSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var texto = ""
    @State private var tipo: TipoAnotacion = .observacion

    var body: some View {
        NavigationStack {
            Form {
                Picker("Tipo", selection: $tipo) {
                    ForEach(TipoAnotacion.allCases) { t in Text(t.etiqueta).tag(t) }
                }
                TextEditor(text: $texto).frame(minHeight: 120)
            }
            .navigationTitle("Anotación")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Agregar") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                _ = try await ServicioDocumentos().anotar(
                                    documentoId: doc.id, tipo: tipo, texto: texto
                                )
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo anotar." }
                        }
                    }
                }
            }
        }
    }
}

private struct ArchivarSheet: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    var onCerrar: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var folioFinal = ""
    @State private var comentario = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Folio definitivo", text: $folioFinal)
                TextField("Comentario", text: $comentario, axis: .vertical)
            }
            .navigationTitle("Archivar")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Archivar") {
                        Task {
                            guard let doc = modelo.documento else { return }
                            do {
                                _ = try await ServicioDocumentos().archivar(
                                    documentoId: doc.id, folioFinal: folioFinal, comentario: comentario
                                )
                                onCerrar(); dismiss()
                            } catch { modelo.errorTexto = "No se pudo archivar." }
                        }
                    }
                }
            }
            .onAppear { folioFinal = modelo.documento?.folio ?? "" }
        }
    }
}

private struct QRSimpleSheet: View {
    let texto: String
    let matriz: [[Bool]]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if !matriz.isEmpty {
                    QRMatrixView(matriz: matriz)
                        .frame(width: 220, height: 220)
                } else {
                    Text(texto)
                        .font(.system(size: 14, weight: .bold))
                        .textSelection(.enabled)
                }
                Text(texto)
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                    .textSelection(.enabled)
            }
            .padding()
            .navigationTitle("Código QR")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } }
            }
        }
    }
}

private struct QRMatrixView: View {
    let matriz: [[Bool]]
    var body: some View {
        Canvas { ctx, size in
            let n = matriz.count
            guard n > 0 else { return }
            let cell = size.width / CGFloat(n)
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white))
            for (y, fila) in matriz.enumerated() {
                for (x, on) in fila.enumerated() {
                    if on {
                        let rect = CGRect(x: CGFloat(x) * cell, y: CGFloat(y) * cell, width: cell, height: cell)
                        ctx.fill(Path(rect), with: .color(Color(red: 0.02, green: 0.19, blue: 0.16)))
                    }
                }
            }
        }
    }
}

private struct BotonAccion: View {
    let titulo: String
    let icono: String
    let accion: () -> Void
    var body: some View {
        Button(action: accion) {
            Label(titulo, systemImage: icono)
                .font(.system(size: 12, weight: .semibold))
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(EstiloInstitucional.verdeFondoIcono)
                .foregroundColor(EstiloInstitucional.verdePrincipal)
                .clipShape(Capsule())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private struct SeccionCard<Contenido: View>: View {
    let titulo: String
    @ViewBuilder let contenido: () -> Contenido
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(titulo)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
            VStack(alignment: .leading, spacing: 6) {
                contenido()
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(EstiloInstitucional.bordeSuave, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
    }
}

private struct BadgeEstado: View {
    let estado: EstadoDocumento
    var body: some View {
        Text(estado.etiqueta.uppercased())
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(EstiloInstitucional.verdePrincipal)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(EstiloInstitucional.verdeSuave)
            .clipShape(Capsule())
    }
}

private struct BadgeUrgencia: View {
    let urgencia: UrgenciaDocumento
    var body: some View {
        Text(urgencia.etiqueta)
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(urgencia == .extraurgente ? EstiloInstitucional.rojoUrgencia :
                        (urgencia == .urgente ? Color.orange : EstiloInstitucional.verdePrincipal))
            .clipShape(Capsule())
    }
}

private struct BadgeVencimiento: View {
    let texto: String
    var body: some View {
        Text(texto)
            .font(.system(size: 10, weight: .bold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(EstiloInstitucional.fondo)
            .clipShape(Capsule())
    }
}

private struct FlujoPasosView: View {
    let estado: EstadoDocumento
    private let pasos: [(EstadoDocumento, String)] = [
        (.recibido, "Recibido"), (.pendienteTurno, "Pendiente de turno"), (.turnado, "Turnado"),
        (.enDistribucion, "En distribución interna"), (.enAtencion, "En atención"),
        (.respuestasCompletas, "Respuestas completas"), (.consolidado, "Consolidado"),
        (.enRevision, "En revisión"), (.enValidacion, "En validación"), (.validado, "Validado"),
        (.concluido, "Concluido")
    ]
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(pasos, id: \.0) { (estadoPaso, etiqueta) in
                let actual = estadoPaso == estado
                let hecho = indice(estado) > indice(estadoPaso)
                HStack(spacing: 8) {
                    Circle()
                        .fill(actual ? EstiloInstitucional.verdePrincipal :
                              (hecho ? Color.green : Color.gray.opacity(0.3)))
                        .frame(width: 10, height: 10)
                    Text(etiqueta)
                        .font(.system(size: 12, weight: actual ? .bold : .regular))
                        .foregroundColor(actual ? EstiloInstitucional.verdePrincipal : EstiloInstitucional.textoSecundario)
                }
            }
        }
    }
    private func indice(_ e: EstadoDocumento) -> Int {
        pasos.firstIndex(where: { $0.0 == e }) ?? -1
    }
}

private struct AsignacionRow: View {
    let a: AsignacionDocumento
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("\(a.asignadorNombre) → \(a.areaDestino.isEmpty ? (a.asignadoNombre) : a.areaDestino)")
                    .font(.system(size: 13, weight: .semibold))
                Spacer()
                Text(a.etiquetaNivel)
                    .font(.system(size: 10, weight: .semibold))
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(EstiloInstitucional.fondo)
                    .clipShape(Capsule())
            }
            Text(fechaHora(a.fechaAsignacion))
                .font(.system(size: 11))
                .foregroundColor(EstiloInstitucional.textoSecundario)
            if !a.comentario.isEmpty {
                Text("“\(a.comentario)”")
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
        }
        .padding(.vertical, 4)
    }
    private func fechaHora(_ f: Date?) -> String {
        guard let f else { return "—" }
        let df = DateFormatter(); df.dateStyle = .short; df.timeStyle = .short
        return df.string(from: f)
    }
}

private struct RespuestaRow: View {
    let r: RespuestaOficial
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(r.usuarioNombre.isEmpty ? "—" : r.usuarioNombre)
                    .font(.system(size: 13, weight: .bold))
                Spacer()
                Text(r.etiquetaRama)
                    .font(.system(size: 10, weight: .semibold))
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(EstiloInstitucional.fondo)
                    .clipShape(Capsule())
            }
            Text(r.texto)
                .font(.system(size: 13))
            if !r.observaciones.isEmpty {
                Text("Obs: \(r.observaciones)")
                    .font(.system(size: 11))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            Text(fechaHora(r.fechaHora))
                .font(.system(size: 11))
                .foregroundColor(EstiloInstitucional.textoSecundario)
        }
        .padding(.vertical, 4)
    }
    private func fechaHora(_ f: Date?) -> String {
        guard let f else { return "—" }
        let df = DateFormatter(); df.dateStyle = .short; df.timeStyle = .short
        return df.string(from: f)
    }
}

private struct PasoHistorialRow: View {
    let paso: PasoHistorial
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(paso.etiquetaAccion)
                    .font(.system(size: 13, weight: .bold))
                Spacer()
                Text(fechaHora(paso.fechaHora))
                    .font(.system(size: 11))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            if !paso.estadoAnterior.isEmpty || !paso.estadoNuevo.isEmpty {
                Text("\(paso.estadoAnterior) → \(paso.estadoNuevo)")
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            if !paso.comentario.isEmpty {
                Text("“\(paso.comentario)”")
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            if !paso.area.isEmpty {
                Text("📍 \(paso.area)")
                    .font(.system(size: 11))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            Text("\(paso.usuarioNombre.isEmpty ? "Sistema" : paso.usuarioNombre) · \(paso.dispositivo)")
                .font(.system(size: 11))
                .foregroundColor(EstiloInstitucional.textoSecundario)
        }
        .padding(.vertical, 4)
        .overlay(Divider(), alignment: .bottom)
    }
    private func fechaHora(_ f: Date?) -> String {
        guard let f else { return "—" }
        let df = DateFormatter(); df.dateStyle = .short; df.timeStyle = .short
        return df.string(from: f)
    }
}

private struct VincularForm: View {
    @ObservedObject var modelo: DetalleDocumentoViewModel
    @State private var otroId = ""
    @State private var tipo: TipoRelacion = .relacionadoCon
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("ID documento", text: $otroId)
                    .keyboardType(.numberPad)
                Picker("Vínculo", selection: $tipo) {
                    ForEach(TipoRelacion.allCases) { t in Text(t.etiqueta).tag(t) }
                }
                Button("Vincular") {
                    Task {
                        guard let id = Int(otroId), let doc = modelo.documento, id != doc.id else { return }
                        do {
                            try await ServicioDocumentos().crearRelacion(
                                documentoId: doc.id, otroId: id, tipo: tipo
                            )
                            await modelo.cargar(id: doc.id)
                            otroId = ""
                        } catch { modelo.errorTexto = "No se pudo vincular." }
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(EstiloInstitucional.verdePrincipal)
            }
        }
    }
}
