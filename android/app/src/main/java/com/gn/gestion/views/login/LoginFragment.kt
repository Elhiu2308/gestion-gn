package com.gn.gestion.views.login

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.services.BiometricGate
import com.gn.gestion.services.BiometricKeyStore
import com.gn.gestion.services.FcmRegistrador
import com.gn.gestion.services.TokenProvider
import com.gn.gestion.socketio.SocketIOBackground
import com.gn.gestion.viewmodels.GestionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val vm: GestionViewModel by activityViewModels()

    private lateinit var lblError: TextView
    private lateinit var formulario: LinearLayout
    private lateinit var btnBiometria: MaterialButton
    private lateinit var btnUsuarioContrasena: MaterialButton
    private lateinit var switchActivarBiometria: MaterialSwitch
    private lateinit var tvEstadoBiometriaLogin: TextView

    private var eligioCredencialesEnEstaVista = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eligioCredencialesEnEstaVista = false

        if (vm.syncActivo && TokenProvider.sessionActiva(requireContext())) {
            irAlDashboard()
            return
        }

        if (!vm.syncActivo) {
            irAlDashboard()
            return
        }

        BiometricGate.registrar(requireContext())

        val inputUser =
            view.findViewById<TextInputEditText>(R.id.input_usuario)

        val inputPass =
            view.findViewById<TextInputEditText>(R.id.input_contrasena)

        val btnIngresar =
            view.findViewById<MaterialButton>(R.id.btn_ingresar)

        lblError = view.findViewById(R.id.lbl_error)
        formulario = view.findViewById(R.id.ll_formulario)
        btnBiometria = view.findViewById(R.id.btn_biometria)

        btnUsuarioContrasena =
            view.findViewById(R.id.btn_usuario_contrasena)

        switchActivarBiometria =
            view.findViewById(R.id.switch_activar_biometria)

        tvEstadoBiometriaLogin =
            view.findViewById(R.id.tv_estado_biometria_login)

        val separador =
            view.findViewById<TextView>(R.id.lbl_separador)

        view.findViewById<View>(R.id.lbl_config_servidor)
            .setOnClickListener {
                if (findNavController().currentDestination?.id == R.id.login) {
                    findNavController().navigate(
                        R.id.action_login_to_servidor
                    )
                }
            }

        val biometriaActiva =
            vm.biometriaUtilizable() && BiometricGate.disponible()

        btnBiometria.isVisible = biometriaActiva
        btnUsuarioContrasena.isVisible = biometriaActiva
        separador.isVisible = biometriaActiva

        formulario.isVisible = !biometriaActiva

        configurarSwitchBiometria()

        btnBiometria.setOnClickListener {
            iniciarConBiometria(btnBiometria)
        }

        btnUsuarioContrasena.setOnClickListener {

            eligioCredencialesEnEstaVista = true

            btnBiometria.isVisible = false
            btnUsuarioContrasena.isVisible = false
            separador.isVisible = false

            formulario.isVisible = true
            lblError.isVisible = false

            inputUser.requestFocus()
        }

        btnIngresar.setOnClickListener {
            val usuario = inputUser.text
                ?.toString()
                ?.trim()
                .orEmpty()

            val contrasena = inputPass.text
                ?.toString()
                .orEmpty()

            if (usuario.isBlank() || contrasena.isBlank()) {
                mostrarError("Ingresa usuario y contraseña")
                return@setOnClickListener
            }

            btnIngresar.isEnabled = false

            vm.iniciarSesion(
                usuario = usuario,
                contrasena = contrasena,

                onError = { mensaje ->
                    if (!isAdded) return@iniciarSesion

                    btnIngresar.isEnabled = true
                    mostrarError(mensaje)

                    Toast.makeText(
                        requireContext(),
                        mensaje,
                        Toast.LENGTH_LONG
                    ).show()
                },

                onSuccess = {
                    if (!isAdded) return@iniciarSesion

                    btnIngresar.isEnabled = true

                    inputPass.setText("")

                    solicitarActivacionBiometrica()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()

        BiometricGate.registrar(requireContext())

        if (!::btnBiometria.isInitialized) return

        val separador =
            view?.findViewById<View>(R.id.lbl_separador)

        if (eligioCredencialesEnEstaVista) {
            btnBiometria.isVisible = false
            btnUsuarioContrasena.isVisible = false
            separador?.isVisible = false
            formulario.isVisible = true
            return
        }

        val biometriaDisponible =
            vm.biometriaUtilizable() && BiometricGate.disponible()

        btnBiometria.isVisible = biometriaDisponible
        btnUsuarioContrasena.isVisible = biometriaDisponible
        separador?.isVisible = biometriaDisponible

        formulario.isVisible = !biometriaDisponible
    }

    override fun onDestroyView() {
        super.onDestroyView()

        BiometricGate.registrar(
            requireContext().applicationContext
        )
    }

    private fun iniciarConBiometria(btn: MaterialButton) {
        val ctx = requireContext()

        val iv = TokenProvider.ivBiometrico(ctx)
        val cifrado = TokenProvider.refreshCifrado(ctx)

        if (iv == null || cifrado == null) {
            mostrarError(
                "No hay una sesión guardada. Ingresa con tu contraseña."
            )
            return
        }

        val cipher = runCatching {
            BiometricKeyStore.cipherDescifrado(iv)
        }.getOrNull()

        if (cipher == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                vm.desactivarBiometria()
            }

            mostrarError(
                "Tu acceso biométrico fue invalidado. " +
                        "Ingresa con tu contraseña."
            )
            return
        }

        btn.isEnabled = false

        BiometricGate.autenticar(
            activity = requireActivity(),
            titulo = "Acceso biométrico",
            subtitulo = TokenProvider.nombreCompleto(ctx)
                .ifBlank { "Gestión Documental GN" },
            negativo = "Usar contraseña",
            cipher = cipher,

            onOk = { autorizado ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val refresh = runCatching {
                        BiometricKeyStore.descifrar(
                            autorizado,
                            cifrado
                        )
                    }.getOrNull()

                    if (refresh == null) {
                        btn.isEnabled = true

                        viewLifecycleOwner.lifecycleScope.launch {
                            vm.desactivarBiometria()
                        }

                        mostrarError(
                            "Tu acceso biométrico venció. " +
                                    "Ingresa con tu contraseña."
                        )

                        return@launch
                    }

                    vm.iniciarSesionBiometrica(
                        refreshToken = refresh,

                        onError = { mensaje ->
                            if (!isAdded) {
                                return@iniciarSesionBiometrica
                            }

                            btn.isEnabled = true
                            mostrarError(mensaje)
                        },

                        onSuccess = {
                            if (isAdded) {
                                trasLogin()
                            }
                        }
                    )
                }
            },

            onFallo = { mensaje ->
                btn.isEnabled = true

                if (!mensaje.contains("cancel", ignoreCase = true)) {
                    mostrarError(mensaje)
                }
            }
        )
    }

    private fun configurarSwitchBiometria() {
        BiometricGate.registrar(requireContext())

        val ctx = requireContext()

        val prefs = ctx.getSharedPreferences(
            "gn_prefs",
            Context.MODE_PRIVATE
        )

        val declinoAntes = prefs.getBoolean(
            "biometria_privacidad_rechazada",
            false
        )

        val disponible = BiometricGate.disponible()
        val yaActiva = vm.biometriaActivada() && disponible

        var ajustando = false

        switchActivarBiometria.isChecked = yaActiva

        tvEstadoBiometriaLogin.text = when {
            yaActiva ->
                "Activo. Podrás entrar con tu huella o tu rostro."

            !disponible ->
                BiometricGate.motivoNoDisponible()

            else ->
                "Puedes activarlo al iniciar sesión con tu contraseña."
        }

        if (declinoAntes) {
            switchActivarBiometria.isEnabled = false

            tvEstadoBiometriaLogin.text =
                "Activación deshabilitada por tu elección anterior."
        }

        switchActivarBiometria.setOnCheckedChangeListener { _, marcado ->
            if (ajustando) {
                return@setOnCheckedChangeListener
            }

            if (!marcado) {
                prefs.edit()
                    .putBoolean(
                        "biometria_activacion_solicitada",
                        false
                    )
                    .apply()

                return@setOnCheckedChangeListener
            }

            if (!disponible) {
                if (
                    BiometricGate.abrirRegistroBiometrico(
                        requireActivity()
                    )
                ) {
                    Toast.makeText(
                        ctx,
                        "Registra tu huella o rostro y vuelve a la app",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        ctx,
                        BiometricGate.motivoNoDisponible(),
                        Toast.LENGTH_LONG
                    ).show()
                }

                ajustando = true
                switchActivarBiometria.isChecked = false
                ajustando = false

                return@setOnCheckedChangeListener
            }

            prefs.edit()
                .putBoolean(
                    "biometria_activacion_solicitada",
                    true
                )
                .apply()

            Toast.makeText(
                ctx,
                "Activa tu contraseña para completar la activación biométrica",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun solicitarActivacionBiometrica() {
        BiometricGate.registrar(requireContext())

        val disponible = BiometricGate.disponible()
        val yaActiva = vm.biometriaActivada()
        val hayRefresh = vm.ultimoRefreshDisponible()

        val declinoAntes = requireContext()
            .getSharedPreferences("gn_prefs", Context.MODE_PRIVATE)
            .getBoolean("biometria_privacidad_rechazada", false)

        if (
            declinoAntes ||
            !disponible ||
            yaActiva ||
            !hayRefresh
        ) {
            trasLogin()
            return
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
            .setTitle("Aviso de privacidad")
            .setMessage(
                "Para agilizar tu acceso, GestioN puede activar el " +
                        "desbloqueo con huella o rostro. La contraseña nunca " +
                        "se guarda en el dispositivo: el refresh token se cifra " +
                        "en el Keystore y solo se descifra al validar tu identidad " +
                        "con el sistema biométrico. Puedes desactivarlo en " +
                        "cualquier momento. ¿Deseas activarlo ahora?"
            )
            .setCancelable(false)

            .setPositiveButton("Aceptar y activar") { _, _ ->
                activarBiometriaDesdeLogin()
            }

            .setNeutralButton("No volver a preguntar") { _, _ ->
                requireContext()
                    .getSharedPreferences(
                        "gn_prefs",
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putBoolean(
                        "biometria_privacidad_rechazada",
                        true
                    )
                    .apply()

                trasLogin()
            }

            .setNegativeButton("No, gracias") { _, _ ->
                trasLogin()
            }

            .show()
    }

    private fun activarBiometriaDesdeLogin() {
        BiometricGate.registrar(requireContext())

        if (!BiometricGate.disponible()) {
            trasLogin()
            return
        }

        val cipher = runCatching {
            BiometricKeyStore.cipherCifrado()
        }.getOrNull()

        if (cipher == null) {
            mostrarError(
                "No se pudo preparar el acceso biométrico"
            )

            trasLogin()
            return
        }

        btnBiometria.isEnabled = false

        BiometricGate.autenticar(
            activity = requireActivity(),
            titulo = "Activar acceso biométrico",
            subtitulo =
                "Confirma tu identidad para poder entrar con huella o rostro",
            negativo = "Cancelar",
            cipher = cipher,

            onOk = { autorizado ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val error = vm.activarBiometria(autorizado)

                    if (!isAdded) {
                        return@launch
                    }

                    btnBiometria.isEnabled = true

                    if (error != null) {
                        mostrarError(error)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Acceso biométrico activado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    trasLogin()
                }
            },

            onFallo = { mensaje ->
                btnBiometria.isEnabled = true

                if (!mensaje.contains("cancel", ignoreCase = true)) {
                    mostrarError(mensaje)
                }

                trasLogin()
            }
        )
    }

    private fun mostrarError(mensaje: String) {
        lblError.isVisible = true
        lblError.text = mensaje
    }

    private fun trasLogin() {
        SocketIOBackground.iniciar(requireContext())
        FcmRegistrador.registrar(requireContext())
        irAlDashboard()
    }

    private fun irAlDashboard() {
        if (findNavController().currentDestination?.id == R.id.login) {
            findNavController().navigate(
                R.id.action_login_to_dashboard
            )
        }
    }
}