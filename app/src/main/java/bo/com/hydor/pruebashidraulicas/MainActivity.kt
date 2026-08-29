package bo.com.hydor.pruebashidraulicas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.PressureReadingEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HydorApp() }
    }
}

private object Routes {
    const val DASHBOARD = "dashboard"
    const val NEW_TEST = "new_test"
    const val PROJECTS = "projects"
    const val REPORTS = "reports"
    const val TEST_READY = "test_ready/{testId}"
    const val ACTIVE_TEST = "active_test/{testId}"

    fun testReady(testId: Long) = "test_ready/$testId"
    fun activeTest(testId: Long) = "active_test/$testId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydorApp() {
    val navController = rememberNavController()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("HYDOR", fontWeight = FontWeight.Bold)
                            Text("Pruebas Hidráulicas", fontSize = 12.sp)
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(padding)
            ) {
                composable(Routes.DASHBOARD) { Dashboard(navController) }
                composable(Routes.NEW_TEST) { NewHydraulicTestScreen(navController) }
                composable(Routes.PROJECTS) { ProjectsScreen(navController) }
                composable(Routes.REPORTS) { ReportsScreen(navController) }
                composable(
                    Routes.TEST_READY,
                    arguments = listOf(navArgument("testId") { type = NavType.LongType })
                ) { backStack ->
                    TestReadyScreen(navController, backStack.arguments?.getLong("testId") ?: 0L)
                }
                composable(
                    Routes.ACTIVE_TEST,
                    arguments = listOf(navArgument("testId") { type = NavType.LongType })
                ) { backStack ->
                    ActiveTestScreen(navController, backStack.arguments?.getLong("testId") ?: 0L)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Trabajo de campo", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Registro offline, lecturas de presión e informes técnicos.")

        Button(
            onClick = { navController.navigate(Routes.NEW_TEST) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Nueva prueba hidráulica") }

        OutlinedButton(
            onClick = { navController.navigate(Routes.PROJECTS) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Proyectos y tramos") }

        OutlinedButton(
            onClick = { navController.navigate(Routes.REPORTS) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Informes") }

        Spacer(Modifier.weight(1f))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Modo de almacenamiento", fontWeight = FontWeight.Bold)
                Text("Local / sin conexión")
                Text("Cámara + confirmación humana de lectura")
            }
        }
    }
}

@Composable
private fun NewHydraulicTestScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf("") }
    var battery by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var startValve by remember { mutableStateOf("") }
    var endValve by remember { mutableStateOf("") }
    var diameter by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var targetPressure by remember { mutableStateOf("") }
    var durationHours by remember { mutableStateOf("4") }
    var operator by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val valid = project.isNotBlank() && startValve.isNotBlank() && endValve.isNotBlank() &&
        diameter.toIntOrNull() != null && length.toDoubleOrNull() != null &&
        targetPressure.toDoubleOrNull() != null && durationHours.toDoubleOrNull() != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Nueva prueba hidráulica", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Registra el tramo que será sometido a presión. Los datos se guardan en el teléfono.")

        Field("Proyecto / obra", project) { project = it }
        Field("Batería / sector", battery) { battery = it }
        Field("Barrio / zona", neighborhood) { neighborhood = it }
        Field("Llave de paso inicial", startValve) { startValve = it }
        Field("Llave de paso final", endValve) { endValve = it }
        Field("Diámetro de tubería (mm)", diameter) { diameter = it }
        Field("Longitud del tramo (m)", length) { length = it }
        Field("Presión objetivo (bar)", targetPressure) { targetPressure = it }
        Field("Duración del ensayo (horas)", durationHours) { durationHours = it }
        Field("Operador responsable", operator) { operator = it }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Flujo previsto", fontWeight = FontWeight.Bold)
                Text("1. Llenado por gravedad")
                Text("2. Purga completa del aire")
                Text("3. Presurización")
                Text("4. Lectura inicial")
                Text("5. Lecturas intermedias y final")
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        val existingProject = dao.findProjectByName(project.trim())
                        val projectId = existingProject?.id ?: dao.insertProject(
                            ProjectEntity(name = project.trim(), location = neighborhood.trim())
                        )
                        val sectionId = dao.insertSection(
                            SectionEntity(
                                projectId = projectId,
                                battery = battery.trim(),
                                neighborhood = neighborhood.trim(),
                                startValve = startValve.trim(),
                                endValve = endValve.trim(),
                                diameterMm = diameter.toInt(),
                                lengthMeters = length.toDouble()
                            )
                        )
                        val durationMinutes = (durationHours.toDouble() * 60.0).toInt().coerceAtLeast(1)
                        val testId = dao.insertTest(
                            HydraulicTestEntity(
                                sectionId = sectionId,
                                operatorName = operator.trim(),
                                targetPressureBar = targetPressure.toDouble(),
                                durationMinutes = durationMinutes,
                                startedAt = 0L,
                                status = "READY"
                            )
                        )
                        navController.navigate(Routes.testReady(testId))
                    } catch (e: Exception) {
                        error = "No se pudo guardar: ${e.message ?: "error desconocido"}"
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = valid && !saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (saving) "Guardando..." else "Guardar tramo y preparar prueba") }

        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancelar")
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TestReadyScreen(navController: NavHostController, testId: Long) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()
    var test by remember { mutableStateOf<HydraulicTestEntity?>(null) }
    var section by remember { mutableStateOf<SectionEntity?>(null) }
    var project by remember { mutableStateOf<ProjectEntity?>(null) }

    LaunchedEffect(testId) {
        test = dao.getTest(testId)
        section = test?.let { dao.getSection(it.sectionId) }
        project = section?.let { dao.getProject(it.projectId) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Preparación de la prueba", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Antes de iniciar el cronómetro confirma las condiciones del tramo.")

        if (test == null || section == null) {
            CircularProgressIndicator()
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(project?.name ?: "Proyecto", fontWeight = FontWeight.Bold)
                    Text("Tramo: ${section!!.startValve} → ${section!!.endValve}")
                    Text("Longitud: ${formatNumber(section!!.lengthMeters)} m")
                    Text("Diámetro: ${section!!.diameterMm} mm")
                    Text("Presión objetivo: ${formatNumber(test!!.targetPressureBar)} bar")
                    Text("Duración: ${formatDuration(test!!.durationMinutes)}")
                }
            }
        }

        PreparationItem("Tubería completamente llena")
        PreparationItem("Aire purgado por el punto más alto")
        PreparationItem("Válvulas y accesorios asegurados")
        PreparationItem("Manómetro instalado y visible")

        Button(
            onClick = {
                scope.launch {
                    dao.startTest(testId, System.currentTimeMillis())
                    navController.navigate(Routes.activeTest(testId))
                }
            },
            enabled = test != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Iniciar ensayo") }

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Volver") }
    }
}

