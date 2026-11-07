import SwiftUI

struct ConfiguracionView: View {

    @Environment(\.dismiss) private var dismiss

    @State private var urlTexto = ConfiguracionServidor.url
    @State private var automatica = ConfiguracionServidor.automatica
    @State private var estaCargando = false
    @State private var mensajeError: String?
    @State private var mensajeExito: String?

    private let servidorInstalado = "http://192.168.1.161:5000"

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        encabezado
                        seccionDireccion
                        acciones
                        enlaceVolver

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
            .navigationTitle("Servidor")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
            .alert(mensajeError ?? "", isPresented: Binding(
                get: { mensajeError != nil },
                set: { if !$0 { mensajeError = nil } }
            )) {
                Button("Entendido", role: .cancel) {}
            }
            .alert(mensajeExito ?? "", isPresented: Binding(
                get: { mensajeExito != nil },
                set: { if !$0 { mensajeExito = nil } }
            )) {
                Button("Entendido", role: .cancel) {}
            }
        }
    }

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "server.rack")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }
            Text("Servidor de conexión")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
            Text("Configura la dirección del servidor para conectar la aplicación con el sistema web.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineSpacing(2)
        }
    }

    private var seccionDireccion: some View {
        SeccionDocumento(titulo: "Dirección del servidor") {
            Text("Ingresa la dirección IP o el nombre del equipo donde está instalado el servidor de GestióN.")
                .font(.system(size: 13))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineSpacing(2)

            VStack(alignment: .leading, spacing: 6) {
                Text("EJEMPLO DE DIRECCIÓN")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                    .tracking(0.5)

                HStack(spacing: 8) {
                    Image(systemName: "link")
                        .font(.system(size: 13))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                    Text("http://192.168.0.3:5000")
                        .font(.system(size: 14, weight: .medium, design: .monospaced))
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(EstiloInstitucional.verdeFondoIcono)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("URL del servidor")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(EstiloInstitucional.textoSecundario)
                TextField("http://192.168.1.161:5000", text: $urlTexto)
                    .keyboardType(.URL)
                    .autocorrectionDisabled(true)
                    .textInputAutocapitalization(.never)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.97, green: 0.97, blue: 0.98))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                    )
            }

            Toggle("Sincronización automática", isOn: $automatica)
                .tint(EstiloInstitucional.verdePrincipal)
                .font(.system(size: 14))
        }
    }

    private var acciones: some View {
        VStack(spacing: 12) {
            BotonServidor(
                titulo: "Probar conexión",
                icono: "wifi",
                color: EstiloInstitucional.verdeSuave,
                colorTexto: EstiloInstitucional.verdePrincipal,
                cargando: estaCargando
            ) {
                Task { await probarConexion() }
            }

            BotonServidor(
                titulo: "Buscar servidor en la red",
                icono: "dot.radiowaves.left.and.right",
                color: EstiloInstitucional.doradoFondoIcono,
                colorTexto: EstiloInstitucional.verdePrincipal,
                cargando: false
            ) {
                Task { await buscarEnRed() }
            }

            Button {
                Task { await guardar() }
            } label: {
                Label("Guardar y usar este servidor", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 15, weight: .bold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(EstiloInstitucional.verdePrincipal)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: EstiloInstitucional.verdePrincipal.opacity(0.3), radius: 8, y: 4)
            }
            .buttonStyle(PlainButtonStyle())
            .disabled(estaCargando)
        }
    }

    private var enlaceVolver: some View {
        Button {
            urlTexto = servidorInstalado
        } label: {
            Text("Volver al servidor instalado")
                .font(.system(size: 14, weight: .semibold))
                .frame(maxWidth: .infinity)
                .foregroundColor(EstiloInstitucional.verdePrincipal)
                .padding(.vertical, 10)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func probarConexion() async {
        estaCargando = true
        defer { estaCargando = false }
        ConfiguracionServidor.guardar(url: urlTexto, automatica: automatica)
        let responde = await ServicioConfiguracion.sondea(urlTexto)
        if responde {
            mensajeExito = "Conexión correcta con el servidor."
        } else {
            mensajeError = "No se pudo contactar con el servidor en esa dirección."
        }
    }

    private func buscarEnRed() async {
        estaCargando = true
        defer { estaCargando = false }
        if let encontrado = await ServicioConfiguracion().buscarServidorEnRed(partiendoDe: urlTexto) {
            urlTexto = encontrado
            mensajeExito = "Servidor encontrado: \(encontrado)"
        } else {
            mensajeError = "No se encontró ningún servidor de GestióN en la red."
        }
    }

    private func guardar() async {
        estaCargando = true
        defer { estaCargando = false }
        ConfiguracionServidor.guardar(url: urlTexto, automatica: automatica)
        do {
            try await ServicioConfiguracion().sincronizar()
            mensajeExito = "Servidor guardado y sincronización completada."
        } catch {
            mensajeExito = "Servidor guardado. La sincronización se reintentará."
        }
    }
}

private struct BotonServidor: View {

    let titulo: String
    let icono: String
    let color: Color
    let colorTexto: Color
    let cargando: Bool
    let accion: () -> Void

    var body: some View {
        Button(action: accion) {
            HStack {
                if cargando {
                    ProgressView()
                } else {
                    Image(systemName: icono)
                    Text(titulo)
                        .font(.system(size: 15, weight: .semibold))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(color)
            .foregroundColor(colorTexto)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(cargando)
    }
}
