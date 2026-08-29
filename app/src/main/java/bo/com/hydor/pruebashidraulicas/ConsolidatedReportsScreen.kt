package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReportBlue = Color(0xFF123A63)
private val ReportGreen = Color(0xFF26734D)
private val ReportRed = Color(0xFFB3261E)
private val ReportAmber = Color(0xFFA96400)

private data class ConsolidatedReportItem(
    val project: ProjectEntity,
    val sections: List<SectionEntity>,
    val latestTests: Map<Long, HydraulicTestEntity>,
    val consolidatedAt: Long
)

@Composable
fun ConsolidatedReportsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var reports by remember { mutableStateOf<List<ConsolidatedReportItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val projects = dao.getProjects()
        val allTests = dao.getAllTests()
        reports = projects.mapNotNull { project ->
            if (!NetworkLayoutStore.isConsolidated(context, project.id)) return@mapNotNull null
            val sections = dao.getSectionsForProject(project.id)
            val layout = NetworkLayoutStore.load(context, project.id, sections.map { it.id }.toSet())
            val included = sections.filter { it.id in layout.includedSectionIds }
            val latest = allTests
                .filter { test -> included.any { it.id == test.sectionId } }
                .groupBy { it.sectionId }
                .mapValues { (_, list) -> list.maxByOrNull { it.id }!! }
            ConsolidatedReportItem(
                project = project,
                sections = included,
                latestTests = latest,
                consolidatedAt = NetworkLayoutStore.consolidatedAt(context, project.id)
                    .takeIf { it > 0L } ?: project.createdAt
            )
        }.sortedByDescending { it.consolidatedAt }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Informes y resultados", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = ReportBlue)
        Text(
            "Aquí aparecen únicamente los proyectos que fueron consolidados desde Proyectos y tramos. Desde esta pantalla se genera y guarda el PDF final.",
            fontSize = 12.sp,
            color = Color(0xFF5F6368)
        )

        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            reports.isEmpty() -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No hay informes consolidados", fontWeight = FontWeight.Bold)
                    Text("Primero arma y guarda una red en Proyectos y tramos y pulsa CONSOLIDAR INFORME.", fontSize = 12.sp)
                }
            }
            else -> reports.forEach { item ->
                val passed = item.sections.count { item.latestTests[it.id]?.status == "PASSED" }
                val review = item.sections.count { item.latestTests[it.id]?.status == "REVIEW" }
                val pending = item.sections.size - passed - review
                val totalMeters = item.sections.sumOf { it.lengthMeters }
                val overallColor = when {
                    review > 0 -> ReportRed
                    pending > 0 -> ReportAmber
                    else -> ReportGreen
                }
                val overall = when {
                    review > 0 -> "REQUIERE REVISIÓN"
                    pending > 0 -> "CON TRAMOS PENDIENTES"
                    else -> "ACEPTABLE"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.project.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = ReportBlue)
                                if (item.project.location.isNotBlank()) Text(item.project.location, fontSize = 12.sp)
                            }
                            Text("CONSOLIDADO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ReportGreen)
                        }

                        HorizontalDivider()
                        Text(
                            "Fecha de consolidación: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.consolidatedAt))}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${item.sections.size} tramos · ${String.format(Locale.US, "%.2f", totalMeters)} m totales", fontSize = 12.sp)
                        Text("$passed aceptables · $review revisión · $pending pendientes", fontSize = 12.sp)
                        Text(overall, fontWeight = FontWeight.ExtraBold, color = overallColor)

                        ConsolidatedProjectExportButton(projectId = item.project.id)
                    }
                }
            }
        }

        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Volver")
        }
    }
}
