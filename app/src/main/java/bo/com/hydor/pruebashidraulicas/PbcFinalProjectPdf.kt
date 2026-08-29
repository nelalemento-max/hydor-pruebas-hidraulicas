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
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PbcFinalProjectPdf {
    private const val PAGE_W = 842
    private const val PAGE_H = 595
    private const val M = 24f
    private val NAVY = Color.rgb(14, 46, 77)
    private val BLUE = Color.rgb(18, 88, 158)
    private val YELLOW = Color.rgb(255, 210, 31)
    private val GREEN = Color.rgb(38, 145, 76)
    private val RED = Color.rgb(191, 45, 45)
    private val AMBER = Color.rgb(210, 145, 0)
    private val LIGHT = Color.rgb(244, 247, 249)
    private val GRID = Color.rgb(218, 225, 231)
    private val TEXT = Color.rgb(36, 44, 51)

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

        fun newPage(): PdfDocument.Page {
            pageNo++
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            drawHeader(page.canvas, logo, pageNo)
            return page
        }

        val totalLength = bundles.sumOf { it.section.lengthMeters }
        val passed = bundles.count { it.test?.status == "PASSED" }
        val review = bundles.count { it.test?.status == "REVIEW" }
        val pending = bundles.size - passed - review

        var page = newPage()
        val c = page.canvas
        sectionLabel(c, 24f, 82f, 250f, "1. DATOS GENERALES")
        panel(c, 24f, 104f, 250f, 146f)
        val leftData = listOf(
            "Proyecto" to project.name,
            "Ubicación" to project.location.ifBlank { "No registrada" },
            "Empresa" to project.company.ifBlank { "Laboratorio PBC Bolivia" },
            "Fecha de informe" to SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
            "Tramos consolidados" to bundles.size.toString()
        )
        drawKeyValues(c, 36f, 121f, 226f, leftData)

        sectionLabel(c, 292f, 82f, 245f, "2. RESUMEN DEL PROYECTO")
        panel(c, 292f, 104f, 245f, 146f)
        stat(c, 307f, 124f, "TRAMOS", bundles.size.toString(), BLUE)
        stat(c, 383f, 124f, "LONGITUD", "${fmt(totalLength)} m", BLUE)
        stat(c, 474f, 124f, "ACEPTADOS", passed.toString(), GREEN)
        stat(c, 307f, 188f, "REVISIÓN", review.toString(), AMBER)
        stat(c, 383f, 188f, "PENDIENTES", pending.toString(), if (pending == 0) GREEN else AMBER)
        val overall = if (review > 0) "REQUIERE REVISIÓN" else if (pending > 0) "INCOMPLETO" else "CUMPLE"
        val overallColor = if (review > 0) RED else if (pending > 0) AMBER else GREEN
        badge(c, 463f, 199f, 62f, 28f, overall, overallColor)

        sectionLabel(c, 557f, 82f, 261f, "3. CRITERIO DE EVALUACIÓN")
        panel(c, 557f, 104f, 261f, 146f)
        val firstTest = bundles.mapNotNull { it.test }.firstOrNull()
        val criteria = listOf(
            "Presión de ensayo" to (firstTest?.let { "${fmt(it.targetPressureBar)} bar" } ?: "Según cada tramo"),
            "Duración" to (firstTest?.let { formatMinutes(it.durationMinutes) } ?: "Según cada prueba"),
            "Caída máxima" to (firstTest?.let { "${fmt(it.maxAllowedDropBar)} bar" } ?: "Configurada por tramo"),
            "Método" to "Prueba hidráulica con manómetro",
            "Evaluación" to "Comparación contra límite configurado"
        )
        drawKeyValues(c, 569f, 121f, 237f, criteria)

        sectionLabel(c, 24f, 267f, 794f, "4. RED GRÁFICA CONSOLIDADA")
        panel(c, 24f, 289f, 794f, 126f)
        drawHorizontalNetwork(c, bundles, topology, bends, 42f, 305f, 758f, 92f)

        sectionLabel(c, 24f, 432f, 794f, "5. RESULTADOS POR TRAMO")
        drawResultsTable(c, bundles, topology, 24f, 454f, 794f, 96f)
        footer(c, pageNo)
        pdf.finishPage(page)

        bundles.forEachIndexed { index, bundle ->
            page = newPage()
            val cc = page.canvas
            val test = bundle.test
            val code = topology[bundle.section.id].orEmpty().uppercase().ifBlank { (index + 1).toString() }
            sectionLabel(cc, 24f, 82f, 794f, "DETALLE DEL TRAMO $code · ${bundle.section.startValve} → ${bundle.section.endValve}")

            panel(cc, 24f, 106f, 260f, 150f)
            drawKeyValues(cc, 36f, 123f, 236f, listOf(
                "Código" to code,
                "Inicio – final" to "${bundle.section.startValve} → ${bundle.section.endValve}",
                "Ubicación" to bundle.section.neighborhood,
                "Longitud" to "${fmt(bundle.section.lengthMeters)} m",
                "Diámetro" to "Ø ${bundle.section.diameterInches}\"",
                "Operador" to (test?.operatorName?.ifBlank { "No registrado" } ?: "Sin prueba")
            ))

            panel(cc, 300f, 106f, 248f, 150f)
            if (test != null) {
                val finalP = bundle.readings.lastOrNull()?.confirmedPressureBar ?: test.targetPressureBar
                val drop = max(0.0, test.targetPressureBar - finalP)
                val limit = test.targetPressureBar - test.maxAllowedDropBar
                val extras = extraTimeByTest[test.id].orEmpty()
                drawKeyValues(cc, 312f, 123f, 224f, listOf(
                    "Presión inicial" to "${fmt(test.targetPressureBar)} bar",
                    "Presión final" to "${fmt(finalP)} bar",
                    "Caída acumulada" to "${fmt(drop)} bar",
                    "Límite mínimo" to "${fmt(limit)} bar",
                    "Duración" to formatMinutes(test.durationMinutes),
                    "Tiempo extra" to if (extras.isEmpty()) "No" else extras.joinToString(" + ") { "$it min" }
                ))
            } else drawCentered(cc, "SIN PRUEBA REGISTRADA", 300f, 106f, 248f, 150f, RED, 14f)

            panel(cc, 564f, 106f, 254f, 150f)
            val status = when (test?.status) {
                "PASSED" -> "ACEPTABLE" to GREEN
                "REVIEW" -> "REQUIERE REVISIÓN" to RED
                "IN_PROGRESS" -> "EN CURSO" to AMBER
                else -> "SIN RESULTADO" to AMBER
            }
            drawCentered(cc, status.first, 576f, 132f, 230f, 55f, status.second, 16f)
            drawWrapped(cc, if (test?.status == "PASSED") "La presión se mantuvo dentro del rango permitido configurado para la prueba." else "Revise el comportamiento registrado y la especificación técnica aplicable antes de aceptar el tramo.", 576f, 200f, 230f, bodyPaint(9f), 11f)

            sectionLabel(cc, 24f, 274f, 500f, "6. GRÁFICA DE PRESIÓN / TIEMPO")
            panel(cc, 24f, 296f, 500f, 204f)
            if (test != null && bundle.readings.isNotEmpty()) drawPressureChart(cc, bundle.readings, test, 56f, 322f, 440f, 148f)
            else drawCentered(cc, "Sin lecturas suficientes", 24f, 296f, 500f, 204f, Color.GRAY, 12f)

            sectionLabel(cc, 540f, 274f, 278f, "7. EVIDENCIA FOTOGRÁFICA")
            panel(cc, 540f, 296f, 278f, 204f)
            val photoPath = bundle.readings.firstOrNull { !it.imagePath.isNullOrBlank() }?.imagePath
            if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
                val bmp = BitmapFactory.decodeFile(photoPath)
                if (bmp != null) cc.drawBitmap(bmp, null, RectF(556f, 312f, 802f, 484f), Paint(Paint.ANTI_ALIAS_FLAG))
            } else drawCentered(cc, "Sin fotografía registrada", 540f, 296f, 278f, 204f, Color.GRAY, 12f)

            drawConclusionBox(cc, bundle, test, 24f, 516f, 794f, 39f)
            footer(cc, pageNo)
            pdf.finishPage(page)
        }

        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    private fun drawHeader(c: Canvas, logo: android.graphics.Bitmap?, pageNo: Int) {
        c.drawColor(Color.WHITE)
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 66f, Paint().apply { color = NAVY })
        c.drawRect(555f, 0f, PAGE_W.toFloat(), 66f, Paint().apply { color = YELLOW })
        if (logo != null) c.drawBitmap(logo, null, RectF(18f, 8f, 72f, 62f), Paint(Paint.ANTI_ALIAS_FLAG))
        val whiteTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 21f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("LABORATORIO PBC BOLIVIA", 84f, 28f, whiteTitle)
        c.drawText("LABORATORIO TÉCNICO · PRUEBAS HIDRÁULICAS", 84f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD })
        c.drawText("INFORME DE PRUEBA DE PRESIÓN", 580f, 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = 13f; typeface = Typeface.DEFAULT_BOLD })
        c.drawText("RED DE DISTRIBUCIÓN DE AGUA", 590f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = 10f; typeface = Typeface.DEFAULT_BOLD })
        c.drawText("Pág. $pageNo", 786f, 58f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = 7f })
    }

    private fun footer(c: Canvas, pageNo: Int) {
        c.drawLine(M, 567f, PAGE_W - M, 567f, Paint().apply { color = NAVY; strokeWidth = 1f })
        c.drawText("Laboratorio PBC Bolivia · Informe generado por la aplicación PBC", M, 580f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7.5f })
        c.drawText("Página $pageNo", 770f, 580f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7.5f })
    }

    private fun sectionLabel(c: Canvas, x: Float, y: Float, w: Float, title: String) {
        c.drawRoundRect(RectF(x, y, x + w, y + 18f), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YELLOW })
        c.drawText(title, x + 9f, y + 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = 10f; typeface = Typeface.DEFAULT_BOLD })
    }

    private fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT })
        c.drawRoundRect(RectF(x, y, x + w, y + h), 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; style = Paint.Style.STROKE; strokeWidth = 1f })
    }

    private fun drawKeyValues(c: Canvas, x: Float, y: Float, w: Float, rows: List<Pair<String, String>>) {
        var yy = y
        val key = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = 8.2f; typeface = Typeface.DEFAULT_BOLD }
        val value = bodyPaint(8.3f)
        rows.forEach { (k, v) ->
            c.drawText(k, x, yy, key)
            drawEllipsized(c, v, x + 91f, yy, w - 91f, value)
            yy += 22f
        }
    }

    private fun stat(c: Canvas, x: Float, y: Float, label: String, value: String, color: Int) {
        c.drawCircle(x + 15f, y + 10f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawText(value, x + 34f, y + 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = NAVY; textSize = 13f; typeface = Typeface.DEFAULT_BOLD })
        c.drawText(label, x, y + 31f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.DKGRAY; textSize = 7.2f; typeface = Typeface.DEFAULT_BOLD })
    }

    private fun badge(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, color: Int) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 7f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText(text, x + w / 2f, y + h / 2f + 2.5f, p)
    }

    private fun drawResultsTable(c: Canvas, bundles: List<FinalProjectPdf.TestBundle>, topology: Map<Long, String>, x: Float, y: Float, w: Float, h: Float) {
        val cols = floatArrayOf(55f, 135f, 70f, 80f, 80f, 75f, 75f, 130f)
        val scale = w / cols.sum()
        val widths = cols.map { it * scale }
        val headerH = 21f
        val rowH = ((h - headerH) / max(1, minOf(6, bundles.size))).coerceAtLeast(12f)
        c.drawRect(x, y, x + w, y + headerH, Paint().apply { color = BLUE })
        val headers = listOf("CÓD.", "INICIO – FINAL", "Ø", "LONG. m", "P. INI", "P. FIN", "CAÍDA", "RESULTADO")
        var xx = x
        headers.forEachIndexed { i, s ->
            c.drawText(s, xx + widths[i] / 2f, y + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 7f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER })
            xx += widths[i]
        }
        bundles.take(6).forEachIndexed { r, b ->
            val yy = y + headerH + r * rowH
            if (r % 2 == 1) c.drawRect(x, yy, x + w, yy + rowH, Paint().apply { color = Color.rgb(250, 251, 252) })
            val test = b.test
            val finalP = if (test != null) b.readings.lastOrNull()?.confirmedPressureBar ?: test.targetPressureBar else null
            val drop = if (test != null && finalP != null) max(0.0, test.targetPressureBar - finalP) else null
            val status = when (test?.status) { "PASSED" -> "ACEPTABLE"; "REVIEW" -> "REVISIÓN"; "IN_PROGRESS" -> "EN CURSO"; else -> "SIN PRUEBA" }
            val values = listOf(
                topology[b.section.id].orEmpty().uppercase().ifBlank { "—" },
                "${b.section.startValve} – ${b.section.endValve}",
                b.section.diameterInches,
                fmt(b.section.lengthMeters),
                test?.let { fmt(it.targetPressureBar) } ?: "—",
                finalP?.let { fmt(it) } ?: "—",
                drop?.let { fmt(it) } ?: "—",
                status
            )
            xx = x
            values.forEachIndexed { i, s ->
                drawEllipsized(c, s, xx + 3f, yy + rowH / 2f + 3f, widths[i] - 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == 7) when (test?.status) { "PASSED" -> GREEN; "REVIEW" -> RED; else -> TEXT } else TEXT; textSize = 7f; typeface = if (i == 7) Typeface.DEFAULT_BOLD else Typeface.DEFAULT })
                xx += widths[i]
            }
            c.drawLine(x, yy + rowH, x + w, yy + rowH, Paint().apply { color = GRID; strokeWidth = .7f })
        }
    }

    private data class Geo(val s: android.graphics.PointF, val e: android.graphics.PointF)

    private fun drawHorizontalNetwork(c: Canvas, bundles: List<FinalProjectPdf.TestBundle>, topology: Map<Long, String>, bends: Map<Long, Float>, x: Float, y: Float, w: Float, h: Float) {
        val sections = bundles.map { it.section }
        if (sections.isEmpty()) return
        val byCode = sections.associateBy { topology[it.id].orEmpty().lowercase() }
        val numeric = byCode.keys.filter { it.matches(Regex("^\\d+$")) }.sortedBy { it.toInt() }
        if (numeric.isEmpty()) { drawCentered(c, "Asigne posiciones 1, 2, 3… para representar la red", x, y, w, h, Color.GRAY, 10f); return }
        val maxLen = sections.maxOfOrNull { it.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
        fun visual(s: SectionEntity) = 80f + 95f * (s.lengthMeters / maxLen).toFloat()
        val total = numeric.sumOf { visual(byCode[it]!!).toDouble() }.toFloat()
        val scale = (w - 70f) / total.coerceAtLeast(1f)
        val geo = mutableMapOf<Long, Geo>()
        var cur = android.graphics.PointF(x + 24f, y + h / 2f)
        numeric.forEach { code ->
            val s = byCode[code] ?: return@forEach
            val len = visual(s) * scale
            val end = android.graphics.PointF(cur.x + len, cur.y)
            geo[s.id] = Geo(android.graphics.PointF(cur.x, cur.y), end)
            cur = end
        }
        byCode.forEach { (code, s) ->
            val m = Regex("^(\\d+)([ab])$").matchEntire(code) ?: return@forEach
            val parent = byCode[m.groupValues[1]] ?: return@forEach
            val pg = geo[parent.id] ?: return@forEach
            val dir = if (m.groupValues[2] == "a") -1f else 1f
            val len = (visual(s) * scale * .72f).coerceAtMost(110f)
            geo[s.id] = Geo(android.graphics.PointF(pg.e.x, pg.e.y), android.graphics.PointF(pg.e.x + len, pg.e.y + dir * 31f))
        }
        geo.forEach { (id, g) ->
            val bundle = bundles.first { it.section.id == id }
            val statusColor = when (bundle.test?.status) { "PASSED" -> GREEN; "REVIEW" -> RED; "IN_PROGRESS" -> AMBER; else -> BLUE }
            val bend = bends[id] ?: 0f
            val mx = (g.s.x + g.e.x) / 2f
            val my = (g.s.y + g.e.y) / 2f
            val path = Path().apply { moveTo(g.s.x, g.s.y); quadTo(mx, my + bend * 22f, g.e.x, g.e.y) }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 222, 228); style = Paint.Style.STROKE; strokeWidth = 9f })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor; style = Paint.Style.STROKE; strokeWidth = 5f })
            c.drawCircle(g.s.x, g.s.y, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            c.drawCircle(g.s.x, g.s.y, 3.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY })
            val label = topology[id].orEmpty().uppercase().ifBlank { "—" }
            c.drawText("$label · ${bundle.section.lengthMeters.toInt()} m · Ø${bundle.section.diameterInches}\"", mx - 25f, my - 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = 6.8f; typeface = Typeface.DEFAULT_BOLD })
        }
    }

    private fun drawPressureChart(c: Canvas, readings: List<PressureReadingEntity>, test: HydraulicTestEntity, x: Float, y: Float, w: Float, h: Float) {
        val vals = readings.map { it.confirmedPressureBar } + test.targetPressureBar + (test.targetPressureBar - test.maxAllowedDropBar)
        var minV = vals.minOrNull() ?: 0.0
        var maxV = vals.maxOrNull() ?: 1.0
        if (maxV - minV < .2) { maxV += .15; minV -= .15 } else { val p=(maxV-minV)*.18; maxV+=p; minV-=p }
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; strokeWidth = 1f }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 6.8f }
        repeat(6) { i ->
            val yy = y + h * i / 5f
            c.drawLine(x, yy, x + w, yy, axis)
            c.drawText(String.format(Locale.US, "%.2f", maxV - (maxV - minV) * i / 5.0), x - 30f, yy + 2f, txt)
        }
        repeat(7) { i -> c.drawLine(x + w * i / 6f, y, x + w * i / 6f, y + h, axis) }
        fun yy(v: Double) = y + h - (((v - minV) / (maxV - minV)).toFloat() * h)
        c.drawLine(x, yy(test.targetPressureBar), x + w, yy(test.targetPressureBar), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; strokeWidth = 1.2f })
        c.drawLine(x, yy(test.targetPressureBar - test.maxAllowedDropBar), x + w, yy(test.targetPressureBar - test.maxAllowedDropBar), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; strokeWidth = 1.2f })
        val t0 = readings.first().capturedAt
        val t1 = readings.last().capturedAt.coerceAtLeast(t0 + 1)
        val p = Path()
        readings.forEachIndexed { i, r ->
            val xx = x + ((r.capturedAt - t0).toFloat() / (t1 - t0).toFloat()) * w
            val yv = yy(r.confirmedPressureBar)
            if (i == 0) p.moveTo(xx, yv) else p.lineTo(xx, yv)
            c.drawCircle(xx, yv, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN })
            c.drawText(fmt(r.confirmedPressureBar), xx - 9f, yv - 6f, txt)
        }
        if (readings.size > 1) c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; style = Paint.Style.STROKE; strokeWidth = 2.4f })
        c.drawText("Presión (bar)", x - 31f, y - 8f, Paint(txt).apply { typeface = Typeface.DEFAULT_BOLD })
        c.drawText("Tiempo", x + w / 2f - 14f, y + h + 16f, Paint(txt).apply { typeface = Typeface.DEFAULT_BOLD })
    }

    private fun drawConclusionBox(c: Canvas, bundle: FinalProjectPdf.TestBundle, test: HydraulicTestEntity?, x: Float, y: Float, w: Float, h: Float) {
        val color = when (test?.status) { "PASSED" -> GREEN; "REVIEW" -> RED; else -> AMBER }
        c.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 28 })
        c.drawText("CONCLUSIÓN TÉCNICA", x + 10f, y + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD })
        val text = when (test?.status) {
            "PASSED" -> "El tramo ${bundle.section.startValve} → ${bundle.section.endValve} presenta resultado ACEPTABLE respecto al límite configurado en la prueba."
            "REVIEW" -> "El tramo ${bundle.section.startValve} → ${bundle.section.endValve} requiere REVISIÓN TÉCNICA antes de su aceptación definitiva."
            else -> "El tramo no cuenta todavía con un resultado final consolidado."
        }
        drawWrapped(c, text, x + 10f, y + 27f, w - 20f, bodyPaint(7.5f), 9f)
    }

    private fun bodyPaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = size }
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun formatMinutes(m: Int) = if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"

    private fun drawCentered(c: Canvas, text: String, x: Float, y: Float, w: Float, h: Float, color: Int, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText(text, x + w / 2f, y + h / 2f + size / 3f, p)
    }

    private fun drawEllipsized(c: Canvas, value: String, x: Float, y: Float, maxW: Float, p: Paint) {
        if (p.measureText(value) <= maxW) { c.drawText(value, x, y, p); return }
        var s = value
        while (s.length > 2 && p.measureText("$s…") > maxW) s = s.dropLast(1)
        c.drawText("$s…", x, y, p)
    }

    private fun drawWrapped(c: Canvas, value: String, x: Float, y: Float, maxW: Float, p: Paint, leading: Float) {
        val words = value.split(" ")
        var line = ""
        var yy = y
        words.forEach { word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (p.measureText(candidate) > maxW && line.isNotBlank()) { c.drawText(line, x, yy, p); yy += leading; line = word }
            else line = candidate
        }
        if (line.isNotBlank()) c.drawText(line, x, yy, p)
    }
}
