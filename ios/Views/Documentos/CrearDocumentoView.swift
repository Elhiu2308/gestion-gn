import SwiftUI
import UIKit

struct CrearDocumentoView: View {

    @Environment(\.dismiss) private var dismiss
    @StateObject private var vistaModelo = CrearDocumentoViewModel()

    let origen: OrigenDocumento

    var documento: Documento? = nil
    var onCreada: (Documento) -> Void = { _ in }

    @State private var mostrandoVistaPrevia = false

    @State private var dgDestino: [NivelOrganizacional] = []
    @State private var dirDestino: [NivelOrganizacional] = []
    @State private var subDestino: [NivelOrganizacional] = []
    @State private var deptDestino: [NivelOrganizacional] = []
    @State private var selDgDestino: Int? = nil
    @State private var selDirDestino: Int? = nil
    @State private var selSubDestino: Int? = nil
    @State private var selDeptDestino: Int? = nil
    @State private var personasDestino: [PersonaCatalogo] = []
    @State private var selPersonaDestino: Int? = nil
    @State private var cargandoOrg = false

    @State private var orgOrigenDg: Int? = nil
    @State private var orgOrigenDir: Int? = nil
    @State private var orgOrigenSub: Int? = nil
    @State private var orgOrigenDept: Int? = nil
    @State private var textoAreaOrigen: String = ""

