import SwiftUI

struct DashboardView: View {

    @StateObject private var vistaModelo = DashboardViewModel()
    @State private var mostrandoEscaner = false
    @State private var mostrandoIngresar = false
    @State private var mostrandoConfirmacionCierre = false

    let onCerrarSesion: () -> Void

    private var nombreCompleto: String {
        ServicioKeychain.nombreCompletoUsuario()
    }

    private var subdireccionTexto: String {
        ServicioKeychain.subdireccionUsuario()
    }

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {

                        VStack(alignment: .center, spacing: 6) {
                            Text(nombreCompleto)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(EstiloInstitucional.textoPrincipal)
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                                .minimumScaleFactor(0.78)
                                .frame(maxWidth: .infinity, alignment: .center)

                            Text(subdireccionTexto)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                                .multilineTextAlignment(.center)
                                .lineLimit(3)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .padding(.top, 2)

                        }
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                        .padding(.top, 4)

                        Button {
                            mostrandoIngresar = true
                        } label: {
                            HStack(spacing: 16) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 18)
                                        .fill(Color.white.opacity(0.22))
                                        .frame(width: 66, height: 66)
                                    Image(systemName: "qrcode.viewfinder")
                                        .font(.system(size: 30, weight: .medium))
                                        .foregroundColor(.white)
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Nuevo documento")
                                        .font(.system(size: 19, weight: .bold))
                                        .foregroundColor(.white)

                                    Text("Escanea o registra un documento para iniciar su seguimiento.")
                                        .font(.system(size: 13))
                                        .foregroundColor(Color(red: 0.89, green: 0.94, blue: 0.92))
                                        .lineLimit(2)

                                    HStack(spacing: 4) {
                                        Text("COMENZAR")
                                            .font(.system(size: 12, weight: .bold))
                                        Image(systemName: "arrow.right")
                                            .font(.system(size: 11, weight: .bold))
                                    }
                                    .foregroundColor(.white)
                                    .padding(.top, 6)
                                }

