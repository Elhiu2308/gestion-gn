import SwiftUI

struct MasView: View {

    @State private var mostrandoServidor = false

    var body: some View {
        NavigationStack {
            ZStack {
                EstiloInstitucional.fondo.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        encabezado

                        TarjetaModulo(
                            titulo: "Expedientes",
                            descripcion: "Consulta y administra los expedientes registrados.",
                            icono: "folder.fill",
                            fondoIcono: EstiloInstitucional.verdeFondoIcono
                        ) {
                            ExpedientesView()
                        }

                        TarjetaModulo(
                            titulo: "Archivo",
                            descripcion: "Documentos archivados e histórico del sistema.",
                            icono: "archivebox.fill",
                            fondoIcono: EstiloInstitucional.doradoFondoIcono
                        ) {
                            ArchivoView()
                        }

                        TarjetaModulo(
                            titulo: "Recepción",
                            descripcion: "Escanea el QR y registra la recepción de documentos.",
                            icono: "qrcode.viewfinder",
                            fondoIcono: EstiloInstitucional.verdeFondoIcono
                        ) {
                            RecepcionQrView()
                        }

                        BotonModulo(
                            titulo: "Servidor",
                            descripcion: "Configura la dirección del backend de GestióN.",
                            icono: "server.rack",
                            fondoIcono: EstiloInstitucional.verdeFondoIcono
                        ) {
                            mostrandoServidor = true
                        }

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
            .sheet(isPresented: $mostrandoServidor) {
                ConfiguracionView()
            }
        }
    }

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "square.grid.2x2.fill")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }
            Text("Más módulos")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(EstiloInstitucional.textoPrincipal)
            Text("Accede a los módulos de expedientes, archivo, recepción y configuración.")
                .font(.system(size: 14))
                .foregroundColor(EstiloInstitucional.textoSecundario)
                .lineSpacing(2)
        }
    }
}

private struct TarjetaModulo<Destino: View>: View {

    let titulo: String
    let descripcion: String
    let icono: String
    let fondoIcono: Color
    @ViewBuilder let destino: () -> Destino

    var body: some View {
        NavigationLink {
            destino()
        } label: {
            ContenidoModulo(
                titulo: titulo,
                descripcion: descripcion,
                icono: icono,
                fondoIcono: fondoIcono
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private struct BotonModulo: View {

    let titulo: String
    let descripcion: String
    let icono: String
    let fondoIcono: Color
    let accion: () -> Void

    var body: some View {
        Button(action: accion) {
            ContenidoModulo(
                titulo: titulo,
                descripcion: descripcion,
                icono: icono,
                fondoIcono: fondoIcono
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private struct ContenidoModulo: View {

    let titulo: String
    let descripcion: String
    let icono: String
    let fondoIcono: Color

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 15)
                    .fill(fondoIcono)
                    .frame(width: 54, height: 54)
                Image(systemName: icono)
                    .font(.system(size: 24))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(titulo)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                Text(descripcion)
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
                .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
    }
}
