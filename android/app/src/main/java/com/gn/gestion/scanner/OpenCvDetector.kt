package com.gn.gestion.scanner;

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Detección de esquinas del documento usando OpenCV.
 *
 * Se intenta inicializar OpenCV de forma perezosa; si la librería nativa no
 * está disponible se devuelve null y la app usa el detector Kotlin
 * (DocumentDetector) como respaldo.
 */
object OpenCvDetector {

    private const val LADO_MAX = 1600f

    @Volatile
    private var listo = false

    private fun initYUsable(): Boolean {
        if (listo) return true
        listo = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        return listo
    }

    /** Devuelve las 4 esquinas en coordenadas de la imagen original (o null). */
    fun detectCorners(bitmap: Bitmap): List<PointF>? {
        if (!initYUsable()) return null
        return runCatching { detectar(bitmap) }.getOrNull()
    }

    private fun detectar(bitmap: Bitmap): List<PointF>? {
        val mayorLado = max(bitmap.width, bitmap.height).toFloat().coerceAtLeast(1f)
        val escala = if (mayorLado > LADO_MAX) LADO_MAX / mayorLado else 1f
        val w = (bitmap.width * escala).toInt().coerceAtLeast(1)
        val h = (bitmap.height * escala).toInt().coerceAtLeast(1)
        val pequena = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val rgb = Mat()
        val gray = Mat()
        val edges = Mat()
        val approx = MatOfPoint2f()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Utils.bitmapToMat(pequena, rgb)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // Buscar entre los contornos ordenados por área descendente el primero que represente un cuadrilátero válido
            val candidatosSorted = contours.sortedByDescending { Imgproc.contourArea(it) }
            var foundPts: Array<Point>? = null
            
            for (candidato in candidatosSorted) {
                val area = Imgproc.contourArea(candidato)
                // Ignorar contornos extremadamente pequeños (menos del 4% de la imagen)
                if (area < (w * h * 0.04f)) continue
                
                val cnt = MatOfPoint2f(*candidato.toArray())
                val epsilon = 0.02 * Imgproc.arcLength(cnt, true)
                Imgproc.approxPolyDP(cnt, approx, epsilon, true)
                
                if (approx.total() == 4L) {
                    foundPts = approx.toArray()
                    break
                }
            }
            
            if (foundPts == null) return null

            val pts = foundPts
            // Escala inversa a la imagen original + orden TL, TR, BR, BL.
            val escalaInv = 1f / escala
            val puntos = orderCorners(pts)
                .map { PointF((it.x * escalaInv).toFloat(), (it.y * escalaInv).toFloat()) }
            return puntos
        } finally {
            rgb.release(); gray.release(); edges.release()
            approx.release(); hierarchy.release()
            contours.forEach { it.release() }
            if (pequena != bitmap) pequena.recycle()
        }
    }

    /** Ordena un cuadrilátero de forma geométrica precisa como TL, TR, BR, BL. */
    private fun orderCorners(pts: Array<Point>): List<Point> {
        if (pts.size < 4) return pts.toList()
        
        // Ordenar primero por coordenada Y ascendente
        val sortedByY = pts.sortedBy { it.y }
        
        // Las dos de arriba tendrán los valores de Y más pequeños
        val topTwo = sortedByY.take(2).sortedBy { it.x }
        val tl = topTwo[0]
        val tr = topTwo[1]
        
        // Las dos de abajo tendrán los valores de Y más grandes
        val bottomTwo = sortedByY.drop(2).sortedBy { it.x }
        val bl = bottomTwo[0]
        val br = bottomTwo[1]
        
        return listOf(tl, tr, br, bl)
    }
}