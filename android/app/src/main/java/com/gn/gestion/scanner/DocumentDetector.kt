package com.gn.gestion.scanner;

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detector de bordes del documento y corrección de perspectiva.
 *
 * Algoritmo similar a los scanneadores estándar:
 * 1. Se trabaja sobre una versión reducida en escala de grises para rendimiento.
 * 2. Se aplica reducción de ruido (blur medio) y binarización adaptativa.
 * 3. Se detecta el contorno de mayor área entre el fondo y el documento (el
 *    papel suele ser lo más claro o un bloque uniforme).
 * 4. Se ajusta el cuadrilátero de 4 esquinas y se aplica corrección de
 *    perspectiva (homografía) para generar una vista "frontal" del documento.
 */
object DocumentDetector {

    /** Esquinas (4 puntos) del documento detectado en las coordenadas de la imagen original. */
    class CornerResult(val corners: List<PointF>, val boxScaleX: Float, val boxScaleY: Float)

    /**
     * Detecta el contorno del documento en la imagen.
     * Devuelve las 4 esquinas en píxeles de la imagen original (o null si no detecta).
     */
    fun detectCorners(bitmap: Bitmap): List<PointF>? {
        // Escala de análisis (mantenerla pequeña para velocidad)
        val scale = 0.2f
        val smallW = max(1, (bitmap.width * scale).toInt())
        val smallH = max(1, (bitmap.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)

        try {
            val gray = IntArray(smallW * smallH)
            for (y in 0 until smallH) {
                var idx = y * smallW
                for (x in 0 until smallW) {
                    val c = small.getPixel(x, y)
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    gray[idx++] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                }
            }

            // Reducción de ruido (promedio de 3x3) y gradiente
            val blurred = blur(gray, smallW, smallH)

            // Umbral adaptativo local: marca píxeles que difieren de su vecindad
            // (bordes del papel contra mesa/fondo)
            val edges = adaptiveThreshold(blurred, smallW, smallH)

            // Extraer puntos candidatos de borde (las coordenadas en la imagen pequeña)
            val points = ArrayList<PointF>()
            for (y in 1 until smallH - 1) {
                var idx = y * smallW
                for (x in 1 until smallW - 1) {
                    if (edges[idx] > 0) {
                        points.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }

            if (points.size < 4) return null

            // Convex hull (Andrew monotone chain)
            val hull = convexHull(points)
            if (hull.size < 4) return null

            // Dejar los 4 puntos "extremos" -> esquinas aproximadas (usar el cuarto más lejano)
            val corners = approximateQuadSingle(hull)

            // Escalar de regreso a la imagen original
            val sx = bitmap.width.toFloat() / smallW
            val sy = bitmap.height.toFloat() / smallH
            return corners.map { PointF(it.x * sx, it.y * sy) }
        } finally {
            if (small != bitmap) small.recycle()
        }
    }

    /**
     * Aplica corrección de perspectiva: mapea el cuadrilátero [src] a un
     * rectángulo y devuelve el documento "frontal" mejorado.
     */
    fun cropAndStraighten(
        bitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap {
        val pts = orderPoints(corners)
        val (tl, tr, br, bl) = pts

        // Dimensiones de salida (basadas en las longitudes de los lados)
        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)
        val outW = max(1, max(widthTop, widthBottom).toInt())
        val outH = max(1, max(heightLeft, heightRight).toInt())

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

        // Matriz de homografía H tal que H*(src) = dst.
        // dst = (0,0),(outW,0),(outW,outH),(0,outH)
        val srcX = floatArrayOf(tl.x, tr.x, br.x, bl.x)
        val srcY = floatArrayOf(tl.y, tr.y, br.y, bl.y)
        val dstX = floatArrayOf(0f, outW.toFloat(), outW.toFloat(), 0f)
        val dstY = floatArrayOf(0f, 0f, outH.toFloat(), outH.toFloat())

        // solve min-cost at each dst pixel: inverse mapping via homography
        val hom = solveHomography(srcX, srcY, dstX, dstY)

        // build inverse for bilinear sampling
        val inv = invertHomography(hom)
        val srcPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(srcPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val outPixels = IntArray(outW * outH)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val (sxr, syr) = applyHomography(inv, x.toFloat(), y.toFloat())
                val px = sxr.toInt()
                val py = syr.toInt()
                if (px in 0 until bitmap.width && py in 0 until bitmap.height) {
                    outPixels[y * outW + x] = srcPixels[py * bitmap.width + px]
                } else {
                    outPixels[y * outW + x] = Color.WHITE
                }
            }
        }
        result.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

        // Recorte de márgenes blancos producidos (pequeño recorte del borde)
        val trimmed = trimWhiteBorders(result, 0.02f)
        if (trimmed != result) result.recycle()
        return trimmed
    }

    // ------------------------------------------------------------------
    // Utilidades de imagen pequeña
    // ------------------------------------------------------------------
    private fun blur(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var n = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val yy = y + dy
                        val xx = x + dx
                        if (yy in 0 until h && xx in 0 until w) {
                            sum += src[yy * w + xx]
                            n++
                        }
                    }
                }
                out[y * w + x] = sum / n
            }
        }
        return out
    }

    // Marca como borde los píxeles cuya diferencia con la media local es alta.
    private fun adaptiveThreshold(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        val radius = 8
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var n = 0
                for (dy in -radius..radius) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -radius..radius) {
                        val xx = x + dx
                        if (xx in 0 until w) {
                            sum += src[yy * w + xx]
                            n++
                        }
                    }
                }
                val mean = sum / n
                val v = src[y * w + x]
                // Alto contraste local -> posible borde del documento
                if (abs(v - mean) > 30) {
                    out[y * w + x] = 255
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Geometría
    // ------------------------------------------------------------------
    private fun distance(a: PointF, b: PointF): Float =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    /** Ordena los 4 puntos de forma geométrica precisa: TL, TR, BR, BL */
    private fun orderPoints(pts: List<PointF>): Array<PointF> {
        val sortedByY = pts.sortedBy { it.y }
        val topTwo = sortedByY.take(2).sortedBy { it.x }
        val tl = topTwo[0]
        val tr = topTwo[1]

        val bottomTwo = sortedByY.drop(2).sortedBy { it.x }
        val bl = bottomTwo[0]
        val br = bottomTwo[1]

        return arrayOf(tl, tr, br, bl)
    }

    /** Convex hull (Andrew monotone chain). */
    private fun convexHull(points: List<PointF>): List<PointF> {
        val p = points.sortedWith(
            compareBy({ it.x }, { it.y })
        )
        if (p.size <= 1) return p

        val lower = ArrayList<PointF>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], pt) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(pt)
        }
        val upper = ArrayList<PointF>()
        for (i in p.indices.reversed()) {
            val pt = p[i]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], pt) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(pt)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        lower.addAll(upper)
        return lower
    }

    private fun cross(o: PointF, a: PointF, b: PointF): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    /** Reduce el hull a ~4 esquinas seleccionando los extremos por pliegue de la forma. */
    private fun approximateQuadSingle(hull: List<PointF>): List<PointF> {
        if (hull.size <= 4) return hull

        // Primero encontrar los 4 extremos geométricos (TL, TR, BR, BL) en O(N)
        var tlIdx = 0; var brIdx = 0; var trIdx = 0; var blIdx = 0
        var minSum = Float.MAX_VALUE; var maxSum = -Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE; var minDiff = Float.MAX_VALUE

        for (i in hull.indices) {
            val p = hull[i]
            val sum = p.x + p.y
            val diff = p.x - p.y
            if (sum < minSum) { minSum = sum; tlIdx = i }
            if (sum > maxSum) { maxSum = sum; brIdx = i }
            if (diff > maxDiff) { maxDiff = diff; trIdx = i }
            if (diff < minDiff) { minDiff = diff; blIdx = i }
        }

        val uniqueIndices = mutableSetOf(tlIdx, trIdx, brIdx, blIdx)
        if (uniqueIndices.size == 4) {
            return listOf(hull[tlIdx], hull[trIdx], hull[brIdx], hull[blIdx])
        }

        // Si los extremos coinciden, usamos una búsqueda combinatoria ultra rápida reduciendo el hull
        val sampledHull = if (hull.size > 12) {
            hull.filterIndexed { index, _ -> index in uniqueIndices || index % (hull.size / 6) == 0 }
        } else {
            hull
        }

        var best: List<PointF> = sampledHull.take(4)
        var bestArea = -1f
        val size = sampledHull.size
        for (a in 0 until size) {
            for (b in a + 1 until size) {
                for (c in b + 1 until size) {
                    for (d in c + 1 until size) {
                        val quad = listOf(sampledHull[a], sampledHull[b], sampledHull[c], sampledHull[d])
                        val area = quadArea(quad)
                        if (area > bestArea) {
                            bestArea = area
                            best = quad
                        }
                    }
                }
            }
        }
        return best
    }

    private fun quadArea(q: List<PointF>): Float {
        // Fórmula del área del cuadrilátero (shoelace)
        var sum = 0f
        for (i in q.indices) {
            val j = (i + 1) % q.size
            sum += q[i].x * q[j].y - q[j].x * q[i].y
        }
        return abs(sum / 2f)
    }

    // ------------------------------------------------------------------
    // Homografía (Direct Linear Transform simplificado con resolución por inversa)
    // ------------------------------------------------------------------
    private fun solveHomography(
        srcX: FloatArray, srcY: FloatArray,
        dstX: FloatArray, dstY: FloatArray
    ): FloatArray {
        // H (3x3, fila mayor) tal que H * [src,1] = [dst,1]
        val a = Array(8) { FloatArray(9) }
        for (i in 0..3) {
            val x = srcX[i]; val y = srcY[i]
            val u = dstX[i]; val v = dstY[i]
            a[2 * i] = floatArrayOf(-x, -y, -1f, 0f, 0f, 0f, u * x, u * y, u)
            a[2 * i + 1] = floatArrayOf(0f, 0f, 0f, -x, -y, -1f, v * x, v * y, v)
        }
        return solveLeastSquares(a)
    }

    private fun applyHomography(h: FloatArray, u: Float, v: Float): Pair<Float, Float> {
        val w = h[6] * u + h[7] * v + h[8]
        val x = (h[0] * u + h[1] * v + h[2]) / w
        val y = (h[3] * u + h[4] * v + h[5]) / w
        return x to y
    }

    private fun invertHomography(h: FloatArray): FloatArray {
        val m = Array(3) { FloatArray(3) }
        for (i in 0 until 3) for (j in 0 until 3) m[i][j] = h[i * 3 + j]
        val inv = invert3x3(m)
        return FloatArray(9) { inv[it / 3][it % 3] }
    }

    private fun invert3x3(m: Array<FloatArray>): Array<FloatArray> {
        val det =
            m[0][0] * (m[1][1] * m[2][2] - m[2][1] * m[1][2]) -
            m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
            m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
        val inv = Array(3) { FloatArray(3) }
        if (abs(det) < 1e-9) {
            inv[0][0] = 1f; inv[1][1] = 1f; inv[2][2] = 1f
            return inv
        }
        inv[0][0] = (m[1][1] * m[2][2] - m[2][1] * m[1][2]) / det
        inv[0][1] = (m[0][2] * m[2][1] - m[0][1] * m[2][2]) / det
        inv[0][2] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) / det
        inv[1][0] = (m[1][2] * m[2][0] - m[1][0] * m[2][2]) / det
        inv[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) / det
        inv[1][2] = (m[0][2] * m[1][0] - m[0][0] * m[1][2]) / det
        inv[2][0] = (m[1][0] * m[2][1] - m[1][1] * m[2][0]) / det
        inv[2][1] = (m[0][1] * m[2][0] - m[0][0] * m[2][1]) / det
        inv[2][2] = (m[0][0] * m[1][1] - m[0][1] * m[1][0]) / det
        return inv
    }

    private fun solveLeastSquares(a: Array<FloatArray>): FloatArray {
        // SVD pequeño o eliminación Gauss-Jordan sobre el sistema 8x8 (8x9 aumentada)
        val m = Array(8) { FloatArray(9) }
        for (i in 0 until 8) for (j in 0 until 9) m[i][j] = a[i][j]
        gaussJordan(m, 8)
        val h = FloatArray(9)
        for (i in 0 until 8) h[i] = m[i][8]
        h[8] = 1f
        return h
    }

    private fun gaussJordan(m: Array<FloatArray>, n: Int) {
        for (col in 0 until n) {
            var pivot = col
            for (r in col until n) {
                if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            }
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            val pv = m[col][col]
            if (abs(pv) < 1e-9) continue
            for (c in col..n) m[col][c] /= pv
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col]
                if (abs(factor) < 1e-9) continue
                for (c in col..n) m[r][c] -= factor * m[col][c]
            }
        }
    }

    /** Recorta márgenes blancos casi uniformes resultantes del remapeo. */
    private fun trimWhiteBorders(bmp: Bitmap, marginRatio: Float): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val minX = (w * marginRatio).toInt().coerceAtLeast(0)
        val minY = (h * marginRatio).toInt().coerceAtLeast(0)
        val maxX = (w * (1f - marginRatio)).toInt().coerceAtMost(w - 1)
        val maxY = (h * (1f - marginRatio)).toInt().coerceAtMost(h - 1)
        if (maxX <= minX || maxY <= minY) return bmp
        return Bitmap.createBitmap(bmp, minX, minY, maxX - minX, maxY - minY)
    }
}
