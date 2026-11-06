package com.gn.gestion.views.camara;

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.scanner.DocumentDetector
import com.gn.gestion.scanner.ImageProcessor
import com.gn.gestion.scanner.OpenCvDetector
import com.gn.gestion.scanner.ScanOverlayView
import com.gn.gestion.scanner.SensorEstabilidad
import com.gn.gestion.viewmodels.EscaneoViewModel;
import com.gn.gestion.views.prepararNuevoDocumento
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fragmento de captura con cámara. Implementa auto-detección y auto-captura.
 */
class CamaraFragment : Fragment() {

    private val viewModel: EscaneoViewModel by viewModels({ requireActivity() })

    private var imageCapture: ImageCapture? = null
    private var camara: Camera? = null
    private var flashActivo = false
    private var overlay: ScanOverlayView? = null
    private var lblEstado: TextView? = null
    private var sensorEst: SensorEstabilidad? = null
    private var analisisExecutor: ExecutorService? = null

    private val permisosCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                startCamera()
            } else {
                lblEstado?.text = "Se necesita el permiso de cámara para escanear"
            }
        }

    /** Importa una imagen de la galería y la lleva a la vista previa. */
    private val galeriaLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                        ImageProcessor.fromByteArray(ins.readBytes())
                    }?.let { ImageProcessor.maxScaleDown(it) }
                }
                if (decoded == null) {
                    Toast.makeText(requireContext(), "No se pudo abrir la imagen", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                viewModel.empezarConImagen(decoded)
                irAFormulario(listOf(decoded))
            }
        }

    /** Genera el PDF con OCR y abre directamente el formulario "Nuevo Documento". */
    private fun irAFormulario(bitmaps: List<Bitmap>) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Generando documento…", Toast.LENGTH_SHORT).show()
            try {
                val args = prepararNuevoDocumento(requireContext(), viewModel, bitmaps)
                findNavController().navigate(R.id.nuevo, args)
            } catch (e: Exception) {
                Log.e(TAG, "Error generando el documento", e)
                Toast.makeText(requireContext(), "No se pudo generar el documento", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun alternarFlash() {
        val cam = camara
        if (cam == null) {
            Toast.makeText(requireContext(), "Cámara no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(requireContext(), "Este dispositivo no tiene flash", Toast.LENGTH_SHORT).show()
            return
        }
        flashActivo = !flashActivo
        cam.cameraControl.enableTorch(flashActivo)
        view?.findViewById<ImageButton>(R.id.btn_flash)?.alpha = if (flashActivo) 1f else 0.55f
    }

    private var framesAnalizados = 0
    private var framesEstables = 0
    private var esquinasPrevias: List<PointF>? = null
    private val autoCapturando = AtomicBoolean(false)

    companion object {
        private const val TAG = "GN_ESCANER"
        private const val MIN_AREA = 0.15f
        private const val MAX_MOV_ESQUINA = 25f
        private const val FRAMES_ESTABLES_REQ = 6
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camara, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        overlay = view.findViewById(R.id.scan_overlay)
        lblEstado = view.findViewById(R.id.lbl_estado)

        view.findViewById<View>(R.id.btn_cerrar).setOnClickListener {
            findNavController().popBackStack()
        }

        view.findViewById<View>(R.id.btn_flash).alpha = 0.55f
        view.findViewById<View>(R.id.btn_flash).setOnClickListener {
            alternarFlash()
        }

        view.findViewById<View>(R.id.btn_galeria).setOnClickListener {
            galeriaLauncher.launch("image/*")
        }

        view.findViewById<MaterialButton>(R.id.btn_capturar).setOnClickListener {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()

            val scanner = GmsDocumentScanning.getClient(options)
            
            scanner.getStartScanIntent(requireActivity())
                .addOnSuccessListener { intentSender ->
                    try {
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        mlKitScannerLauncher.launch(request)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error lanzando ML Kit Scanner", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al inicializar ML Kit Scanner, fallback a cámara manual", e)
                }
        }

        sensorEst = SensorEstabilidad(requireContext()).apply { registrar() }
        analisisExecutor = Executors.newSingleThreadExecutor()
        overlay?.startScan()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permisosCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private val mlKitScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                try {
                    requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                        val bytes = ins.readBytes()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            // ML Kit entrega el papel ya recortado y plano.
                            viewModel.empezarConImagen(
                                bmp,
                                listOf(
                                    PointF(0f, 0f),
                                    PointF(bmp.width.toFloat(), 0f),
                                    PointF(bmp.width.toFloat(), bmp.height.toFloat()),
                                    PointF(0f, bmp.height.toFloat())
                                )
                            )
                            irAFormulario(listOf(bmp))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando resultado ML Kit", e)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (flashActivo) {
            runCatching { camara?.cameraControl?.enableTorch(false) }
            flashActivo = false
        }
        camara = null
        sensorEst?.liberar()
        overlay?.stopScan()
        analisisExecutor?.shutdown()
        analisisExecutor = null
    }

    private fun startCamera() {
        val view = view ?: return
        val previewView = view.findViewById<PreviewView>(R.id.preview_view)
        val ctx = requireContext()
        val providerFuture = ProcessCameraProvider.getInstance(ctx)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                
                // Use default resolution to avoid compatibility issues with fixed sizes
                val preview = Preview.Builder()
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val cap = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                imageCapture = cap

                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                
                analisis.setAnalyzer(analisisExecutor!!) { proxy ->
                    analizar(proxy, previewView)
                }

                provider.unbindAll()
                camara = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    selector,
                    preview, cap, analisis
                )
                flashActivo = false
                Log.d(TAG, "Cámara vinculada OK")
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara: ${e.message}", e)
                lblEstado?.text = "Error de cámara: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Procesa cada frame del preview: detecta el cuadrilátero y decide auto-captura. */
    private fun analizar(proxy: ImageProxy, previewView: PreviewView) {
        // use proxy.toBitmap() from CameraX 1.3+
        val imagen = runCatching { proxy.toBitmap() }.getOrNull() ?: run { proxy.close(); return }
        
        framesAnalizados++
        var imagenAlta: Bitmap? = null
        var pequena: Bitmap? = null
        try {
            imagenAlta = ImageProcessor.maxScaleDown(imagen, 800)
            pequena = ImageProcessor.maxScaleDown(imagenAlta, 400)

            val esquinas = OpenCvDetector.detectCorners(pequena)
                ?: DocumentDetector.detectCorners(pequena)

            var areaRatio = 0f
            var movimiento = Float.MAX_VALUE
            val prev = esquinasPrevias
            if (esquinas != null && esquinas.size == 4) {
                areaRatio = areaCuadrilatero(esquinas) / max(1f, pequena.width * pequena.height.toFloat())
                if (prev != null && prev.size == 4) {
                    var suma = 0f
                    for (i in 0..3) {
                        suma += distancia(esquinas[i], prev[i])
                    }
                    movimiento = suma / 4f
                }
            }

            val detectado = esquinas != null && esquinas.size == 4 && areaRatio >= MIN_AREA
            val sensorEstable = sensorEst?.estable == true
            val quieto = movimiento < MAX_MOV_ESQUINA

            if (detectado && quieto) framesEstables++ else framesEstables = 0

            // Actualiza el overlay con las esquinas mapeadas a la vista
            val main = ContextCompat.getMainExecutor(requireContext())
            val iw = pequena.width
            val ih = pequena.height
            val esquinasVista = esquinas?.let { mapearAVista(it, previewView.width, previewView.height, iw, ih) }
            val estableOverlay = detectado && quieto && framesEstables >= 3
            val progreso = if (detectado) {
                (framesEstables / FRAMES_ESTABLES_REQ.toFloat()).coerceIn(0f, 1f)
            } else 0f
            
            main.execute {
                overlay?.setContorno(esquinasVista, estableOverlay)
                if (detectado) {
                    overlay?.setEstabilidad(progreso)
                } else {
                    overlay?.setEstabilidad(0f)
                }
                actualizarLbl(lblEstado, detectado, quieto, sensorEstable, framesEstables)
            }

            // Dispara captura automática
            val autoMode = view?.findViewById<SwitchMaterial>(R.id.switch_auto)?.isChecked ?: true
            if (
                autoMode &&
                detectado && quieto && sensorEstable &&
                framesEstables >= FRAMES_ESTABLES_REQ &&
                !autoCapturando.get()
            ) {
                autoCapturando.set(true)
                main.execute { capturar() }
            }

            esquinasPrevias = esquinas
        } catch (_: Exception) {
        } finally {
            if (pequena != null && pequena != imagenAlta && pequena != imagen) pequena.recycle()
            if (imagenAlta != null && imagenAlta != imagen) imagenAlta.recycle()
            imagen.recycle()
            proxy.close()
        }
    }

    private fun mapearAVista(
        esquinas: List<PointF>,
        vw: Int, vh: Int,
        iw: Int, ih: Int
    ): List<PointF> {
        if (vw <= 0 || vh <= 0 || iw <= 0 || ih <= 0) return esquinas
        // fitCenter: la imagen se escala para caber completa dentro de la vista
        val scale = min(vw.toFloat() / iw, vh.toFloat() / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = (vh - ih * scale) / 2f
        return esquinas.map { PointF(it.x * scale + dx, it.y * scale + dy) }
    }

    private fun actualizarLbl(
        tv: TextView?,
        detectado: Boolean,
        quieto: Boolean,
        sensor: Boolean,
        estables: Int
    ) {
        tv ?: return
        if (!detectado) {
            tv.text = "Buscando documento..."
            tv.setTextColor(0xAAFFFFFF.toInt())
        } else if (!sensor || !quieto) {
            tv.text = "Mantenga el celular quieto"
            tv.setTextColor(0xFFFFCC00.toInt())
        } else {
            tv.text = "Capturando en ${FRAMES_ESTABLES_REQ - estables}..."
            tv.setTextColor(0xFF00FF00.toInt())
        }
    }

    private fun capturar() {
        val cap = imageCapture ?: return
        val file = File(requireContext().cacheDir, "temp_scan_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()

        cap.takePicture(opts, ContextCompat.getMainExecutor(requireContext()), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    viewModel.empezarConImagen(bmp)
                    irAFormulario(listOf(bmp))
                } else {
                    Toast.makeText(requireContext(), "No se pudo leer la captura", Toast.LENGTH_SHORT).show()
                }
                autoCapturando.set(false)
            }
            override fun onError(exception: ImageCaptureException) {
                autoCapturando.set(false)
            }
        })
    }

    private fun areaCuadrilatero(q: List<PointF>): Float {
        var sum = 0f
        for (i in q.indices) {
            val j = (i + 1) % q.size
            sum += q[i].x * q[j].y - q[j].x * q[i].y
        }
        return Math.abs(sum / 2f)
    }

    private fun distancia(a: PointF, b: PointF): Float =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
}
