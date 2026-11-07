package com.gn.gestion.views

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import com.gn.gestion.scanner.DocumentClassifier
import com.gn.gestion.scanner.EncabezadoDocumento
import com.gn.gestion.viewmodels.EscaneoViewModel
import com.gn.gestion.viewmodels.guardarPdf

suspend fun prepararNuevoDocumento(
    context: Context,
    vm: EscaneoViewModel,
    pages: List<Bitmap>
): Bundle {
    val ocrPorPagina = pages.map { vm.analizarOcr(context, it) }
    val textoOcr = ocrPorPagina.joinToString("\n") { it.texto }.trim()

    val cabeza = ocrPorPagina.firstOrNull()
        ?.let { EncabezadoDocumento.detectar(it) }
        ?: EncabezadoDocumento.Encabezado("", "")
    val detalle = ocrPorPagina.firstOrNull()
        ?.let { EncabezadoDocumento.descripcionCorta(it) }
        .orEmpty()
    val asuntoFinal = when {
        cabeza.asunto.isNotBlank() && detalle.isNotBlank() ->
            if (detalle.startsWith(cabeza.asunto)) cabeza.asunto else "${cabeza.asunto}\n\n$detalle"
        cabeza.asunto.isNotBlank() -> cabeza.asunto
        else -> detalle
    }

    val folioFinal = cabeza.folio
    val nombreArchivo = folioFinal.ifBlank {
        DocumentClassifier.clasificar(textoOcr).nombreSugerido
    }
    val path = guardarPdf(context, pages, nombreArchivo, ocrPorPagina)

    return Bundle().apply {
        putString("folio", folioFinal)
        putString("asunto", asuntoFinal)
        putString("pdfPath", path)
        putString("textoOcr", textoOcr)
    }
}