package com.gn.gestion.viewmodels;

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gn.gestion.scanner.DocumentDetector
import com.gn.gestion.scanner.ImageProcessor
import com.gn.gestion.scanner.OcrService
import com.gn.gestion.scanner.OpenCvDetector
import com.gn.gestion.scanner.PdfGenerator
import com.gn.gestion.scanner.ResultadoOcr
import com.gn.gestion.scanner.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado compartido del escáner entre los Fragments
 * (menú de captura, cámara, vista previa y opciones).
 * Conserva la imagen cruda y la rectificada automáticamente.
 */
class EscaneoViewModel : ViewModel() {

    private val _raw = MutableStateFlow<Bitmap?>(null)
    val raw: StateFlow<Bitmap?> = _raw

    private val _base = MutableStateFlow<Bitmap?>(null)
    val base: StateFlow<Bitmap?> = _base

    private val _filter = MutableStateFlow(ScanFilter.MAGIC)
    val filter: StateFlow<ScanFilter> = _filter

    private val _brightness = MutableStateFlow(0)
    val brightness: StateFlow<Int> = _brightness

    private val _contrast = MutableStateFlow(0)
    val contrast: StateFlow<Int> = _contrast

    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation

    private val _corners = MutableStateFlow<List<PointF>?>(null)
    val corners: StateFlow<List<PointF>?> = _corners

    private val _detectOK = MutableStateFlow(true)
    val detectOK: StateFlow<Boolean> = _detectOK

    private val _busyDetecting = MutableStateFlow(false)
    val busyDetecting: StateFlow<Boolean> = _busyDetecting

    private val _pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pages: StateFlow<List<Bitmap>> = _pages

    private val _textoOcr = MutableStateFlow("")
    val textoOcr: StateFlow<String> = _textoOcr

    private val _ocrBusy = MutableStateFlow(false)
    val ocrBusy: StateFlow<Boolean> = _ocrBusy

