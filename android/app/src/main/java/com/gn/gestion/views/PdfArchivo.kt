package com.gn.gestion.views

import android.content.Context
import android.net.Uri
import com.gn.gestion.models.Documento
import com.gn.gestion.scanner.PdfGenerator
import java.io.File
import java.io.FileOutputStream
import android.os.Environment

/**
 * Resuelve a un archivo legible la ruta guardada de un PDF o genera
 * un PDF oficial en caso de que aún no exista un archivo escaneado.
 */
object PdfArchivo {

    fun leer(context: Context, ruta: String?): File? {
        if (ruta.isNullOrBlank()) return null

        // 1. Ruta directa / absoluta
        val directo = File(ruta)
        if (directo.exists() && directo.isFile && directo.length() > 0L) return directo

        // 2. Buscar por el nombre del archivo en carpetas conocidas
        val nombre = ruta.substringAfterLast('/')
        val dirs = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "pdfs"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "pdfs"),
            File(context.cacheDir, "pdfs"),
            File(context.filesDir, "pdfs"),
            context.filesDir,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        for (dir in dirs) {
            val candidato = File(dir, nombre)
            if (candidato.exists() && candidato.isFile && candidato.length() > 0L) {
                return candidato
            }
        }

        // 3. MediaStore
        return copiarDeMediaStore(context, ruta)
    }

    /**
     * Obtiene o genera siempre un archivo PDF válido para un [Documento].
     */
    fun obtenerOGenerarPdf(context: Context, doc: Documento): File {
        // A) Buscar por pdfPath si existe
        if (!doc.pdfPath.isNullOrBlank()) {
            leer(context, doc.pdfPath)?.let { return it }
        }

        // B) Buscar por pdfNombre si existe
        if (doc.pdfNombre.isNotBlank()) {
            leer(context, doc.pdfNombre)?.let { return it }
        }

        // C) Buscar por folio / uuid
        val folioLimpio = PdfGenerator.sanitizeFileName(doc.folio)
        if (folioLimpio.isNotBlank()) {
            leer(context, "$folioLimpio.pdf")?.let { return it }
        }

        val uuidLimpio = PdfGenerator.sanitizeFileName(doc.uuid)
        if (uuidLimpio.isNotBlank()) {
            leer(context, "$uuidLimpio.pdf")?.let { return it }
        }

        // D) Si no existe ningún archivo físico previo, generar el PDF oficial del documento
        return PdfGenerator.generarPdfDocumento(context, doc)
    }

    private fun copiarDeMediaStore(context: Context, ruta: String): File? = try {
        val uri = when {
            ruta.startsWith("content://") -> Uri.parse(ruta)
            ruta.startsWith("/external/") -> Uri.parse("content://media$ruta")
            else -> return null
        }
        val id = uri.lastPathSegment ?: return null
        val dir = File(context.cacheDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        val destino = File(dir, "$id.pdf")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(destino).use { outs -> ins.copyTo(outs) }
        }
        destino.takeIf { it.exists() && it.length() > 0L }
    } catch (_: Exception) {
        null
    }
}