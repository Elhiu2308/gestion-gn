import SwiftUI

/// Pantalla "Documentos": selector de bandeja y listado de resultados.
///
/// No incluye búsqueda, filtros ni alta. Esas acciones viven en "Buscar
/// documento" para que la pantalla principal conserve el comportamiento
/// homologado con Android.
struct DocumentosView: View {

    @StateObject private var vistaModelo: DocumentosViewModel

    init(seleccionInicial: BandejaDocumentos = .misDocumentos) {
        _vistaModelo = StateObject(wrappedValue: DocumentosViewModel(opcion: seleccionInicial))
    }

    var body: some View {
        ZStack {
            EstiloInstitucional.fondo.ignoresSafeArea()

            VStack(spacing: 0) {
                selectorBandeja
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 8)

                Group {
                    if vistaModelo.estaCargando && vistaModelo.documentos.isEmpty {
                        ProgressView()
                            .scaleEffect(1.2)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let error = vistaModelo.errorTexto {
                        estadoVacio(icono: "wifi.exclamationmark", texto: error)
                    } else if vistaModelo.documentosVisibles.isEmpty {
                        estadoVacio(
                            icono: "doc.text.magnifyingglass",
                            texto: vistaModelo.opcion.mensajeVacio
                        )
                    } else {
                        ScrollView(showsIndicators: false) {
                            LazyVStack(spacing: 10) {
                                HStack {
                                    Text("\(vistaModelo.documentosVisibles.count) documento(s)")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(EstiloInstitucional.textoSecundario)
                                    Spacer()
                                }
                                .padding(.horizontal, 4)

                                ForEach(vistaModelo.documentosVisibles) { documento in
                                    NavigationLink {
                                        DetalleDocumentoView(documentoId: documento.id)
                                    } label: {
                                        DocumentoFila(documento: documento)
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                    }
                }
            }
        }
        .navigationTitle("GestióN Documental")
        .navigationBarTitleDisplayMode(.inline)
        .barraInstitucional()
        .onChange(of: vistaModelo.opcion) { _, _ in
            Task { await vistaModelo.recargar() }
        }
        .task { await vistaModelo.cargar() }
        .refreshable { await vistaModelo.recargar() }
    }

    private var selectorBandeja: some View {
        HStack(spacing: 10) {
            Image(systemName: "tray.full.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(EstiloInstitucional.verdePrincipal)

            Picker("Bandeja", selection: $vistaModelo.opcion) {
                ForEach(BandejaDocumentos.allCases) { opcion in
                    Text(opcion.titulo).tag(opcion)
                }
            }
            .pickerStyle(.menu)
            .tint(EstiloInstitucional.verdePrincipal)
            .labelsHidden()

            Spacer()

            if vistaModelo.estaCargando {
                ProgressView()
                    .controlSize(.small)
                    .tint(EstiloInstitucional.verdePrincipal)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 52)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 15))
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
    }

    private func estadoVacio(icono: String, texto: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icono)
                .font(.system(size: 48))
                .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.6))
            Text(texto)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Pantalla "Buscar documento" (`GET /api/buscar`).
///
/// Reproduce el buscador de Android: texto rápido, panel de filtros plegable,
/// filtros combinables y botón verde Buscar.
struct BuscarDocumentoView: View {

    @StateObject private var vistaModelo = BuscarDocumentoViewModel()
    @State private var mostrandoFiltros = false
    @FocusState private var buscadorEnfocado: Bool

    var body: some View {
        ZStack {
            EstiloInstitucional.fondo.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 12) {
                    campoBusqueda
                    acciones
                    if mostrandoFiltros {
                        panelFiltros
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                    resultados
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
        }
        .navigationTitle("GestióN Documental")
        .navigationBarTitleDisplayMode(.inline)
        .barraInstitucional()
        .scrollDismissesKeyboard(.interactively)
        .animation(.easeInOut(duration: 0.2), value: mostrandoFiltros)
    }

    // MARK: Búsqueda rápida

    private var campoBusqueda: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(EstiloInstitucional.verdePrincipal)

            TextField("Folio, asunto, destinatario o área", text: $vistaModelo.busqueda)
                .font(.system(size: 15))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($buscadorEnfocado)
                .submitLabel(.search)
                .onSubmit { ejecutarBusqueda() }

            if !vistaModelo.busqueda.isEmpty {
                Button {
                    vistaModelo.busqueda = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                }
                .buttonStyle(PlainButtonStyle())
                .accessibilityLabel("Limpiar búsqueda")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 52)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 15))
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
    }

    private var acciones: some View {
        HStack(spacing: 8) {
            Button {
                mostrandoFiltros.toggle()
            } label: {
                HStack(spacing: 7) {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .font(.system(size: 18, weight: .semibold))
                    Text("Filtros")
                        .font(.system(size: 14, weight: .semibold))
                    Image(systemName: mostrandoFiltros ? "chevron.up" : "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                }
                .foregroundColor(EstiloInstitucional.verdePrincipal)
                .padding(.horizontal, 14)
                .frame(height: 52)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(EstiloInstitucional.verdePrincipal, lineWidth: 1.2)
                )
            }
            .buttonStyle(PlainButtonStyle())

            Button(action: ejecutarBusqueda) {
                HStack(spacing: 8) {
                    if vistaModelo.estaCargando {
                        ProgressView()
                            .tint(.white)
                            .controlSize(.small)
                    }
                    Text("Buscar")
                        .font(.system(size: 15, weight: .bold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(EstiloInstitucional.verdePrincipal)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(PlainButtonStyle())
            .disabled(vistaModelo.estaCargando)
        }
    }

    // MARK: Filtros avanzados

    private var panelFiltros: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text("FILTROS AVANZADOS")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoSecundario)

                Text("Combina los campos que necesites. Los vacíos se ignoran.")
                    .font(.system(size: 12))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
            }
            .padding(.bottom, 2)

            CampoTextoFiltro(titulo: "Folio", texto: $vistaModelo.folio)
            CampoTextoFiltro(titulo: "Asunto", texto: $vistaModelo.asunto)
            CampoTextoFiltro(titulo: "Descripción", texto: $vistaModelo.descripcion)
            CampoTextoFiltro(titulo: "Destinatario", texto: $vistaModelo.destinatario)
            CampoTextoFiltro(titulo: "Remitente", texto: $vistaModelo.remitente)
            CampoTextoFiltro(titulo: "Área de origen", texto: $vistaModelo.areaOrigen)
            CampoTextoFiltro(titulo: "Área de destino", texto: $vistaModelo.areaDestino)

            menuTipo
            menuUrgencia

            HStack(alignment: .bottom, spacing: 8) {
                CampoFechaFiltro(titulo: "Fecha desde", fecha: $vistaModelo.fechaDesde)
                CampoFechaFiltro(titulo: "Fecha hasta", fecha: $vistaModelo.fechaHasta)
            }

            HStack {
                Spacer()
                Button {
                    vistaModelo.limpiarFiltros()
                } label: {
                    Text("Limpiar Filtros")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(EstiloInstitucional.rojoUrgencia)
                        .padding(.vertical, 6)
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
    }

    private var menuTipo: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("Tipo de documento")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(EstiloInstitucional.textoSecundario)

            Menu {
                Button("Todos") { vistaModelo.tipoSeleccionado = "" }
                ForEach(TipoDocumento.allCases) { tipo in
                    Button(tipo.etiqueta) {
                        vistaModelo.tipoSeleccionado = tipo.rawValue
                    }
                }
            } label: {
                HStack {
                    Text(vistaModelo.tipoTitulo)
                        .font(.system(size: 14))
                        .foregroundColor(EstiloInstitucional.textoPrincipal)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                }
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(EstiloInstitucional.fondo)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }

    private var menuUrgencia: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("Urgencia")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(EstiloInstitucional.textoSecundario)

            Menu {
                Button("Todos") { vistaModelo.urgenciaSeleccionada = "" }
                ForEach(UrgenciaDocumento.allCases) { urgencia in
                    Button(urgencia.etiqueta) {
                        vistaModelo.urgenciaSeleccionada = urgencia.rawValue
                    }
                }
            } label: {
                HStack {
                    Text(vistaModelo.urgenciaTitulo)
                        .font(.system(size: 14))
                        .foregroundColor(EstiloInstitucional.textoPrincipal)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                }
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(EstiloInstitucional.fondo)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }

    // MARK: Resultados

    @ViewBuilder
    private var resultados: some View {
        if vistaModelo.estaCargando {
            HStack {
                Spacer()
                ProgressView()
                    .padding(.vertical, 28)
                Spacer()
            }
        } else if let mensaje = vistaModelo.mensajeEstado {
            VStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 38))
                    .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.55))
                Text(mensaje)
                    .font(.system(size: 14))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 36)
        } else if !vistaModelo.haBuscado {
            Text("Escribe lo que recuerdas del documento o abre Filtros para afinar la búsqueda.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 20)
                .padding(.vertical, 36)
        } else {
            VStack(spacing: 10) {
                HStack {
                    Text("\(vistaModelo.resultados.count) resultado(s)")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                    Spacer()
                }
                .padding(.horizontal, 4)

                LazyVStack(spacing: 10) {
                    ForEach(vistaModelo.resultados) { documento in
                        NavigationLink {
                            DetalleDocumentoView(documentoId: documento.id)
                        } label: {
                            DocumentoFila(documento: documento)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }

    private func ejecutarBusqueda() {
        buscadorEnfocado = false
        Task { await vistaModelo.buscar() }
    }
}

// MARK: - Campos reutilizables

private struct CampoTextoFiltro: View {

    let titulo: String
    @Binding var texto: String

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(titulo)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(EstiloInstitucional.textoSecundario)

            TextField(titulo, text: $texto)
                .font(.system(size: 14))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(EstiloInstitucional.fondo)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                )
        }
    }
}

private struct CampoFechaFiltro: View {

    let titulo: String
    @Binding var fecha: Date?

    @State private var mostrandoCalendario = false
    @State private var seleccion = Date()

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(titulo)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(EstiloInstitucional.textoSecundario)

            Button {
                seleccion = fecha ?? Date()
                mostrandoCalendario = true
            } label: {
                HStack(spacing: 6) {
                    Text(textoFecha)
                        .font(.system(size: 13))
                        .foregroundColor(
                            fecha == nil
                                ? EstiloInstitucional.textoSecundario
                                : EstiloInstitucional.textoPrincipal
                        )
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)

                    Spacer(minLength: 2)

                    Image(systemName: "calendar")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                }
                .padding(.horizontal, 10)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(EstiloInstitucional.fondo)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
        .sheet(isPresented: $mostrandoCalendario) {
            NavigationStack {
                VStack(spacing: 12) {
                    DatePicker(
                        titulo,
                        selection: $seleccion,
                        displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .tint(EstiloInstitucional.verdePrincipal)
                    .padding(.horizontal, 8)

                    Button {
                        fecha = nil
                        mostrandoCalendario = false
                    } label: {
                        Text("Limpiar fecha")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(EstiloInstitucional.rojoUrgencia)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .padding(.bottom, 16)
                }
                .navigationTitle(titulo)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancelar") { mostrandoCalendario = false }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        Button("Aplicar") {
                            fecha = seleccion
                            mostrandoCalendario = false
                        }
                        .bold()
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
    }

    private var textoFecha: String {
        guard let fecha else { return "Seleccionar" }

        let formateador = DateFormatter()
        formateador.locale = Locale(identifier: "es_MX")
        formateador.dateFormat = "dd/MM/yyyy"
        return formateador.string(from: fecha)
    }
}

/// Tarjeta de documento réplica de `item_documento.xml` (Android).
struct DocumentoFila: View {

    let documento: Documento

    private var colorUrgencia: Color {
        switch documento.urgencia {
        case .extraurgente: return EstiloInstitucional.rojoUrgencia
        case .urgente: return .orange
        default: return EstiloInstitucional.verdePrincipal
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(EstiloInstitucional.verdePrincipal)
                .frame(width: 6)
                .padding(.vertical, 2)

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(documento.tipo.etiqueta.uppercased())
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                    Spacer()
                    Circle()
                        .fill(colorUrgencia)
                        .frame(width: 10, height: 10)
                }

                Text(documento.folio.isEmpty ? "Sin folio asignado" : documento.folio)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                    .lineLimit(1)

                Text(documento.asunto.isEmpty ? "Sin asunto registrado" : documento.asunto)
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                    .lineLimit(1)

                HStack {
                    Text(documento.estado.etiqueta.uppercased())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(EstiloInstitucional.verdeSuave)
                        .clipShape(Capsule())

                    if documento.compartido {
                        Text("A/B")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(EstiloInstitucional.fondo)
                            .clipShape(Capsule())
                    }

                    Spacer()

                    if let dias = documento.diasRestantes {
                        Text("\(dias) días")
                            .font(.system(size: 10))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                    }

                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.5))
                }
                .padding(.top, 4)
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
