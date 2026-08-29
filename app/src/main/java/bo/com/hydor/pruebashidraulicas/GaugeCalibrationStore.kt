package bo.com.hydor.pruebashidraulicas

import android.content.Context
import kotlin.math.abs

data class CalibrationSample(val angleDeg: Double, val pressureBar: Double)

data class GaugeCalibration(
    val name: String,
    val maxBar: Double,
    val samples: List<CalibrationSample>,
    val slope: Double?,
    val intercept: Double?,
    val updatedAt: Long
) {
    val isCalibrated: Boolean get() = slope != null && intercept != null && samples.size >= 2

    fun pressureForAngle(angleDeg: Double): Double {
        val raw = if (isCalibrated) {
            slope!! * angleDeg + intercept!!
        } else {
            ((angleDeg - 135.0) / 270.0) * maxBar
        }
        return raw.coerceIn(0.0, maxBar)
    }
}

object GaugeCalibrationStore {
    private const val PREFS = "hydor_gauge_calibration"
    private const val KEY_NAME = "name"
    private const val KEY_MAX = "max_bar"
    private const val KEY_SAMPLES = "samples"
    private const val KEY_UPDATED = "updated"

    fun load(context: Context): GaugeCalibration {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString(KEY_NAME, "Manómetro principal") ?: "Manómetro principal"
        val maxBar = p.getFloat(KEY_MAX, 10f).toDouble().coerceAtLeast(1.0)
        val samples = decodeSamples(p.getString(KEY_SAMPLES, "").orEmpty())
        val fit = fit(samples)
        return GaugeCalibration(name, maxBar, samples, fit?.first, fit?.second, p.getLong(KEY_UPDATED, 0L))
    }

    fun saveProfile(context: Context, name: String, maxBar: Double, resetSamples: Boolean) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = p.edit()
            .putString(KEY_NAME, name.trim().ifBlank { "Manómetro principal" })
            .putFloat(KEY_MAX, maxBar.coerceAtLeast(1.0).toFloat())
            .putLong(KEY_UPDATED, System.currentTimeMillis())
        if (resetSamples) editor.putString(KEY_SAMPLES, "")
        editor.apply()
    }

    fun addConfirmedSample(context: Context, angleDeg: Double, pressureBar: Double) {
        val current = load(context)
        val filtered = current.samples.filterNot { abs(it.angleDeg - angleDeg) < 1.0 }
        val updated = (filtered + CalibrationSample(angleDeg, pressureBar.coerceIn(0.0, current.maxBar)))
            .takeLast(12)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAMPLES, encodeSamples(updated))
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun clearSamples(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAMPLES, "")
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    private fun fit(samples: List<CalibrationSample>): Pair<Double, Double>? {
        if (samples.size < 2) return null
        val n = samples.size.toDouble()
        val meanX = samples.sumOf { it.angleDeg } / n
        val meanY = samples.sumOf { it.pressureBar } / n
        val denom = samples.sumOf { (it.angleDeg - meanX) * (it.angleDeg - meanX) }
        if (denom < 4.0) return null
        val slope = samples.sumOf { (it.angleDeg - meanX) * (it.pressureBar - meanY) } / denom
        if (!slope.isFinite() || abs(slope) < 0.0001) return null
        val intercept = meanY - slope * meanX
        return slope to intercept
    }

    private fun encodeSamples(samples: List<CalibrationSample>): String =
        samples.joinToString(";") { "${it.angleDeg},${it.pressureBar}" }

    private fun decodeSamples(value: String): List<CalibrationSample> = value.split(';').mapNotNull { row ->
        val parts = row.split(',')
        val a = parts.getOrNull(0)?.toDoubleOrNull()
        val p = parts.getOrNull(1)?.toDoubleOrNull()
        if (a != null && p != null) CalibrationSample(a, p) else null
    }
}
