package com.gn.gestion.scanner;

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gn.gestion.R

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dim = resources.displayMetrics.density

    private val paintMask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
    }
    private val paintFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.g_dorado)
        style = Paint.Style.STROKE
        strokeWidth = 3f * dim
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(10f * dim, 0f, 0f, colorCompat(R.color.g_doradoc))
    }
    private val paintCorner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f * dim
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(14f * dim, 0f, 0f, colorCompat(R.color.g_dorado))
    }
    private val paintMarker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.g_doradoc)
        style = Paint.Style.FILL
    }
    private val paintLineOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.g_dorado)
        style = Paint.Style.STROKE
        strokeWidth = 6f * dim
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(22f * dim, 0f, 0f, colorCompat(R.color.g_doradoc))
    }
    private val paintLineCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * dim
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(10f * dim, 0f, 0f, Color.WHITE)
    }

    private val paintQuadFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 0, 200, 120)
        style = Paint.Style.FILL
    }
    private val paintQuadOk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.verde_claro)
        style = Paint.Style.STROKE
        strokeWidth = 3f * dim
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(12f * dim, 0f, 0f, colorCompat(R.color.verde_claro))
    }
    private val paintQuadMove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.ambar)
        style = Paint.Style.STROKE
        strokeWidth = 3f * dim
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(12f * dim, 0f, 0f, colorCompat(R.color.ambar))
    }
    private val paintVertexOk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.verde_claro)
        style = Paint.Style.FILL
    }
    private val paintVertexMove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.ambar)
        style = Paint.Style.FILL
    }
    private val paintBarBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val paintBarFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCompat(R.color.verde_claro)
        style = Paint.Style.FILL
    }

    private var sweep = 0f
    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private var contorno: List<PointF>? = null
    private var contornoEstable = false
    private var estabilidad = 0f

    private fun colorCompat(resId: Int): Int {
        @Suppress("DEPRECATION")
        return resources.getColor(resId, context.theme)
    }

    fun startScan() {
        if (animator?.isRunning == true) return
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 2400L
        a.repeatMode = ValueAnimator.RESTART
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener { va ->
            val t = va.animatedValue as Float
            sweep = if (t < 0.5f) t * 2f else (1f - t) * 2f
            pulse = ((Math.sin(t * Math.PI * 8.0) + 1.0) / 2.0).toFloat()
            invalidate()
        }
        a.start()
        animator = a
    }

    fun stopScan() {
        animator?.cancel()
        animator = null
    }

    fun setContorno(pts: List<PointF>?, estable: Boolean) {
        contorno = pts
        contornoEstable = estable
        invalidate()
    }

    fun setEstabilidad(valor: Float) {
        estabilidad = valor.coerceIn(0f, 1f)
        invalidate()
    }

    fun limpiarContorno() {
        contorno = null
        estabilidad = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopScan()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cuad = contorno
        if (cuad != null && cuad.size == 4) {
            dibujarContorno(canvas, cuad, w, h)
        } else {
            dibujarMarcoFijo(canvas, w, h)
        }

        if (contorno != null) {
            dibujarBarraEstabilidad(canvas)
        }
    }

    private fun dibujarMarcoFijo(canvas: Canvas, w: Float, h: Float) {
        val m = 28f * dim
        val frame = RectF(m, h * 0.16f, w - m, h * 0.78f)

        canvas.drawRect(0f, 0f, w, frame.top, paintMask)
        canvas.drawRect(0f, frame.bottom, w, h, paintMask)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, paintMask)
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, paintMask)

        canvas.drawRect(frame, paintFrame)

        val laserY = frame.top + sweep * frame.height()
        canvas.drawLine(frame.left, laserY, frame.right, laserY, paintLineOuter)
        canvas.drawLine(frame.left, laserY, frame.right, laserY, paintLineCore)

        val diamond = paintLineCore.apply { style = Paint.Style.FILL }
        val dh = 8f * dim
        canvas.drawCircle(frame.left, laserY, dh, diamond)
        canvas.drawCircle(frame.right, laserY, dh, diamond)

        val cLen = 38f * dim
        val cw = 4f * dim
        drawCorner(canvas, frame.left, frame.top, cLen, cw)
        drawCorner(canvas, frame.right - cLen, frame.top, cLen, cw)
        drawCorner(canvas, frame.left, frame.bottom - cLen, cLen, cw)
        drawCorner(canvas, frame.right - cLen, frame.bottom - cLen, cLen, cw)

        val pr = 5f * dim + pulse * 3f * dim
        val midT = (frame.top + frame.bottom) / 2f
        val midL = (frame.left + frame.right) / 2f
        paintMarker.alpha = (120 + 135 * pulse).toInt()
        canvas.drawCircle(midL, frame.top, pr, paintMarker)
        canvas.drawCircle(midL, frame.bottom, pr, paintMarker)
        canvas.drawCircle(frame.left, midT, pr, paintMarker)
        canvas.drawCircle(frame.right, midT, pr, paintMarker)
        paintMarker.alpha = 255
    }

    private fun dibujarContorno(canvas: Canvas, cuad: List<PointF>, w: Float, h: Float) {

        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, w, h, Path.Direction.CW)
            moveTo(cuad[0].x, cuad[0].y)
            lineTo(cuad[1].x, cuad[1].y)
            lineTo(cuad[2].x, cuad[2].y)
            lineTo(cuad[3].x, cuad[3].y)
            close()
        }
        canvas.drawPath(path, paintMask)

        val quad = Path().apply {
            moveTo(cuad[0].x, cuad[0].y)
            lineTo(cuad[1].x, cuad[1].y)
            lineTo(cuad[2].x, cuad[2].y)
            lineTo(cuad[3].x, cuad[3].y)
            close()
        }
        canvas.drawPath(quad, paintQuadFill)
        canvas.drawPath(quad, if (contornoEstable) paintQuadOk else paintQuadMove)

        val r = 7f * dim + pulse * 2f * dim
        val vp = if (contornoEstable) paintVertexOk else paintVertexMove
        for (p in cuad) {
            canvas.drawCircle(p.x, p.y, r, vp)
        }
    }

    private fun dibujarBarraEstabilidad(canvas: Canvas) {
        val barW = width * 0.62f
        val barH = 7f * dim
        val left = (width - barW) / 2f
        val top = height * 0.90f

        val bg = RectF(left, top, left + barW, top + barH)
        canvas.drawRoundRect(bg, barH / 2f, barH / 2f, paintBarBg)

        val fill = estabilidad * barW
        val fg = RectF(left, top, left + fill.coerceAtLeast(barH), top + barH)
        paintBarFill.color = if (estabilidad >= 0.95f) colorCompat(R.color.verde_claro) else colorCompat(R.color.ambar)
        canvas.drawRoundRect(fg, barH / 2f, barH / 2f, paintBarFill)
    }

    private fun drawCorner(canvas: Canvas, x: Float, y: Float, len: Float, thickness: Float) {
        val p = paintCorner.apply { strokeWidth = thickness }
        canvas.drawLine(x, y, x + len, y, p)
        canvas.drawLine(x, y, x, y + len, p)
    }
}