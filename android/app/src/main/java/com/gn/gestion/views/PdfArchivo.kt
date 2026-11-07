package com.gn.gestion.views

import android.content.Context
import android.net.Uri
import com.gn.gestion.models.Documento
import com.gn.gestion.scanner.PdfGenerator
import java.io.File
import java.io.FileOutputStream
import android.os.Environment

object PdfArchivo {

    fun leer(context: Context, ruta: String?): File? {
        if (ruta.isNullOrBlank()) return null

        val directo = File(ruta)
        if (directo.exists() && directo.isFile && directo.length() > 0L) return directo

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

        return copiarDeMediaStore(context, ruta)
    }

    fun obtenerOGenerarPdf(context: Context, doc: Documento): File {

        if (!doc.pdfPath.isNullOrBlank()) {
            leer(context, doc.pdfPath)?.let { return it }
        }

        if (doc.pdfNombre.isNotBlank()) {
            leer(context, doc.pdfNombre)?.let { return it }
        }

        val folioLimpio = PdfGenerator.sanitizeFileName(doc.folio)
        if (folioLimpio.isNotBlank()) {
            leer(context, "$folioLimpio.pdf")?.let { return it }
        }

        val uuidLimpio = PdfGenerator.sanitizeFileName(doc.uuid)
        if (uuidLimpio.isNotBlank()) {
            leer(context, "$uuidLimpio.pdf")?.let { return it }
        }

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