package com.gn.gestion.scanner;

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gn.gestion.R

/**
 * Resalta las cajas de texto detectadas por OCR con un borde
 * dorado brillante, estilo "resaltado de texto en pantalla".
 */
class OcrHighlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dim = resources.displayMetrics.density

    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.g_dorado)
        style = Paint.Style.STROKE
        strokeWidth = 3f * dim
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(16f * dim, 0f, 0f, colorCompat(R.color.g_doradoc))
    }
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 212, 193, 156)
        style = Paint.Style.FILL
    }
    private val paintCorners = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * dim
        strokeCap = Paint.Cap.ROUND
    }

    private var cajas = emptyList<RectF>()

    private fun colorCompat(resId: Int): Int {
        @Suppress("DEPRECATION")
        return resources.getColor(resId, context.theme)
    }

    /** Define las cajas a resaltar (coordenadas del bitmap) y el tamaño del bitmap. */
    fun mostrarCajas(cajasBitmap: List<Rect>, anchoBmp: Int, altoBmp: Int) {
        if (anchoBmp <= 0 || altoBmp <= 0) {
            this.cajas = emptyList()
            invalidate()
            return
        }
        this.cajas = cajasBitmap.map { r ->
            RectF(
                r.left / anchoBmp.toFloat() * width,
                r.top / altoBmp.toFloat() * height,
                r.right / anchoBmp.toFloat() * width,
                r.bottom / altoBmp.toFloat() * height
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cajas.isEmpty()) return
        val tick = 8f * dim
        cajas.forEach { r ->
            canvas.drawRoundRect(r, 6f * dim, 6f * dim, paintFill)
            canvas.drawRoundRect(r, 6f * dim, 6f * dim, paintBorder)
            // esquinas decorativas
            canvas.drawLine(r.left, r.top, r.left + tick, r.top, paintCorners)
            canvas.drawLine(r.left, r.top, r.left, r.top + tick, paintCorners)
            canvas.drawLine(r.right, r.bottom, r.right - tick, r.bottom, paintCorners)
            canvas.drawLine(r.right, r.bottom, r.right, r.bottom - tick, paintCorners)
        }
    }
}