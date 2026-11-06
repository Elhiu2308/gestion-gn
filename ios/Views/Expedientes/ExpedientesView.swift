import SwiftUI

/// Pantalla de expedientes: listado y creación.
///
/// Réplica de la pantalla Android: encabezado institucional, tarjeta de
/// "Total registrado" con botón **+ Nuevo** y listado de expedientes en
/// tarjetas con badge de estado.
struct ExpedientesView: View {

    var alRegresar: (() -> Void)? = nil

    @StateObject private var vistaModelo = ExpedientesViewModel()
    @State private var mostrandoFiltros = false

    var body: some View {
        ZStack {
            EstiloInstitucional.fondo.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    encabezado
                    barraBusqueda
                    tarjetaTotal
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
        .sheet(isPresented: Binding(
            get: { vistaModelo.mostrandoCrear },
            set: { if !$0 { vistaModelo.cerrarCrear() } }
        )) {
            CrearExpedienteView { _ in
                Task { await vistaModelo.cargar() }
            }
        }
        .sheet(isPresented: $mostrandoFiltros) {
            FiltrosExpedientesSheet(vistaModelo: vistaModelo)
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
                Image(systemName: "folder.fill")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }
            Text("Expedientes")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
            Text("Consulta y administra los expedientes registrados.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
        }
    }

    // MARK: - Búsqueda y filtros

    private var barraBusqueda: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)

                TextField(
                    "Buscar por número, descripción, área o clasificación...",
                    text: $vistaModelo.busqueda
                )
                .font(.system(size: 14))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