@Composable
private fun ActiveTestScreen(navController: NavHostController, testId: Long) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()

    var test by remember { mutableStateOf<HydraulicTestEntity?>(null) }
    var section by remember { mutableStateOf<SectionEntity?>(null) }
    var readings by remember { mutableStateOf<List<PressureReadingEntity>>(emptyList()) }
    var pressureInput by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        test = dao.getTest(testId)
        section = test?.let { dao.getSection(it.sectionId) }
        readings = dao.getReadings(testId)
    }

    LaunchedEffect(testId) { reload() }
    LaunchedEffect(test?.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val currentTest = test
    val totalMillis = (currentTest?.durationMinutes ?: 0) * 60_000L
    val elapsed = if ((currentTest?.startedAt ?: 0L) > 0L) now - currentTest!!.startedAt else 0L
    val remaining = max(0L, totalMillis - elapsed)
    val progress = if (totalMillis > 0) (elapsed.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f) else 0f

    val initialPressure = readings.firstOrNull()?.confirmedPressureBar
    val lastPressure = readings.lastOrNull()?.confirmedPressureBar
    val difference = if (initialPressure != null && lastPressure != null) initialPressure - lastPressure else null
    val reduction = if (difference != null && initialPressure != null && initialPressure != 0.0) difference / initialPressure * 100.0 else null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ensayo en curso", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        section?.let { Text("${it.startValve} → ${it.endValve} · ${formatNumber(it.lengthMeters)} m · Ø ${it.diameterMm} mm") }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tiempo restante", fontWeight = FontWeight.Bold)
                Text(formatClock(remaining), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Presión objetivo: ${formatNumber(currentTest?.targetPressureBar ?: 0.0)} bar")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Inicial", initialPressure?.let { "${formatNumber(it)} bar" } ?: "—", Modifier.weight(1f))
            StatCard("Última", lastPressure?.let { "${formatNumber(it)} bar" } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Diferencia", difference?.let { "${formatNumber(it)} bar" } ?: "—", Modifier.weight(1f))
            StatCard("Reducción", reduction?.let { "${formatNumber(it)} %" } ?: "—", Modifier.weight(1f))
        }

        Text("Registrar lectura", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("En esta etapa la lectura puede confirmarse manualmente. La cámara se conectará en el siguiente bloque.")
        Field("Presión confirmada (bar)", pressureInput) { pressureInput = it }
        Button(
            onClick = {
                val pressure = pressureInput.toDoubleOrNull() ?: return@Button
                scope.launch {
                    dao.insertReading(
                        PressureReadingEntity(
                            testId = testId,
                            capturedAt = System.currentTimeMillis(),
                            detectedPressureBar = null,
                            confirmedPressureBar = pressure,
                            imagePath = null,
                            detectionConfidence = null,
                            source = "MANUAL"
                        )
                    )
                    pressureInput = ""
                    message = "Lectura guardada"
                    reload()
                }
            },
            enabled = pressureInput.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("Guardar lectura") }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Text("Historial de lecturas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (readings.isEmpty()) {
            Text("Todavía no existen lecturas. Registra la presión inicial.")
        } else {
            readings.forEachIndexed { index, reading ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Lectura ${index + 1}", fontWeight = FontWeight.Bold)
                            Text(formatDateTime(reading.capturedAt))
                        }
                        Text("${formatNumber(reading.confirmedPressureBar)} bar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (remaining == 0L && currentTest != null) {
            Button(
                onClick = {
                    scope.launch {
                        dao.finishTest(testId, System.currentTimeMillis(), "FINISHED")
                        navController.navigate(Routes.REPORTS) {
                            popUpTo(Routes.DASHBOARD)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Finalizar ensayo") }
        } else {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        dao.finishTest(testId, System.currentTimeMillis(), "FINISHED_EARLY")
                        navController.navigate(Routes.REPORTS) {
                            popUpTo(Routes.DASHBOARD)
                        }
                    }
                },
                enabled = readings.size >= 2,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Finalizar anticipadamente") }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreparationItem(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✓", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(text)
        }
    }
}

@Composable
private fun ProjectsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var projects by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    var sectionCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        projects = dao.getProjects()
        sectionCounts = projects.associate { it.id to dao.getSectionsForProject(it.id).size }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Proyectos y tramos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Archivo local de campo almacenado en este dispositivo.")

        if (projects.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sin proyectos registrados todavía", fontWeight = FontWeight.Bold)
                    Text("Crea la primera prueba para iniciar el archivo de campo.")
                }
            }
        } else {
            projects.forEach { project ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(project.name, fontWeight = FontWeight.Bold)
                        if (project.location.isNotBlank()) Text(project.location)
                        Text("Tramos registrados: ${sectionCounts[project.id] ?: 0}")
                    }
                }
            }
        }

        Button(onClick = { navController.navigate(Routes.NEW_TEST) }, modifier = Modifier.fillMaxWidth()) {
            Text("Crear nuevo tramo")
        }
        TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}

@Composable
private fun ReportsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var tests by remember { mutableStateOf<List<HydraulicTestEntity>>(emptyList()) }

    LaunchedEffect(Unit) { tests = dao.getAllTests() }
    val finished = tests.filter { it.status.startsWith("FINISHED") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Informes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Ensayos finalizados disponibles para el futuro generador de PDF.")

        if (finished.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Todavía no existen ensayos finalizados", fontWeight = FontWeight.Bold)
                    Text("Finaliza una prueba para que aparezca aquí.")
                }
            }
        } else {
            finished.forEach { test ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ensayo #${test.id}", fontWeight = FontWeight.Bold)
                        Text("Operador: ${test.operatorName.ifBlank { "No registrado" }}")
                        Text("Objetivo: ${formatNumber(test.targetPressureBar)} bar")
                        Text("Estado: ${test.status}")
                    }
                }
            }
        }

        TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}

private fun formatClock(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

private fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