    var body: some View {
        ZStack {
            EstiloInstitucional.fondo.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    encabezado
                    seccionVistaPrevia
                    seccionInformacion
                    if !vistaModelo.esEdicion {
                        seccionTurnar
                    }
                    seccionRemitente
                    seccionComplementarios
                    botonGuardar

                    Text("GestióN · Gestión Documental")
                        .font(.system(size: 11))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                }
                .foregroundStyle(.black)
                .tint(.black)
                .padding(.horizontal, 20)
                .padding(.top, 12)
            }
        }
        .navigationTitle(vistaModelo.esEdicion ? "Editar Documento" : "Nuevo Documento")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if let documento {
                vistaModelo.prepararEdicion(documento)
            } else {
                vistaModelo.preparar(origen: origen)
            }
            await cargarOrganigrama()
        }
        .sheet(isPresented: $mostrandoVistaPrevia) {
            if let url = vistaModelo.urlVistaPrevia {
                PantallaVistaPrevia(url: url)
            }
        }
        .alert(vistaModelo.errorTexto ?? "", isPresented: Binding(
            get: { vistaModelo.errorTexto != nil },
            set: { if !$0 { vistaModelo.limpiarError() } }
        )) {
            Button("Entendido", role: .cancel) {}
        }
    }

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "doc.text.viewfinder")
                    .font(.system(size: 13))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)

                Text("GESTIÓN DOCUMENTAL")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .tracking(0.6)
            }

            Text(vistaModelo.esEdicion ? "Editar Documento" : "Nuevo Documento")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.black)

            Text(
                vistaModelo.esEdicion
                    ? "Actualiza la información registrada del documento."
                    : "Registra y turna un documento oficial."
            )
            .font(.system(size: 14))
            .foregroundColor(.black)
        }
    }

    private var seccionVistaPrevia: some View {
        SeccionDocumento(titulo: "Vista previa del documento") {
            if vistaModelo.tienePrevia {
                if !vistaModelo.paginas.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(
                                Array(vistaModelo.paginas.enumerated()),
                                id: \.offset
                            ) { indice, imagen in

                                VStack(spacing: 4) {
                                    Image(uiImage: imagen)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 96, height: 128)
                                        .clipShape(
                                            RoundedRectangle(cornerRadius: 10)
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10)
                                                .stroke(
                                                    EstiloInstitucional.bordeSuave,
                                                    lineWidth: 1
                                                )
                                        )

                                    Text("Página \(indice + 1)")
                                        .font(.system(size: 11))
                                        .foregroundColor(.black)
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }
                } else {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(EstiloInstitucional.verdeFondoIcono)
                                .frame(width: 56, height: 56)

                            Image(systemName: vistaModelo.iconoArchivo)
                                .font(.system(size: 24))
                                .foregroundColor(EstiloInstitucional.verdePrincipal)
                        }

                        VStack(alignment: .leading, spacing: 3) {
                            Text(vistaModelo.nombreArchivo)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.black)
                                .lineLimit(1)

                            Text("Archivo listo para adjuntar.")
                                .font(.system(size: 12))
                                .foregroundColor(.black)
                        }

                        Spacer()
                    }
                }

                Button {
                    mostrandoVistaPrevia = true
                } label: {
                    Label(
                        "Toca para ver el PDF completo",
                        systemImage: "eye.fill"
                    )
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(EstiloInstitucional.verdeFondoIcono)
                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            } else {
                HStack(spacing: 10) {
                    Image(systemName: "doc")
                        .foregroundColor(.black)

                    Text("Sin archivo adjunto. Puedes registrar el documento con sus datos.")
                        .font(.system(size: 13))
                        .foregroundColor(.black)
                }
            }
        }
    }

    private var seccionInformacion: some View {
        SeccionDocumento(titulo: "Información del documento") {

            CampoMenu(
                etiqueta: "Tipo de documento",
                seleccion: vistaModelo.tipo.etiqueta,
                opciones: TipoDocumento.allCases.map(\.etiqueta),
                textoVacio: "Seleccionar tipo"
            ) { etiqueta in
                if let tipo = TipoDocumento.allCases.first(
                    where: { $0.etiqueta == etiqueta }
                ) {
                    vistaModelo.tipo = tipo
                }
            }

            CampoTexto(
                etiqueta: "Folio",
                texto: $vistaModelo.folio,
                marcador: "Sin folio"
            )

            CampoTextoArea(
                etiqueta: "Asunto / Descripción",
                texto: $vistaModelo.asunto,
                marcador: "Describe el asunto del documento"
            )

            VStack(alignment: .leading, spacing: 6) {
                Text("Fecha del documento")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.black)

                DatePicker(
                    "",
                    selection: $vistaModelo.fechaDocumento,
                    displayedComponents: .date
                )
                .labelsHidden()
                .datePickerStyle(.compact)
                .tint(.black)
            }
        }
    }

    private var seccionTurnar: some View {
        SeccionDocumento(
            titulo: "Turnar a (para)",
            subtitulo: "Selecciona el destino siguiendo la estructura organizacional real."
        ) {
            if cargandoOrg {
                ProgressView()
                    .scaleEffect(0.9)
                    .tint(.black)
            }

            CampoMenu(
                etiqueta: "1. Área",
                seleccion: nombreSeleccionado(
                    dirDestino,
                    id: selDirDestino
                ),
                opciones: dirDestino.map(\.nombre),
                textoVacio: "Seleccionar Área"
            ) { nombre in

                if let nivel = dirDestino.first(
                    where: { $0.nombre == nombre }
                ) {
                    selDirDestino = nivel.id
                    selSubDestino = nil
                    selDeptDestino = nil
                    selPersonaDestino = nil

                    subDestino = []
                    deptDestino = []
                    personasDestino = []

                    Task {
                        await cargarSubdireccionesDestino(dirId: nivel.id)
                    }
                }
            }

            CampoMenu(
                etiqueta: "2. Subdirección",
                seleccion: nombreSeleccionado(
                    subDestino,
                    id: selSubDestino
                ),
                opciones: subDestino.map(\.nombre),
                textoVacio: selDirDestino == nil
                    ? "Primero seleccione el Área"
                    : "Seleccionar Subdirección",
                habilitado: selDirDestino != nil
            ) { nombre in

                if let nivel = subDestino.first(
                    where: { $0.nombre == nombre }
                ) {
                    selSubDestino = nivel.id
                    selDeptDestino = nil
                    selPersonaDestino = nil

                    deptDestino = []
                    personasDestino = []

                    Task {
                        await cargarDepartamentosDestino(subId: nivel.id)

                        await cargarPersonasDestino(
                            filtros: ["subdireccion": "\(nivel.id)"]
                        )
                    }
                }
            }

            if !deptDestino.isEmpty {
                CampoMenu(
                    etiqueta: "3. Departamento",
                    seleccion: nombreSeleccionado(
                        deptDestino,
                        id: selDeptDestino
                    ),
                    opciones: deptDestino.map(\.nombre),
                    textoVacio: "Seleccionar Departamento",
                    habilitado: selSubDestino != nil
                ) { nombre in

                    if let nivel = deptDestino.first(
                        where: { $0.nombre == nombre }
                    ) {
                        selDeptDestino = nivel.id
                        selPersonaDestino = nil
                        personasDestino = []

                        Task {
                            await cargarPersonasDestino(
                                filtros: [
                                    "subdireccion": "\(selSubDestino ?? 0)"
                                ]
                            )
                        }
                    }
                }
            }

            CampoMenu(
                etiqueta: "Persona",
                seleccion: nombrePersonaSeleccionada,
                opciones: personasDestino.map(\.nombre),
                textoVacio: selDirDestino == nil
                    ? "Primero seleccione el Área"
                    : "Seleccionar Persona",
                habilitado: selDirDestino != nil
            ) { nombre in

                if let persona = personasDestino.first(
                    where: { $0.nombre == nombre }
                ) {
                    selPersonaDestino = persona.id
                    vistaModelo.persona = persona.nombre
                }
            }

            if selDirDestino == nil || selPersonaDestino == nil {
                Text("Seleccione el Área, la Subdirección y la Persona.")
                    .font(.system(size: 12))
                    .foregroundColor(.black)
            }
        }
    }

    private var seccionRemitente: some View {
        SeccionDocumento(titulo: "Remitente (automático)") {
            FilaValor(
                etiqueta: "Área",
                valor: vistaModelo.remitenteArea
            )

            FilaValor(
                etiqueta: "Subdirección",
                valor: vistaModelo.remitenteSubdireccion
            )

            FilaValor(
                etiqueta: "Persona",
                valor: vistaModelo.remitentePersona
            )
        }
    }

    private var seccionComplementarios: some View {
        SeccionDocumento(titulo: "Datos complementarios") {

            CampoTexto(
                etiqueta: "Referencia",
                texto: $vistaModelo.referencia,
                marcador: "Referencia del documento"
            )

            CampoTexto(
                etiqueta: "Número de anexos",
                texto: $vistaModelo.anexos,
                marcador: "0",
                teclado: .numberPad
            )

            CampoMenu(
                etiqueta: "Clasificación",
                seleccion: vistaModelo.clasificacion.etiqueta,
                opciones: ClasificacionDocumento.allCases.map(\.etiqueta),
                textoVacio: "Seleccionar clasificación"
            ) { etiqueta in

                if let c = ClasificacionDocumento.allCases.first(
                    where: { $0.etiqueta == etiqueta }
                ) {
                    vistaModelo.clasificacion = c
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Plazo de atención")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.black)

                DatePicker(
                    "",
                    selection: $vistaModelo.plazo,
                    displayedComponents: [.date, .hourAndMinute]
                )
                .labelsHidden()
                .datePickerStyle(.compact)
                .tint(.black)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Nivel de urgencia")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.black)

                HStack(spacing: 8) {
                    ForEach(
                        UrgenciaDocumento.allCases,
                        id: \.self
                    ) { nivel in

                        BotonUrgencia(
                            titulo: nivel.etiqueta,
                            seleccionado: vistaModelo.urgencia == nivel
                        ) {
                            vistaModelo.urgencia = nivel
                        }
                    }
                }
            }

            CampoTextoArea(
                etiqueta: "Notas u observaciones",
                texto: $vistaModelo.notas,
                marcador: "Escribe notas u observaciones"
            )
        }
    }

    private var botonGuardar: some View {
        Button {
            Task {

                vistaModelo.area = textoAreaOrigen

                let personaSeleccionada = personasDestino.first(
                    where: { $0.id == selPersonaDestino }
                )

                if let personaSeleccionada {
                    vistaModelo.persona = personaSeleccionada.nombre
                }

                if let doc = await vistaModelo.guardar(
                    orgOrigen: (
                        orgOrigenDg,
                        orgOrigenDir,
                        orgOrigenSub,
                        orgOrigenDept
                    ),
                    orgDestino: (
                        selDgDestino ?? dgDestino.first?.id,
                        selDirDestino,
                        selSubDestino,
                        selDeptDestino
                    ),
                    personaId: selPersonaDestino,
                    personaDestino: personaSeleccionada,
                    areaOrigenTexto: textoAreaOrigen
                ) {
                    onCreada(doc)
                    dismiss()
                }
            }
        } label: {

            HStack {
                if vistaModelo.estaCargando {
                    ProgressView()
                        .tint(.white)
                } else {
                    Image(systemName: "square.and.arrow.down.fill")

                    Text(vistaModelo.esEdicion ? "Guardar cambios" : "Guardar documento")
                        .font(.system(size: 16, weight: .bold))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(EstiloInstitucional.verdePrincipal)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(
                color: EstiloInstitucional.verdePrincipal.opacity(0.3),
                radius: 8,
                y: 4
            )
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(vistaModelo.estaCargando)
    }

    private func cargarOrganigrama() async {
        cargandoOrg = true
        defer { cargandoOrg = false }

        do {

            let perfil = try await RepositorioPerfil.shared.me()

            orgOrigenDg = perfil.idDireccionGeneral
            orgOrigenDir = perfil.idDireccion
            orgOrigenSub = perfil.idSubdireccion
            textoAreaOrigen = perfil.area

            let dgs = try await RepositorioOrganigrama.shared
                .direccionesGeneralesPadre()

            if let dg = dgs.first {
                orgOrigenDg = orgOrigenDg ?? dg.id

                dgDestino = [dg]
                selDgDestino = dg.id

                dirDestino = try await RepositorioOrganigrama.shared
                    .hijos(.direccion, padreId: dg.id)
            }

        } catch {
            vistaModelo.errorTexto =
                "No se pudo cargar la estructura organizacional."
        }
    }

    private func cargarSubdireccionesDestino(dirId: Int) async {
        do {
            subDestino = try await RepositorioOrganigrama.shared
                .hijos(.subdireccion, padreId: dirId)
        } catch {
            subDestino = []
        }
    }

    private func cargarDepartamentosDestino(subId: Int) async {
        do {
            deptDestino = try await RepositorioOrganigrama.shared
                .hijos(.departamento, padreId: subId)
        } catch {
            deptDestino = []
        }
    }

    private func cargarPersonasDestino(filtros: [String: String]) async {
        do {
            personasDestino = try await RepositorioOrganigrama.shared
                .personas(filtros: filtros)
        } catch {
            personasDestino = []
        }
    }

    private func nombreSeleccionado(
        _ lista: [NivelOrganizacional],
        id: Int?
    ) -> String {

        guard let id,
              let nivel = lista.first(where: { $0.id == id })
        else {
            return ""
        }

        return nivel.nombre
    }

    private var nombrePersonaSeleccionada: String {
        guard let id = selPersonaDestino,
              let p = personasDestino.first(where: { $0.id == id })
        else {
            return ""
        }

        return p.nombre
    }
}

@MainActor
final class CrearDocumentoViewModel: ObservableObject {

    @Published var tipo: TipoDocumento = .oficio
    @Published var folio = ""
    @Published var asunto = ""
    @Published var fechaDocumento = Date()
    @Published private(set) var documentoEnEdicion: Documento?

    var esEdicion: Bool { documentoEnEdicion != nil }

    @Published var area = ""
    @Published var subdireccion = ""
    @Published var persona = ""

    @Published var referencia = ""
    @Published var anexos = ""
    @Published var clasificacion: ClasificacionDocumento = .publico
    @Published var plazo = Date()
    @Published var urgencia: UrgenciaDocumento = .ordinario
    @Published var notas = ""

    @Published var estaCargando = false
    @Published var errorTexto: String?

    @Published private(set) var paginas: [UIImage] = []
    @Published private(set) var urlArchivo: URL?
    @Published private(set) var rutaPDF: URL?

    let remitenteArea = ServicioKeychain.areaUsuario()

    let remitenteSubdireccion =
        UserDefaults.standard.string(forKey: "subdireccion") ?? ""

    let remitentePersona = {
        let completo =
            UserDefaults.standard.string(forKey: "nombre_completo") ?? ""

        return completo.isEmpty
            ? ServicioKeychain.nombreUsuario()
            : completo
    }()

    private let servicio = ServicioDocumentos()

    var tienePrevia: Bool {
        !paginas.isEmpty || urlArchivo != nil || rutaPDF != nil
    }

    var urlVistaPrevia: URL? {
        rutaPDF ?? urlArchivo
    }

    var nombreArchivo: String {
        if let urlArchivo {
            return urlArchivo.lastPathComponent
        }

        if let rutaPDF {
            return rutaPDF.lastPathComponent
        }

        return "Documento"
    }

    var iconoArchivo: String {
        guard let url = urlVistaPrevia else {
            return "doc"
        }

        return url.pathExtension.lowercased() == "pdf"
            ? "doc.richtext.fill"
            : "photo.fill"
    }

    func limpiarError() {
        errorTexto = nil
    }

    func prepararEdicion(_ documento: Documento) {
        documentoEnEdicion = documento
        tipo = documento.tipo
        folio = documento.folio
        asunto = documento.asunto
        referencia = documento.referencia
        anexos = documento.numAnexos > 0 ? String(documento.numAnexos) : ""
        clasificacion = documento.clasificacion
        urgencia = documento.urgencia
        notas = documento.notas
        area = documento.areaDestino
        persona = documento.destinatario

        let formato = DateFormatter()
        formato.dateFormat = "yyyy-MM-dd"
        if let fecha = formato.date(from: documento.fechaDocumento) {
            fechaDocumento = fecha
        }
        if let limite = formato.date(from: documento.fechaLimite) {
            plazo = limite
        }
    }

    func preparar(origen: OrigenDocumento) {
        guard paginas.isEmpty,
              urlArchivo == nil,
              rutaPDF == nil
        else {
            return
        }

        switch origen {
        case .ninguno:
            break

        case .paginas(let imagenes):
            paginas = imagenes

            rutaPDF = ServicioEscaner.guardarPDF(
                paginas: imagenes,
                nombre: ServicioEscaner.nombreUnico()
            )

        case .imagen(let imagen):
            paginas = [imagen]

            rutaPDF = ServicioEscaner.guardarPDF(
                paginas: [imagen],
                nombre: ServicioEscaner.nombreUnico(prefijo: "imagen")
            )

        case .archivo(let url):
            urlArchivo = url
        }
    }

    func guardar(
        orgOrigen: (Int?, Int?, Int?, Int?),
        orgDestino: (Int?, Int?, Int?, Int?),
        personaId: Int?,
        personaDestino: PersonaCatalogo?,
        areaOrigenTexto: String
    ) async -> Documento? {

        guard !asunto.trimmingCharacters(
            in: .whitespacesAndNewlines
        ).isEmpty else {

            errorTexto = "El asunto del documento es obligatorio."
            return nil
        }

        estaCargando = true
        defer { estaCargando = false }

        errorTexto = nil

        let formatoFecha = DateFormatter()
        formatoFecha.dateFormat = "yyyy-MM-dd"

        if let documentoEnEdicion {
            let cuerpo: [String: Any] = [
                "tipo": tipo.rawValue,
                "folio": folio,
                "asunto": asunto,
                "referencia": referencia,
                "num_anexos": Int(anexos) ?? 0,
                "urgencia": urgencia.rawValue,
                "clasificacion": clasificacion.rawValue,
                "notas": notas,
                "fecha_documento": formatoFecha.string(from: fechaDocumento),
                "fecha_limite": formatoFecha.string(from: plazo)
            ]

            do {
                return try await servicio.actualizar(
                    id: documentoEnEdicion.id,
                    cuerpo: cuerpo
                )
            } catch {
                errorTexto = "No se pudieron guardar los cambios. Verifica la conexión con el servidor."
                return nil
            }
        }

        let datos = DatosDocumento(
            tipo: tipo,
            folio: folio,
            asunto: asunto,
            destinatario: persona,
            idDestinatario: personaId,
            referencia: referencia,
            numAnexos: Int(anexos) ?? 0,
            fechaDocumento: formatoFecha.string(from: fechaDocumento),
            fechaLimite: formatoFecha.string(from: plazo),
            urgencia: urgencia,
            clasificacion: clasificacion,
            notas: notas,
            areaOrigen: areaOrigenTexto.isEmpty
                ? remitenteArea
                : areaOrigenTexto,
            areaDestino: area,
            orgDgOrigenId: orgOrigen.0,
            orgDirOrigenId: orgOrigen.1,
            orgSubOrigenId: orgOrigen.2,
            orgDeptOrigenId: orgOrigen.3,
            orgDgDestinoId: orgDestino.0,
            orgDirDestinoId: orgDestino.1,
            orgSubDestinoId: orgDestino.2,
            orgDeptDestinoId: orgDestino.3
        )

        do {
            let documento = try await servicio.crear(datos: datos)

            await notificarAsignacionAlGuardar(
                documento: documento,
                persona: personaDestino,
                orgDestino: orgDestino
            )

            if let rutaPDF {
                try? await servicio.subirArchivo(
                    documentoId: documento.id,
                    url: rutaPDF,
                    nombre: rutaPDF.lastPathComponent,
                    mimeType: "application/pdf"
                )

            } else if let urlArchivo {

                let mime = urlArchivo.pathExtension.lowercased() == "pdf"
                    ? "application/pdf"
                    : "image/\(urlArchivo.pathExtension.lowercased())"

                try? await servicio.subirArchivo(
                    documentoId: documento.id,
                    url: urlArchivo,
                    nombre: urlArchivo.lastPathComponent,
                    mimeType: mime
                )
            }

            return documento

        } catch {
            errorTexto =
                "No se pudo guardar el documento. Verifica la conexión con el servidor."

            return nil
        }
    }

    private func notificarAsignacionAlGuardar(
        documento: Documento,
        persona: PersonaCatalogo?,
        orgDestino: (Int?, Int?, Int?, Int?)
    ) async {

        let estadosIniciales: Set<EstadoDocumento> = [
            .recibido,
            .pendienteTurno,
            .pendienteAsignacion,
            .registrado,
            .pendiente
        ]

        guard estadosIniciales.contains(documento.estado) else {
            return
        }

        guard let destino = destinoAsignacion(
            persona: persona,
            orgDestino: orgDestino
        ) else {
            return
        }

        _ = try? await servicio.turnar(
            documentoId: documento.id,
            opcionTipo: destino.tipo,
            opcionId: destino.opcionId,
            usuarioDestinoId: persona?.idUsuario ?? persona?.id,
            comentario: "Turnado al registrar el documento"
        )
    }

    private func destinoAsignacion(
        persona: PersonaCatalogo?,
        orgDestino: (Int?, Int?, Int?, Int?)
    ) -> (tipo: String, opcionId: Int)? {

        if let persona {

            if let id = persona.idDepartamento {
                return ("departamento", id)
            }

            if let id = persona.idSubdireccion {
                return ("subdireccion", id)
            }

            if let id = persona.idDireccion {
                return ("direccion", id)
            }

            let usuarioId = persona.idUsuario ?? persona.id
            if usuarioId > 0 {
                return ("usuario", usuarioId)
            }
        }

        if let id = orgDestino.3 {
            return ("departamento", id)
        }

        if let id = orgDestino.2 {
            return ("subdireccion", id)
        }

        if let id = orgDestino.1 {
            return ("direccion", id)
        }

        return nil
    }
}

struct SeccionDocumento<Contenido: View>: View {

    let titulo: String
    var subtitulo: String? = nil

    @ViewBuilder let contenido: () -> Contenido

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {

            VStack(alignment: .leading, spacing: 3) {
                Text(titulo)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.black)

                if let subtitulo {
                    Text(subtitulo)
                        .font(.system(size: 12))
                        .foregroundColor(.black)
                }
            }

            VStack(alignment: .leading, spacing: 14) {
                contenido()
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .foregroundStyle(.black)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(
                    EstiloInstitucional.bordeSuave,
                    lineWidth: 1
                )
        )
        .shadow(
            color: Color.black.opacity(0.03),
            radius: 4,
            y: 2
        )
    }
}

private struct CampoTexto: View {

    let etiqueta: String
    @Binding var texto: String

    var marcador: String = ""
    var teclado: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {

            Text(etiqueta)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.black)

            TextField(marcador, text: $texto)
                .keyboardType(teclado)
                .foregroundColor(.black)
                .tint(.black)
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .background(
                    Color(red: 0.97, green: 0.97, blue: 0.98)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            EstiloInstitucional.bordeSuave,
                            lineWidth: 1
                        )
                )
        }
    }
}