                                Spacer()
                            }
                            .padding(18)
                            .background(EstiloInstitucional.verdePrincipal)
                            .clipShape(RoundedRectangle(cornerRadius: 22))
                            .shadow(color: EstiloInstitucional.verdePrincipal.opacity(0.3), radius: 8, y: 4)
                        }
                        .buttonStyle(PlainButtonStyle())
                        .padding(.horizontal, 20)

                        VStack(alignment: .leading, spacing: 12) {
                            Text("Tus documentos")
                                .font(.system(size: 19, weight: .bold))
                                .foregroundColor(EstiloInstitucional.textoPrincipal)
                                .padding(.horizontal, 20)

                            NavigationLink {
                                DocumentosView()
                            } label: {
                                HStack(spacing: 14) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 15)
                                            .fill(EstiloInstitucional.verdeFondoIcono)
                                            .frame(width: 54, height: 54)
                                        Image(systemName: "folder.fill")
                                            .font(.system(size: 24))
                                            .foregroundColor(EstiloInstitucional.verdePrincipal)
                                    }

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text("Mis documentos")
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundColor(EstiloInstitucional.textoPrincipal)

                                        Text("Consulta los documentos ingresados y asignados a ti.")
                                            .font(.system(size: 13))
                                            .foregroundColor(EstiloInstitucional.textoSecundario)
                                            .lineLimit(2)
                                    }

                                    Spacer()

                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                                }
                                .padding(16)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18)
                                        .stroke(Color(red: 0.88, green: 0.91, blue: 0.89), lineWidth: 1)
                                )
                                .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
                            }
                            .buttonStyle(PlainButtonStyle())
                            .padding(.horizontal, 20)

                            NavigationLink {
                                BuscarDocumentoView()
                            } label: {
                                HStack(spacing: 14) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 15)
                                            .fill(EstiloInstitucional.doradoFondoIcono)
                                            .frame(width: 54, height: 54)
                                        Image(systemName: "magnifyingglass")
                                            .font(.system(size: 22, weight: .semibold))
                                            .foregroundColor(EstiloInstitucional.verdePrincipal)
                                    }

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text("Buscar documento")
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundColor(EstiloInstitucional.textoPrincipal)

                                        Text("Localiza expedientes por folio, asunto o datos disponibles.")
                                            .font(.system(size: 13))
                                            .foregroundColor(EstiloInstitucional.textoSecundario)
                                            .lineLimit(2)
                                    }

                                    Spacer()

                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                                }
                                .padding(16)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18)
                                        .stroke(Color(red: 0.91, green: 0.87, blue: 0.80), lineWidth: 1)
                                )
                                .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
                            }
                            .buttonStyle(PlainButtonStyle())
                            .padding(.horizontal, 20)
                        }

                        if let resumen = vistaModelo.resumen {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Resumen de expedientes")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                                    .padding(.horizontal, 20)

                                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                                    NavigationLink {
                                        DocumentosView(seleccionInicial: .misDocumentos)
                                    } label: {
                                        tarjetaMetrica(
                                            titulo: "Total",
                                            valor: resumen.total,
                                            icono: "doc.text.fill",
                                            colorFondo: EstiloInstitucional.cardBgDocumentos,
                                            colorBorde: EstiloInstitucional.cardStrokeDocumentos,
                                            colorIcono: EstiloInstitucional.dorado
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())

                                    NavigationLink {
                                        DocumentosView(seleccionInicial: .pendientesTurno)
                                    } label: {
                                        tarjetaMetrica(
                                            titulo: "Pendientes",
                                            valor: resumen.pendientes,
                                            icono: "clock.fill",
                                            colorFondo: EstiloInstitucional.cardBgCompartidos,
                                            colorBorde: EstiloInstitucional.cardStrokeCompartidos,
                                            colorIcono: Color(red: 0.96, green: 0.65, blue: 0.14)
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())

                                    NavigationLink {
                                        DocumentosView(seleccionInicial: .enTramite)
                                    } label: {
                                        tarjetaMetrica(
                                            titulo: "En proceso",
                                            valor: resumen.enProceso,
                                            icono: "arrow.triangle.2.circlepath",
                                            colorFondo: EstiloInstitucional.cardBgProyectos,
                                            colorBorde: EstiloInstitucional.cardStrokeProyectos,
                                            colorIcono: EstiloInstitucional.verdeClaro
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())

                                    NavigationLink {
                                        DocumentosView(seleccionInicial: .archivados)
                                    } label: {
                                        tarjetaMetrica(
                                            titulo: "Finalizados",
                                            valor: resumen.finalizados,
                                            icono: "checkmark.seal.fill",
                                            colorFondo: EstiloInstitucional.cardBgSubir,
                                            colorBorde: EstiloInstitucional.cardStrokeSubir,
                                            colorIcono: Color(red: 0.12, green: 0.53, blue: 0.90)
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                                .padding(.horizontal, 20)
                            }
                        }

                        VStack(alignment: .leading, spacing: 12) {
                            Text("Movimientos recientes")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(EstiloInstitucional.textoPrincipal)
                                .padding(.horizontal, 20)

                            VStack(spacing: 0) {
                                if vistaModelo.estaCargando {
                                    HStack {
                                        Spacer()
                                        ProgressView()
                                            .padding(20)
                                        Spacer()
                                    }
                                } else if vistaModelo.movimientos.isEmpty {
                                    HStack {
                                        Spacer()
                                        Text("Aún no hay movimientos registrados.")
                                            .font(.footnote)
                                            .foregroundColor(EstiloInstitucional.textoSecundario)
                                            .padding(20)
                                        Spacer()
                                    }
                                } else {
                                    ForEach(Array(vistaModelo.movimientos.prefix(5).enumerated()), id: \.element.id) { index, movimiento in
                                        if index > 0 {
                                            Divider()
                                                .padding(.leading, 48)
                                        }

                                        HStack(alignment: .top, spacing: 12) {
                                            ZStack {
                                                Circle()
                                                    .fill(EstiloInstitucional.verdeSuave)
                                                    .frame(width: 36, height: 36)
                                                Image(systemName: "arrow.triangle.2.circlepath")
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                                            }

                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(movimiento.accion)
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundColor(EstiloInstitucional.textoPrincipal)

                                                Text(movimiento.documentoUuid.isEmpty ? "Documento" : movimiento.documentoUuid)
                                                    .font(.system(size: 12))
                                                    .foregroundColor(EstiloInstitucional.textoSecundario)

                                                Text("\(movimiento.realizadoPor) · \(movimiento.realizadoEn.formatted(date: .abbreviated, time: .shortened))")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(EstiloInstitucional.textoSecundario.opacity(0.8))
                                            }

                                            Spacer()
                                        }
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 12)
                                    }
                                }
                            }
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 18))
                            .overlay(
                                RoundedRectangle(cornerRadius: 18)
                                    .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
                            .padding(.horizontal, 20)
                        }

                        VStack(spacing: 4) {
                            Text("GestióN · Gestión Documental")
                                .font(.system(size: 11))
                                .foregroundColor(EstiloInstitucional.textoSecundario)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 10)
                        .padding(.bottom, 24)
                    }
                    .padding(.top, 12)
                }
            }
            .navigationTitle("GestióN Documental")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    NavigationLink {
                        NotificacionesView()
                    } label: {
                        Image(systemName: "bell.fill")
                    }
                    .accessibilityLabel("Notificaciones")
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        mostrandoConfirmacionCierre = true
                    } label: {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                    }
                    .accessibilityLabel("Cerrar sesión")
                }
            }
            .barraInstitucional()
            .task {
                await vistaModelo.cargar()
            }
            .sheet(isPresented: $mostrandoIngresar) {
                IngresarDocumentoView { _ in
                    Task { await vistaModelo.cargar() }
                }
            }
            .confirmationDialog(
                "Cerrar sesión",
                isPresented: $mostrandoConfirmacionCierre,
                titleVisibility: .visible
            ) {
                Button("Cerrar sesión", role: .destructive) {
                    vistaModelo.vaciarSesion()
                    onCerrarSesion()
                }
                Button("Cancelar", role: .cancel) {}
            } message: {
                Text("¿Deseas cerrar sesión en este dispositivo?")
            }
            .alert(
                vistaModelo.errorTexto ?? "",
                isPresented: Binding(
                    get: { vistaModelo.errorTexto != nil },
                    set: { if !$0 { vistaModelo.limpiarError() } }
                )
            ) {
                Button("Entendido", role: .cancel) {}
            } message: {
                Text(vistaModelo.errorTexto ?? "")
            }
        }
    }

    private func tarjetaMetrica(
        titulo: String,
        valor: Int,
        icono: String,
        colorFondo: Color,
        colorBorde: Color,
        colorIcono: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icono)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(colorIcono)
                Spacer()
                Text("\(valor)")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
            }

            Text(titulo)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(EstiloInstitucional.textoSecundario)
        }
        .padding(14)
        .background(colorFondo)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(colorBorde, lineWidth: 1)
        )
    }
}
