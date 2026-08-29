package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.*

@Composable
fun ProjectNetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var projects by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    var sections by remember { mutableStateOf<Map<Long, List<SectionEntity>>>(emptyMap()) }
    var latestTests by remember { mutableStateOf<Map<Long, HydraulicTestEntity>>(emptyMap()) }

    LaunchedEffect(Unit) {
        projects = dao.getProjects()
        sections = projects.associate { project -> project.id to dao.getSectionsForProject(project.id) }
        latestTests = dao.getAllTests()
            .sortedByDescending { it.id }
            .distinctBy { it.sectionId }
            .associateBy { it.sectionId }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Red de proyectos y tramos", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Cada tramo se dibuja como una tubería entre su punto inicial y final. Los puntos compartidos forman ramificaciones automáticamente.", fontSize = 12.sp)

        if (projects.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("Aún no existen proyectos. Al crear pruebas y tramos, la red se construirá aquí.", Modifier.padding(18.dp))
            }
        }

        projects.forEach { project ->
            val projectSections = sections[project.id].orEmpty()
            val projectTests = projectSections.mapNotNull { section -> latestTests[section.id]?.let { section.id to it } }.toMap()
            WaterNetworkDiagram(
                project = project,
                sections = projectSections,
                testsBySection = projectTests,
                modifier = Modifier.fillMaxWidth()
            )

            if (projectSections.isNotEmpty()) {
                Text("Tramos del proyecto", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                projectSections.forEach { section ->
                    val test = latestTests[section.id]
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${section.startValve} → ${section.endValve}", fontWeight = FontWeight.Bold)
                            Text("${section.lengthMeters.toInt()} m · Ø ${section.diameterInches}\" · ${section.neighborhood}", fontSize = 12.sp)
                            Text(
                                when (test?.status) {
                                    "PASSED" -> "ACEPTADO"
                                    "REVIEW" -> "REQUIERE REVISIÓN"
                                    "IN_PROGRESS" -> "PRUEBA EN CURSO"
                                    "READY" -> "PREPARADO"
                                    else -> "SIN PRUEBA"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = onBack) { Text("Volver") }
    }
}
