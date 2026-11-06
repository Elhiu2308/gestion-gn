package com.gn.gestion.scanner;

import java.util.Locale

/**
 * Detecta los datos del encabezado de un documento oficial escaneado:
 * el número/folio (OFICIO, CPI, TARJETA, ...) que figura en la parte
 * superior de la página y el asunto, usando las líneas del OCR con su
 * posición en la imagen para elegir las de arriba.
 */
object EncabezadoDocumento {

    data class Encabezado(val folio: String, val asunto: String)

    private val TIPOS_FOLIO = listOf(
        "OFICIO", "TARJETA", "MEMORANDUM", "MEMO", "CPI", "CPIC",
        "OF.CIO", "ACUERDO", "CIRCULAR", "EXTRAORDINARIO", "RECIBO",
        "NOTA", "SOLICITUD", "COMUNICADO"
    )

    fun detectar(ocr: ResultadoOcr): Encabezado =
        Encabezado(detectarFolio(ocr), detectarAsunto(ocr))

    // ---------------------------------------------------------------- folio

    private fun detectarFolio(ocr: ResultadoOcr): String {
        val lineas = lineasArriba(ocr, 18)
        // 1) Línea del tipo + código, p. ej. "OFICIO NÚM.  GN/DGC/7777".
        for (fila in lineas) {
            extraerConTipo(normalizar(fila.texto))?.let { return it }
        }
        // 2) Código folio con formato institucional "XXX/YYYY/NNNN/2026".
        //    P. ej. "GN/UOEC/02613/2026" -> "GN_UOEC_02613_2026". Se prefiere el
        //    cuyas letras/número coinciden con la cabecera "DOCUMENTO: ...".
        extraerFolioFormato(ocr.texto, tokensDocumento(lineas), tokensTop(lineas))?.let { return it }
        // 3) Etiqueta "NO. DOCUMENTO:  <código>".
        val cabecera = codigoNoDocumento(lineas)
        cabecera?.let { return it }
        // 4) Línea con "NÚM./NO./N°" + código, p. ej. "NO. 7777/2026".
        for (fila in lineas) {
            val lim = normalizar(fila.texto)
            if (lim.contains("DOCUMENTO")) continue
            extraerNumeral(lim)?.let { return it }
        }
        return ""
    }

    /** Código de la etiqueta "NO. DOCUMENTO: ..." de la parte superior, si existe. */
    private fun codigoNoDocumento(lineas: List<LineaOcr>): String? {
        for (fila in lineas) {
            extraerNoDocumento(normalizar(fila.texto))?.let { return it }
        }
        return null
    }

    /** "NO. DOCUMENTO: ..." tolerante al ruido OCR ("DOCUMENLO" en vez de "DOCUMENTO"). */
    private val NO_DOCUMENTO = Regex(
        "(?:NO\\.?|NUMERO|N[O°º])\\s*(?:DE\\s*)?DOCUMENT?[TL]?O?\\s*[:.\u2013-]?\\s*([A-Z0-9][A-Z0-9./_\\- ]{2,40})"
    )

    private fun extraerNoDocumento(limpia: String): String? {
        val m = NO_DOCUMENTO.find(limpia) ?: return null
        val clave = m.groupValues[1].trim()
        if (clave.isBlank()) return null
        return clave.takeIf { it.any { c -> c.isDigit() } }
    }

    /** "DOCUMENTO: ..." cuando el "No." va en otra línea del OCR. */
    private val DOCUMENTO_LABEL = Regex(
        "^DOCUMENT?[TL]?O?\\s*[:.\u2013-]?\\s*([A-Z0-9][A-Z0-9./_\\- ]{2,40})"
    )

    /** Tokens del código de la línea "DOCUMENTO: ..." de la parte superior. */
    private fun tokensDocumento(lineas: List<LineaOcr>): Set<String> {
        val codigos = ArrayList<String>()
        for (fila in lineas) {
            val lim = normalizar(fila.texto)
            extraerNoDocumento(lim)?.let { codigos.add(it); continue }
            DOCUMENTO_LABEL.find(lim)?.groupValues?.get(1)
                ?.takeIf { it.any { c -> c.isDigit() } }
                ?.let { codigos.add(it) }
        }
        return codigos.flatMap(::tokensCodigo).toSet()
    }

