package com.gn.gestion.scanner;

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Canvas

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    MAGIC("Auto-Mejora"),
    BW("Blanco y Negro"),
    GRAYSCALE("Grises")
}

/**
 * Procesador de imágenes del "escáner". Aplica filtros profesionales
 * (auto-contraste, nivel de negro, máscara de enfoque) para que el
 * resultado parezca capturado con un escáner real.
 */
object ImageProcessor {

    fun maxScaleDown(bitmap: Bitmap, maxDimension: Int = 2200): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxDim
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun applyFilter(
        original: Bitmap,
        filter: ScanFilter,
        brightness: Int = 0,
        contrast: Int = 0
    ): Bitmap {
        return when (filter) {
            ScanFilter.ORIGINAL -> {
                if (brightness == 0 && contrast == 0) original
                else adjustBrightnessContrast(original, brightness, contrast)
            }
            ScanFilter.GRAYSCALE -> {
                // Escala de grises tipo escáner físico: desaturación + auto-nivelar fondo + enfoque de texto
                val bmp = adjustBrightnessContrast(original, brightness, contrast)
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                val grayscale = applyColorMatrix(bmp, matrix)
                if (bmp != original) bmp.recycle()
                val leveled = autoLevel(grayscale)
                val sharp = sharpen(leveled)
                if (leveled != original) leveled.recycle()
                sharp
            }
            ScanFilter.BW -> {
                // Filtro B/N Avanzado: Binarización Adaptativa Local de Bradley-Roth (Gaussian-like adaptive thresholding)
                // Elimina arrugas, sombras ambientales y el color amarillento, dejando texto negro nítido y fondo blanco puro.
                val resAdapt = adaptiveThresholdLocal(original)
                val sharp = sharpen(resAdapt)
                resAdapt.recycle()
                sharp
            }
            ScanFilter.MAGIC -> {
                // Auto-mejora: contraste, nitidez, mejora del fondo
                val auto = adjustBrightnessContrast(original, brightness, contrast)
                val autoLevel = autoLevel(auto)
                val sharp = sharpen(autoLevel)
                if (auto != original) auto.recycle()
                sharp
            }
        }
    }

    private fun adjustBrightnessContrast(src: Bitmap, brightness: Int, contrast: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        if (brightness != 0 || contrast != 0) {
            val matrix = ColorMatrix()
            val b = brightness / 100f
            val c = contrast / 100f
            matrix.set(
                floatArrayOf(
                    1f + c, 0f, 0f, 0f, b * 255f,
                    0f, 1f + c, 0f, 0f, b * 255f,
                    0f, 0f, 1f + c, 0f, b * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Binarización Adaptativa Local (Umberlado de Bradley-Roth / Gaussian Thresholding simplificado).
     * Analiza bloques locales de la imagen para eliminar sombras ambientales uniformes y dejar el fondo
     * inmaculadamente blanco y las letras negras nítidas.
     */
    fun adaptiveThresholdLocal(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Crear una imagen integral (Summed-area table) para calcular medias locales en O(1)
        val integral = IntArray(width * height)
        for (y in 0 until height) {
            var sum = 0
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                sum += gray
                if (y == 0) {
                    integral[y * width + x] = sum
                } else {
                    integral[y * width + x] = integral[(y - 1) * width + x] + sum
                }
            }
        }
        
        // Ventana local adaptativa (S se basa en el tamaño de la imagen, ej: octava parte de la anchura)
        val S = width / 8
        val T = 0.15f // Tolerancia porcentual (15% más oscuro que la media local es texto)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = maxOf(0, x - S / 2)
                val x2 = minOf(width - 1, x + S / 2)
                val y1 = maxOf(0, y - S / 2)
                val y2 = minOf(height - 1, y + S / 2)
                
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                
                val sum = integral[y2 * width + x2] - 
                          (if (x1 > 0) integral[y2 * width + (x1 - 1)] else 0) - 
                          (if (y1 > 0) integral[(y1 - 1) * width + x2] else 0) + 
                          (if (x1 > 0 && y1 > 0) integral[(y1 - 1) * width + (x1 - 1)] else 0)
                
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                
                // Si el valor actual es notablemente más oscuro que la media local de su entorno, es texto (negro), de lo contrario es fondo (blanco puro)
                val v = if (gray * count < sum * (1.0f - T)) 0 else 255
                pixels[y * width + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Normaliza el histograma para que los fondos se aclaren y el texto se oscurezca.
     * Estira los valores de luminancia dentro del rango encontrado.
     */
    private fun autoLevel(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var min = 255
        var max = 0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            if (luma < min) min = luma
            if (luma > max) max = luma
        }
        if (max - min < 16) return src

        val range = (max - min).toFloat()
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF)
            val g = ((p shr 8) and 0xFF)
            val b = (p and 0xFF)
            val nr = (((r - min) * 255f / range)).toInt().coerceIn(0, 255)
            val ng = (((g - min) * 255f / range)).toInt().coerceIn(0, 255)
            val nb = (((b - min) * 255f / range)).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        val target = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        return target
    }

    /** Máscara de enfoque (unsharp masking) por convolución ligera. */
    private fun sharpen(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val inp = IntArray(width * height)
        src.getPixels(inp, 0, width, 0, 0, width, height)
        val rad = 1
        val rowPixels = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f; var g = 0f; var b = 0f; var count = 0
                for (dy in -rad..rad) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        val rowOffset = ny * width
                        for (dx in -rad..rad) {
                            val nx = x + dx
                            if (nx in 0 until width) {
                                val p = inp[rowOffset + nx]
                                r += (p shr 16) and 0xFF
                                g += (p shr 8) and 0xFF
                                b += p and 0xFF
                                count++
                            }
                        }
                    }
                }
                val orig = inp[y * width + x]
                val or = (orig shr 16) and 0xFF
                val og = (orig shr 8) and 0xFF
                val ob = orig and 0xFF
                val blurR = r / count
                val blurG = g / count
                val blurB = b / count
                val amount = 0.7f
                val sr = (or + amount * (or - blurR)).toInt().coerceIn(0, 255)
                val sg = (og + amount * (og - blurG)).toInt().coerceIn(0, 255)
                val sb = (ob + amount * (ob - blurB)).toInt().coerceIn(0, 255)
                rowPixels[x] = (0xFF shl 24) or (sr shl 16) or (sg shl 8) or sb
            }
            out.setPixels(rowPixels, 0, width, 0, y, width, 1)
        }
        return out
    }

    fun fromByteArray(data: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(data, 0, data.size)
}
