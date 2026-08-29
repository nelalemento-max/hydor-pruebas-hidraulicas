package bo.com.hydor.pruebashidraulicas

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.PressureReadingEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Informe compacto PBC: resumen + tabla + gráfica consolidada + conclusión + anexo fotográfico. */
object PbcCompactReportPdf {
    private const val W = 842
    private const val H = 595
    private val NAVY = Color.rgb(14, 46, 77)
    private val BLUE = Color.rgb(18, 88, 158)
    private val YELLOW = Color.rgb(255, 210, 31)
    private val GREEN = Color.rgb(38, 145, 76)
    private val RED = Color.rgb(191, 45, 45)
    private val AMBER = Color.rgb(210, 145, 0)
    private val LIGHT = Color.rgb(246, 248, 250)
    private val GRID = Color.rgb(190, 199, 207)
    private val TEXT = Color.rgb(40, 47, 54)

    fun build(
        context: Context,
        project: ProjectEntity,
        bundles: List<FinalProjectPdf.TestBundle>,
        topology: Map<Long, String>,
        bends: Map<Long, Float>,
        extraTimeByTest: Map<Long, List<Int>>
    ): ByteArray {
        val pdf = PdfDocument()
        val out = ByteArrayOutputStream()
        val logo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.pbc_logo) }.getOrNull()
        var pageNo = 0
        fun newPage(title: String): PdfDocument.Page {
            pageNo++
            return pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()).also { drawHeader(it.canvas, logo, title, pageNo) }
        }

        val totalLength = bundles.sumOf { it.section.lengthMeters }
        val passed = bundles.count { it.test?.status == "PASSED" }
        val review = bundles.count { it.test?.status == "REVIEW" }
        val pending = bundles.size - passed - review

        // PÁGINA 1: resumen, red y tabla.
        var page = newPage("INFORME CONSOLIDADO DE PRUEBA DE PRESIÓN")
        var c = page.canvas
        label(c, 24f, 78f, 246f, "1. DATOS GENERALES")
        panel(c, 24f, 100f, 246f, 122f)
        keyValues(c, 36f, 118f, 222f, listOf(
            "Proyecto" to project.name,
            "Ubicación" to project.location.ifBlank { "No registrada" },
            "Fecha" to SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
            "Tramos" to bundles.size.toString(),
            "Longitud total" to "${fmt(totalLength)} m"
        ))
        label(c, 286f, 78f, 250f, "2. RESUMEN TÉCNICO")
        panel(c, 286f, 100f, 250f, 122f)
        stat(c, 302f, 122f, "ACEPTADOS", passed.toString(), GREEN)
        stat(c, 376f, 122f, "REVISIÓN", review.toString(), AMBER)
        stat(c, 450f, 122f, "PENDIENTES", pending.toString(), if (pending == 0) GREEN else AMBER)
        val overall = if (review > 0) "REQUIERE REVISIÓN" else if (pending > 0) "INCOMPLETO" else "CUMPLE"
        badge(c, 314f, 177f, 194f, 28f, overall, if (review > 0) RED else if (pending > 0) AMBER else GREEN)
        label(c, 552f, 78f, 266f, "3. CRITERIO DE ENSAYO")
        panel(c, 552f, 100f, 266f, 122f)
        val first = bundles.mapNotNull { it.test }.firstOrNull()
        keyValues(c, 564f, 118f, 242f, listOf(
            "Presión ensayo" to (first?.let { "${fmt(it.targetPressureBar)} bar" } ?: "Según tramo"),
            "Caída máxima" to (first?.let { "${fmt(it.maxAllowedDropBar)} bar" } ?: "Según tramo"),
            "Duración" to (first?.let { mins(it.durationMinutes) } ?: "Según prueba"),
            "Método" to "Prueba hidráulica con manómetro"
        ))

        label(c, 24f, 240f, 794f, "4. RED CONSOLIDADA DE TRAMOS")
        panel(c, 24f, 262f, 794f, 112f)
        drawHorizontalNetwork(c, bundles, topology, bends, 46f, 278f, 748f, 78f)

        label(c, 24f, 392f, 794f, "5. RESULTADOS POR TRAMO")
        drawResultsTable(c, bundles, topology, 24f, 414f, 794f, 135f)
        footer(c, pageNo)
        pdf.finishPage(page)

        // PÁGINA 2: gráfica consolidada y conclusión.
        page = newPage("COMPORTAMIENTO CONSOLIDADO DEL ENSAYO")
        c = page.canvas
        label(c, 24f, 78f, 520f, "6. PRESIÓN PROMEDIO VS. TIEMPO")
        panel(c, 24f, 100f, 520f, 300f)
        drawAveragePressureChart(c, bundles, 55f, 126f, 460f, 235f)
        val allReadings = bundles.sumOf { it.readings.count { r -> r.source != "PROGRAMMED" } }
        drawWrapped(c, "La curva representa el comportamiento promedio consolidado de las lecturas registradas durante los ensayos. Se usa para visualizar la tendencia general presión–tiempo sin repetir una gráfica por cada tramo.", 45f, 374f, 480f, body(8.5f), 11f)

        label(c, 560f, 78f, 258f, "7. INDICADORES DEL REGISTRO")
        panel(c, 560f, 100f, 258f, 300f)
        val tested = bundles.count { it.test != null }
        val avgDrop = bundles.mapNotNull { b ->
            val t = b.test ?: return@mapNotNull null
            val last = b.readings.lastOrNull()?.confirmedPressureBar ?: t.targetPressureBar
            max(0.0, t.targetPressureBar - last)
        }.averageOrZero()
        keyValues(c, 574f, 124f, 230f, listOf(
            "Tramos ensayados" to tested.toString(),
            "Lecturas reales" to allReadings.toString(),
            "Caída promedio" to "${fmt(avgDrop)} bar",
            "Aceptados" to passed.toString(),
            "En revisión" to review.toString(),
            "Pendientes" to pending.toString()
        ), 26f)
        val photoCount = bundles.sumOf { b -> b.readings.count { !it.imagePath.isNullOrBlank() } }
        badge(c, 584f, 305f, 210f, 34f, "$photoCount FOTOS EN ANEXO", BLUE)
        drawWrapped(c, "Las fotografías originales de los manómetros se agrupan al final del informe para mantener este cuerpo principal compacto y legible.", 580f, 355f, 214f, body(8.5f), 11f)

        label(c, 24f, 420f, 794f, "8. CONCLUSIÓN TÉCNICA")
        panel(c, 24f, 442f, 794f, 102f)
        val conclusion = when {
            review > 0 -> "El proyecto consolidado presenta $review tramo(s) que requieren revisión técnica. Los demás resultados se mantienen según el estado registrado y confirmado en campo."
            pending > 0 -> "El proyecto contiene $pending tramo(s) pendientes o sin resultado definitivo. El presente informe refleja únicamente la información consolidada disponible al momento de su generación."
            else -> "Los ${bundles.size} tramos consolidados presentan resultado ACEPTABLE conforme a los límites de presión configurados y confirmados durante las pruebas de campo."
        }
        drawWrapped(c, conclusion, 40f, 468f, 760f, body(10f, true), 14f)
        drawWrapped(c, "Los criterios de aceptación deben corresponder a las especificaciones técnicas y documentos contractuales aplicables al proyecto.", 40f, 515f, 760f, body(8f), 11f)
        footer(c, pageNo)
        pdf.finishPage(page)

        // ANEXO FOTOGRÁFICO: 6 fotos por página, agrupadas por tramo.
        val photos = bundles.flatMap { b ->
            val code = topology[b.section.id].orEmpty().uppercase().ifBlank { "TRAMO" }
            b.readings.filter { !it.imagePath.isNullOrBlank() && File(it.imagePath!!).exists() }
                .map { Triple(code, b, it) }
        }
        photos.chunked(6).forEachIndexed { annexIndex, chunk ->
            page = newPage("ANEXO FOTOGRÁFICO ${annexIndex + 1}")
            c = page.canvas
            label(c, 24f, 78f, 794f, "ANEXO · REGISTRO FOTOGRÁFICO DE MANÓMETROS")
            val cellW = 246f
            val cellH = 216f
            chunk.forEachIndexed { i, item ->
                val col = i % 3
                val row = i / 3
                val x = 24f + col * 264f
                val y = 106f + row * 226f
                panel(c, x, y, cellW, cellH)
                val reading = item.third
                val bmp = BitmapFactory.decodeFile(reading.imagePath)
                if (bmp != null) c.drawBitmap(bmp, null, RectF(x + 10f, y + 12f, x + cellW - 10f, y + 156f), Paint(Paint.ANTI_ALIAS_FLAG))
                c.drawText("${item.first} · ${item.second.section.startValve} → ${item.second.section.endValve}", x + 12f, y + 177f, body(8.2f, true))
                c.drawText("${fmt(reading.confirmedPressureBar)} bar · ${time(reading.capturedAt)}", x + 12f, y + 194f, body(8f))
                c.drawText(if (reading.source == "CAMERA") "Lectura por cámara" else reading.source, x + 12f, y + 208f, body(7f))
            }
            footer(c, pageNo)
            pdf.finishPage(page)
        }

        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    private fun drawHeader(c: Canvas, logo: android.graphics.Bitmap?, title: String, pageNo: Int) {
        c.drawColor(Color.WHITE)
        c.drawRect(0f, 0f, W.toFloat(), 65f, Paint().apply { color = NAVY })
        c.drawRect(575f, 0f, W.toFloat(), 65f, Paint().apply { color = YELLOW })
        if (logo != null) c.drawBitmap(logo, null, RectF(18f, 7f, 72f, 61f), Paint(Paint.ANTI_ALIAS_FLAG))
        c.drawText("LABORATORIO PBC BOLIVIA", 84f, 27f, titlePaint(21f, Color.WHITE))
        c.drawText("LABORATORIO TÉCNICO · PRUEBAS HIDRÁULICAS", 84f, 47f, body(9f, true, Color.WHITE))
        drawWrapped(c, title, 594f, 19f, 222f, titlePaint(10.5f, NAVY), 12f)
        c.drawText("Pág. $pageNo", 790f, 57f, body(7f, false, NAVY))
    }

    private fun footer(c: Canvas, pageNo: Int) {
        c.drawLine(24f, 566f, 818f, 566f, Paint().apply { color = NAVY; strokeWidth = 1f })
        c.drawText("Laboratorio PBC Bolivia · Informe generado por la aplicación PBC", 24f, 580f, body(7f))
        c.drawText("Página $pageNo", 774f, 580f, body(7f))
    }

    private fun label(c: Canvas, x: Float, y: Float, w: Float, text: String) {
        c.drawRoundRect(RectF(x, y, x + w, y + 18f), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YELLOW })
        c.drawText(text, x + 8f, y + 13f, titlePaint(9.5f, NAVY))
    }

    private fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT })
        c.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; style = Paint.Style.STROKE; strokeWidth = 1f })
    }

    private fun keyValues(c: Canvas, x: Float, y: Float, w: Float, rows: List<Pair<String, String>>, gap: Float = 21f) {
        var yy = y
        rows.forEach { (k, v) ->
            c.drawText(k, x, yy, body(8f, true, NAVY))
            drawEllipsized(c, v, x + 90f, yy, w - 90f, body(8.2f))
            yy += gap
        }
    }

    private fun stat(c: Canvas, x: Float, y: Float, label: String, value: String, color: Int) {
        c.drawCircle(x + 12f, y + 8f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawText(value, x + 29f, y + 11f, titlePaint(12f, NAVY))
        c.drawText(label, x, y + 31f, body(6.8f, true))
    }

    private fun badge(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, color: Int) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val p = body(8f, true, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
        c.drawText(text, x + w / 2f, y + h / 2f + 3f, p)
    }

    private fun drawResultsTable(c: Canvas, bundles: List<FinalProjectPdf.TestBundle>, topology: Map<Long, String>, x: Float, y: Float, w: Float, h: Float) {
        val cols = floatArrayOf(48f, 130f, 62f, 72f, 72f, 68f, 70f, 94f, 178f)
        val total = cols.sum()
        val widths = cols.map { it * w / total }
        val headerH = 24f
        val maxRows = min(bundles.size, 7)
        val rowH = if (maxRows == 0) 22f else (h - headerH) / maxRows
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; style = Paint.Style.STROKE; strokeWidth = 0.9f }
        c.drawRect(x, y, x + w, y + h, gridPaint)
        c.drawRect(x, y, x + w, y + headerH, Paint().apply { color = BLUE })
        val headers = listOf("COD.", "INICIO – FINAL", "Ø", "LONG. m", "P. INI", "P. FIN", "CAÍDA", "TIEMPO", "RESULTADO")
        var xx = x
        headers.forEachIndexed { i, head ->
            c.drawText(head, xx + 4f, y + 16f, body(6.8f, true, Color.WHITE))
            xx += widths[i]
            if (i < headers.lastIndex) c.drawLine(xx, y, xx, y + h, gridPaint)
        }
        for (i in 0..maxRows) c.drawLine(x, y + headerH + i * rowH, x + w, y + headerH + i * rowH, gridPaint)
        bundles.take(maxRows).forEachIndexed { index, b ->
            val t = b.test
            val finalP = t?.let { b.readings.lastOrNull()?.confirmedPressureBar ?: it.targetPressureBar }
            val drop = if (t != null && finalP != null) max(0.0, t.targetPressureBar - finalP) else null
            val status = when (t?.status) { "PASSED" -> "ACEPTABLE"; "REVIEW" -> "REVISIÓN"; "IN_PROGRESS" -> "EN CURSO"; else -> "SIN PRUEBA" }
            val values = listOf(
                topology[b.section.id].orEmpty().uppercase().ifBlank { "—" },
                "${b.section.startValve} – ${b.section.endValve}",
                b.section.diameterInches + "\"",
                fmt(b.section.lengthMeters),
                t?.let { fmt(it.targetPressureBar) } ?: "—",
                finalP?.let { fmt(it) } ?: "—",
                drop?.let { fmt(it) } ?: "—",
                t?.let { mins(it.durationMinutes) } ?: "—",
                status
            )
            xx = x
            val yy = y + headerH + index * rowH
            if (index % 2 == 1) c.drawRect(x, yy, x + w, yy + rowH, Paint().apply { color = Color.rgb(251, 252, 253) })
            values.forEachIndexed { col, value ->
                val p = body(if (col == 1 || col == 8) 6.7f else 7f, col == 0 || col == 8, if (col == 8) statusColor(t?.status) else TEXT)
                drawEllipsized(c, value, xx + 4f, yy + rowH / 2f + 2.5f, widths[col] - 8f, p)
                xx += widths[col]
            }
        }
    }

    private fun drawAveragePressureChart(c: Canvas, bundles: List<FinalProjectPdf.TestBundle>, x: Float, y: Float, w: Float, h: Float) {
        val tested = bundles.filter { it.test != null && it.readings.isNotEmpty() }
        if (tested.isEmpty()) {
            c.drawText("Sin lecturas suficientes para construir la gráfica consolidada.", x + 30f, y + h / 2f, body(10f))
            return
        }
        val samples = 13
        val series = (0 until samples).map { index ->
            val fraction = index.toDouble() / (samples - 1)
            val values = tested.mapNotNull { b -> pressureAtFraction(b.readings, b.test!!, fraction) }
            fraction to values.averageOrZero()
        }
        val allY = series.map { it.second }.filter { it.isFinite() }
        var minP = allY.minOrNull() ?: 0.0
        var maxP = allY.maxOrNull() ?: 1.0
        val allTests = tested.map { it.test!! }
        minP = min(minP, allTests.minOf { it.targetPressureBar - it.maxAllowedDropBar })
        maxP = max(maxP, allTests.maxOf { it.targetPressureBar })
        val pad = max(0.2, (maxP - minP) * 0.15)
        minP -= pad; maxP += pad
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 226, 231); strokeWidth = 0.8f }
        val axis = body(7f)
        repeat(6) { i ->
            val yy = y + h * i / 5f
            c.drawLine(x, yy, x + w, yy, grid)
            val value = maxP - (maxP - minP) * i / 5.0
            c.drawText(String.format(Locale.US, "%.1f", value), x - 27f, yy + 2f, axis)
        }
        repeat(7) { i ->
            val xx = x + w * i / 6f
            c.drawLine(xx, y, xx, y + h, grid)
            c.drawText("${(i * 100 / 6)}%", xx - 8f, y + h + 17f, axis)
        }
        fun yy(v: Double) = y + h - ((v - minP) / (maxP - minP)).toFloat() * h
        val path = Path()
        series.forEachIndexed { i, (fraction, value) ->
            val xx = x + fraction.toFloat() * w
            val py = yy(value)
            if (i == 0) path.moveTo(xx, py) else path.lineTo(xx, py)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; style = Paint.Style.STROKE; strokeWidth = 2.5f })
        series.forEach { (fraction, value) -> c.drawCircle(x + fraction.toFloat() * w, yy(value), 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN }) }
        val avgTarget = allTests.map { it.targetPressureBar }.averageOrZero()
        val avgLimit = allTests.map { it.targetPressureBar - it.maxAllowedDropBar }.averageOrZero()
        c.drawLine(x, yy(avgTarget), x + w, yy(avgTarget), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; strokeWidth = 1.2f })
        c.drawLine(x, yy(avgLimit), x + w, yy(avgLimit), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; strokeWidth = 1.2f })
        c.drawText("Presión promedio", x + w - 98f, y + 15f, body(7f, true, BLUE))
        c.drawText("Presión inicial promedio", x + w - 122f, y + 30f, body(7f, false, NAVY))
        c.drawText("Límite mínimo promedio", x + w - 118f, y + 44f, body(7f, false, RED))
        c.drawText("Tiempo relativo del ensayo", x + w / 2f - 52f, y + h + 32f, body(8f, true, NAVY))
        c.save(); c.rotate(-90f, x - 40f, y + h / 2f); c.drawText("Presión (bar)", x - 40f, y + h / 2f, body(8f, true, NAVY)); c.restore()
    }

    private fun pressureAtFraction(readings: List<PressureReadingEntity>, test: HydraulicTestEntity, fraction: Double): Double? {
        if (readings.isEmpty()) return test.targetPressureBar
        val ordered = readings.sortedBy { it.capturedAt }
        val t0 = ordered.first().capturedAt
        val t1 = max(t0 + 1, ordered.last().capturedAt)
        val target = t0 + ((t1 - t0) * fraction).toLong()
        val nearest = ordered.minByOrNull { abs(it.capturedAt - target) }
        return nearest?.confirmedPressureBar ?: test.targetPressureBar
    }

    private data class G(val sx: Float, val sy: Float, val ex: Float, val ey: Float)
    private fun drawHorizontalNetwork(c: Canvas, bundles: List<FinalProjectPdf.TestBundle>, topology: Map<Long, String>, bends: Map<Long, Float>, x: Float, y: Float, w: Float, h: Float) {
        if (bundles.isEmpty()) return
        val ordered = bundles.sortedWith(compareBy({ topology[it.section.id].orEmpty().filter { ch -> ch.isDigit() }.toIntOrNull() ?: 999 }, { topology[it.section.id].orEmpty() }))
        val main = ordered.filter { topology[it.section.id].orEmpty().matches(Regex("^\\d+$")) }
        val maxLen = ordered.maxOfOrNull { it.section.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
        val totalWeighted = main.sumOf { 0.6 + it.section.lengthMeters / maxLen }.coerceAtLeast(1.0)
        var cursorX = x + 10f
        val centerY = y + h / 2f
        val geo = mutableMapOf<Long, G>()
        main.forEach { b ->
            val len = (w - 45f) * ((0.6 + b.section.lengthMeters / maxLen) / totalWeighted).toFloat()
            geo[b.section.id] = G(cursorX, centerY, cursorX + len, centerY)
            cursorX += len
        }
        ordered.filterNot { it in main }.forEach { b ->
            val code = topology[b.section.id].orEmpty().lowercase()
            val m = Regex("^(\\d+)([ab])$").matchEntire(code) ?: return@forEach
            val parent = ordered.firstOrNull { topology[it.section.id].orEmpty() == m.groupValues[1] } ?: return@forEach
            val pg = geo[parent.section.id] ?: return@forEach
            val dir = if (m.groupValues[2] == "a") -1f else 1f
            val branchLen = 55f + 70f * (b.section.lengthMeters / maxLen).toFloat()
            geo[b.section.id] = G(pg.ex, pg.ey, pg.ex + branchLen * 0.75f, pg.ey + dir * min(34f, branchLen * 0.38f))
        }
        ordered.forEach { b ->
            val g = geo[b.section.id] ?: return@forEach
            val status = statusColor(b.test?.status)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = status; style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
            val bend = bends[b.section.id] ?: 0f
            val path = Path().apply {
                moveTo(g.sx, g.sy)
                quadTo((g.sx + g.ex) / 2f, (g.sy + g.ey) / 2f + bend * 12f, g.ex, g.ey)
            }
            c.drawPath(path, p)
            c.drawCircle(g.sx, g.sy, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY })
            c.drawCircle(g.ex, g.ey, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY })
            val code = topology[b.section.id].orEmpty().uppercase().ifBlank { "—" }
            val mx = (g.sx + g.ex) / 2f
            val my = (g.sy + g.ey) / 2f
            c.drawText("$code · ${b.section.startValve}–${b.section.endValve} · ${b.section.lengthMeters.toInt()} m", mx - 38f, my - 9f, body(6.5f, true, NAVY))
        }
    }

    private fun statusColor(status: String?): Int = when (status) { "PASSED" -> GREEN; "REVIEW" -> RED; "IN_PROGRESS" -> AMBER; else -> Color.GRAY }
    private fun body(size: Float, bold: Boolean = false, color: Int = TEXT) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    private fun titlePaint(size: Float, color: Int = NAVY) = body(size, true, color)
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun mins(m: Int) = if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
    private fun time(ts: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
    private fun Iterable<Double>.averageOrZero(): Double { var n = 0; var sum = 0.0; for (v in this) if (v.isFinite()) { sum += v; n++ }; return if (n == 0) 0.0 else sum / n }

    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxW: Float, p: Paint, lineH: Float) {
        var yy = y
        var line = ""
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) > maxW && line.isNotEmpty()) { c.drawText(line, x, yy, p); yy += lineH; line = word } else line = candidate
        }
        if (line.isNotEmpty()) c.drawText(line, x, yy, p)
    }

    private fun drawEllipsized(c: Canvas, value: String, x: Float, y: Float, maxW: Float, p: Paint) {
        if (p.measureText(value) <= maxW) { c.drawText(value, x, y, p); return }
        var s = value
        while (s.length > 3 && p.measureText("$s…") > maxW) s = s.dropLast(1)
        c.drawText("$s…", x, y, p)
    }
}