private struct CampoTextoArea: View {

    let etiqueta: String
    @Binding var texto: String

    var marcador: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {

            Text(etiqueta)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.black)

            TextField(
                marcador,
                text: $texto,
                axis: .vertical
            )
            .lineLimit(3...6)
            .foregroundColor(.black)
            .tint(.black)
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(
                Color(red: 0.97, green: 0.97, blue: 0.98)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        EstiloInstitucional.bordeSuave,
                        lineWidth: 1
                    )
            )
        }
    }
}

private struct CampoMenu: View {

    let etiqueta: String
    let seleccion: String
    let opciones: [String]

    var textoVacio: String = "Seleccionar"
    var habilitado: Bool = true

    let onSeleccionar: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {

            Text(etiqueta)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.black)

            Menu {
                if opciones.isEmpty {
                    Text("Sin opciones disponibles")
                        .foregroundColor(.black)
                } else {
                    ForEach(opciones, id: \.self) { opcion in
                        Button(opcion) {
                            onSeleccionar(opcion)
                        }
                    }
                }
            } label: {

                HStack {
                    Text(
                        seleccion.isEmpty
                            ? textoVacio
                            : seleccion
                    )
                    .font(.system(size: 15))
                    .foregroundColor(.black)
                    .lineLimit(1)

                    Spacer()

                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.black)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity)
                .background(
                    Color(red: 0.97, green: 0.97, blue: 0.98)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            EstiloInstitucional.bordeSuave,
                            lineWidth: 1
                        )
                )
            }
            .tint(.black)
            .disabled(!habilitado)
            .opacity(habilitado ? 1 : 0.55)
        }
    }
}

private struct BotonUrgencia: View {

    let titulo: String
    let seleccionado: Bool
    let accion: () -> Void

    private var color: Color {
        switch titulo {
        case "Urgente":
            return .orange

        case "Extraurgente":
            return EstiloInstitucional.rojoUrgencia

        default:
            return EstiloInstitucional.verdePrincipal
        }
    }

    var body: some View {
        Button(action: accion) {

            Text(titulo)
                .font(.system(size: 13, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    seleccionado
                        ? color
                        : Color(red: 0.97, green: 0.97, blue: 0.98)
                )
                .foregroundColor(
                    seleccionado ? .white : .black
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            seleccionado
                                ? color
                                : EstiloInstitucional.bordeSuave,
                            lineWidth: 1
                        )
                )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private struct FilaValor: View {

    let etiqueta: String
    let valor: String

    var body: some View {
        HStack(alignment: .top) {

            Text(etiqueta)
                .font(.system(size: 13))
                .foregroundColor(.black)

            Spacer(minLength: 16)

            Text(valor.isEmpty ? "—" : valor)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.black)
                .multilineTextAlignment(.trailing)
        }
    }
}
