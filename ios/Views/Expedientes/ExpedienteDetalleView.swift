import SwiftUI

/// Detalle del expediente: información, documentos vinculados y acciones.
struct ExpedienteDetalleView: View {

    let expedienteId: Int
    @StateObject private var vistaModelo = ExpedienteDetalleViewModel(expedienteId: 0)

    init(expedienteId: Int) {
        self.expedienteId = expedienteId
        _vistaModelo = StateObject(wrappedValue: ExpedienteDetalleViewModel(expedienteId: expedienteId))
    }

    var body: some View {
        Form {
            if let expediente = vistaModelo.expediente {
                SeccionExpediente(expediente: expediente)
                SeccionDocumentos(vistaModelo: vistaModelo)
            } else {
                ProgressView().frame(maxWidth: 360)
            }
        }
        .navigationTitle("GestióN Documental")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await vistaModelo.cargar() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
    }
}

private struct SeccionExpediente: View {

    let expediente: Expediente

    var body: some View {
        Section("Expediente") {
            Labeled("Número", expediente.numero.isEmpty ? "—" : expediente.numero)
            Labeled("Descripción", expediente.descripcion.isEmpty ? "—" : expediente.descripcion)
            if !expediente.clasificacion.isEmpty {
                Labeled("Clasificación", expediente.clasificacion, destacado: true)
            }
            if !expediente.areaResponsable.isEmpty {
                Labeled("Área responsable", expediente.areaResponsable)
            }
            if let fecha = expediente.creadoEn {
                Labeled("Registrado", fecha.formatted(date: .abbreviated, time: .omitted))
            }
        }
    }

    private func Labeled(_ etiqueta: String, _ valor: String, destacado: Bool = false) -> some View {
        HStack {
            Text(etiqueta)
                .foregroundColor(EstiloInstitucional.textoSecundario)
            Spacer()
            Text(valor)
                .foregroundColor(
                    destacado ? EstiloInstitucional.verdePrincipal : EstiloInstitucional.textoPrincipal
                )
        }
        .frame(maxWidth: 360)
    }
}

private struct SeccionDocumentos: View {

    @ObservedObject var vistaModelo: ExpedienteDetalleViewModel

    var body: some View {
        Section("Documentos") {
            ForEach(vistaModelo.documentos) { documento in
                NavigationLink {
                    DetalleDocumentoView(documentoId: documento.id)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(documento.folio.isEmpty ? documento.asunto : documento.folio)
                            .font(.subheadline.weight(.semibold))
                        Text(documento.asunto)
                            .font(.footnote)
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                    }
                }
            }
            if vistaModelo.documentos.isEmpty {
                Text("Sin documentos vinculados.")
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class ExpedienteDetalleViewModel: ObservableObject {

    let expedienteId: Int
    @Published private(set) var expediente: Expediente?
    @Published private(set) var documentos: [Documento] = []
    @Published private(set) var estaCargando = false

    private let servicio = ServicioExpedientes()

    init(expedienteId: Int) {
        self.expedienteId = expedienteId
    }

    func cargar() async {
        estaCargando = true
        defer { estaCargando = false }
        do {
            let (expedienteServidor, documentosServidor) = try await servicio.detalle(id: expedienteId)
            expediente = expedienteServidor
            documentos = documentosServidor
        } catch {
            expediente = nil
        }
    }
}