    /** Imagen rectificada + filtro + ajustes, lista para previsualizar. */
    val procesada = combine(
        _base, _filter, _brightness, _contrast, _rotation
    ) { base, f, b, c, r ->
        base?.let {
            val rotated = ImageProcessor.rotate(it, r.toFloat())
            ImageProcessor.applyFilter(rotated, f, b, c)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** Carga una imagen cruda (cámara o galería) y aplica el recorte automáticamente. */
    fun empezarConImagen(bmp: Bitmap, sugerirEsquinas: List<PointF>? = null) {
        _raw.value = bmp
        // Muestra la imagen de inmediato para que la vista previa nunca quede vacía,
        // y luego la sustituye por el recorte rectificado cuando la detección termina.
        _base.value = bmp
        _corners.value = sugerirEsquinas
        _filter.value = ScanFilter.MAGIC
        _brightness.value = 0
        _contrast.value = 0
        _rotation.value = 0
        if (sugerirEsquinas != null) return

        viewModelScope.launch {
            _busyDetecting.value = true
            val esquinas = withContext(Dispatchers.IO) {
                try {
                    OpenCvDetector.detectCorners(bmp) ?: DocumentDetector.detectCorners(bmp)
                } catch (e: Exception) {
                    null
                }
            }
            if (esquinas != null && esquinas.size == 4) {
                _corners.value = esquinas
                val recortada = withContext(Dispatchers.IO) {
                    try {
                        DocumentDetector.cropAndStraighten(bmp, esquinas)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (recortada != null) {
                    _base.value = recortada
                    _detectOK.value = true
                } else {
                    _detectOK.value = false
                }
            } else {
                _detectOK.value = false
            }
            _busyDetecting.value = false
        }
    }

    /** Detecta el documento en la imagen cruda y aplica corrección de perspectiva. */
    fun autoDetectar() {
        val src = _raw.value ?: return
        viewModelScope.launch {
            _busyDetecting.value = true
            val resultado = withContext(Dispatchers.IO) {
                try {
                    // OpenCV primero; si no está disponible, el detector Kotlin.
                    val corners = OpenCvDetector.detectCorners(src)
                        ?: DocumentDetector.detectCorners(src)
                    if (corners != null && corners.size == 4) {
                        DocumentDetector.cropAndStraighten(src, corners)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            _base.value = resultado ?: src
            _detectOK.value = resultado != null
            _busyDetecting.value = false
        }
    }

    /** Ejecuta OCR sobre la imagen final procesada y guarda el texto extraído. */
    fun reconocerTexto(context: Context) {
        val bmp = _base.value ?: return
        viewModelScope.launch {
            if (_ocrBusy.value) return@launch
            _ocrBusy.value = true
            val texto = extraerTexto(context, bmp)
            _ocrBusy.value = false
        }
    }

    /** OCR suspendible; devuelve el texto y lo guarda en [textoOcr]. */
    suspend fun extraerTexto(context: Context, bmp: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val procesado = ImageProcessor.applyFilter(bmp, _filter.value, _brightness.value, _contrast.value)
            val texto = OcrService.reconocer(context, procesado)
            if (texto.isNotBlank()) _textoOcr.value = texto
            if (procesado != bmp) {
                procesado.recycle()
            }
            texto
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * OCR en una sola pasada que devuelve el texto y las cajas de cada línea,
     * necesario para generar un PDF buscable. Guarda el texto en [textoOcr].
     */
    suspend fun analizarOcr(context: Context, bmp: Bitmap): ResultadoOcr =
        withContext(Dispatchers.Default) {
            try {
                val procesado = ImageProcessor.applyFilter(
                    bmp, _filter.value, _brightness.value, _contrast.value
                )
                val resultado = OcrService.analizar(context, procesado)
                if (resultado.texto.isNotBlank()) _textoOcr.value = resultado.texto
                if (procesado != bmp) {
                    procesado.recycle()
                }
                resultado
            } catch (e: Exception) {
                ResultadoOcr("", emptyList())
            }
        }

    /**
     * OCR SIN aplicar el filtro (imagen original), útil cuando el filtro
     * binariza y borra texto fino del encabezado.
     */
    suspend fun analizarOcrOriginal(context: Context, bmp: Bitmap): ResultadoOcr =
        withContext(Dispatchers.Default) {
            try {
                val resultado = OcrService.analizar(context, bmp)
                if (resultado.texto.isNotBlank()) _textoOcr.value = resultado.texto
                resultado
            } catch (e: Exception) {
                ResultadoOcr("", emptyList())
            }
        }

    fun setFilter(f: ScanFilter) {
        _filter.value = f
    }

    fun setBrightness(v: Int) {
        _brightness.value = v
    }

    fun setContrast(v: Int) {
        _contrast.value = v
    }

    fun rotar() {
        _rotation.value = (_rotation.value + 90) % 360
    }

    fun addPage(bmp: Bitmap) {
        _pages.value = _pages.value + bmp
        limpiarImagen()
    }

    fun deletePage(index: Int) {
        val current = _pages.value
        if (index in current.indices) _pages.value = current.toMutableList().apply { removeAt(index) }
    }

    fun movePage(from: Int, to: Int) {
        val current = _pages.value.toMutableList()
        if (from in current.indices && to in current.indices && from != to) {
            val item = current.removeAt(from)
            current.add(to, item)
            _pages.value = current
        }
    }

    fun limpiarImagen() {
        _raw.value = null
        _base.value = null
        _filter.value = ScanFilter.MAGIC
        _brightness.value = 0
        _contrast.value = 0
        _rotation.value = 0
        _detectOK.value = true
        _busyDetecting.value = false
    }

    /** Reinicia todos los datos del escáner (imagen y páginas agregadas). */
    fun limpiarTodo() {
        limpiarImagen()
        _pages.value = emptyList()
        _textoOcr.value = ""
        _ocrBusy.value = false
    }
}

/**
 * Genera el PDF y devuelve la ruta. Si se pasan los resultados de OCR se añade
 * la capa de texto invisible para que el PDF sea buscable.
 */
suspend fun guardarPdf(
    context: Context,
    bitmaps: List<Bitmap>,
    nombre: String? = null,
    ocrPorPagina: List<ResultadoOcr?>? = null
): String = withContext(Dispatchers.IO) {
    val fileName = if (!nombre.isNullOrBlank()) {
        PdfGenerator.sanitizeFileName(nombre)
    } else {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        "Documento_$stamp"
    }
    val file = PdfGenerator.save(context, bitmaps, fileName, ocrPorPagina)
    file.absolutePath
}