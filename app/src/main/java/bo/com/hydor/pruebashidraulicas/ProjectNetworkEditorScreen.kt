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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val EditorBlue = Color(0xFF123A63)
private val EditorGreen = Color(0xFF26734D)
private val EditorAmber = Color(0xFFA96400)
private val EditorRed = Color(0xFFB3261E)
private val EditorGray = Color(0xFF8A949E)

@Composable
fun ProjectNetworkEditorScreen(projectId: Long, onBack: () -> Unit, onGoReports: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var sections by remember { mutableStateOf<List<SectionEntity>>(emptyList()) }
    var testsBySection by remember { mutableStateOf<Map<Long, HydraulicTestEntity>>(emptyMap()) }
    var included by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bends by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var saved by remember { mutableStateOf(false) }
    var consolidated by remember { mutableStateOf(false) }

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
        saved = state.saved
        consolidated = state.consolidated
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(project?.name ?: "Proyecto", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = EditorBlue)
        Text("Arma la red con los tramos que realmente pertenecen a este proyecto. La longitud visual respeta la proporción relativa entre tramos.", fontSize = 12.sp)

        if (sections.isEmpty()) {
            Text("Este proyecto todavía no tiene tramos registrados.")
        } else {
            EditableNetworkDiagram(
                sections = sections.filter { it.id in included },
                testsBySection = testsBySection,
                bends = bends
            )

            Text("Tramos disponibles", fontWeight = FontWeight.Bold, color = EditorBlue)
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
                        if (section.id in included) {
                            Text("Curvatura del tubo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = bends[section.id] ?: 0f,
                                onValueChange = { value -> bends = bends + (section.id to value); saved = false; consolidated = false },
                                valueRange = -1f..1f
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Doblar arriba", fontSize = 10.sp, color = Color.Gray)
                                Text("Recto", fontSize = 10.sp, color = Color.Gray)
                                Text("Doblar abajo", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    NetworkLayoutStore.saveLayout(context, projectId, included, bends)
                    saved = true
                    consolidated = false
                    NetworkLayoutStore.setConsolidated(context, projectId, false)
                },
                enabled = included.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (saved) "GRÁFICO GUARDADO" else "GUARDAR GRÁFICO", fontWeight = FontWeight.Bold) }

            Button(
                onClick = {
                    NetworkLayoutStore.saveLayout(context, projectId, included, bends)
                    NetworkLayoutStore.setConsolidated(context, projectId, true)
                    saved = true
                    consolidated = true
                },
                enabled = included.isNotEmpty(),
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

private data class EditorNode(val name: String, val depth: Int, val row: Int)

private fun editorLayout(sections: List<SectionEntity>): List<EditorNode> {
    if (sections.isEmpty()) return emptyList()
    val nodes = linkedSetOf<String>()
    val outgoing = linkedMapOf<String, MutableList<String>>()
    val incoming = mutableMapOf<String, Int>()
    sections.forEach {
        nodes += it.startValve; nodes += it.endValve
        outgoing.getOrPut(it.startValve) { mutableListOf() }.add(it.endValve)
        incoming[it.endValve] = (incoming[it.endValve] ?: 0) + 1
        incoming.putIfAbsent(it.startValve, incoming[it.startValve] ?: 0)
    }
    val roots = nodes.filter { (incoming[it] ?: 0) == 0 }.ifEmpty { listOf(nodes.first()) }
    val depth = mutableMapOf<String, Int>()
    val queue = ArrayDeque<String>()
    roots.forEach { depth[it] = 0; queue.add(it) }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val d = (depth[current] ?: 0) + 1
        outgoing[current].orEmpty().forEach { child ->
            if (depth[child] == null || d < depth[child]!!) { depth[child] = d; queue.add(child) }
        }
    }
    nodes.filter { it !in depth }.forEachIndexed { index, n -> depth[n] = index }
    val rows = mutableMapOf<Int, Int>()
    return nodes.map { n ->
        val d = depth[n] ?: 0
        val r = rows[d] ?: 0
        rows[d] = r + 1
        EditorNode(n, d, r)
    }
}

@Composable
private fun EditableNetworkDiagram(
    sections: List<SectionEntity>,
    testsBySection: Map<Long, HydraulicTestEntity>,
    bends: Map<Long, Float>
) {
    val layout = remember(sections) { editorLayout(sections) }
    val maxDepth = layout.maxOfOrNull { it.depth } ?: 0
    val maxRows = max(1, layout.groupBy { it.depth }.maxOfOrNull { it.value.size } ?: 1)
    val maxLength = sections.maxOfOrNull { it.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
    val minLength = sections.minOfOrNull { it.lengthMeters }?.coerceAtLeast(1.0) ?: 1.0
    val canvasWidth = max(820, 300 + maxDepth * 260)
    val canvasHeight = max(360, 170 + maxRows * 125)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vista de red", fontWeight = FontWeight.Bold, color = EditorBlue)
            Text("Escala relativa: menor ${minLength.toInt()} m · mayor ${maxLength.toInt()} m", fontSize = 10.sp, color = Color.Gray)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(canvasWidth.dp).height(canvasHeight.dp).background(Color(0xFFF7F9FB), RoundedCornerShape(12.dp))) {
                    val left = 90f; val baseGap = 190f; val yGap = 110f; val radius = 17f
                    val points = layout.associate { node ->
                        val sameDepth = layout.filter { it.depth == node.depth }
                        val totalH = (sameDepth.size - 1) * yGap
                        val offsetY = (size.height - totalH) / 2f
                        node.name to Offset(left + node.depth * baseGap, offsetY + node.row * yGap)
                    }
                    sections.forEach { section ->
                        val p1 = points[section.startValve] ?: return@forEach
                        val rawP2 = points[section.endValve] ?: return@forEach
                        val relative = (section.lengthMeters / maxLength).toFloat().coerceIn(0.30f, 1f)
                        val desiredX = max(120f, baseGap * relative)
                        val direction = if (rawP2.x >= p1.x) 1f else -1f
                        val p2 = Offset(p1.x + desiredX * direction, rawP2.y)
                        val bend = bends[section.id] ?: 0f
                        val control = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f + bend * 90f)
                        val pipeColor = when (testsBySection[section.id]?.status) {
                            "PASSED" -> EditorGreen; "REVIEW" -> EditorRed; "IN_PROGRESS" -> EditorAmber; "READY" -> EditorBlue; else -> EditorGray
                        }
                        val path = Path().apply { moveTo(p1.x, p1.y); quadraticBezierTo(control.x, control.y, p2.x, p2.y) }
                        drawPath(path, Color(0xFFD5DCE3), style = androidx.compose.ui.graphics.drawscope.Stroke(18f))
                        drawPath(path, pipeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(10f))
                        val mid = Offset((p1.x + p2.x) / 2f, control.y - 10f)
                        drawContext.canvas.nativeCanvas.drawText("${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\"", mid.x - 45f, mid.y, Paint().apply {
                            isAntiAlias = true; textSize = 23f; this.color = android.graphics.Color.rgb(65, 76, 86)
                        })
                    }
                    points.forEach { (name, point) ->
                        drawCircle(Color.White, radius + 5f, point); drawCircle(EditorBlue, radius, point); drawCircle(Color.White, radius - 7f, point)
                        drawContext.canvas.nativeCanvas.drawText(name, point.x - 28f, point.y - 28f, Paint().apply {
                            isAntiAlias = true; textSize = 24f; isFakeBoldText = true; this.color = android.graphics.Color.rgb(18, 58, 99)
                        })
                    }
                }
            }
        }
    }
}
