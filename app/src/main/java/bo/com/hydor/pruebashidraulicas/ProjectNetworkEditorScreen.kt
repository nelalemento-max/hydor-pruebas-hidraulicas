package bo.com.hydor.pruebashidraulicas

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import kotlin.math.max

private val EditorBlue = Color(0xFF123A63)
private val EditorGreen = Color(0xFF26734D)
private val EditorAmber = Color(0xFFA96400)
private val EditorRed = Color(0xFFB3261E)
private val EditorGray = Color(0xFF8A949E)
private val EditorBackground = Color(0xFFF7F9FB)

@Composable
fun ProjectNetworkEditorScreen(projectId: Long, onBack: () -> Unit, onGoReports: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var sections by remember { mutableStateOf<List<SectionEntity>>(emptyList()) }
    var testsBySection by remember { mutableStateOf<Map<Long, HydraulicTestEntity>>(emptyMap()) }
    var included by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bends by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var topology by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var saved by remember { mutableStateOf(false) }
    var consolidated by remember { mutableStateOf(false) }
    var showCurveControls by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        project = dao.getProject(projectId)
        sections = dao.getSectionsForProject(projectId)
        val allTests = dao.getAllTests()
        testsBySection = allTests.filter { test -> sections.any { it.id == test.sectionId } }
            .groupBy { it.sectionId }
            .mapValues { (_, list) -> list.maxByOrNull { it.id }!! }
        val state = NetworkLayoutStore.load(context, projectId, sections.map { it.id }.toSet())
        included = state.includedSectionIds
        bends = state.bends
        topology = state.topologyCodes
        saved = state.saved
        consolidated = state.consolidated
    }

    val selectedSections = sections.filter { it.id in included }
    val validation = remember(selectedSections, topology) { validateTopology(selectedSections, topology) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(project?.name ?: "Proyecto", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = EditorBlue)
        Text(
            "HYDOR arma la red automáticamente usando códigos simples. Es más rápido y estable que arrastrar tubos con el dedo.",
            fontSize = 12.sp
        )

        if (sections.isEmpty()) {
            Text("Este proyecto todavía no tiene tramos registrados.")
        } else {
            AutoNetworkDiagram(
                sections = selectedSections,
                testsBySection = testsBySection,
                topology = topology,
                bends = bends
            )

            Card(colors = CardDefaults.cardColors(containerColor = if (validation.valid) EditorGreen.copy(alpha = 0.10f) else EditorAmber.copy(alpha = 0.10f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (validation.valid) "✓ RED COMPLETA" else "⚠ FALTA DEFINIR LA RED", fontWeight = FontWeight.ExtraBold, color = if (validation.valid) EditorGreen else EditorAmber)
                    Text(validation.message, fontSize = 11.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2F8))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cómo numerar", fontWeight = FontWeight.Bold, color = EditorBlue)
                    Text("1 = primer tramo", fontSize = 11.sp)
                    Text("2 = continúa después del 1 · 3 = continúa después del 2", fontSize = 11.sp)
                    Text("1a = ramificación izquierda desde el final del 1", fontSize = 11.sp)
                    Text("1b = ramificación derecha desde el final del 1", fontSize = 11.sp)
                    Text("2a / 2b = ramificaciones desde el final del 2, y así sucesivamente.", fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tramos disponibles", fontWeight = FontWeight.Bold, color = EditorBlue)
                TextButton(onClick = { showCurveControls = !showCurveControls }) {
                    Text(if (showCurveControls) "OCULTAR CURVAS" else "AJUSTAR CURVAS")
                }
            }

            sections.forEach { section ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = section.id in included,
                                onCheckedChange = { checked ->
                                    included = if (checked) included + section.id else included - section.id
                                    if (!checked) topology = topology - section.id
                                    saved = false
                                    consolidated = false
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${section.startValve} → ${section.endValve}", fontWeight = FontWeight.Bold)
                                Text("${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\" · ${section.neighborhood}", fontSize = 12.sp)
                            }
                        }

                        if (section.id in included) {
                            OutlinedTextField(
                                value = topology[section.id].orEmpty(),
                                onValueChange = { raw ->
                                    val clean = raw.lowercase().filter { it.isDigit() || it == 'a' || it == 'b' }.take(4)
                                    topology = topology + (section.id to clean)
                                    saved = false
                                    consolidated = false
                                },
                                label = { Text("Posición en la red") },
                                supportingText = { Text("Ej.: 1, 2, 3, 1a, 1b, 2a...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (showCurveControls && section.id in included) {
                            Text("Curvatura opcional", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = bends[section.id] ?: 0f,
                                onValueChange = { value ->
                                    bends = bends + (section.id to value)
                                    saved = false
                                    consolidated = false
                                },
                                valueRange = -1f..1f
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Arriba", fontSize = 10.sp, color = Color.Gray)
                                Text("Recto", fontSize = 10.sp, color = Color.Gray)
                                Text("Abajo", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    NetworkLayoutStore.saveLayout(context, projectId, included, bends, topology)
                    saved = true
                    consolidated = false
                    NetworkLayoutStore.setConsolidated(context, projectId, false)
                },
                enabled = included.isNotEmpty() && validation.valid,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (saved) "GRÁFICO GUARDADO" else "GUARDAR GRÁFICO", fontWeight = FontWeight.Bold) }

            if (!validation.valid && included.isNotEmpty()) {
                Text("Guardar está bloqueado hasta que todos los tramos seleccionados tengan una posición válida y formen una sola red.", color = EditorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    NetworkLayoutStore.saveLayout(context, projectId, included, bends, topology)
                    NetworkLayoutStore.setConsolidated(context, projectId, true)
                    saved = true
                    consolidated = true
                },
                enabled = included.isNotEmpty() && validation.valid,
                colors = ButtonDefaults.buttonColors(containerColor = EditorGreen),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (consolidated) "CONSOLIDADO EN INFORMES" else "CONSOLIDAR INFORME", fontWeight = FontWeight.Bold) }

            if (consolidated) {
                OutlinedButton(onClick = onGoReports, modifier = Modifier.fillMaxWidth()) { Text("VER EN INFORMES Y RESULTADOS") }
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Volver") }
    }
}

private data class TopologyValidation(val valid: Boolean, val message: String)

private fun validateTopology(sections: List<SectionEntity>, topology: Map<Long, String>): TopologyValidation {
    if (sections.isEmpty()) return TopologyValidation(false, "Selecciona al menos un tramo.")
    val codes = sections.map { topology[it.id].orEmpty().trim().lowercase() }
    if (codes.any { it.isBlank() }) return TopologyValidation(false, "Asigna un número o ramificación a cada tramo seleccionado.")
    if (codes.size != codes.toSet().size) return TopologyValidation(false, "Hay posiciones repetidas. Cada tramo debe tener un código distinto.")
    if ("1" !in codes) return TopologyValidation(false, "La red debe comenzar con el tramo 1.")

    val regex = Regex("^(\\d+)([ab])?$")
    for (code in codes) {
        val match = regex.matchEntire(code) ?: return TopologyValidation(false, "Código no válido: $code")
        val base = match.groupValues[1].toIntOrNull() ?: return TopologyValidation(false, "Código no válido: $code")
        val branch = match.groupValues[2]
        if (branch.isEmpty() && base > 1 && "${base - 1}" !in codes) {
            return TopologyValidation(false, "Para usar $code primero debe existir el tramo ${base - 1}.")
        }
        if (branch.isNotEmpty() && "$base" !in codes) {
            return TopologyValidation(false, "$code necesita que exista primero el tramo principal $base.")
        }
    }
    return TopologyValidation(true, "Todos los tramos seleccionados están relacionados. HYDOR puede construir y guardar la red.")
}

private data class PipeGeometry(val start: Offset, val end: Offset)

private fun buildAutoGeometry(sections: List<SectionEntity>, topology: Map<Long, String>): Map<Long, PipeGeometry> {
    val byCode = sections.associateBy { topology[it.id].orEmpty().trim().lowercase() }
    val maxLength = sections.maxOfOrNull { it.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
    val result = mutableMapOf<Long, PipeGeometry>()

    fun visualLength(section: SectionEntity): Float = 150f + 250f * (section.lengthMeters / maxLength).toFloat().coerceIn(0.0f, 1.0f)

    val numericCodes = byCode.keys.filter { it.matches(Regex("^\\d+$")) }.sortedBy { it.toInt() }
    var cursor = Offset(90f, 230f)
    numericCodes.forEach { code ->
        val section = byCode[code] ?: return@forEach
        val len = visualLength(section)
        val end = Offset(cursor.x + len, cursor.y)
        result[section.id] = PipeGeometry(cursor, end)
        cursor = end
    }

    byCode.forEach { (code, section) ->
        val m = Regex("^(\\d+)([ab])$").matchEntire(code) ?: return@forEach
        val baseCode = m.groupValues[1]
        val side = m.groupValues[2]
        val parent = byCode[baseCode] ?: return@forEach
        val parentGeometry = result[parent.id] ?: return@forEach
        val len = visualLength(section)
        val direction = if (side == "a") -1f else 1f
        val start = parentGeometry.end
        val end = Offset(start.x + len * 0.72f, start.y + direction * max(130f, len * 0.48f))
        result[section.id] = PipeGeometry(start, end)
    }

    return result
}

@Composable
private fun AutoNetworkDiagram(
    sections: List<SectionEntity>,
    testsBySection: Map<Long, HydraulicTestEntity>,
    topology: Map<Long, String>,
    bends: Map<Long, Float>
) {
    val geometry = remember(sections, topology) { buildAutoGeometry(sections, topology) }
    val canvasWidth = max(1000, ((geometry.values.maxOfOrNull { max(it.start.x, it.end.x) } ?: 800f) + 180f).toInt()).dp
    val canvasHeight = 620.dp

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VISTA AUTOMÁTICA DE LA RED", fontWeight = FontWeight.ExtraBold, color = EditorBlue)
            Text("La escala es relativa: los tramos más largos se dibujan más largos. Las ramificaciones nacen automáticamente del tramo base.", fontSize = 10.sp, color = Color.Gray)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(canvasWidth).height(canvasHeight).background(EditorBackground, RoundedCornerShape(12.dp))) {
                    val grid = Color(0xFFDDE4EA).copy(alpha = 0.60f)
                    var gx = 0f
                    while (gx < size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += 50f }
                    var gy = 0f
                    while (gy < size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f); gy += 50f }

                    sections.forEach { section ->
                        val g = geometry[section.id] ?: return@forEach
                        val bend = bends[section.id] ?: 0f
                        val control = Offset((g.start.x + g.end.x) / 2f, (g.start.y + g.end.y) / 2f + bend * 90f)
                        val pipeColor = when (testsBySection[section.id]?.status) {
                            "PASSED" -> EditorGreen
                            "REVIEW" -> EditorRed
                            "IN_PROGRESS" -> EditorAmber
                            "READY" -> EditorBlue
                            else -> EditorGray
                        }
                        val path = Path().apply { moveTo(g.start.x, g.start.y); quadraticBezierTo(control.x, control.y, g.end.x, g.end.y) }
                        drawPath(path, Color(0xFFD5DCE3), style = androidx.compose.ui.graphics.drawscope.Stroke(22f))
                        drawPath(path, pipeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(12f))
                        drawCircle(Color.White, 17f, g.start)
                        drawCircle(EditorBlue, 11f, g.start)
                        drawCircle(Color.White, 17f, g.end)
                        drawCircle(EditorBlue, 11f, g.end)

                        val code = topology[section.id].orEmpty().uppercase()
                        val mid = Offset((g.start.x + g.end.x) / 2f, (g.start.y + g.end.y) / 2f)
                        drawContext.canvas.nativeCanvas.drawText(
                            "$code · ${section.startValve}→${section.endValve} · ${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\"",
                            mid.x - 70f,
                            mid.y - 18f,
                            Paint().apply { isAntiAlias = true; textSize = 21f; this.color = android.graphics.Color.rgb(55, 67, 78) }
                        )
                    }
                }
            }
        }
    }
}
