package com.gn.gestion.scanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.gn.gestion.models.Documento
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    private const val A4_ANCHO = 595
    private const val A4_ALTO = 842
    private const val MARGEN = 24f

    fun save(
        context: Context,
        pages: List<Bitmap>,
        fileName: String,
        ocrPorPagina: List<ResultadoOcr?>? = null
    ): File {
        val documento = PdfDocument()
        val nombreLimpio = sanitizeFileName(fileName)

        try {
            for ((indice, bitmap) in pages.withIndex()) {
                val fuente = bitmap.toSoftwareArgb8888()
                val vertical = fuente.height >= fuente.width
                val pageW = if (vertical) A4_ANCHO else A4_ALTO
                val pageH = if (vertical) A4_ALTO else A4_ANCHO
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, indice + 1).create()
                val page = documento.startPage(pageInfo)
                val canvas = page.canvas

                val utilW = pageW - MARGEN * 2
                val utilH = pageH - MARGEN * 2
                val escala = minOf(utilW / fuente.width.toFloat(), utilH / fuente.height.toFloat())
                val ancho = fuente.width * escala
                val alto = fuente.height * escala
                val destino = RectF(
                    (pageW - ancho) / 2f,
                    (pageH - alto) / 2f,
                    (pageW + ancho) / 2f,
                    (pageH + alto) / 2f
                )
                canvas.drawBitmap(fuente, null, destino, null)

                val lineas = ocrPorPagina?.getOrNull(indice)?.lineas.orEmpty()
                if (lineas.isNotEmpty()) {
                    dibujarCapaTextoInvisible(canvas, lineas, destino, fuente.width, fuente.height)
                }

                documento.finishPage(page)
            }

            val dirPrivado = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "pdfs")
            if (!dirPrivado.exists()) dirPrivado.mkdirs()
            val archivoPrivado = File(dirPrivado, "$nombreLimpio.pdf")
            FileOutputStream(archivoPrivado).use { salida ->
                documento.writeTo(salida)
            }

            runCatching {
                guardarEnDescargas(context, "application/pdf", "$nombreLimpio.pdf") { salida ->
                    archivoPrivado.inputStream().use { ins -> ins.copyTo(salida) }
                }
            }

            return archivoPrivado
        } finally {
            try {
                documento.close()
            } catch (_: Exception) {}
        }
    }

    fun generarPdfDocumento(context: Context, doc: Documento): File {
        val nombreLimpio = sanitizeFileName(doc.folio.ifBlank { doc.uuid })
        val dirPrivado = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "pdfs")
        if (!dirPrivado.exists()) dirPrivado.mkdirs()
        val destino = File(dirPrivado, "$nombreLimpio.pdf")

        if (destino.exists() && destino.length() > 0L) return destino

        val documento = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_ANCHO, A4_ALTO, 1).create()
            val page = documento.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            val paintVerde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C4D38")
                textSize = 16f
                isFakeBoldText = true
            }
            val paintDorado = Paint().apply {
                color = Color.parseColor("#B38038")
                strokeWidth = 3f
            }
            val paintGris = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 11f
            }
            val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 13f
            }
            val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 14f
                isFakeBoldText = true
            }

            var y = 50f

            canvas.drawText("GUARDIA NACIONAL · GESTIÓN DOCUMENTAL", 40f, y, paintVerde)
            y += 18f
            canvas.drawText("DOCUMENTO OFICIAL REGISTRADO EN EL SISTEMA", 40f, y, paintGris)
            y += 20f

            canvas.drawLine(40f, y, 555f, y, paintDorado)
            y += 30f

            val tipoStr = doc.tipo.ifBlank { "DOCUMENTO" }
            val folioStr = doc.folio.ifBlank { "Sin folio" }
            canvas.drawText("$tipoStr: $folioStr", 40f, y, paintBold)
            y += 25f

            canvas.drawText("ASUNTO:", 40f, y, paintVerde)
            y += 18f
            val asuntoStr = doc.asunto.ifBlank { "Sin asunto registrado." }
            for (linea in asuntoStr.chunked(65)) {
                canvas.drawText(linea, 40f, y, paintTexto)
                y += 16f
            }
            y += 20f

            val campos = listOf(
                "Remitente" to doc.remitente.ifBlank { "—" },
                "Destinatario" to doc.destinatario.ifBlank { "—" },
                "Área Origen" to doc.areaOrigen.ifBlank { "—" },
                "Área Destino" to doc.areaDestino.ifBlank { "—" },
                "Clasificación" to doc.clasificacion.ifBlank { "PUBLICO" },
                "Estado" to doc.estado.ifBlank { "RECIBIDO" },
                "Urgencia" to doc.urgencia.ifBlank { "ORDINARIO" }
            )

            for ((label, valor) in campos) {
                canvas.drawText("$label:", 40f, y, paintGris)
                canvas.drawText(valor, 160f, y, paintTexto)
                y += 20f
            }

            y += 15f
            canvas.drawLine(40f, y, 555f, y, paintDorado)
            y += 30f

            val textoQr = doc.codigoQr.ifBlank { "GESTIONN::${doc.uuid}" }
            val qrBmp = qrBitmap(textoQr, 120, 120)
            if (qrBmp != null) {
                canvas.drawBitmap(qrBmp, 40f, y, null)
                canvas.drawText("Verificación Digital Guardia Nacional", 180f, y + 25f, paintVerde)
                canvas.drawText("UUID: ${doc.uuid}", 180f, y + 45f, paintGris)
                canvas.drawText("Sello Digital: Auténtico", 180f, y + 65f, paintGris)
            }

            documento.finishPage(page)
            FileOutputStream(destino).use { outs ->
                documento.writeTo(outs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                documento.close()
            } catch (_: Exception) {}
        }

        return destino
    }

    private fun qrBitmap(texto: String, ancho: Int, alto: Int): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, ancho, alto)
            val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
            for (x in 0 until ancho) {
                for (y in 0 until alto) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun dibujarCapaTextoInvisible(
        canvas: Canvas,
        lineas: List<LineaOcr>,
        destino: RectF,
        anchoOriginal: Int,
        altoOriginal: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 0
        }
        val factorX = destino.width() / anchoOriginal
        val factorY = destino.height() / altoOriginal

        for (linea in lineas) {
            val caja: Rect = linea.caja
            if (caja.isEmpty) continue
            val escala = minOf(caja.width() / 6f, caja.height() / 2f).coerceAtLeast(1f)
            paint.textSize = escala * 2f

            val centroX = destino.left + (caja.exactCenterX() * factorX)
            val centroY = destino.top + (caja.exactCenterY() * factorY)
            val anchoTexto = paint.measureText(linea.texto)
            val x = centroX - anchoTexto / 2f
            val y = centroY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(linea.texto, x, y, paint)
        }
    }

    private fun guardarEnDescargas(
        context: Context,
        mime: String,
        nombre: String,
        escribir: (OutputStream) -> Unit
    ): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var uriInsertada = intentarInsertarMediaStore(context, mime, nombre)

            if (uriInsertada == null) {
                val base = nombre.substringBeforeLast(".")
                val ext = if (nombre.contains(".")) ".${nombre.substringAfterLast(".")}" else ""
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                uriInsertada = intentarInsertarMediaStore(context, mime, "${base}_$stamp$ext")
            }

            if (uriInsertada == null) {
                val base = nombre.substringBeforeLast(".")
                val ext = if (nombre.contains(".")) ".${nombre.substringAfterLast(".")}" else ""
                uriInsertada = intentarInsertarMediaStore(context, mime, "${base}_${System.currentTimeMillis()}$ext")
            }

            if (uriInsertada != null) {
                try {
                    context.contentResolver.openOutputStream(uriInsertada)?.use { escribir(it) }
                        ?: error("No se pudo escribir en el OutputStream de MediaStore")

                    val valoresFinales = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uriInsertada, valoresFinales, null, null)
                    return uriInsertada
                } catch (e: Exception) {
                    try { context.contentResolver.delete(uriInsertada, null, null) } catch (_: Exception) {}
                }
            }

            return guardarEnAlmacenamientoPrivado(context, nombre, escribir)
        }

        return try {
            val descargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!descargas.exists()) descargas.mkdirs()
            val destino = File(descargas, nombre)
            FileOutputStream(destino).use { escribir(it) }
            Uri.fromFile(destino)
        } catch (_: Exception) {
            guardarEnAlmacenamientoPrivado(context, nombre, escribir)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun intentarInsertarMediaStore(
        context: Context,
        mime: String,
        nombre: String
    ): Uri? = try {
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nombre)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
    } catch (_: Exception) {
        null
    }

    private fun guardarEnAlmacenamientoPrivado(
        context: Context,
        nombre: String,
        escribir: (OutputStream) -> Unit
    ): Uri {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        val destino = File(dir, nombre)
        FileOutputStream(destino).use { escribir(it) }
        return Uri.fromFile(destino)
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.isBlank()) "Documento" else cleaned
    }

    private fun Bitmap.toSoftwareArgb8888(): Bitmap {
        if (!isRecycled && config == Bitmap.Config.ARGB_8888) return this
        val copia = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(copia).drawBitmap(this, 0f, 0f, null)
        return copia
    }
}