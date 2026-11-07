import AVFoundation
import SwiftUI

struct RecepcionQrView: View {

    var alRegresar: (() -> Void)? = nil

    @StateObject private var vistaModelo = RecepcionViewModel()

    @State private var documento: Documento?
    @State private var escaneando = true
    @State private var camaraNoDisponible = false
    @State private var permisoCamara: Bool?
    @State private var linternaEncendida = false
    @State private var registrando = false
    @State private var mostrandoBusquedaManual = false
    @State private var mostrandoDetalle = false
    @State private var textoBusqueda = ""
    @State private var mensajeError: String?
    @State private var mensajeExito: String?

    private let servicio = ServicioDocumentos()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            camara
                .ignoresSafeArea()

            if permisoCamara == true && !camaraNoDisponible {
                MascaraEscaner()
                    .fill(Color.black.opacity(0.48), style: FillStyle(eoFill: true))
                    .ignoresSafeArea()
                    .allowsHitTesting(false)

                MarcoRecepcionQR()
                    .allowsHitTesting(false)
            }

            VStack(spacing: 0) {
                barraSuperior

                Spacer()

                if let mensajeError {
                    aviso(mensajeError, icono: "exclamationmark.triangle.fill",
                          color: EstiloInstitucional.rojoUrgencia)
                }
                if let mensajeExito {
                    aviso(mensajeExito, icono: "checkmark.circle.fill",
                          color: EstiloInstitucional.verdeClaro)
                }

                if let documento {
                    tarjetaDocumento(documento)
                } else {
                    instruccion
                }
            }
            .padding(.bottom, 10)
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
            await prepararCamara()
            await vistaModelo.cargarDocumentos()
        }
        .onAppear {
            if !camaraNoDisponible && documento == nil {
                escaneando = true
            }
        }
        .onDisappear {
            escaneando = false
            ServicioLinterna.establecer(false)
            linternaEncendida = false
        }
        .alert("Búsqueda manual", isPresented: $mostrandoBusquedaManual) {
            TextField("Folio, UUID o código", text: $textoBusqueda)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
            Button("Buscar") {
                manejarCodigo(textoBusqueda)
                textoBusqueda = ""
            }
            Button("Cancelar", role: .cancel) {
                textoBusqueda = ""
            }
        } message: {
            Text("Escribe el folio o el código que aparece en el documento.")
        }
        .fullScreenCover(isPresented: $mostrandoDetalle) {
            if let documento {
                DetalleDocumentoView(documentoId: documento.id)
            }
        }
    }

    @ViewBuilder
    private var camara: some View {
        if EscannerQRVista.esCompatible && !camaraNoDisponible {
            switch permisoCamara {
            case .some(true):
                EscanerQRContinuo(
                    activo: $escaneando,
                    alEscanear: manejarCodigo,
                    alFallar: { _ in
                        DispatchQueue.main.async {
                            camaraNoDisponible = true
                        }
                    }
                )
            case .some(false):
                camaraAlternativa
            case .none:
                ProgressView()
                    .controlSize(.large)
                    .tint(.white)
            }
        } else {
            camaraAlternativa
        }
    }

    private func prepararCamara() async {
        guard EscannerQRVista.esCompatible else {
            permisoCamara = false
            return
        }
        if AVCaptureDevice.authorizationStatus(for: .video) == .authorized {
            permisoCamara = true
            return
        }
        let concedido = await AVCaptureDevice.requestAccess(for: .video)
        permisoCamara = concedido
        if !concedido {
            camaraNoDisponible = true
        }
    }

    private var camaraAlternativa: some View {
        VStack(spacing: 14) {
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 64, weight: .light))
                .foregroundColor(.white.opacity(0.75))

            Text("La cámara no está disponible")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(.white)

            Text("Puedes identificar el documento con la búsqueda manual por folio o UUID.")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                mostrandoBusquedaManual = true
            } label: {
                Label("Búsqueda manual", systemImage: "keyboard")
                    .font(.system(size: 14, weight: .semibold))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.16))
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
            .buttonStyle(PlainButtonStyle())
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }

    private var barraSuperior: some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Recepción digital")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                Text("Apunta al código QR del documento")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.75))
            }

            Spacer()

            if vistaModelo.estaCargando {
                ProgressView()
                    .tint(.white)
            }

            if ServicioLinterna.disponible {
                botonBarra(
                    icono: linternaEncendida ? "flashlight.on.fill" : "flashlight.off.fill",
                    activo: linternaEncendida
                ) {
                    linternaEncendida.toggle()
                    if !ServicioLinterna.establecer(linternaEncendida) {
                        linternaEncendida = false
                    }
                }
            }

            botonBarra(icono: "keyboard") {
                mostrandoBusquedaManual = true
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 22)
        .background(
            LinearGradient(
                colors: [Color.black.opacity(0.7), Color.black.opacity(0.0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea(edges: .top)
        )
    }

    private func botonBarra(
        icono: String,
        activo: Bool = false,
        accion: @escaping () -> Void
    ) -> some View {
        Button(action: accion) {
            Image(systemName: icono)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 40, height: 40)
                .background(activo ? EstiloInstitucional.dorado : Color.black.opacity(0.35))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(Color.white.opacity(0.25), lineWidth: 1)
                )
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var instruccion: some View {
        VStack(spacing: 8) {
            if vistaModelo.estaCargando {
                Text("Cargando documentos…")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white.opacity(0.85))
            } else {
                Text("Escanea el código QR del documento")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                Text("El código aparece en el acuse o en la ficha del documento.")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(Color.black.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .padding(.horizontal, 24)
        .padding(.bottom, 22)
    }

    private func tarjetaDocumento(_ documento: Documento) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Documento encontrado", systemImage: "checkmark.seal.fill")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Spacer()
                Text(documento.estado.rawValue.replacingOccurrences(of: "_", with: " "))
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(EstiloInstitucional.verdeSuave)
                    .clipShape(Capsule())
            }

            Text(documento.folio.isEmpty ? "Sin folio" : documento.folio)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)

            Text(documento.asunto.isEmpty ? "Sin asunto" : documento.asunto)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineLimit(3)

            Divider()

            fila("Remitente", documento.remitente)
            fila("Área destino", documento.areaDestino.isEmpty
                 ? documento.areaActual
                 : documento.areaDestino)
            fila("Tipo", documento.tipo.rawValue.replacingOccurrences(of: "_", with: " "))

            HStack(spacing: 8) {
                etiqueta(documento.urgencia.etiqueta,
                         color: colorUrgencia(documento.urgencia))
                etiqueta(documento.clasificacion.etiqueta,
                         color: EstiloInstitucional.dorado)
                Spacer()
            }

            HStack(spacing: 10) {
                Button {
                    reiniciarEscaner()
                } label: {
                    Label("Reintentar", systemImage: "arrow.clockwise")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(EstiloInstitucional.fondo)
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(PlainButtonStyle())

                Button {
                    Task { await registrarRecepcion(documento) }
                } label: {
                    HStack(spacing: 6) {
                        if registrando {
                            ProgressView().tint(.white)
                        } else {
                            Image(systemName: "checkmark.circle.fill")
                            Text("Confirmar recepción")
                                .font(.system(size: 14, weight: .bold))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(EstiloInstitucional.verdePrincipal)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(PlainButtonStyle())
                .disabled(registrando)
            }

            Button {
                mostrandoDetalle = true
            } label: {
                HStack(spacing: 4) {
                    Text("Ver detalle completo")
                        .font(.system(size: 13, weight: .semibold))
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 11, weight: .bold))
                }
                .foregroundColor(EstiloInstitucional.verdePrincipal)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(color: Color.black.opacity(0.25), radius: 16, y: 6)
        .padding(.horizontal, 16)
    }

    private func fila(_ titulo: String, _ valor: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(titulo)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .frame(width: 84, alignment: .leading)
            Text(valor.isEmpty ? "—" : valor)
                .font(.system(size: 12))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func etiqueta(_ texto: String, color: Color) -> some View {
        Text(texto.uppercased())
            .font(.system(size: 9, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }

    private func colorUrgencia(_ urgencia: UrgenciaDocumento) -> Color {
        switch urgencia {
        case .ordinario: return EstiloInstitucional.verdePrincipal
        case .urgente: return Color(red: 0.96, green: 0.65, blue: 0.14)
        case .extraurgente: return EstiloInstitucional.rojoUrgencia
        }
    }

    private func aviso(_ texto: String, icono: String, color: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icono)
            Text(texto)
                .font(.system(size: 12, weight: .semibold))
                .lineLimit(3)
            Spacer(minLength: 0)
        }
        .foregroundColor(.white)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(color)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 24)
        .padding(.bottom, 8)
    }

    private func normalizarCodigo(_ valor: String) -> String {
        var texto = valor.trimmingCharacters(in: .whitespacesAndNewlines)

        for prefijo in ["GESTIONN::", "GESTIONN:", "GESTION::", "GESTION:"] {
            if let rango = texto.range(of: prefijo) {
                texto = String(texto[rango.upperBound...])
                break
            }
        }

        if let rango = texto.range(of: "uuid=") {
            texto = String(texto[rango.upperBound...])
            if let fin = texto.firstIndex(where: { $0 == "&" || $0 == "#" }) {
                texto = String(texto[..<fin])
            }
        }

        if let url = URL(string: texto),
           url.scheme != nil,
           let ultimo = url.pathComponents.last,
           !ultimo.isEmpty {
            texto = ultimo
        }

        return texto.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
    }

    private func manejarCodigo(_ valor: String) {
        let clave = normalizarCodigo(valor)
        guard !clave.isEmpty else { return }

        if vistaModelo.estaCargando {
            avisarError("Espera un momento: aún se están cargando los documentos.")
            reiniciarEscanerConRetraso()
            return
        }

        let encontrado = vistaModelo.buscar(clave)
        guard let encontrado else {
            avisarError("El código \(clave) no corresponde a un documento disponible.")
            reiniciarEscanerConRetraso()
            return
        }

        documento = encontrado
        escaneando = false
        mensajeError = nil
        mensajeExito = nil
        ServicioLinterna.establecer(false)
        linternaEncendida = false
    }

    private func reiniciarEscaner() {
        documento = nil
        mensajeError = nil
        mensajeExito = nil
        escaneando = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            escaneando = true
        }
    }

    private func reiniciarEscanerConRetraso() {
        guard documento == nil else { return }
        escaneando = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            if documento == nil { escaneando = true }
        }
    }

    private func registrarRecepcion(_ documento: Documento) async {
        registrando = true
        defer { registrando = false }

        do {
            _ = try await servicio.recibir(documentoId: documento.id)
            vistaModelo.marcarRecibido(id: documento.id)
            if let actualizado = vistaModelo.documentos.first(where: { $0.id == documento.id }) {
                self.documento = actualizado
            }
            mensajeError = nil
            mensajeExito = "Recepción registrada correctamente."
        } catch {
            mensajeExito = nil
            avisarError((error as? LocalizedError)?.errorDescription
                        ?? "No se pudo registrar la recepción.")
        }
    }

    private func avisarError(_ texto: String) {
        mensajeExito = nil
        mensajeError = texto
        Task {
            try? await Task.sleep(for: .seconds(5))
            if mensajeError == texto {
                mensajeError = nil
            }
        }
    }
}

private struct MascaraEscaner: Shape {

    func path(in rect: CGRect) -> Path {
        var camino = Path(rect)
        let lado = min(rect.width, rect.height) * 0.68
        let marco = CGRect(
            x: rect.midX - lado / 2,
            y: rect.midY - lado / 2,
            width: lado,
            height: lado
        )
        camino.addRoundedRect(in: marco, cornerSize: CGSize(width: 24, height: 24))
        return camino
    }
}

private struct MarcoRecepcionQR: View {

    var body: some View {
        GeometryReader { proxy in
            let lado = min(proxy.size.width, proxy.size.height) * 0.68
            ZStack {
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color.white.opacity(0.4), lineWidth: 1)
                    .frame(width: lado, height: lado)

                EsquinasRecepcion()
                    .stroke(
                        EstiloInstitucional.dorado,
                        style: StrokeStyle(lineWidth: 5, lineCap: .round)
                    )
                    .frame(width: lado, height: lado)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .offset(y: -proxy.safeAreaInsets.top / 2)
        }
        .ignoresSafeArea()
    }
}

private struct EsquinasRecepcion: Shape {

    func path(in rect: CGRect) -> Path {
        let largo = rect.width * 0.18
        var camino = Path()

        camino.move(to: CGPoint(x: rect.minX, y: rect.minY + largo))
        camino.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        camino.addLine(to: CGPoint(x: rect.minX + largo, y: rect.minY))

        camino.move(to: CGPoint(x: rect.maxX - largo, y: rect.minY))
        camino.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        camino.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + largo))

        camino.move(to: CGPoint(x: rect.maxX, y: rect.maxY - largo))
        camino.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        camino.addLine(to: CGPoint(x: rect.maxX - largo, y: rect.maxY))

        camino.move(to: CGPoint(x: rect.minX + largo, y: rect.maxY))
        camino.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        camino.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - largo))

        return camino
    }
}

@MainActor
final class RecepcionViewModel: ObservableObject {

    @Published private(set) var documentos: [Documento] = []
    @Published private(set) var estaCargando = false
    @Published private(set) var errorCarga: String?

    private let servicio = ServicioDocumentos()

    init() {}

    func cargarDocumentos() async {
        estaCargando = true
        defer { estaCargando = false }
        do {
            documentos = try await servicio.listar()
            errorCarga = nil
        } catch {
            documentos = []
            errorCarga = "No se pudieron cargar los documentos."
        }
    }

    func buscar(_ clave: String) -> Documento? {
        let buscada = clave.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !buscada.isEmpty else { return nil }

        return documentos.first { documento in
            let candidatos = [
                documento.uuid,
                documento.folio,
                String(documento.id)
            ]
            return candidatos.contains {
                $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == buscada
            }
        }
    }

    func marcarRecibido(id: Int) {
        guard let indice = documentos.firstIndex(where: { $0.id == id }) else { return }
        documentos[indice].estado = .recibido
    }
}
