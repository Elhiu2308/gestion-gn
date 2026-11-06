package com.gn.gestion.scanner;

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Una línea de texto reconocida junto con su posición en la imagen. */
data class LineaOcr(val texto: String, val caja: Rect)

/** Texto plano del documento más las líneas con su caja, en una sola pasada del OCR. */
data class ResultadoOcr(val texto: String, val lineas: List<LineaOcr>)

/**
 * Reconocimiento óptico de caracteres (OCR) con ML Kit.
 * Extrae el texto del documento escaneado y lo guarda en el campo texto_ocr.
 */
object OcrService {

    @Volatile
    private var reconocedor: TextRecognizer? = null

    private fun obtener(context: Context): TextRecognizer {
        reconocedor?.let { return it }
        synchronized(this) {
            reconocedor?.let { return it }
            val r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            reconocedor = r
            return r
        }
    }

    /**
     * Analiza el bitmap una sola vez y devuelve el texto completo junto con
     * cada línea y su caja. Las cajas permiten dibujar después una capa de
     * texto invisible sobre el PDF para que sea buscable.
     */
    suspend fun analizar(context: Context, bmp: Bitmap): ResultadoOcr =
        withContext(Dispatchers.Default) {
            if (bmp.isRecycled) return@withContext ResultadoOcr("", emptyList())
            try {
                val imagen = InputImage.fromBitmap(bmp, 0)
                val resultado = obtener(context).process(imagen).await()
                val lineas = ArrayList<LineaOcr>()
                resultado.textBlocks.forEach { bloque ->
                    bloque.lines.forEach { linea ->
                        val caja = linea.boundingBox
                        if (caja != null && caja.width() > 8 && caja.height() > 8) {
                            lineas.add(LineaOcr(linea.text, caja))
                        }
                    }
                }
                ResultadoOcr(resultado.text.trim().orEmpty(), lineas)
            } catch (e: Exception) {
                ResultadoOcr("", emptyList())
            }
        }

    /** Procesa el bitmap final (rectificado) y devuelve el texto reconocido. */
    suspend fun reconocer(context: Context, bmp: Bitmap): String =
        analizar(context, bmp).texto

    /** Procesa el bitmap y devuelve las cajas (rectángulos) de cada línea de texto detectada. */
    suspend fun cajasTexto(context: Context, bmp: Bitmap): List<Rect> =
        analizar(context, bmp).lineas.map { it.caja }
}