import SwiftUI

struct CrearExpedienteView: View {

    @Environment(\.dismiss) private var dismiss
    @StateObject private var vistaModelo = CrearExpedienteViewModel()
    let onCreado: (Expediente) -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        encabezado

                        VStack(alignment: .leading, spacing: 14) {
                            campo(
                                titulo: "Número del expediente",
                                marcador: "EXP-2025-001",
                                texto: $vistaModelo.numero
                            )

                            campo(
                                titulo: "Descripción",
                                marcador: "Describe el asunto del expediente",
                                texto: $vistaModelo.descripcion,
                                multilinea: true
                            )

                            campo(
                                titulo: "Área responsable",
                                marcador: "Área que resguarda el expediente",
                                texto: $vistaModelo.areaResponsable
                            )

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Clasificación")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(EstiloInstitucional.textoSecundario)

                                Picker("Clasificación", selection: $vistaModelo.clasificacion) {
                                    ForEach(ClasificacionDocumento.allCases) { clasificacion in
                                        Text(clasificacion.etiqueta).tag(clasificacion.rawValue)
                                    }
                                }
                                .pickerStyle(.segmented)
                            }
                        }
                        .padding(16)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                        )

                        if let error = vistaModelo.errorTexto {
                            Label(error, systemImage: "exclamationmark.triangle.fill")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(EstiloInstitucional.rojoUrgencia)
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(EstiloInstitucional.rojoUrgencia.opacity(0.08))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }

                        Button {
                            Task {
                                if let expediente = await vistaModelo.crear() {
                                    onCreado(expediente)
                                    dismiss()
                                }
                            }
                        } label: {
                            HStack(spacing: 8) {
                                if vistaModelo.estaCargando {
                                    ProgressView().tint(.white)
                                } else {
                                    Image(systemName: "folder.badge.plus")
                                    Text("Crear expediente")
                                        .font(.system(size: 16, weight: .bold))
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 15)
                            .background(vistaModelo.puedeCrear
                                        ? EstiloInstitucional.verdePrincipal
                                        : EstiloInstitucional.verdePrincipal.opacity(0.4))
                            .foregroundColor(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(PlainButtonStyle())
                        .disabled(!vistaModelo.puedeCrear || vistaModelo.estaCargando)
                    }
                    .padding(20)
                }
            }
            .navigationTitle("Nuevo expediente")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "folder.fill")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }
            Text("Nuevo expediente")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
            Text("Registra el expediente para agrupar y dar seguimiento a sus documentos.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineSpacing(2)
        }
    }

    private func campo(
        titulo: String,
        marcador: String,
        texto: Binding<String>,
        multilinea: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(titulo)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(EstiloInstitucional.textoSecundario)

            Group {
                if multilinea {
                    TextField(marcador, text: texto, axis: .vertical)
                        .lineLimit(3...5)
                } else {
                    TextField(marcador, text: texto)
                }
            }
            .font(.system(size: 15))
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(EstiloInstitucional.fondo.opacity(0.7))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
            )
        }
    }
}

@MainActor
final class CrearExpedienteViewModel: ObservableObject {

    @Published var numero = ""
    @Published var descripcion = ""
    @Published var areaResponsable = ""
    @Published var clasificacion = ClasificacionDocumento.confidencial.rawValue
    @Published var estaCargando = false
    @Published var errorTexto: String?

    private let servicio = ServicioExpedientes()

    var puedeCrear: Bool {
        !numero.trimmingCharacters(in: .whitespaces).isEmpty
            && !descripcion.trimmingCharacters(in: .whitespaces).isEmpty
    }

    func crear() async -> Expediente? {
        guard puedeCrear else {
            errorTexto = "Captura el número y la descripción del expediente."
            return nil
        }

        estaCargando = true
        defer { estaCargando = false }
        errorTexto = nil

        do {
            return try await servicio.crear(
                datos: DatosExpediente(
                    numero: numero.trimmingCharacters(in: .whitespaces),
                    descripcion: descripcion.trimmingCharacters(in: .whitespaces),
                    areaResponsable: areaResponsable.trimmingCharacters(in: .whitespaces),
                    clasificacion: clasificacion
                )
            )
        } catch {
            errorTexto = (error as? LocalizedError)?.errorDescription
                ?? "No se pudo crear el expediente."
            return nil
        }
    }
}
