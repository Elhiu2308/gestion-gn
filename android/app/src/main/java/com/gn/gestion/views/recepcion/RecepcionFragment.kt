package com.gn.gestion.views.recepcion

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.models.Documento
import com.gn.gestion.models.servidorIdRequerido
import com.gn.gestion.services.FlujoRepository
import com.gn.gestion.util.mensajeServidor
import com.gn.gestion.viewmodels.GestionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class RecepcionFragment : Fragment(R.layout.fragment_recepcion) {

    private val vm: GestionViewModel by activityViewModels()
    private lateinit var flujo: FlujoRepository

    private lateinit var previewView: PreviewView
    private lateinit var cardConfirmacion: MaterialCardView
    private lateinit var loadingLayout: View
    private lateinit var tvLoadingText: TextView

    private var camara: Camera? = null
    private var flashActivo = false
    private var escaneoPausado = false
    private var documentoActual: Documento? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val qrReader = MultiFormatReader()

    private val permisosCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                iniciarCamara()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Se requiere permiso de cámara para escanear QR",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        flujo = FlujoRepository.get(requireContext())

        previewView = view.findViewById(R.id.preview_view)
        cardConfirmacion = view.findViewById(R.id.card_confirmacion_recepcion)
        loadingLayout = view.findViewById(R.id.loading_layout)
        tvLoadingText = view.findViewById(R.id.tv_loading_text)

        view.findViewById<View>(R.id.btn_flash)?.setOnClickListener {
            toggleFlash()
        }

        view.findViewById<View>(R.id.btn_buscar_manual)?.setOnClickListener {
            dialogoBusquedaManual()
        }

        view.findViewById<View>(R.id.btn_escanear_otro)?.setOnClickListener {
            reiniciarEscaneo()
        }

        view.findViewById<View>(R.id.btn_ver_detalle_rec)?.setOnClickListener {
            documentoActual?.let { doc ->
                findNavController().navigate(
                    R.id.action_recepcion_to_detalle,
                    Bundle().apply { putLong("id", doc.id) }
                )
            }
        }

        view.findViewById<View>(R.id.btn_confirmar_recepcion)?.setOnClickListener {
            confirmarRecepcion()
        }

        verificarPermisosYCamara()
    }

    private fun verificarPermisosYCamara() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCamara()
        } else {
            permisosCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleFlash() {
        val cam = camara ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(requireContext(), "El dispositivo no tiene linterna", Toast.LENGTH_SHORT).show()
            return
        }
        flashActivo = !flashActivo
        cam.cameraControl.enableTorch(flashActivo)
    }

    private fun iniciarCamara() {
        val ctx = requireContext()
        val providerFuture = ProcessCameraProvider.getInstance(ctx)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    procesarFrameQr(imageProxy)
                }

                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()

                camara = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                Log.e("RecepcionFragment", "Error al iniciar la cámara", e)
                Toast.makeText(ctx, "Error al acceder a la cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun procesarFrameQr(imageProxy: ImageProxy) {
        if (escaneoPausado) {
            imageProxy.close()
            return
        }

        if (imageProxy.format == ImageFormat.YUV_420_888 || imageProxy.format == ImageFormat.YUV_422_888) {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val source = PlanarYUVLuminanceSource(
                bytes,
                imageProxy.width,
                imageProxy.height,
                0,
                0,
                imageProxy.width,
                imageProxy.height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = qrReader.decodeWithState(binaryBitmap)
                val rawText = result.text.orEmpty().trim()
                if (rawText.isNotBlank()) {
                    escaneoPausado = true
                    requireActivity().runOnUiThread {
                        procesarCodigoQrDetectado(rawText)
                    }
                }
            } catch (_: NotFoundException) {
            } catch (e: Exception) {
                Log.d("RecepcionFragment", "Exception analizando QR: ${e.message}")
            } finally {
                qrReader.reset()
            }
        }
        imageProxy.close()
    }

    private fun procesarCodigoQrDetectado(contenidoQr: String) {

        val uuidExtraido = parsearUuidODatos(contenidoQr)
        Log.i("RecepcionFragment", "QR detectado: $contenidoQr -> UUID: $uuidExtraido")

        mostrarCargando(true, "Buscando documento en el servidor...")

        lifecycleScope.launch {
            val localId = withContext(Dispatchers.IO) {
                vm.descargarYObtenerId(uuidExtraido, 0L)
            }

            val doc = if (localId != null && localId > 0L) {
                vm.documentoPorId(localId)
            } else {
                vm.todos.value.firstOrNull {
                    it.uuid.equals(uuidExtraido, ignoreCase = true) ||
                        it.folio.equals(uuidExtraido, ignoreCase = true)
                }
            }

            mostrarCargando(false)

            if (doc != null) {
                documentoActual = doc
                mostrarTarjetaConfirmacion(doc)
            } else {
                MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                    .setTitle("Documento no encontrado")
                    .setMessage("No se encontró ningún documento asociado al código QR:\n\n$contenidoQr")
                    .setPositiveButton("Escanear de nuevo") { _, _ ->
                        reiniciarEscaneo()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun parsearUuidODatos(raw: String): String {
        if (raw.startsWith("GESTIONN::")) {
            return raw.removePrefix("GESTIONN::").trim()
        }
        if (raw.contains("uuid=")) {
            return raw.substringAfter("uuid=").substringBefore("&").trim()
        }
        return raw.trim()
    }

    private fun mostrarTarjetaConfirmacion(doc: Documento) {
        cardConfirmacion.isVisible = true

        view?.findViewById<TextView>(R.id.rec_folio)?.text = "Folio: ${doc.folio.ifBlank { "Sin folio" }}"
        view?.findViewById<TextView>(R.id.rec_asunto)?.text = doc.asunto.ifBlank { "Sin asunto" }
        view?.findViewById<TextView>(R.id.rec_remitente)?.text = "Remitente: ${doc.remitente.ifBlank { "—" }}"
        view?.findViewById<TextView>(R.id.rec_area_destino)?.text = "Área destino: ${doc.areaDestino.ifBlank { "—" }}"

        val badgeEstado = view?.findViewById<TextView>(R.id.rec_estado_badge)
        val btnConfirmar = view?.findViewById<MaterialButton>(R.id.btn_confirmar_recepcion)

        val yaRecibido = doc.estado.equals("RECIBIDO", true)
        if (yaRecibido) {
            badgeEstado?.text = "RECIBIDO"
            badgeEstado?.setBackgroundResource(R.drawable.bg_badge)
            btnConfirmar?.isEnabled = false
            btnConfirmar?.text = "Ya Recibido"
        } else {
            badgeEstado?.text = doc.estado.uppercase()
            badgeEstado?.setBackgroundResource(R.drawable.bg_badge)
            btnConfirmar?.isEnabled = true
            btnConfirmar?.text = "Confirmar Recepción"
        }
    }

    private fun confirmarRecepcion() {
        val doc = documentoActual ?: return
        val sid = doc.servidorIdRequerido

        if (sid == null) {
            Toast.makeText(
                requireContext(),
                "El documento no se encuentra sincronizado con el servidor",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        mostrarCargando(true, "Registrando recepción y trazabilidad...")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    flujo.recibir(sid)
                }
                vm.sincronizar()

                val docActualizado = vm.documentoPorId(doc.id) ?: doc
                documentoActual = docActualizado

                mostrarCargando(false)
                mostrarTarjetaConfirmacion(docActualizado)

                MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                    .setTitle("Recepción Confirmada")
                    .setMessage("Se ha registrado exitosamente la recepción del documento ${doc.folio}.\nEstado actualizado: RECIBIDO")
                    .setPositiveButton("Ver detalle") { _, _ ->
                        findNavController().navigate(
                            R.id.action_recepcion_to_detalle,
                            Bundle().apply { putLong("id", doc.id) }
                        )
                    }
                    .setNegativeButton("Aceptar", null)
                    .show()

            } catch (e: Exception) {
                mostrarCargando(false)
                MaterialAlertDialogBuilder(requireContext(), R.style.GestioN_Dialog_Base)
                    .setTitle("Error de recepción")
                    .setMessage(e.mensajeServidor())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun crearCampoTexto(
        ctx: android.content.Context,
        label: String,
        placeholder: String = ""
    ): Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText> {
        val til = com.google.android.material.textfield.TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = label
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = ContextCompat.getColor(ctx, R.color.g_verde)
            hintTextColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.g_verde))
            setBoxCornerRadii(
                com.gn.gestion.util.dp(ctx, 12).toFloat(),
                com.gn.gestion.util.dp(ctx, 12).toFloat(),
                com.gn.gestion.util.dp(ctx, 12).toFloat(),
                com.gn.gestion.util.dp(ctx, 12).toFloat()
            )
            if (placeholder.isNotBlank()) {
                placeholderText = placeholder
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, com.gn.gestion.util.dp(ctx, 6), 0, com.gn.gestion.util.dp(ctx, 6))
            }
        }

        val input = com.google.android.material.textfield.TextInputEditText(til.context).apply {
            setPadding(com.gn.gestion.util.dp(ctx, 16), com.gn.gestion.util.dp(ctx, 14), com.gn.gestion.util.dp(ctx, 16), com.gn.gestion.util.dp(ctx, 14))
        }
        til.addView(input)
        return Pair(til, input)
    }

    private fun column(vararg views: View): android.widget.LinearLayout =
        android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(com.gn.gestion.util.dp(requireContext(), 20), com.gn.gestion.util.dp(requireContext(), 14), com.gn.gestion.util.dp(requireContext(), 20), com.gn.gestion.util.dp(requireContext(), 8))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            for (v in views) {
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, com.gn.gestion.util.dp(requireContext(), 4), 0, com.gn.gestion.util.dp(requireContext(), 4)) }
                addView(v, lp)
            }
        }

    private fun dialogoBusquedaManual() {
        val ctx = requireContext()
        val (tilBusqueda, inputBusqueda) = crearCampoTexto(ctx, "Folio o UUID del documento")
        val dialogView = column(tilBusqueda)

        MaterialAlertDialogBuilder(ctx, R.style.GestioN_Dialog_Base)
            .setTitle("Búsqueda Manual")
            .setMessage("Ingresa el Folio o UUID del documento a recibir:")
            .setView(dialogView)
            .setPositiveButton("Buscar") { _, _ ->
                val query = inputBusqueda.text.toString().trim()
                if (query.isNotBlank()) {
                    escaneoPausado = true
                    procesarCodigoQrDetectado(query)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun reiniciarEscaneo() {
        escaneoPausado = false
        documentoActual = null
        cardConfirmacion.isVisible = false
        mostrarCargando(false)
    }

    private fun mostrarCargando(mostrar: Boolean, texto: String = "") {
        loadingLayout.isVisible = mostrar
        if (mostrar) tvLoadingText.text = texto
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}