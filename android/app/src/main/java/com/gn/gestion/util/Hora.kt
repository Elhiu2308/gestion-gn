package com.gn.gestion.util

object Hora {

    private val RE24 = Regex("""^\s*([01]?\d|2[0-3])\s*:\s*([0-5]\d)\s*$""")

    private val RE12 = Regex("""^\s*(\d{1,2})\s*:\s*([0-5]\d)\s*([AaPp][Mm])\s*$""")

    fun a24(valor: String): String {
        val v = valor.trim()
        if (v.isEmpty()) return ""
        RE24.matchEntire(v)?.let { m ->
            return "%02d:%s".format(java.util.Locale.US, m.groupValues[1].toInt(), m.groupValues[2])
        }
        RE12.matchEntire(v)?.let { m ->
            var h = m.groupValues[1].toInt() % 12
            if (m.groupValues[3].uppercase() == "PM") h += 12
            return "%02d:%s".format(java.util.Locale.US, h, m.groupValues[2])
        }
        return v
    }

    fun a12(valor: String): String {
        val v = valor.trim()
        if (v.isEmpty()) return ""
        RE12.matchEntire(v)?.let { m ->
            val h = m.groupValues[1].toInt()
            return "%d:%s %s".format(
                java.util.Locale.US,
                if (h == 0) 12 else h,
                m.groupValues[2],
                m.groupValues[3].uppercase()
            )
        }
        val m = RE24.matchEntire(v) ?: return v
        val h24 = m.groupValues[1].toInt()
        val sufijo = if (h24 >= 12) "PM" else "AM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "%d:%s %s".format(java.util.Locale.US, h12, m.groupValues[2], sufijo)
    }
}