    /** Divide un código y corta al aparecer una palabra de etiqueta (ORIGEN, ...). */
    private fun tokensCodigo(codigo: String): List<String> {
        val out = ArrayList<String>()
        for (tok in codigo.split(Regex("[^A-Z0-9]+"))) {
            if (tok.length < 3) continue
            if (tok in STOP_ENCABEZADO) break
            out.add(tok.replace('O', '0'))
        }
        return out
    }

    /** Folio institucional "LETRAS/LETRAS/NÚMERO/AÑO" -> con guiones bajos. */
    private val FOLIO_FORMATO = Regex(
        "([A-Z]{1,6})/([A-Z]{1,8})/([0-9]{2,8})/(20[0-9]{2})"
    )

    private fun extraerFolioFormato(texto: String, headToks: Set<String>, topToks: Set<String>): String? {
        val limpio = normalizar(texto)
        val candidatos = FOLIO_FORMATO.findAll(limpio)
            .map { m ->
                Triple(
                    Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3]),
                    m.value,
                    m.groupValues[4]
                )
            }
            .filter { it.third in "2000".."2099" }
            .toList()
        if (candidatos.isEmpty()) return null
        val seleccionado = candidatos.firstOrNull { matchHead(it, headToks) }
            ?: candidatos.firstOrNull { matchHead(it, topToks) }
            ?: candidatos.first()
        return limpiarFolio(seleccionado.second)
    }

    private fun matchHead(partes: Triple<Triple<String, String, String>, String, String>, toks: Set<String>): Boolean {
        val (_, b, n) = partes.first
        return toks.contains(b.replace('O', '0')) || toks.contains(n)
    }

    private val STOP_ENCABEZADO = setOf(
        "ORIGEN", "DESTINO", "REFERENCIA", "FECHA", "FIRMA", "MENSAJE", "HOJA",
        "ASUNTO", "ENVIO", "ENVLO", "ENVIADO", "MAT", "DIR", "GRAL", "GNAL",
        "GERAL", "GENERAL", "PTO", "TO", "NUM", "NO", "CI", "REG", "ETIQUETA",
        "MARGARITA", "MAZA", "PARADA", "ANIO", "AÑO", "AREA", "UNIDAD", "COMANDANCIA",
        "DOCUMENTO", "DOCUMENLO", "DER", "HUMS", "GDIA", "NAL", "ORG", "ORIG"
    )

    /** Tokens identificables de la parte superior (sin palabras de encabezado usuales). */
    private fun tokensTop(lineas: List<LineaOcr>): Set<String> =
        lineas.flatMap { l ->
            normalizar(l.texto).split(Regex("[^A-Z0-9]+"))
                .filter { it.length >= 3 && it !in STOP_ENCABEZADO }
                .map { it.replace('O', '0') }
        }.toSet()

    private fun limpiarFolio(codigo: String): String =
        codigo.replace('/', '_')
            .replace(Regex("[\\s.]+$"), "")
            .trim()

    private fun extraerConTipo(limpia: String): String? {
        val tipo = TIPOS_FOLIO.firstOrNull {
            limpia == it || limpia.startsWith("$it ") ||
                limpia.startsWith("$it:") || limpia.startsWith("$it.")
        } ?: return null
        var resto = limpia.substring(tipo.length).trim()
        resto = quitarNumeral(resto)
        if (resto.isBlank()) return null
        val codigo = tomarCodigo(resto)
        if (codigo.isBlank()) return null
        if (codigo.length in 3..30 && codigo.any { it.isDigit() }) {
            return "$tipo $codigo".trim()
        }
        return null
    }

    private val NUMERAL = Regex("(?:N[UÚM]|N[O°º])\\.?\\s*[:\u2013-]?\\s*([A-Z0-9][A-Z0-9./_\\-]{2,24})")

    private fun extraerNumeral(limpia: String): String? {
        val m = NUMERAL.find(limpia) ?: return null
        val clave = m.groupValues[1].trim()
        if (clave.isBlank()) return null
        return clave.takeIf { it.length in 3..30 && it.any { c -> c.isDigit() } }
    }

    private fun quitarNumeral(s: String): String =
        s.trimStart('.', ':', '-', '\u2013', '\u2014', '|', '(', ')', ' ')
            .replace(Regex("^N°|^Nº|^N[UÚM]|^NO\\.?"), "")
            .trimStart('.', ':', '-', '\u2013', '\u2014', '|', '(', ')', ' ')

    private fun tomarCodigo(resto: String): String {
        val parte = resto.takeWhile { it.isLetterOrDigit() || it in "/._-" || it == ' ' }
        return parte.split(' ').filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    // ---------------------------------------------------------------- asunto

    private val ASUNTO_LABEL = Regex(
        "^[\\s\\u00A0]*ASUNT[O0]?\\s*[:.\\u2013\\-]?\\s*", RegexOption.IGNORE_CASE
    )

    private fun detectarAsunto(ocr: ResultadoOcr): String {
        val lineas = lineasArriba(ocr, 18)
        val idx = lineas.indexOfFirst {
            val t = normalizar(it.texto)
            t.startsWith("ASUNTO") || t.startsWith("ASUNT0") ||
                t.contains("ASUNTO:") || t.contains("ASUNT0:")
        }
        if (idx >= 0) {
            val resto = lineas[idx].texto.trim()
                .replaceFirst(ASUNTO_LABEL, "")
                .trim()
            val partes = ArrayList<String>()
            if (resto.isNotBlank()) partes.add(resto)
            for (i in idx + 1 until minOf(idx + 3, lineas.size)) {
                val sig = lineas[i].texto.trim()
                if (sig.length < 3) continue
                val up = normalizar(sig)
                if (TIPOS_FOLIO.any { up == it || up.startsWith("$it ") } ||
                    up.startsWith("EXPEDIENTE") || up.startsWith("FECHA") ||
                    up.startsWith("REFERENCIA") || up.startsWith("ANEXO") ||
                    up.startsWith("C. ") || up.startsWith("CC. ")
                ) break
                partes.add(sig)
                if (partes.joinToString(" ").length >= 130) break
            }
            val asunto = partes.joinToString(" ").trim().take(150)
            if (asunto.isNotBlank()) return asunto
        }
        // Sin etiqueta "ASUNTO": la primera línea sustantiva del cuerpo.
        for (fila in lineas) {
            val t = normalizar(fila.texto)
            if (t.length < 15) continue
            if (esLineaCabecera(t)) continue
            return fila.texto.trim().take(150)
        }
        return ""
    }

    /**
     * Pequeña descripción para el formulario: las primeras líneas del cuerpo
     * de la hoja, ignorando la cabecera institucional (logo, folio, etiquetas).
     */
    fun descripcionCorta(ocr: ResultadoOcr, maxChars: Int = 220): String {
        val lineas = lineasArriba(ocr, 40)
        val salida = StringBuilder()
        for (fila in lineas) {
            val t = fila.texto.trim()
            if (t.length < 3) continue
            if (esLineaCabecera(normalizar(t))) continue
            if (salida.isNotEmpty() && salida.length + t.length + 1 > maxChars) break
            if (salida.isNotEmpty()) salida.append(' ')
            salida.append(t)
            if (salida.length > maxChars) break
        }
        return salida.toString().trim().take(maxChars)
    }

    /** Detección de líneas de cabecera institucional/fecha/folio para descartarlas. */
    private fun esLineaCabecera(t: String): Boolean {
        if (t.length < 8) return true
        if (t.startsWith("GN/") || FOLIO_FORMATO.containsMatchIn(t)) return true
        return listOf(
            "GN", "SIIGNA", "C.E.I", "CEI", "C EI", "CUERPO ESPECIAL",
            "GOBIERNO", "MINISTERIO", "SECRETARIA", "DIRECCION GENERAL",
            "DIRECCION DE", "FISCALIA", "CONSEJO", "FECHA", "FOLIO",
            "NÚM", "NUM", "N0", "NO.", "N°", "Nº", "DOCUMENTO", "ASUNT",
            "EXPEDIENTE", "ESTADO", "REPUBLICA"
        ).any { t.startsWith(it) }
    }

    // ---------------------------------------------------------------- util

    /** Devuelve hasta [max] líneas ordenadas de arriba a abajo (parte superior). */
    private fun lineasArriba(ocr: ResultadoOcr, max: Int): List<LineaOcr> =
        ocr.lineas
            .filter { !it.caja.isEmpty }
            .sortedWith(compareBy({ it.caja.top }, { it.caja.left }))
            .take(max)

    private fun normalizar(s: String): String =
        s.trim().replace(Regex("\\s+"), " ").uppercase(Locale.getDefault())
}