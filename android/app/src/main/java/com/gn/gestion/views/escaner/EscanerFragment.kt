package com.gn.gestion.views.escaner;

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.scanner.ImageProcessor
import com.gn.gestion.viewmodels.EscaneoViewModel;
import com.gn.gestion.views.prepararNuevoDocumento
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EscanerFragment : Fragment(R.layout.fragment_escaner) {

    private val scanVM: EscaneoViewModel by activityViewModels()

    private companion object {
        private const val TAG = "GN_ESCANER"
    }

    private fun mostrarProcesando(activo: Boolean) {
        view?.findViewById<View>(R.id.progress)?.isVisible = activo
        view?.findViewById<View>(R.id.lbl_procesando)?.isVisible = activo
        view?.findViewById<View>(R.id.btn_camara)?.isEnabled = !activo
        view?.findViewById<View>(R.id.btn_subir)?.isEnabled = !activo
        view?.findViewById<View>(R.id.btn_documento)?.isEnabled = !activo
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) cargarImagen(uri)
        }

    private val documentoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val tipo = requireContext().contentResolver.getType(uri).orEmpty()
            if (tipo.startsWith("image/")) {
                cargarImagen(uri)
            } else {
                cargarArchivo(uri)
            }
        }

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) return@registerForActivityResult
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.mapNotNull { it.imageUri }.orEmpty()
            if (uris.isEmpty()) {
                Toast.makeText(requireContext(), "No se obtuvo la imagen", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            viewLifecycleOwner.lifecycleScope.launch {
                mostrarProcesando(true)
                val bitmaps = withContext(Dispatchers.IO) {
                    uris.mapNotNull { u ->
                        requireContext().contentResolver.openInputStream(u)?.use { ins ->
                            ImageProcessor.fromByteArray(ins.readBytes())
                        }?.let { ImageProcessor.maxScaleDown(it) }
                    }
                }
                if (bitmaps.isEmpty()) {
                    mostrarProcesando(false)
                    Toast.makeText(requireContext(), "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                generarYContinuar(bitmaps)
            }
        }

    private fun cargarImagen(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            mostrarProcesando(true)
            val bmp = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                    ImageProcessor.fromByteArray(ins.readBytes())
                }?.let { ImageProcessor.maxScaleDown(it) }
            }
            if (bmp == null) {
                mostrarProcesando(false)
                Toast.makeText(requireContext(), "No se pudo abrir la imagen", Toast.LENGTH_SHORT).show()
                return@launch
            }
            generarYContinuar(listOf(bmp))
        }
    }

    private fun cargarArchivo(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            mostrarProcesando(true)
            val path = withContext(Dispatchers.IO) {
                try {
                    val nombre = "Documento_${System.currentTimeMillis()}.pdf"
                    val destino = File(requireContext().filesDir, nombre)
                    requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                        destino.outputStream().use { out -> ins.copyTo(out) }
                    }
                    destino.absolutePath
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo copiar el documento", e)
                    null
                }
            }
            if (path == null) {
                mostrarProcesando(false)
                Toast.makeText(requireContext(), "No se pudo abrir el documento", Toast.LENGTH_SHORT).show()
                return@launch
            }
            mostrarProcesando(false)
            findNavController().navigate(
                R.id.nuevo,
                Bundle().apply { putString("pdfPath", path) }
            )
        }
    }

    private suspend fun generarYContinuar(bitmaps: List<Bitmap>) {
        try {
            val args = prepararNuevoDocumento(requireContext(), scanVM, bitmaps)
            mostrarProcesando(false)
            findNavController().navigate(R.id.nuevo, args)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando el documento", e)
            mostrarProcesando(false)
            Toast.makeText(requireContext(), "No se pudo generar el documento", Toast.LENGTH_SHORT).show()
        }
    }

    private fun escanear() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender ->
                try {
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo abrir el escáner", e)
                    abrirCamaraPropia()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Escáner de Google no disponible, se usa la cámara propia", e)
                abrirCamaraPropia()
            }
    }

    private fun abrirCamaraPropia() {
        findNavController().navigate(R.id.action_escanner_to_camara)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_camara).setOnClickListener { escanear() }
        view.findViewById<View>(R.id.btn_subir).setOnClickListener { galleryLauncher.launch("image/*") }
        view.findViewById<View>(R.id.btn_documento).setOnClickListener {
            documentoLauncher.launch(arrayOf("image/*", "application/pdf"))
        }
    }
}