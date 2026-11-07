
import SwiftUI

struct NotificacionesView: View {

    @StateObject private var vistaModelo = NotificacionesViewModel()

    @State private var notificacionSinDocumento: Notificacion?

    var body: some View {

        ZStack {

            EstiloInstitucional.fondo
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {

                VStack(alignment: .leading, spacing: 16) {

                    encabezado

                    listado

                    Text("GestióN · Gestión Documental")
                        .font(.system(size: 11))
                        .foregroundColor(
                            EstiloInstitucional.textoSecundario
                        )
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

        .task {
            await vistaModelo.cargar()
        }

        .refreshable {
            await vistaModelo.cargar()
        }

        .alert(item: $notificacionSinDocumento) { notificacion in

            Alert(
                title: Text("Notificación"),
                message: Text(notificacion.mensaje),
                dismissButton: .default(Text("Aceptar"))
            )
        }
    }

    private var encabezado: some View {

        VStack(alignment: .leading, spacing: 6) {

            HStack(spacing: 6) {

                Image(systemName: "bell.fill")
                    .font(.system(size: 13))
                    .foregroundColor(
                        EstiloInstitucional.verdePrincipal
                    )

                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(
                        EstiloInstitucional.verdePrincipal
                    )
                    .tracking(0.6)
            }

            HStack(alignment: .firstTextBaseline) {

                Text("Notificaciones")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(
                        EstiloInstitucional.textoPrincipal
                    )

                Spacer(minLength: 8)

                Button {
                    Task {
                        await vistaModelo.marcarTodasLeidas()
                    }
                } label: {
                    Label("Marcar todas", systemImage: "checkmark.circle")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(
                            EstiloInstitucional.verdePrincipal
                        )
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            EstiloInstitucional.verdeSuave
                        )
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(vistaModelo.noLeidas == 0)
                .opacity(vistaModelo.noLeidas == 0 ? 0.45 : 1)
                .accessibilityLabel("Marcar todas las notificaciones como leídas")
            }

            HStack(spacing: 8) {

                Text("Avisos y trámites recibidos")
                    .font(.system(size: 14))
                    .foregroundColor(
                        EstiloInstitucional.textoSecundario
                    )

                if vistaModelo.noLeidas > 0 {

                    Text("\(vistaModelo.noLeidas)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(
                            EstiloInstitucional.rojoUrgencia
                        )
                        .clipShape(Capsule())
                }
            }
        }
    }

    private var listado: some View {

        Group {

            if vistaModelo.estaCargando {

                HStack {

                    Spacer()

                    ProgressView()
                        .padding(20)

                    Spacer()
                }

            } else if vistaModelo.notificaciones.isEmpty {

                VStack(spacing: 10) {

                    Image(systemName: "bell.slash")
                        .font(.system(size: 42))
                        .foregroundColor(
                            EstiloInstitucional.textoSecundario.opacity(0.6)
                        )

                    Text("Aún no hay notificaciones.")
                        .font(.system(size: 14))
                        .foregroundColor(
                            EstiloInstitucional.textoSecundario
                        )
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)

            } else {

                ForEach(vistaModelo.notificaciones) { notificacion in

                    FilaNotificacion(
                        notificacion: notificacion
                    ) {

                        seleccionar(notificacion)
                    }
                }
            }
        }
    }

    private func seleccionar(_ notificacion: Notificacion) {

        if !notificacion.leida {

            Task {
                await vistaModelo.marcarLeida(
                    id: notificacion.id
                )
            }
        }

        if let documentoId = notificacion.documentoId,
           documentoId > 0 {

            CentroNavegacion.shared.abrir(
                documentoId: documentoId,
                notificacionId: notificacion.id
            )

        } else {

            notificacionSinDocumento = notificacion
        }
    }
}

private struct FilaNotificacion: View {

    let notificacion: Notificacion

    let onSeleccionar: () -> Void

    private var esDocumento: Bool {

        NotificacionPresentacion.esDocumento(
            tipo: notificacion.tipo,
            documentoId: notificacion.documentoId
        )
    }

    private var icono: String {

        esDocumento
            ? "doc.text.fill"
            : "bell.badge.fill"
    }

    private var colorIcono: Color {

        esDocumento
            ? EstiloInstitucional.verdePrincipal
            : EstiloInstitucional.dorado
    }

    private var titulo: String {

        NotificacionPresentacion.titulo(
            tipo: notificacion.tipo,
            documentoId: notificacion.documentoId
        )
    }

    var body: some View {

        HStack(alignment: .top, spacing: 12) {

            ZStack {

                RoundedRectangle(cornerRadius: 13)
                    .fill(
                        esDocumento
                            ? EstiloInstitucional.verdeFondoIcono
                            : EstiloInstitucional.doradoFondoIcono
                    )
                    .frame(width: 46, height: 46)

                Image(systemName: icono)
                    .font(.system(size: 19))
                    .foregroundColor(colorIcono)
            }

            VStack(alignment: .leading, spacing: 3) {

                HStack {

                    Text(titulo)
                        .font(
                            .system(
                                size: 14,
                                weight: notificacion.leida ? .regular : .bold
                            )
                        )
                        .foregroundColor(
                            notificacion.leida
                                ? EstiloInstitucional.textoSecundario
                                : EstiloInstitucional.textoPrincipal
                        )

                    Spacer()

                    if !notificacion.leida {

                        Circle()
                            .fill(
                                EstiloInstitucional.rojoUrgencia
                            )
                            .frame(width: 8, height: 8)
                    }
                }

                Text(notificacion.mensaje)
                    .font(.system(size: 13))
                    .foregroundColor(
                        EstiloInstitucional.textoSecundario
                    )
                    .lineLimit(3)
                    .fixedSize(
                        horizontal: false,
                        vertical: true
                    )

                Text(
                    notificacion.emitidaEn.formatted(
                        date: .numeric,
                        time: .shortened
                    )
                )
                .font(.system(size: 11))
                .foregroundColor(
                    EstiloInstitucional.textoSecundario.opacity(0.8)
                )
                .padding(.top, 1)
            }
        }
        .padding(14)
        .background(Color.white)
        .clipShape(
            RoundedRectangle(cornerRadius: 18)
        )
        .overlay(

            RoundedRectangle(cornerRadius: 18)
                .stroke(
                    notificacion.leida
                        ? EstiloInstitucional.bordeSuave
                        : EstiloInstitucional.verdePrincipal.opacity(0.35),
                    lineWidth: 1
                )
        )
        .shadow(
            color: Color.black.opacity(0.03),
            radius: 3,
            y: 2
        )
        .contentShape(Rectangle())
        .onTapGesture {
            onSeleccionar()
        }
        .accessibilityElement()
        .accessibilityLabel(
            "\(titulo). \(notificacion.mensaje)"
        )
        .accessibilityHint(
            esDocumento
                ? "Abre el documento relacionado."
                : "Muestra el contenido de la notificación."
        )
    }
}

struct BannerAvisoNotificacion: View {

    let aviso: AvisoNotificacion
    let onVerDatos: () -> Void
    let onAbrir: () -> Void
    let onCerrar: () -> Void

    var body: some View {

        VStack(alignment: .leading, spacing: 10) {

            HStack(alignment: .top, spacing: 10) {

                ZStack {

                    RoundedRectangle(cornerRadius: 12)
                        .fill(EstiloInstitucional.verdeFondoIcono)
                        .frame(width: 40, height: 40)

                    Image(systemName: aviso.tieneDocumento
                          ? "doc.text.fill"
                          : "bell.badge.fill")
                        .font(.system(size: 17))
                        .foregroundColor(
                            EstiloInstitucional.verdePrincipal
                        )
                }

                VStack(alignment: .leading, spacing: 3) {

                    Text(aviso.titulo)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(
                            EstiloInstitucional.textoPrincipal
                        )

                    Text(aviso.mensaje)
                        .font(.system(size: 13))
                        .foregroundColor(
                            EstiloInstitucional.textoSecundario
                        )
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 6)

                Button(action: onCerrar) {

                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(
                            EstiloInstitucional.textoSecundario
                        )
                        .padding(6)
                        .background(
                            EstiloInstitucional.fondo
                        )
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Cerrar aviso")
            }

            HStack(spacing: 10) {

                if aviso.tieneDocumento {

                    Button(action: onVerDatos) {

                        Label("Ver datos", systemImage: "doc.text.magnifyingglass")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(
                                EstiloInstitucional.verdePrincipal
                            )
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                EstiloInstitucional.verdeSuave
                            )
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)

                    Button(action: onAbrir) {

                        Label("Abrir", systemImage: "arrow.up.right.square")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                EstiloInstitucional.verdePrincipal
                            )
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)

                } else {

                    Button(action: onCerrar) {

                        Text("Entendido")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(
                                EstiloInstitucional.verdePrincipal
                            )
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }

                Spacer()
            }
        }
        .padding(14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(
                    EstiloInstitucional.verdePrincipal.opacity(0.35),
                    lineWidth: 1
                )
        )
        .shadow(
            color: Color.black.opacity(0.12),
            radius: 12,
            y: 5
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(aviso.titulo). \(aviso.mensaje)")
    }
}

struct DatosDocumentoAvisoView: View {

    let documentoId: Int

    @Environment(\.dismiss) private var dismiss

    @State private var documento: Documento?
    @State private var errorTexto: String?
    @State private var cargando = true

    var body: some View {

        NavigationStack {

            ScrollView(showsIndicators: false) {

                VStack(alignment: .leading, spacing: 14) {

                    if cargando {

                        HStack {

                            Spacer()

                            ProgressView()
                                .padding(30)

                            Spacer()
                        }

                    } else if let documento {

                        VStack(alignment: .leading, spacing: 3) {

                            Text("Folio \(documento.folio)")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(
                                    EstiloInstitucional.verdePrincipal
                                )

                            Text(documento.asunto)
                                .font(.system(size: 19, weight: .bold))
                                .foregroundColor(
                                    EstiloInstitucional.textoPrincipal
                                )
                        }

                        fila("Tipo", documento.tipo.etiqueta)
                        fila("Estado", documento.estado.etiqueta)
                        fila(
                            "Área actual",
                            documento.areaActual.isEmpty
                                ? (documento.areaDestino.isEmpty ? "—" : documento.areaDestino)
                                : documento.areaActual
                        )
                        fila("Remitente", documento.remitente.isEmpty ? "—" : documento.remitente)
                        fila("Destinatario", documento.destinatario.isEmpty ? "—" : documento.destinatario)

                        if let creadoEn = documento.creadoEn {
                            fila(
                                "Registrado",
                                creadoEn.formatted(
                                    date: .numeric,
                                    time: .shortened
                                )
                            )
                        }

                        if !documento.fechaLimite.isEmpty {
                            fila("Fecha límite", documento.fechaLimite)
                        }

                        Button {
                            let id = documento.id
                            dismiss()

                            Task { @MainActor in
                                try? await Task.sleep(
                                    nanoseconds: 280_000_000
                                )
                                CentroNavegacion.shared.abrir(
                                    documentoId: id
                                )
                            }
                        } label: {

                            Label("Abrir documento", systemImage: "arrow.up.right.square")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    EstiloInstitucional.verdePrincipal
                                )
                                .clipShape(
                                    RoundedRectangle(cornerRadius: 14)
                                )
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 6)

                    } else {

                        Text(errorTexto ?? "No se pudieron obtener los datos del documento.")
                            .font(.system(size: 14))
                            .foregroundColor(
                                EstiloInstitucional.textoSecundario
                            )
                    }
                }
                .padding(20)
            }
            .background(EstiloInstitucional.fondo)
            .navigationTitle("Datos del documento")
            .navigationBarTitleDisplayMode(.inline)
            .barraInstitucional()
            .toolbar {

                ToolbarItem(placement: .cancellationAction) {

                    Button("Cerrar") {
                        dismiss()
                    }
                    .foregroundColor(
                        EstiloInstitucional.verdePrincipal
                    )
                }
            }
        }
        .task {

            do {

                documento = try await ServicioDocumentos()
                    .obtener(id: documentoId)

                if documento == nil {
                    errorTexto = "El documento ya no está disponible."
                }

            } catch {

                errorTexto = error.localizedDescription
            }

            cargando = false
        }
    }

    private func fila(_ etiqueta: String, _ valor: String) -> some View {

        HStack(alignment: .top, spacing: 12) {

            Text(etiqueta)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(
                    EstiloInstitucional.textoSecundario
                )
                .frame(width: 112, alignment: .leading)

            Text(valor)
                .font(.system(size: 13))
                .foregroundColor(
                    EstiloInstitucional.textoPrincipal
                )
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 2)
    }
}
