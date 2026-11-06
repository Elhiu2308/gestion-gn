import SwiftUI

/// Pantalla de inicio de sesión de GestióN.
///
/// Si la biometría está activa:
/// - Muestra el acceso rápido con Face ID / Touch ID sin necesidad de escribir credenciales.
/// - Lanza automáticamente el diálogo biométrico al presentarse.
/// - Permite alternar a ingreso con credenciales si se desea.
///
/// Si la biometría no está activa:
/// - Presenta el formulario tradicional de usuario y contraseña.
/// - Incluye el switch institucional para activar biometría al iniciar sesión.
struct LoginView: View {

    @StateObject private var vistaModelo = LoginViewModel()
    @State private var mostrarFormularioCredenciales = false
    @State private var mostrarContrasena = false
    @State private var mostrandoConfiguracion = false
    @State private var mostrarAvisoBiometria = false
    @FocusState private var campoEnfocado: Campo?

    private enum Campo {
        case usuario
        case contrasena
    }

    let onSesionIniciada: () -> Void

    private var primerNombreBiometrico: String {
        let nombre = vistaModelo.nombreUsuarioBiometrico
        return nombre.split(separator: " ").first.map(String.init) ?? nombre
    }

    var body: some View {
        ZStack {
            EstiloInstitucional.fondoLogin

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {

                    // MARK: Identidad Institucional Superior
                    Image("defensa")
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 68)
                        .padding(.horizontal, 24)
                        .padding(.top, 14)

                    // MARK: Presentación de la Aplicación
                    VStack(spacing: 6) {
                        Image("logo_doc")
                            .resizable()
                            .scaledToFit()
                            .frame(height: 36)
                            .padding(.top, 14)

                        Text("Tus expedientes, bajo control")
                            .font(.system(size: 21, weight: .bold))
                            .foregroundColor(EstiloInstitucional.textoPrincipal)
                            .multilineTextAlignment(.center)

                        Text("Organización, seguimiento y trazabilidad documental")
                            .font(.system(size: 13))
                            .foregroundColor(EstiloInstitucional.textoSecundario)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: 300)
                    }
                    .padding(.bottom, 18)

                    // MARK: Tarjeta Principal
                    VStack(spacing: 0) {

                        // Logotipo de la app
                        Image("logo_app")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 76, height: 76)
                            .padding(.top, 4)

                        if vistaModelo.tieneAccesoBiometrico && !mostrarFormularioCredenciales {
                            // =========================================================
                            // MODO BIOMÉTRICO (YA NO HAY NECESIDAD DE INGRESAR CREDENCIALES)
                            // =========================================================
                            VStack(spacing: 6) {
                                Text("¡Hola, \(primerNombreBiometrico)!")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                                    .padding(.top, 8)

                                Text("Acceso rápido con \(vistaModelo.tipoBiometria.nombre)")
                                    .font(.system(size: 13))
                                    .foregroundColor(EstiloInstitucional.textoSecundario)
                                    .multilineTextAlignment(.center)
                                    .padding(.bottom, 18)

                                // Botón principal biométrico
                                Button {
                                    ejecutarLoginBiometrico()
                                } label: {
                                    HStack(spacing: 10) {
                                        if vistaModelo.estaCargando {
                                            ProgressView()
                                                .tint(.white)
                                        } else {
                                            Image(systemName: vistaModelo.tipoBiometria.icono)
                                                .font(.system(size: 20, weight: .semibold))
                                            Text("Ingresar con \(vistaModelo.tipoBiometria.nombre)")
                                                .font(.system(size: 16, weight: .bold))
                                        }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 54)
                                    .foregroundColor(.white)
                                    .background(EstiloInstitucional.verdePrincipal)
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                                    .shadow(color: EstiloInstitucional.verdePrincipal.opacity(0.35), radius: 6, y: 3)
                                }
                                .disabled(vistaModelo.estaCargando)

                                // Opción para ingresar con contraseña u otro usuario
                                Button {
                                    withAnimation(.easeInOut(duration: 0.25)) {
                                        mostrarFormularioCredenciales = true
                                    }
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "key.fill")
                                            .font(.system(size: 12))
                                        Text("Usar usuario y contraseña")
                                            .font(.system(size: 14, weight: .semibold))
                                    }
                                    .foregroundColor(EstiloInstitucional.verdePrincipal)
                                    .padding(.vertical, 12)
                                    .padding(.horizontal, 16)
                                }
                                .padding(.top, 6)
                            }
                        } else {
                            // =========================================================
                            // MODO FORMULARIO TRADICIONAL
                            // =========================================================
                            VStack(spacing: 0) {
                                Text("Iniciar sesión")
                                    .font(.system(size: 23, weight: .bold))
                                    .foregroundColor(EstiloInstitucional.textoPrincipal)
                                    .padding(.top, 8)

                                Text("Ingresa tus credenciales para continuar")
                                    .font(.system(size: 13))
                                    .foregroundColor(EstiloInstitucional.textoSecundario)
                                    .multilineTextAlignment(.center)
                                    .padding(.top, 4)
                                    .padding(.bottom, 16)

                                // Campos de texto
                                VStack(spacing: 14) {

                                    // Campo Usuario
                                    HStack(spacing: 12) {
                                        Image(systemName: "person.fill")
                                            .font(.system(size: 16))
                                            .foregroundColor(EstiloInstitucional.verdePrincipal)
                                            .frame(width: 22)

                                        TextField("Usuario", text: $vistaModelo.usuario)
                                            .font(.system(size: 15))
                                            .autocapitalization(.none)
                                            .disableAutocorrection(true)
                                            .textContentType(.username)
                                            .focused($campoEnfocado, equals: .usuario)
                                            .submitLabel(.next)
                                            .onSubmit { campoEnfocado = .contrasena }

                                        if !vistaModelo.usuario.isEmpty {
                                            Button {
                                                vistaModelo.usuario = ""
                                            } label: {
                                                Image(systemName: "xmark.circle.fill")
                                                    .foregroundColor(EstiloInstitucional.textoSecundario)
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 14)
                                    .frame(height: 54)
                                    .background(Color.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14)
                                            .stroke(
                                                campoEnfocado == .usuario ? EstiloInstitucional.verdePrincipal : EstiloInstitucional.bordeSuave,
                                                lineWidth: campoEnfocado == .usuario ? 1.5 : 1
                                            )
                                    )

                                    // Campo Contraseña
                                    HStack(spacing: 12) {
                                        Image(systemName: "lock.fill")
                                            .font(.system(size: 16))
                                            .foregroundColor(EstiloInstitucional.verdePrincipal)
                                            .frame(width: 22)

                                        if mostrarContrasena {
                                            TextField("Contraseña", text: $vistaModelo.contrasena)
                                                .font(.system(size: 15))
                                                .autocapitalization(.none)
                                                .disableAutocorrection(true)
                                                .focused($campoEnfocado, equals: .contrasena)
                                                .submitLabel(.go)
                                                .onSubmit { ejecutarLoginCredenciales() }
                                        } else {
                                            SecureField("Contraseña", text: $vistaModelo.contrasena)
                                                .font(.system(size: 15))
                                                .textContentType(.password)
                                                .focused($campoEnfocado, equals: .contrasena)
                                                .submitLabel(.go)
                                                .onSubmit { ejecutarLoginCredenciales() }
                                        }

                                        Button {
                                            mostrarContrasena.toggle()
                                        } label: {
                                            Image(systemName: mostrarContrasena ? "eye.slash.fill" : "eye.fill")
                                                .foregroundColor(EstiloInstitucional.textoSecundario)
                                        }
                                    }
                                    .padding(.horizontal, 14)
                                    .frame(height: 54)
                                    .background(Color.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14)
                                            .stroke(
                                                campoEnfocado == .contrasena ? EstiloInstitucional.verdePrincipal : EstiloInstitucional.bordeSuave,
                                                lineWidth: campoEnfocado == .contrasena ? 1.5 : 1
                                            )
                                    )

                                    // Switch Activar Biometría
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack(alignment: .center) {
                                            VStack(alignment: .leading, spacing: 3) {
                                                Text("Acceso biométrico")
                                                    .font(.system(size: 14, weight: .bold))
                                                    .foregroundColor(EstiloInstitucional.textoPrincipal)

                                                Text("Habilita huella o rostro en este dispositivo")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(EstiloInstitucional.textoSecundario)
                                            }

                                            Spacer()

                                            Toggle("", isOn: $vistaModelo.activarBiometria)
                                                .labelsHidden()
                                                .tint(EstiloInstitucional.verdePrincipal)
                                                .onChange(of: vistaModelo.activarBiometria) { _, activo in
                                                    if activo {
                                                        mostrarAvisoBiometria = true
                                                    }
                                                }
                                        }
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 12)
                                        .background(EstiloInstitucional.fondo)
                                        .clipShape(RoundedRectangle(cornerRadius: 14))

                                        Text(
                                            vistaModelo.activarBiometria
                                                ? "Activo. Podrás entrar con tu \(vistaModelo.tipoBiometria.nombre) sin escribir credenciales."
                                                : "Puedes activarlo al iniciar sesión con tu contraseña."
                                        )
                                        .font(.system(size: 12))
                                        .foregroundColor(EstiloInstitucional.textoSecundario)
                                        .padding(.horizontal, 4)

                                        Text("Al activarla aceptas el uso de tus datos biométricos únicamente para proteger el acceso a este dispositivo.")
                                            .font(.system(size: 11))
                                            .foregroundColor(EstiloInstitucional.textoSecundario)
                                            .padding(.horizontal, 4)
                                    }
                                    .padding(.top, 4)

                                    // Botón Iniciar Sesión con credenciales
                                    Button {
                                        ejecutarLoginCredenciales()
                                    } label: {
                                        HStack(spacing: 8) {
                                            if vistaModelo.estaCargando {
                                                ProgressView()
                                                    .tint(.white)
                                            } else {
                                                Text("Iniciar sesión")
                                                    .font(.system(size: 16, weight: .bold))
                                                Image(systemName: "arrow.right")
                                                    .font(.system(size: 14, weight: .bold))
                                            }
                                        }
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 54)
                                        .foregroundColor(.white)
                                        .background(EstiloInstitucional.verdePrincipal)
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                        .shadow(color: EstiloInstitucional.verdePrincipal.opacity(0.35), radius: 6, y: 3)
                                    }
                                    .disabled(vistaModelo.estaCargando || vistaModelo.usuario.trimmingCharacters(in: .whitespaces).isEmpty || vistaModelo.contrasena.isEmpty)
                                    .opacity((vistaModelo.usuario.trimmingCharacters(in: .whitespaces).isEmpty || vistaModelo.contrasena.isEmpty) ? 0.65 : 1.0)
                                    .padding(.top, 6)

                                    // Botón para volver a la opción biométrica si ya estaba activa
                                    if vistaModelo.tieneAccesoBiometrico {
                                        Button {
                                            withAnimation(.easeInOut(duration: 0.25)) {
                                                mostrarFormularioCredenciales = false
                                            }
                                        } label: {
                                            HStack(spacing: 6) {
                                                Image(systemName: vistaModelo.tipoBiometria.icono)
                                                Text("Volver a \(vistaModelo.tipoBiometria.nombre)")
                                            }
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(EstiloInstitucional.verdePrincipal)
                                            .padding(.top, 4)
                                        }
                                    }
                                }
                            }
                        }

                        // MARK: Mensajes de Error
                        if let errorTexto = vistaModelo.errorTexto {
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "exclamationmark.circle.fill")
                                    .foregroundColor(EstiloInstitucional.rojoUrgencia)
                                    .padding(.top, 2)
                                Text(errorTexto)
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(EstiloInstitucional.rojoUrgencia)
                            }
                            .padding(.top, 14)
                            .frame(maxWidth: .infinity, alignment: .center)
                        }
                    }
                    .padding(22)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24)
                            .stroke(EstiloInstitucional.bordeSuave, lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.06), radius: 10, y: 4)
                    .padding(.horizontal, 20)

                    // MARK: Configuración de Servidor (IP)
                    Button {
                        mostrandoConfiguracion = true
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "network")
                                .font(.system(size: 14, weight: .semibold))
                            Text("Configurar servidor (IP)")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(EstiloInstitucional.verdePrincipal)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 16)
                    }
                    .padding(.top, 8)

                    // MARK: Pie Institucional
                    Text("GestióN · Gestión Documental")
                        .font(.system(size: 11))
                        .foregroundColor(EstiloInstitucional.textoSecundario)
                        .padding(.bottom, 24)
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $mostrandoConfiguracion) {
            ConfiguracionView()
        }
        .alert(
            "Aviso de privacidad biométrica",
            isPresented: $mostrarAvisoBiometria
        ) {
            Button("Aceptar y activar") {}
            Button("No activar", role: .cancel) {
                vistaModelo.activarBiometria = false
            }
        } message: {
            Text("La autenticación biométrica se usa solo para desbloquear el acceso a la aplicación en este dispositivo. No se envía tu huella ni tu rostro al servidor; únicamente se guarda de forma segura la sesión protegida por el sistema. Puedes desactivarla cuando quieras.")
        }
        .task {
            // Si la biometría está activa, solicita la verificación automáticamente al abrir la pantalla
            if vistaModelo.tieneAccesoBiometrico && !mostrarFormularioCredenciales {
                try? await Task.sleep(nanoseconds: 300_000_000) // 0.3s para animación inicial limpia
                ejecutarLoginBiometrico()
            }
        }
    }

    private func ejecutarLoginBiometrico() {
        Task {
            await vistaModelo.accederConBiometria()
            if vistaModelo.sesionActiva {
                onSesionIniciada()
            }
        }
    }

    private func ejecutarLoginCredenciales() {
        campoEnfocado = nil
        Task {
            await vistaModelo.iniciarSesion()
            if vistaModelo.sesionActiva {
                onSesionIniciada()
            }
        }
    }
}
