package bo.com.hydor.pruebashidraulicas

import kotlin.math.abs

/**
 * Interpreta referencias técnicas tipo T-0, T-100, P-250, V-035, etc.
 * La letra o prefijo identifica el punto; el último número representa la progresiva en metros.
 * Ej.: T-0 → T-100 = 100 m; P-120 → P-275 = 155 m.
 */
fun deriveTramoLengthMeters(start: String, end: String): Double? {
    val a = extractChainage(start) ?: return null
    val b = extractChainage(end) ?: return null
    val result = abs(b - a)
    return result.takeIf { it > 0.0 }
}

private fun extractChainage(value: String): Double? {
    val clean = value.trim().replace(',', '.')
    if (clean.isBlank()) return null
    val match = Regex("(-?\\d+(?:\\.\\d+)?)\\s*$").find(clean) ?: return null
    return match.groupValues[1].toDoubleOrNull()
}

fun formatDerivedTramoLength(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
