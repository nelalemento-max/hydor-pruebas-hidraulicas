package bo.com.hydor.pruebashidraulicas

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import kotlin.math.hypot
import kotlin.math.max

private val EditorBlue = Color(0xFF123A63)
private val EditorGreen = Color(0xFF26734D)
private val EditorAmber = Color(0xFFA96400)
private val EditorRed = Color(0xFFB3261E)
private val EditorGray = Color(0xFF8A949E)
private val EditorBackground = Color(0xFFF7F9FB)

private const val SNAP_DISTANCE = 48f
private const val ENDPOINT_RADIUS = 22f

@Composable
fun ProjectNetworkEditorScreen(projectId: Long, onBack: () -> Unit, onGoReports: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var sections by remember { mutableStateOf<List<SectionEntity>>(emptyList()) }
    var testsBySection by remember { mutableStateOf<Map<Long, HydraulicTestEntity>>(emptyMap()) }
    var included by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bends by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var nodePoints by remember { mutableStateOf<Map<String, NetworkLayoutStore.NodePoint>>(emptyMap()) }
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
        nodePoints = state.nodePoints
        saved = state.saved
        consolidated = state.consolidated
    }

    val selectedSections = sections.filter { it.id in included }
    val connected = remember(selectedSections, nodePoints) { networkIsConnected(selectedSections, nodePoints) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(project?.name ?: "Proyecto", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = EditorBlue)
        Text("Selecciona los tramos y arma manualmente la red. Arrastra los círculos de las puntas: cuando dos extremos se acercan, HYDOR los une automáticamente.", fontSize = 12.sp)

        if (sections.isEmpty()) {
            Text("Este proyecto todavía no tiene tramos registrados.")
        } else {
            InteractiveNetworkDiagram(
                sections = selectedSections,
                testsBySection = testsBySection,
                bends = bends,
                storedPoints = nodePoints,
                onPointsChanged = { points ->
                    nodePoints = points
                    saved = false
                    consolidated = false
                }
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = if (connected) EditorGreen.copy(alpha = 0.10f) else EditorAmber.copy(alpha = 0.10f))
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (connected) "✓" else "⚠", fontSize = 22.sp, color = if (connected) EditorGreen else EditorAmber)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (connected) "RED COMPLETAMENTE UNIDA" else "FALTAN TUBOS POR UNIR", fontWeight = FontWeight.ExtraBold, color = if (connected) EditorGreen else EditorAmber)
                        Text(if (connected) "Ya puedes guardar o consolidar." else "Une todos los tramos seleccionados en una sola red antes de guardar.", fontSize = 11.sp)
                    }
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
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = section.id in included,
                                onCheckedChange = { checked ->
                                    included = if (checked) included + section.id else included - section.id
                                    saved = false
                                    consolidated = false
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${section.startValve} → ${section.endValve}", fontWeight = FontWeight.Bold)
                                Text("${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\" · ${section.neighborhood}", fontSize = 12.sp)
                            }
                        }
                        if (showCurveControls && section.id in included) {
                            Text("Curvatura del tubo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                Text("Curva arriba", fontSize = 10.sp, color = Color.Gray)
                                Text("Recto", fontSize = 10.sp, color = Color.Gray)
                                Text("Curva abajo", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    NetworkLayoutStore.saveLayout(context, projectId, included, bends, nodePoints)
                    saved = true
                    consolidated = false
                    NetworkLayoutStore.setConsolidated(context, projectId, false)
                },
                enabled = included.isNotEmpty() && connected,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (saved) "GRÁFICO GUARDADO" else "GUARDAR GRÁFICO", fontWeight = FontWeight.Bold) }

            if (!connected && included.isNotEmpty()) {
                Text("Guardar está bloqueado hasta que todos los tubos seleccionados estén conectados entre sí.", color = EditorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    NetworkLayoutStore.saveLayout(context, projectId, included, bends, nodePoints)
                    NetworkLayoutStore.setConsolidated(context, projectId, true)
                    saved = true
                    consolidated = true
                },
                enabled = included.isNotEmpty() && connected,
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

private fun startKey(section: SectionEntity) = "${section.id}:S"
private fun endKey(section: SectionEntity) = "${section.id}:E"

private fun defaultPoints(sections: List<SectionEntity>): Map<String, NetworkLayoutStore.NodePoint> {
    if (sections.isEmpty()) return emptyMap()
    val maxLength = sections.maxOfOrNull { it.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
    val result = mutableMapOf<String, NetworkLayoutStore.NodePoint>()
    sections.forEachIndexed { index, section ->
        val row = index % 5
        val column = index / 5
        val y = 90f + row * 120f
        val x = 90f + column * 520f
        val visualLength = (150f + 270f * (section.lengthMeters / maxLength).toFloat().coerceIn(0f, 1f))
        result[startKey(section)] = NetworkLayoutStore.NodePoint(x, y)
        result[endKey(section)] = NetworkLayoutStore.NodePoint(x + visualLength, y)
    }
    return result
}

@Composable
private fun InteractiveNetworkDiagram(
    sections: List<SectionEntity>,
    testsBySection: Map<Long, HydraulicTestEntity>,
    bends: Map<Long, Float>,
    storedPoints: Map<String, NetworkLayoutStore.NodePoint>,
    onPointsChanged: (Map<String, NetworkLayoutStore.NodePoint>) -> Unit
) {
    val initial = remember(sections) { defaultPoints(sections) }
    var points by remember(sections, storedPoints) {
        mutableStateOf(initial.toMutableMap().apply { putAll(storedPoints.filterKeys { key -> sections.any { key.startsWith("${it.id}:") } }) })
    }
    var draggingKey by remember { mutableStateOf<String?>(null) }

    val canvasWidth = 1180.dp
    val canvasHeight = max(420, 150 + ((sections.size + 4) / 5) * 600).dp

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ARMADO INTERACTIVO DE LA RED", fontWeight = FontWeight.ExtraBold, color = EditorBlue)
            Text("Arrastra una punta hacia otra. Al acercarlas, se encajarán. Las longitudes mantienen una escala relativa para distinguir tramos cortos y largos.", fontSize = 10.sp, color = Color.Gray)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(
                    Modifier
                        .width(canvasWidth)
                        .height(canvasHeight)
                        .background(EditorBackground, RoundedCornerShape(12.dp))
                        .pointerInput(sections, points) {
                            detectDragGestures(
                                onDragStart = { touch ->
                                    draggingKey = points.minByOrNull { (_, p) -> hypot((p.x - touch.x).toDouble(), (p.y - touch.y).toDouble()) }
                                        ?.takeIf { (_, p) -> hypot((p.x - touch.x).toDouble(), (p.y - touch.y).toDouble()) <= ENDPOINT_RADIUS * 2.2 }
                                        ?.key
                                },
                                onDrag = { change, dragAmount ->
                                    val key = draggingKey ?: return@detectDragGestures
                                    change.consume()
                                    val old = points[key] ?: return@detectDragGestures
                                    val updated = points.toMutableMap()
                                    updated[key] = NetworkLayoutStore.NodePoint(
                                        (old.x + dragAmount.x).coerceIn(45f, size.width - 45f),
                                        (old.y + dragAmount.y).coerceIn(45f, size.height - 45f)
                                    )
                                    points = updated
                                },
                                onDragEnd = {
                                    val key = draggingKey
                                    if (key != null) {
                                        val current = points[key]
                                        if (current != null) {
                                            val target = points.entries
                                                .filter { it.key != key && it.key.substringBefore(':') != key.substringBefore(':') }
                                                .minByOrNull { (_, p) -> hypot((p.x - current.x).toDouble(), (p.y - current.y).toDouble()) }
                                            if (target != null) {
                                                val distance = hypot((target.value.x - current.x).toDouble(), (target.value.y - current.y).toDouble())
                                                if (distance <= SNAP_DISTANCE) {
                                                    points = points.toMutableMap().apply { put(key, target.value) }
                                                }
                                            }
                                        }
                                    }
                                    draggingKey = null
                                    onPointsChanged(points)
                                },
                                onDragCancel = { draggingKey = null }
                            )
                        }
                ) {
                    // soft grid
                    val gridColor = Color(0xFFDDE4EA).copy(alpha = 0.65f)
                    var gx = 0f
                    while (gx < size.width) { drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += 50f }
                    var gy = 0f
                    while (gy < size.height) { drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f); gy += 50f }

                    sections.forEach { section ->
                        val p1data = points[startKey(section)] ?: return@forEach
                        val p2data = points[endKey(section)] ?: return@forEach
                        val p1 = Offset(p1data.x, p1data.y)
                        val p2 = Offset(p2data.x, p2data.y)
                        val bend = bends[section.id] ?: 0f
                        val dx = p2.x - p1.x
                        val dy = p2.y - p1.y
                        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                        val nx = -dy / len
                        val ny = dx / len
                        val control = Offset((p1.x + p2.x) / 2f + nx * bend * 110f, (p1.y + p2.y) / 2f + ny * bend * 110f)
                        val pipeColor = when (testsBySection[section.id]?.status) {
                            "PASSED" -> EditorGreen
                            "REVIEW" -> EditorRed
                            "IN_PROGRESS" -> EditorAmber
                            "READY" -> EditorBlue
                            else -> EditorGray
                        }
                        val path = Path().apply { moveTo(p1.x, p1.y); quadraticBezierTo(control.x, control.y, p2.x, p2.y) }
                        drawPath(path, Color(0xFFD5DCE3), style = androidx.compose.ui.graphics.drawscope.Stroke(22f))
                        drawPath(path, pipeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(12f))

                        val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                        drawContext.canvas.nativeCanvas.drawText(
                            "${section.startValve} → ${section.endValve}   ${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\"",
                            mid.x - 90f,
                            mid.y - 18f,
                            Paint().apply { isAntiAlias = true; textSize = 22f; this.color = android.graphics.Color.rgb(55, 67, 78) }
                        )
                    }

                    points.forEach { (key, pointData) ->
                        if (sections.none { key == startKey(it) || key == endKey(it) }) return@forEach
                        val point = Offset(pointData.x, pointData.y)
                        val joined = points.any { (otherKey, otherPoint) ->
                            otherKey != key && otherKey.substringBefore(':') != key.substringBefore(':') &&
                                hypot((otherPoint.x - pointData.x).toDouble(), (otherPoint.y - pointData.y).toDouble()) <= 3.0
                        }
                        drawCircle(Color.White, ENDPOINT_RADIUS + 5f, point)
                        drawCircle(if (joined) EditorGreen else EditorBlue, ENDPOINT_RADIUS, point)
                        drawCircle(Color.White, ENDPOINT_RADIUS - 9f, point)
                    }
                }
            }
            Text("Punta verde = unida a otro tubo · Punta azul = libre / terminal. Para guardar, todos los tubos deben pertenecer a una sola red conectada.", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

private fun networkIsConnected(
    sections: List<SectionEntity>,
    points: Map<String, NetworkLayoutStore.NodePoint>
): Boolean {
    if (sections.isEmpty()) return false
    if (sections.size == 1) return true

    fun endpointsTouch(a: SectionEntity, b: SectionEntity): Boolean {
        val aPoints = listOfNotNull(points[startKey(a)], points[endKey(a)])
        val bPoints = listOfNotNull(points[startKey(b)], points[endKey(b)])
        return aPoints.any { pa -> bPoints.any { pb -> hypot((pa.x - pb.x).toDouble(), (pa.y - pb.y).toDouble()) <= 4.0 } }
    }

    val visited = mutableSetOf<Long>()
    val queue = ArrayDeque<SectionEntity>()
    queue.add(sections.first())
    visited += sections.first().id
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        sections.filter { it.id !in visited && endpointsTouch(current, it) }.forEach {
            visited += it.id
            queue.add(it)
        }
    }
    return visited.size == sections.size
}
