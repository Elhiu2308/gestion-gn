package com.gn.gestion.views.pdf;

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gn.gestion.R
import com.gn.gestion.views.PdfArchivo;
import com.gn.gestion.util.c
import com.gn.gestion.util.dp
import java.io.File

/**
 * Muestra una vista previa del PDF que ya está guardado en el dispositivo.
 * Usa la API nativa [PdfRenderer], sin dependencias externas.
 */
class PdfViewerFragment : Fragment(R.layout.fragment_pdf_viewer) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val path = arguments?.getString("pdfPath")
        val container = view.findViewById<LinearLayout>(R.id.container_paginas)
        container.removeAllViews()

        val file = PdfArchivo.leer(requireContext(), path)
        view.findViewById<TextView>(R.id.pdf_titulo).text =
            file?.name ?: path?.substringAfterLast('/') ?: getString(R.string.pdf_sin_ruta)
        if (file == null) {
            view.findViewById<TextView>(R.id.pdf_titulo).text =
                getString(R.string.pdf_no_encontrado)
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.pdf_no_encontrado)
                textSize = 14f
                setTextColor(requireContext().c(R.color.gris_texto))
                setPadding(0, 24, 0, 0)
                gravity = android.view.Gravity.CENTER
            })
            return
        }

        runCatching { renderPdf(requireContext(), file, container) }
            .onFailure {
                container.removeAllViews()
                container.addView(TextView(requireContext()).apply {
                    text = getString(R.string.pdf_no_encontrado)
                    textSize = 14f
                    setTextColor(requireContext().c(R.color.gris_texto))
                    setPadding(0, 24, 0, 0)
                    gravity = android.view.Gravity.CENTER
                })
            }
    }

    private fun renderPdf(
        ctx: Context,
        file: File,
        container: LinearLayout
    ) {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val density = ctx.resources.displayMetrics.density
            val displayWidth = ctx.resources.displayMetrics.widthPixels
            repeat(renderer.pageCount) { index ->
                renderer.openPage(index).use { page ->
                    // Escala para que la página quepa al ancho de la pantalla.
                    val scale = displayWidth / (page.width * density)
                    val w = (page.width * density * scale).toInt()
                    val h = (page.height * density * scale).toInt()
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val pageBox = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(ctx, 6)
                            bottomMargin = dp(ctx, 10)
                        }
                    }

                    val img = ImageView(ctx).apply {
                        setImageBitmap(bitmap)
                        setBackgroundColor(Color.WHITE)
                        adjustViewBounds = true
                        contentDescription = ctx.getString(R.string.pdf_pagina, index + 1)
                    }

                    val lbl = TextView(ctx).apply {
                        text = ctx.getString(R.string.pdf_pagina, index + 1)
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(ctx.c(R.color.gris_texto))
                    }

                    pageBox.addView(img)
                    pageBox.addView(lbl)
                    container.addView(pageBox)
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }
}