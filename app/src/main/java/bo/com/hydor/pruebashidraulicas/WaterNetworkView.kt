package bo.com.hydor.pruebashidraulicas

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import kotlin.math.max

private val NetworkBlue = Color(0xFF123A63)
private val NetworkGreen = Color(0xFF26734D)
private val NetworkAmber = Color(0xFFA96400)
private val NetworkRed = Color(0xFFB3261E)
private val NetworkGray = Color(0xFF8A949E)
private val NetworkBackground = Color(0xFFF7F9FB)

data class NetworkNodeLayout(
    val name: String,
    val depth: Int,
    val row: Int
)

private fun buildNetworkLayout(sections: List<SectionEntity>): List<NetworkNodeLayout> {
    if (sections.isEmpty()) return emptyList()

    val nodes = linkedSetOf<String>()
    val outgoing = linkedMapOf<String, MutableList<String>>()
    val incomingCount = mutableMapOf<String, Int>()

    sections.forEach { section ->
        val start = section.startValve.ifBlank { "Inicio ${section.id}" }
        val end = section.endValve.ifBlank { "Fin ${section.id}" }
        nodes += start
        nodes += end
        outgoing.getOrPut(start) { mutableListOf() }.add(end)
        incomingCount[end] = (incomingCount[end] ?: 0) + 1
        incomingCount.putIfAbsent(start, incomingCount[start] ?: 0)
    }

    val roots = nodes.filter { (incomingCount[it] ?: 0) == 0 }.ifEmpty { listOf(nodes.first()) }
    val depth = mutableMapOf<String, Int>()
    val queue = ArrayDeque<String>()
    roots.forEach { root -> depth[root] = 0; queue.add(root) }

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val nextDepth = (depth[current] ?: 0) + 1
        outgoing[current].orEmpty().forEach { child ->
            val old = depth[child]
            if (old == null || nextDepth < old) {
                depth[child] = nextDepth
                queue.add(child)
            }
        }
    }

    nodes.filter { it !in depth }.forEachIndexed { index, node -> depth[node] = index }

    val rowsByDepth = mutableMapOf<Int, Int>()
    return nodes.map { node ->
        val d = depth[node] ?: 0
        val row = rowsByDepth[d] ?: 0
        rowsByDepth[d] = row + 1
        NetworkNodeLayout(node, d, row)
    }
}

private fun sectionStatus(section: SectionEntity, testsBySection: Map<Long, HydraulicTestEntity>): String {
    val test = testsBySection[section.id] ?: return "PENDING"
    return test.status
}

private fun statusColor(status: String): Color = when (status) {
    "PASSED" -> NetworkGreen
    "REVIEW" -> NetworkRed
    "IN_PROGRESS" -> NetworkAmber
    "READY" -> NetworkBlue
    else -> NetworkGray
}

@Composable
fun WaterNetworkDiagram(
    project: ProjectEntity,
    sections: List<SectionEntity>,
    testsBySection: Map<Long, HydraulicTestEntity>,
    modifier: Modifier = Modifier
) {
    val layout = remember(sections) { buildNetworkLayout(sections) }
    val maxDepth = layout.maxOfOrNull { it.depth } ?: 0
    val maxRows = max(1, (layout.groupBy { it.depth }.maxOfOrNull { it.value.size } ?: 1))
    val canvasWidth = max(760, 220 + maxDepth * 220)
    val canvasHeight = max(320, 150 + maxRows * 115)

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.ExtraBold, color = NetworkBlue, fontSize = 18.sp)
                    Text(
                        if (sections.isEmpty()) "Sin tramos registrados" else "${sections.size} tramos · ${layout.size} nodos",
                        fontSize = 12.sp,
                        color = Color(0xFF5F6368)
                    )
                }
                Text("RED", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NetworkBlue)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetworkLegendDot(NetworkGray, "Pendiente")
                NetworkLegendDot(NetworkBlue, "Preparada")
                NetworkLegendDot(NetworkAmber, "En curso")
                NetworkLegendDot(NetworkGreen, "Aceptada")
                NetworkLegendDot(NetworkRed, "Revisión")
            }

            if (sections.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(180.dp).background(NetworkBackground, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Los tramos aparecerán aquí como tuberías conectadas.", color = Color.Gray)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    Canvas(
                        Modifier
                            .width(canvasWidth.dp)
                            .height(canvasHeight.dp)
                            .background(NetworkBackground, RoundedCornerShape(12.dp))
                    ) {
                        val leftMargin = 95f
                        val topMargin = 75f
                        val xGap = 205f
                        val yGap = 105f
                        val nodeRadius = 18f

                        val byName = layout.associateBy { it.name }
                        val points = layout.associate { node ->
                            val sameDepth = layout.filter { it.depth == node.depth }
                            val totalHeight = (sameDepth.size - 1) * yGap
                            val depthOffset = (size.height - totalHeight) / 2f
                            node.name to Offset(
                                leftMargin + node.depth * xGap,
                                depthOffset + node.row * yGap
                            )
                        }

                        sections.forEach { section ->
                            val start = section.startValve.ifBlank { "Inicio ${section.id}" }
                            val end = section.endValve.ifBlank { "Fin ${section.id}" }
                            val p1 = points[start] ?: return@forEach
                            val p2 = points[end] ?: return@forEach
                            val color = statusColor(sectionStatus(section, testsBySection))

                            drawLine(
                                color = Color(0xFFD5DCE3),
                                start = p1,
                                end = p2,
                                strokeWidth = 18f
                            )
                            drawLine(
                                color = color,
                                start = p1,
                                end = p2,
                                strokeWidth = 10f
                            )

                            val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            drawContext.canvas.nativeCanvas.drawText(
                                "${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\"",
                                mid.x - 38f,
                                mid.y - 10f,
                                Paint().apply {
                                    isAntiAlias = true
                                    textSize = 24f
                                    color = android.graphics.Color.rgb(68, 78, 88)
                                }
                            )
                        }

                        points.forEach { (name, point) ->
                            drawCircle(Color.White, nodeRadius + 5f, point)
                            drawCircle(NetworkBlue, nodeRadius, point)
                            drawCircle(Color.White, nodeRadius - 7f, point)
                            drawContext.canvas.nativeCanvas.drawText(
                                name,
                                point.x - 28f,
                                point.y - 28f,
                                Paint().apply {
                                    isAntiAlias = true
                                    textSize = 25f
                                    isFakeBoldText = true
                                    color = android.graphics.Color.rgb(18, 58, 99)
                                }
                            )
                        }
                    }
                }
            }

            Text(
                "Cada tramo se conecta por su punto inicial y final. Si varios tramos comparten un nodo, HYDOR forma automáticamente una ramificación.",
                fontSize = 11.sp,
                color = Color(0xFF5F6368)
            )
        }
    }
}

@Composable
private fun NetworkLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = Color(0xFF5F6368))
    }
}