                if !vistaModelo.busqueda.isEmpty {
                    Button {
                        vistaModelo.busqueda = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 50)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 15))
            .overlay(
                RoundedRectangle(cornerRadius: 15)
                    .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
            )

            Button {
                mostrandoFiltros = true
            } label: {
                Image(
                    systemName: vistaModelo.hayFiltros
                        ? "line.3.horizontal.decrease.circle.fill"
                        : "line.3.horizontal.decrease.circle"
                )
                .font(.system(size: 21, weight: .semibold))
                .foregroundColor(EstiloInstitucional.verdePrincipal)
                .frame(width: 50, height: 50)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 15))
                .overlay(
                    RoundedRectangle(cornerRadius: 15)
                        .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
            .accessibilityLabel("Filtros avanzados")
        }
    }

    // MARK: - Total registrado

    private var tarjetaTotal: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 15)
                    .fill(EstiloInstitucional.verdeFondoIcono)
                    .frame(width: 54, height: 54)
                Image(systemName: "folder.badge.person.crop")
                    .font(.system(size: 24))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text("Total registrado")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                Text("\(vistaModelo.expedientes.count) expediente(s)")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
            }

            Spacer()

            Button {
                vistaModelo.iniciarCrear()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .bold))
                    Text("Nuevo")
                        .font(.system(size: 13, weight: .bold))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(EstiloInstitucional.verdePrincipal)
                .foregroundColor(.white)
                .clipShape(Capsule())
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
    }

    // MARK: - Listado

    private var listado: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Listado de expedientes")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)

            if vistaModelo.estaCargando {
                HStack {
                    Spacer()
                    ProgressView().padding(20)
                    Spacer()
                }
            } else if vistaModelo.expedientesFiltrados.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "folder")
                        .font(.system(size: 42))
                        .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.6))
                    Text(vistaModelo.expedientes.isEmpty
                         ? "No hay expedientes registrados."
                         : "No se encontraron expedientes con la búsqueda o filtros seleccionados.")
                        .font(.system(size: 14))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
            } else {
                ForEach(vistaModelo.expedientesFiltrados) { expediente in
                    NavigationLink {
                        ExpedienteDetalleView(expedienteId: expediente.id)
                    } label: {
                        FilaExpediente(expediente: expediente)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }
}

/// Tarjeta de un expediente.
private struct FilaExpediente: View {

    let expediente: Expediente

    private var colorClasificacion: Color {
        switch expediente.clasificacion.uppercased() {
        case "RESERVADO": return EstiloInstitucional.rojoUrgencia
        case "PUBLICO", "PÚBLICO": return EstiloInstitucional.verdePrincipal
        default: return EstiloInstitucional.dorado
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(EstiloInstitucional.verdePrincipal)
                .frame(width: 6)
                .padding(.vertical, 2)

            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text(expediente.numero.isEmpty ? "Sin número" : expediente.numero)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                    Spacer()
                    if !expediente.clasificacion.isEmpty {
                        Text(expediente.clasificacion.uppercased())
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(colorClasificacion)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(colorClasificacion.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }

                Text(expediente.descripcion.isEmpty ? "Sin descripción" : expediente.descripcion)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                    .lineLimit(3)

                if !expediente.areaResponsable.isEmpty {
                    HStack(spacing: 4) {
                        Image(systemName: "building.2.fill")
                            .font(.system(size: 10))
                        Text(expediente.areaResponsable)
                            .lineLimit(1)
                    }
                    .font(.system(size: 11))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                }

                HStack {
                    if let fecha = expediente.creadoEn {
                        Text(fecha.formatted(date: .numeric, time: .omitted))
                            .font(.system(size: 11))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.5))
                }
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
}

// MARK: - Filtros

private struct FiltrosExpedientesSheet: View {

    @ObservedObject var vistaModelo: ExpedientesViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Clasificación") {
                    Picker("Clasificación", selection: $vistaModelo.clasificacionSeleccionada) {
                        Text("Todas las clasificaciones").tag("")
                        Text("Público").tag("PUBLICO")
                        Text("Confidencial").tag("CONFIDENCIAL")
                        Text("Reservado").tag("RESERVADO")
                    }
                }

                Section("Área responsable") {
                    TextField("Escribe el área a filtrar", text: $vistaModelo.area)
                        .textInputAutocapitalization(.words)
                }
            }
            .navigationTitle("Filtros avanzados")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Aplicar") { dismiss() }
                        .bold()
                }
                ToolbarItem(placement: .destructiveAction) {
                    Button("Limpiar") {
                        vistaModelo.clasificacionSeleccionada = ""
                        vistaModelo.area = ""
                    }
                }
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
final class ExpedientesViewModel: ObservableObject {

    @Published private(set) var expedientes: [Expediente] = []
    @Published private(set) var estaCargando = false
    @Published private(set) var mostrandoCrear = false

    @Published var busqueda = ""
    @Published var clasificacionSeleccionada = ""
    @Published var area = ""

    private let servicio = ServicioExpedientes()

    var hayFiltros: Bool {
        !clasificacionSeleccionada.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !area.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var expedientesFiltrados: [Expediente] {
        let consulta = busqueda.trimmingCharacters(in: .whitespacesAndNewlines)
        let clasificacion = clasificacionSeleccionada.trimmingCharacters(in: .whitespacesAndNewlines)
        let areaFiltro = area.trimmingCharacters(in: .whitespacesAndNewlines)

        return expedientes.filter { expediente in
            let coincideTexto = consulta.isEmpty || [
                expediente.numero,
                expediente.descripcion,
                expediente.clasificacion,
                expediente.areaResponsable
            ].contains { $0.localizedCaseInsensitiveContains(consulta) }

            let coincideClasificacion = clasificacion.isEmpty ||
                expediente.clasificacion.localizedCaseInsensitiveContains(clasificacion)
            let coincideArea = areaFiltro.isEmpty ||
                expediente.areaResponsable.localizedCaseInsensitiveContains(areaFiltro)

            return coincideTexto && coincideClasificacion && coincideArea
        }
    }

    func cargar() async {
        estaCargando = true
        defer { estaCargando = false }
        do {
            expedientes = try await servicio.listar()
        } catch {
            // Sin expedientes visibles para este usuario.
        }
    }

    func iniciarCrear() {
        mostrandoCrear = true
    }

    func cerrarCrear() {
        mostrandoCrear = false
    }
}

extension ServicioExpedientes {
    /// Lista los expedientes accesibles para el usuario autenticado.
    func listar() async throws -> [Expediente] {
        try await RepositorioExpedientes.shared.listar()
    }
}
