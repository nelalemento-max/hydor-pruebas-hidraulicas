package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity

@Composable
fun ProjectNetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var projects by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    var sectionCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var selectedProjectId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        projects = dao.getProjects()
        sectionCounts = projects.associate { project -> project.id to dao.getSectionsForProject(project.id).size }
    }

    val selected = selectedProjectId
    if (selected != null) {
        ProjectNetworkEditorScreen(
            projectId = selected,
            onBack = { selectedProjectId = null },
            onGoReports = onBack
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Proyectos y tramos", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Primero elige un proyecto. Dentro de él podrás seleccionar los tramos que forman la red, unirlos, crear ramificaciones, ajustar curvaturas y consolidar el gráfico para el informe.",
            fontSize = 12.sp
        )

        if (projects.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("Aún no existen proyectos. Los proyectos se crean al registrar los primeros tramos de una prueba.", Modifier.padding(18.dp))
            }
        }

        projects.forEach { project ->
            val count = sectionCounts[project.id] ?: 0
            val consolidated = NetworkLayoutStore.isConsolidated(context, project.id)
            Card(
                onClick = { selectedProjectId = project.id },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(project.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        if (project.location.isNotBlank()) Text(project.location, fontSize = 12.sp)
                        Text("$count tramo${if (count == 1) "" else "s"}", fontSize = 12.sp)
                        if (consolidated) {
                            Text("CONSOLIDADO PARA INFORME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text("ABRIR  ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text(
            "El nombre del proyecto sirve solamente para entrar a su propia red. Los tramos no se mezclan con otros proyectos aunque tengan puntos o nombres parecidos.",
            fontSize = 11.sp
        )
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Volver") }
    }
}
