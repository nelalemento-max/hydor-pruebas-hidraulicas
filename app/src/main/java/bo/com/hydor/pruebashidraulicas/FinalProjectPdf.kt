package bo.com.hydor.pruebashidraulicas

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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

object FinalProjectPdf {
    data class TestBundle(
        val section: SectionEntity,
        val test: HydraulicTestEntity?,
        val readings: List<PressureReadingEntity>
    )

    fun build(
        context: Context,
        project: ProjectEntity,
        bundles: List<TestBundle>,
        topology: Map<Long, String>,
        bends: Map<Long, Float>,
        extraTimeByTest: Map<Long, List<Int>>
    ): ByteArray {
        val pdf = PdfDocument()
        val out = ByteArrayOutputStream()
        val pageW = 595
        val pageH = 842
        val margin = 38f
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18,58,99); textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18,58,99); textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45,52,58); textSize = 9.5f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80,88,96); textSize = 7.5f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(210,218,225); strokeWidth = 1f }

        fun newPage() {
            page?.let { pdf.finishPage(it) }
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = 42f
            page!!.canvas.drawText("HYDOR · INFORME TÉCNICO DE PRUEBAS HIDRÁULICAS", margin, y, titlePaint)
            y += 15f
            page!!.canvas.drawText("Generado ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", margin, y, small)
            page!!.canvas.drawText("Página $pageNo", pageW - margin - 45f, y, small)
            y += 16f
            page!!.canvas.drawLine(margin, y, pageW - margin, y, linePaint)
            y += 18f
        }

        fun ensure(space: Float) { if (page == null || y + space > pageH - 45f) newPage() }
        fun text(value: String, paint: Paint = body, indent: Float = 0f, leading: Float = 12f) {
            ensure(leading + 3f)
            val maxChars = if (paint.textSize <= 8f) 95 else 82
            value.chunked(maxChars).forEach { line ->
                ensure(leading)
                page!!.canvas.drawText(line, margin + indent, y, paint)
                y += leading
            }
        }
        fun heading(value: String) { ensure(25f); y += 5f; page!!.canvas.drawText(value, margin, y, hPaint); y += 13f }

        newPage()
        heading("1. IDENTIFICACIÓN DEL PROYECTO")
        text("Proyecto: ${project.name}")
        if (project.company.isNotBlank()) text("Empresa: ${project.company}")
        if (project.location.isNotBlank()) text("Ubicación: ${project.location}")
        val totalLength = bundles.sumOf { it.section.lengthMeters }
        val passed = bundles.count { it.test?.status == "PASSED" }
        val review = bundles.count { it.test?.status == "REVIEW" }
        val pending = bundles.size - passed - review
        text("Tramos consolidados: ${bundles.size} · Longitud total: ${fmt(totalLength)} m")
        text("Resultados: $passed aceptables · $review requieren revisión · $pending pendientes/preparados")

        heading("2. ESQUEMA CONSOLIDADO DE LA RED")
        drawNetwork(page!!.canvas, bundles.map { it.section }, topology, bends, margin, y, pageW - margin * 2, 255f)
        y += 270f
        text("Esquema referencial construido en HYDOR. La longitud gráfica conserva proporción relativa entre tramos; la geometría no sustituye al plano topográfico o de diseño.", small)

        heading("3. RESUMEN DE TRAMOS")
        bundles.sortedWith(compareBy({ topology[it.section.id].orEmpty().filter { c -> c.isDigit() }.toIntOrNull() ?: 999 }, { topology[it.section.id].orEmpty() })).forEach { b ->
            ensure(40f)
            val code = topology[b.section.id].orEmpty().uppercase().ifBlank { "—" }
            val status = when (b.test?.status) { "PASSED" -> "ACEPTABLE"; "REVIEW" -> "REQUIERE REVISIÓN"; "IN_PROGRESS" -> "EN CURSO"; "READY" -> "PREPARADA"; else -> "SIN PRUEBA" }
            text("$code  ${b.section.startValve} → ${b.section.endValve}  |  ${fmt(b.section.lengthMeters)} m  |  Ø ${b.section.diameterInches}\"  |  $status", body)
        }

        bundles.forEachIndexed { index, bundle ->
            val test = bundle.test
            heading("${index + 4}. TRAMO ${topology[bundle.section.id].orEmpty().uppercase().ifBlank { index + 1 }} · ${bundle.section.startValve} → ${bundle.section.endValve}")
            text("Ubicación: ${bundle.section.neighborhood} · Longitud: ${fmt(bundle.section.lengthMeters)} m · Diámetro: Ø ${bundle.section.diameterInches}\"")
            if (test == null) {
                text("No existe una prueba registrada para este tramo.")
                return@forEachIndexed
            }
            val actual = bundle.readings.lastOrNull()?.confirmedPressureBar ?: test.targetPressureBar
            val drop = max(0.0, test.targetPressureBar - actual)
            val limit = test.targetPressureBar - test.maxAllowedDropBar
            val extra = extraTimeByTest[test.id].orEmpty()
            text("Operador: ${test.operatorName.ifBlank { "No registrado" }}")
            text("Presión nominal: ${fmt(test.nominalPressureBar)} bar · Presión de ensayo: ${fmt(test.targetPressureBar)} bar · Límite mínimo: ${fmt(limit)} bar")
            text("Duración programada: ${formatMinutes(test.durationMinutes)}${if (extra.isNotEmpty()) " · Tiempo extra: ${extra.joinToString(" + ")} min" else ""}")
            text("Presión final: ${fmt(actual)} bar · Caída acumulada: ${fmt(drop)} bar · Máxima permitida: ${fmt(test.maxAllowedDropBar)} bar")
            val result = if (test.status == "PASSED") "RESULTADO: ACEPTABLE" else if (test.status == "REVIEW") "RESULTADO: REQUIERE REVISIÓN" else "ESTADO: ${test.status}"
            text(result, Paint(hPaint).apply { color = if (test.status == "PASSED") Color.rgb(38,115,77) else Color.rgb(179,38,30) })

            if (bundle.readings.isNotEmpty()) {
                text("Lecturas registradas:", hPaint)
                bundle.readings.forEachIndexed { i, r ->
                    val src = when (r.source) { "PROGRAMMED" -> "PROGRAMADA"; "MANUAL" -> "MANUAL"; else -> "CÁMARA" }
                    text("${i + 1}. ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.capturedAt))} · ${fmt(r.confirmedPressureBar)} bar · $src", small, 8f, 10f)
                }
                ensure(170f)
                drawPressureChart(page!!.canvas, bundle.readings, test, margin, y, pageW - margin * 2, 145f)
                y += 158f
            }

            val photos = bundle.readings.mapNotNull { it.imagePath }.distinct().take(3)
            if (photos.isNotEmpty()) {
                text("Evidencias fotográficas del manómetro:", hPaint)
                photos.forEach { path ->
                    val f = File(path)
                    if (f.exists()) {
                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp != null) {
                            ensure(155f)
                            val targetW = 180f
                            val targetH = 135f
                            val left = margin
                            page!!.canvas.drawBitmap(bmp, null, android.graphics.RectF(left, y, left + targetW, y + targetH), null)
                            y += targetH + 10f
                        }
                    }
                }
            }
        }

        heading("CONCLUSIÓN GENERAL")
        val conclusion = when {
            review > 0 -> "El proyecto consolidado contiene $review tramo(s) que requieren revisión técnica por comportamiento de presión fuera del criterio configurado o por resultado marcado para revisión."
            pending > 0 -> "El proyecto contiene tramos aún no finalizados. El informe refleja únicamente el estado registrado en HYDOR al momento de su generación."
            else -> "Los ${bundles.size} tramos consolidados presentan resultado ACEPTABLE conforme a los límites de presión configurados para cada prueba en HYDOR."
        }
        text(conclusion)
        text("Este informe se genera a partir de los datos registrados y confirmados por el operador en campo. Los criterios de aceptación deben corresponder a las especificaciones técnicas aplicables a la obra.", small)
        y += 25f
        text("________________________________________")
        text("Firma y sello del responsable técnico")

        page?.let { pdf.finishPage(it) }
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun formatMinutes(m: Int) = if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"

    private data class Geo(val s: android.graphics.PointF, val e: android.graphics.PointF)

    private fun drawNetwork(canvas: android.graphics.Canvas, sections: List<SectionEntity>, topology: Map<Long, String>, bends: Map<Long, Float>, x: Float, y: Float, w: Float, h: Float) {
        if (sections.isEmpty()) return
        val byCode = sections.associateBy { topology[it.id].orEmpty().lowercase() }
        val maxLen = sections.maxOfOrNull { it.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
        fun len(s: SectionEntity) = 55f + 85f * (s.lengthMeters / maxLen).toFloat()
        val geo = mutableMapOf<Long, Geo>()
        var cursor = android.graphics.PointF(x + w / 2f, y + 8f)
        byCode.keys.filter { it.matches(Regex("^\\d+$")) }.sortedBy { it.toInt() }.forEach { code ->
            val s = byCode[code] ?: return@forEach
            val end = android.graphics.PointF(cursor.x, cursor.y + len(s))
            geo[s.id] = Geo(android.graphics.PointF(cursor.x, cursor.y), end)
            cursor = end
        }
        byCode.forEach { (code, s) ->
            val m = Regex("^(\\d+)([ab])$").matchEntire(code) ?: return@forEach
            val parent = byCode[m.groupValues[1]] ?: return@forEach
            val pg = geo[parent.id] ?: return@forEach
            val dir = if (m.groupValues[2] == "a") -1f else 1f
            val l = len(s)
            geo[s.id] = Geo(android.graphics.PointF(pg.e.x, pg.e.y), android.graphics.PointF(pg.e.x + dir * l * 0.58f, pg.e.y + l * 0.72f))
        }
        val pipe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18,58,99); style = Paint.Style.STROKE; strokeWidth = 5f }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7f }
        geo.forEach { (id, g) ->
            val bend = bends[id] ?: 0f
            val mx = (g.s.x + g.e.x) / 2f
            val my = (g.s.y + g.e.y) / 2f
            val path = Path().apply { moveTo(g.s.x, g.s.y); quadTo(mx + bend * 28f, my, g.e.x, g.e.y) }
            canvas.drawPath(path, pipe)
            val sec = sections.first { it.id == id }
            val code = topology[id].orEmpty().uppercase()
            canvas.drawText("$code ${sec.lengthMeters.toInt()}m Ø${sec.diameterInches}\"", mx + 5f, my, label)
        }
    }

    private fun drawPressureChart(canvas: android.graphics.Canvas, readings: List<PressureReadingEntity>, test: HydraulicTestEntity, x: Float, y: Float, w: Float, h: Float) {
        val values = readings.map { it.confirmedPressureBar } + test.targetPressureBar + (test.targetPressureBar - test.maxAllowedDropBar)
        var minV = values.minOrNull() ?: 0.0
        var maxV = values.maxOrNull() ?: 1.0
        if (maxV - minV < 0.2) { maxV += 0.1; minV -= 0.1 } else { val p=(maxV-minV)*0.15; maxV+=p; minV-=p }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225,230,235); strokeWidth = 1f }
        val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18,58,99); strokeWidth = 2.3f; style = Paint.Style.STROKE }
        val limitP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(179,38,30); strokeWidth = 1.5f }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 6.5f }
        repeat(6) { i -> val yy=y+h*i/5f; canvas.drawLine(x,yy,x+w,yy,grid); val v=maxV-(maxV-minV)*i/5.0; canvas.drawText(String.format(Locale.US,"%.1f",v),x,yy-2,txt) }
        repeat(7) { i -> val xx=x+w*i/6f; canvas.drawLine(xx,y,xx,y+h,grid) }
        fun yFor(v:Double)= y+h-(((v-minV)/(maxV-minV)).toFloat()*h)
        val limit = test.targetPressureBar-test.maxAllowedDropBar
        canvas.drawLine(x,yFor(limit),x+w,yFor(limit),limitP)
        val t0 = readings.first().capturedAt
        val t1 = (readings.last().capturedAt).coerceAtLeast(t0+1)
        val pth = Path()
        readings.forEachIndexed { i,r -> val xx=x+((r.capturedAt-t0).toFloat()/(t1-t0).toFloat())*w; val yy=yFor(r.confirmedPressureBar); if(i==0) pth.moveTo(xx,yy) else pth.lineTo(xx,yy) }
        if(readings.size>1) canvas.drawPath(pth,curve)
    }
}
