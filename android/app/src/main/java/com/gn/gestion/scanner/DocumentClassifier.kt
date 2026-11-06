package com.gn.gestion.scanner;

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Clasificador de documentos escaneados (paso 4).
 *
 * A partir del texto extraído con OCR detecta el tipo de documento
 * (factura, recibo, contrato, acta, estado de cuenta, ...), la entidad
 * emisora y un período, y compone un nombre de archivo inteligente como
 * "Factura_Internet_Septiembre".
 */
object DocumentClassifier {

    data class Clasificacion(
        val tipo: String,
        val entidad: String,
        val periodoTxt: String,
        val nombreSugerido: String
    )

    private data class TipoDoc(val nombre: String, val keywords: List<String>)

    // Tipos ordenados de más específico a más genérico.
    private val TIPOS = listOf(
        TipoDoc("Estado de cuenta", listOf(
            "estado de cuenta", "movimientos", "resumen de", "saldo", "banca", "deposito", "retiro"
        )),
        TipoDoc("Contrato", listOf(
            "contrato", "contratante", "contratada", "clausula", "vigencia", "dependencia economica"
        )),
        TipoDoc("Poliza", listOf(
            "poliza", "seguro", "aseguradora", "cobertura", "prima"
        )),
        TipoDoc("Cotizacion", listOf(
            "cotizacion", "presupuesto", "precio unitario", "importe"
        )),
        TipoDoc("Recibo", listOf(
            "recibo", "comprobante de pago", "pago de", "efectivo", "monto recibido"
        )),
        TipoDoc("Acta", listOf(
            "acta", "nacimiento", "matrimonio", "de nacimiento"
        )),
        TipoDoc("Constancia", listOf(
            "constancia", "acredita", "laboral", "de estudio"
        )),
        TipoDoc("Oficio", listOf(
            "oficio", "no. oficio", "numero de oficio", "asunto"
        )),
        TipoDoc("Credencial", listOf(
            "credencial", "identificacion", "c.v.", "curp", "vigencia hasta"
        )),
        TipoDoc("Factura", listOf(
            "factura", "facturado", "cfdi", "folio fiscal", "folio", "rfc", "subtotal", "iva"
        ))
    )

    // Entidades emisoras conocidas (más específicas primero).
    private data class Entidad(val etiqueta: String, val keywords: List<String>)

    private val ENTIDADES = listOf(
        Entidad("CFE", listOf("cfe", "comision federal de electricidad", "luz", "electricidad", "energia")),
        Entidad("Agua", listOf("japami", "sapal", "agua potable", "comision del agua", "hidraulica")),
        Entidad("Internet", listOf("internet", "telmex", "fibra", "megacable", "izzi", "wifi", "telefonia", "infinitum")),
        Entidad("Telefonia", listOf("telcel", "movistar", "at&t", "wom", "celular", "recarga")),
        Entidad("Gas", listOf("gas", "gas lp", "gas natural", "tanque", "estacion de gas")),
        Entidad("SAT", listOf("sat", "servicio de administracion tributaria", "cedula fiscal", "constancia fiscal", "impuestos")),
        Entidad("IMSS", listOf("imss", "seguro social")),
        Entidad("Infonavit", listOf("infonavit", "credito hipotecario")),
        Entidad("Gobierno", listOf("ayuntamiento", "municipio", "gobierno", "oficina de")),
        Entidad("Escuela", listOf("universidad", "colegio", "facultad", "inscripcion", "colegiatura", "cuota escolar"))
    )

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    private val ANIO = Regex("\\b(19|20)\\d{2}\\b")

    /**
     * Clasifica el texto OCR y genera un nombre de archivo inteligente.
     * [fechaCreacionMs] se usa como respaldo cuando el texto no revela período.
     */
    fun clasificar(texto: String, fechaBase: Long = System.currentTimeMillis()): Clasificacion {
        val t = texto.lowercase(Locale.getDefault())
        val tipo = detectarTipo(t) ?: "Documento"
        val entidad = detectarEntidad(t, texto)
        val periodo = detectarPeriodo(t, fechaBase)
        val nombre = PdfGenerator.sanitizeFileName(
            listOf(tipo, entidad, periodo).filter { it.isNotBlank() }.joinToString("_")
        )
        return Clasificacion(tipo, entidad, periodo, nombre)
    }

    private fun detectarTipo(t: String): String? {
        for (tipo in TIPOS) {
            if (tipo.keywords.any { t.contains(it) }) return tipo.nombre
        }
        return null
    }

    private fun detectarEntidad(t: String, original: String): String {
        for (ent in ENTIDADES) {
            if (ent.keywords.any { t.contains(it) }) return ent.etiqueta
        }
        // Intenta obtener la razón social / emisor de los encabezados comunes.
        val emisor = Regex("(?i)(razon social|proveedor|empresa|emisor|prestador)\\s*[:\\-]\\s*([^\\r\\n]{3,60})")
            .find(original)
        if (emisor != null) {
            val nombre = emisor.groupValues[2].trim()
            val limpio = nombre.substringBefore("|").substringBefore("@")
                .replace(Regex("[^A-Za-zÀ-ÿ0-9 ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
            if (limpio.isNotEmpty()) return limpio.take(2).joinToString(" ")
        }
        return ""
    }

    /** Detecta el período (ej. "Septiembre 2026"); si no, usa el mes del [fechaBase]. */
    private fun detectarPeriodo(t: String, fechaBase: Long): String {
        for (mes in MESES) {
            if (t.contains(mes)) {
                val anio = ANIO.find(t)?.value ?: "2026"
                return mes.replaceFirstChar { it.uppercase() } + " " + anio
            }
        }
        val cal = Calendar.getInstance().apply { timeInMillis = fechaBase }
        val mes = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
        val anio = cal.get(Calendar.YEAR)
        return mes.replaceFirstChar { it.uppercase() } + " " + anio
    }
}