package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import kotlin.math.min

private val HydorBlue = Color(0xFF123A63)
private val HydorLightBlue = Color(0xFFEAF2F8)
private val HydorGreen = Color(0xFF26734D)
private val HydorAmber = Color(0xFFA96400)
private val HydorRed = Color(0xFFB3261E)
private val HydorBackground = Color(0xFFF5F7FA)

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

@Composable
fun HydorApp() {
    val navController = rememberNavController()
    val colors = lightColorScheme(
        primary = HydorBlue,
        secondary = HydorGreen,
        background = HydorBackground,
        surface = Color.White
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = HydorBackground,
            topBar = { HydorTopBar() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HydorTopBar() {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = HydorBlue,
            titleContentColor = Color.White
        ),
        title = {
            Column {
                Text("HYDOR", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("CONTROL DE PRUEBAS HIDRÁULICAS", fontSize = 10.sp)
            }
        }
    )
}

@Composable
private fun Dashboard(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Trabajo de campo", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = HydorBlue)
        Text("Registro, seguimiento y evaluación técnica de pruebas hidráulicas, incluso sin conexión.")

        Button(
            onClick = { navController.navigate(Routes.NEW_TEST) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("+  NUEVA PRUEBA HIDRÁULICA", fontWeight = FontWeight.Bold) }

        HomeAction("Proyectos y tramos", "Consulta el archivo técnico guardado en el teléfono") {
            navController.navigate(Routes.PROJECTS)
        }
        HomeAction("Informes y resultados", "Revisa pruebas finalizadas y su evaluación") {
            navController.navigate(Routes.REPORTS)
        }

        Card(colors = CardDefaults.cardColors(containerColor = HydorLightBlue)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MODO CAMPO", fontWeight = FontWeight.Bold, color = HydorBlue)
                Text("Almacenamiento local · Sin conexión")
                Text("Próximo módulo: cámara + reconocimiento del manómetro + confirmación humana")
            }
        }
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HydorBlue)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp)
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
    var startPoint by remember { mutableStateOf("") }
    var endPoint by remember { mutableStateOf("") }
    var diameter by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var nominalPressure by remember { mutableStateOf("") }
    var targetPressure by remember { mutableStateOf("") }
    var maxDrop by remember { mutableStateOf("0.40") }
    var durationHours by remember { mutableStateOf("4") }
    var operator by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val valid = project.isNotBlank() && neighborhood.isNotBlank() && startPoint.isNotBlank() && endPoint.isNotBlank() &&
        diameter.isNotBlank() && length.toDoubleOrNull() != null &&
        nominalPressure.toDoubleOrNull() != null && targetPressure.toDoubleOrNull() != null &&
        maxDrop.toDoubleOrNull() != null && durationHours.toDoubleOrNull() != null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Identificación del trabajo", "Usa las mismas referencias que aparecen en plano y planilla de campo.")
        HelpField("Proyecto / obra", project, "Ej.: Ampliación Sistema de Agua Potable Yamparáez") { project = it }
        HelpField("Batería o grupo de prueba (opcional)", battery, "Ej.: Batería 3. Agrupa varias pruebas del mismo frente.") { battery = it }
        HelpField("Ubicación / barrio", neighborhood, "Ej.: Barrio San José") { neighborhood = it }

        SectionTitle("Tramo sometido a prueba", "Los puntos pueden ser tee, válvula, nodo, tapón u otra referencia del plano.")
        HelpField("Punto inicial del tramo", startPoint, "Ej.: T-35, V-02 o Nodo N-14") { startPoint = it }
        HelpField("Punto final del tramo", endPoint, "Ej.: T-34, V-03 o Nodo N-15") { endPoint = it }
        HelpField("Diámetro de tubería (pulgadas \" )", diameter, "Ej.: 2, 4, 6, 8 o 1 1/2") { diameter = it }
        HelpField("Longitud del tramo (m)", length, "Ej.: 300.00") { length = it }

        SectionTitle("Criterio técnico", "El límite de aceptación debe provenir de la especificación técnica de la obra.")
        HelpField("Presión nominal de la tubería (bar)", nominalPressure, "Ej.: 10.00 bar") { nominalPressure = it }
        HelpField("Presión de ensayo / sometida (bar)", targetPressure, "Ej.: 7.00 bar") { targetPressure = it }
        HelpField("Caída máxima permitida (bar)", maxDrop, "Ej.: 0.40 bar según especificación del proyecto") { maxDrop = it }
        HelpField("Duración del ensayo (horas)", durationHours, "Ej.: 4. Para pruebas rápidas de desarrollo puedes usar 0.05") { durationHours = it }
        HelpField("Operador responsable", operator, "Ej.: Ing. Marco A. Mendoza Torres") { operator = it }

        error?.let { Text(it, color = HydorRed) }

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
                                startValve = startPoint.trim(),
                                endValve = endPoint.trim(),
                                diameterInches = diameter.trim(),
                                lengthMeters = length.toDouble()
                            )
                        )
                        val durationMinutes = (durationHours.toDouble() * 60.0).toInt().coerceAtLeast(1)
                        val testId = dao.insertTest(
                            HydraulicTestEntity(
                                sectionId = sectionId,
                                operatorName = operator.trim(),
                                nominalPressureBar = nominalPressure.toDouble(),
                                targetPressureBar = targetPressure.toDouble(),
                                maxAllowedDropBar = maxDrop.toDouble(),
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
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (saving) "Guardando..." else "GUARDAR Y PREPARAR PRUEBA", fontWeight = FontWeight.Bold) }

        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancelar")
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = HydorBlue)
        Text(subtitle, fontSize = 12.sp, color = Color(0xFF5F6368))
    }
}

@Composable
private fun HelpField(label: String, value: String, example: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(example) },
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
        SectionTitle("Preparación de la prueba", "Confirma físicamente el tramo antes de iniciar el cronómetro.")

        if (test == null || section == null) {
            CircularProgressIndicator()
        } else {
            TechnicalSummary(project, section!!, test!!)
        }

        PreparationItem("Tubería completamente llena por gravedad")
        PreparationItem("Aire purgado por el punto más alto")
        PreparationItem("Extremos, válvulas y accesorios asegurados")
        PreparationItem("Manómetro instalado y visible")

        Button(
            onClick = {
                scope.launch {
                    dao.startTest(testId, System.currentTimeMillis())
                    navController.navigate(Routes.activeTest(testId))
                }
            },
            enabled = test != null,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("INICIAR ENSAYO", fontWeight = FontWeight.Bold) }

        OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Volver al tramo")
        }
    }
}

@Composable
private fun TechnicalSummary(project: ProjectEntity?, section: SectionEntity, test: HydraulicTestEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = HydorLightBlue), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(project?.name ?: "Proyecto", fontWeight = FontWeight.Bold, color = HydorBlue)
            if (section.battery.isNotBlank()) Text("Grupo: ${section.battery}")
            Text("Ubicación: ${section.neighborhood}")
            Text("Tramo: ${section.startValve} → ${section.endValve}")
            Text("${formatNumber(section.lengthMeters)} m · Ø ${formatDiameter(section.diameterInches)}")
            Divider()
            Text("Nominal: ${formatNumber(test.nominalPressureBar)} bar")
            Text("Ensayo: ${formatNumber(test.targetPressureBar)} bar")
            Text("Caída máxima permitida: ${formatNumber(test.maxAllowedDropBar)} bar", fontWeight = FontWeight.Bold)
            Text("Duración: ${formatDuration(test.durationMinutes)}")
        }
    }
}

@Composable
private fun PreparationItem(text: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = HydorGreen)
            Spacer(Modifier.width(12.dp))
            Text(text)
        }
    }
}

private data class Evaluation(
    val title: String,
    val description: String,
    val color: Color,
    val drop: Double,
    val remaining: Double
)

private fun evaluate(readings: List<PressureReadingEntity>, allowedDrop: Double): Evaluation {
    if (readings.isEmpty()) return Evaluation("SIN LECTURAS", "Registra la presión inicial para comenzar la evaluación.", HydorBlue, 0.0, allowedDrop)
    val initial = readings.first().confirmedPressureBar
    val current = readings.last().confirmedPressureBar
    val drop = max(0.0, initial - current)
    val remaining = allowedDrop - drop
    return when {
        drop > allowedDrop -> Evaluation("FUERA DE RANGO", "La caída supera el máximo permitido. Revisar posible fuga antes de aceptar el tramo.", HydorRed, drop, remaining)
        drop >= allowedDrop * 0.75 -> Evaluation("ATENCIÓN", "La presión permanece dentro del límite, pero está próxima al máximo permitido.", HydorAmber, drop, remaining)
        else -> Evaluation("NORMAL", "La caída acumulada se mantiene dentro del rango configurado.", HydorGreen, drop, remaining)
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
    val remainingTime = max(0L, totalMillis - elapsed)
    val progress = if (totalMillis > 0) (elapsed.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f) else 0f

    val initialPressure = readings.firstOrNull()?.confirmedPressureBar
    val lastPressure = readings.lastOrNull()?.confirmedPressureBar
    val evaluation = evaluate(readings, currentTest?.maxAllowedDropBar ?: 0.0)
    val reduction = if (initialPressure != null && initialPressure != 0.0) evaluation.drop / initialPressure * 100.0 else null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ensayo en curso", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = HydorBlue)
        section?.let { Text("${it.startValve} → ${it.endValve} · ${formatNumber(it.lengthMeters)} m · Ø ${formatDiameter(it.diameterInches)}") }

        EvaluationCard(evaluation, currentTest?.maxAllowedDropBar ?: 0.0)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tiempo restante", fontWeight = FontWeight.Bold)
                    Text(formatClock(remainingTime), fontWeight = FontWeight.Bold, color = HydorBlue)
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Ensayo ${formatNumber(currentTest?.targetPressureBar ?: 0.0)} bar · Límite de caída ${formatNumber(currentTest?.maxAllowedDropBar ?: 0.0)} bar", fontSize = 12.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Inicial", initialPressure?.let { "${formatNumber(it)} bar" } ?: "—", Modifier.weight(1f))
            StatCard("Actual", lastPressure?.let { "${formatNumber(it)} bar" } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Caída", "${formatNumber(evaluation.drop)} bar", Modifier.weight(1f))
            StatCard("Reducción", reduction?.let { "${formatNumber(it)} %" } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Límite", "${formatNumber(currentTest?.maxAllowedDropBar ?: 0.0)} bar", Modifier.weight(1f))
            StatCard("Margen", "${formatNumber(evaluation.remaining)} bar", Modifier.weight(1f))
        }

        SectionTitle("Curva de presión", "Línea continua: lecturas reales. Línea horizontal: límite mínimo admisible según la caída configurada.")
        PressureChart(readings, currentTest?.maxAllowedDropBar ?: 0.0)

        SectionTitle("Nueva lectura", "Por ahora confirma manualmente el valor. La siguiente etapa conectará la cámara del celular.")
        HelpField("Presión confirmada (bar)", pressureInput, "Ej.: 6.92") { pressureInput = it }
        Button(
            onClick = {
                val value = pressureInput.toDoubleOrNull() ?: return@Button
                scope.launch {
                    dao.insertReading(
                        PressureReadingEntity(
                            testId = testId,
                            capturedAt = System.currentTimeMillis(),
                            detectedPressureBar = null,
                            confirmedPressureBar = value,
                            imagePath = null,
                            detectionConfidence = null,
                            source = "MANUAL"
                        )
                    )
                    pressureInput = ""
                    message = "Lectura registrada"
                    reload()
                }
            },
            enabled = pressureInput.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text(if (readings.isEmpty()) "REGISTRAR PRESIÓN INICIAL" else "REGISTRAR LECTURA") }

        message?.let { Text(it, color = HydorGreen) }

        if (readings.isNotEmpty()) {
            Text("Historial de lecturas", fontWeight = FontWeight.Bold, color = HydorBlue)
            readings.forEachIndexed { index, reading ->
                ReadingRow(index + 1, reading, readings.first().capturedAt)
            }
        }

        Button(
            onClick = {
                scope.launch {
                    val finalEval = evaluate(readings, currentTest?.maxAllowedDropBar ?: 0.0)
                    val finalStatus = if (readings.isNotEmpty() && finalEval.drop <= (currentTest?.maxAllowedDropBar ?: 0.0)) "PASSED" else "REVIEW"
                    dao.finishTest(testId, System.currentTimeMillis(), finalStatus)
                    navController.navigate(Routes.REPORTS) {
                        popUpTo(Routes.DASHBOARD)
                    }
                }
            },
            enabled = readings.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("FINALIZAR Y EVALUAR PRUEBA", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EvaluationCard(evaluation: Evaluation, allowedDrop: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = evaluation.color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("ESTADO DE LA PRUEBA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(evaluation.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = evaluation.color)
            Text(evaluation.description)
            if (allowedDrop > 0) {
                Text("Caída: ${formatNumber(evaluation.drop)} / ${formatNumber(allowedDrop)} bar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PressureChart(readings: List<PressureReadingEntity>, allowedDrop: Double) {
    Card(Modifier.fillMaxWidth()) {
        if (readings.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text("La gráfica aparecerá al registrar la primera lectura.")
            }
        } else {
            Column(Modifier.padding(14.dp)) {
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.White)
                ) {
                    val left = 42f
                    val right = size.width - 18f
                    val top = 18f
                    val bottom = size.height - 32f
                    val width = right - left
                    val height = bottom - top

                    val initial = readings.first().confirmedPressureBar
                    val limitPressure = initial - allowedDrop
                    val values = readings.map { it.confirmedPressureBar } + limitPressure
                    var yMin = values.minOrNull() ?: 0.0
                    var yMax = values.maxOrNull() ?: 1.0
                    if (yMax - yMin < 0.2) {
                        yMin -= 0.1
                        yMax += 0.1
                    } else {
                        val pad = (yMax - yMin) * 0.15
                        yMin -= pad
                        yMax += pad
                    }

                    val gridColor = Color(0xFFDDE4EA).copy(alpha = 0.70f)
                    for (i in 0..5) {
                        val y = top + (height / 5f) * i
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                    }
                    for (i in 0..6) {
                        val x = left + (width / 6f) * i
                        drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                    }

                    drawLine(Color(0xFFB8C0C8), Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)
                    drawLine(Color(0xFFB8C0C8), Offset(left, top), Offset(left, bottom), strokeWidth = 2f)

                    fun yFor(value: Double): Float = bottom - (((value - yMin) / (yMax - yMin)).toFloat() * height)
                    val limitY = yFor(limitPressure)
                    drawLine(HydorRed.copy(alpha = 0.7f), Offset(left, limitY), Offset(right, limitY), strokeWidth = 3f)

                    val startTime = readings.first().capturedAt
                    val endTime = max(readings.last().capturedAt, startTime + 1L)
                    fun xFor(time: Long): Float = left + (((time - startTime).toFloat() / (endTime - startTime).toFloat()) * width)

                    val path = Path()
                    readings.forEachIndexed { index, reading ->
                        val x = if (readings.size == 1) left + width / 2f else xFor(reading.capturedAt)
                        val y = yFor(reading.confirmedPressureBar)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    if (readings.size > 1) drawPath(path, HydorBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                    readings.forEach { reading ->
                        val x = if (readings.size == 1) left + width / 2f else xFor(reading.capturedAt)
                        val y = yFor(reading.confirmedPressureBar)
                        drawCircle(HydorBlue, radius = 7f, center = Offset(x, y))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Real", color = HydorBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Límite mínimo: ${formatNumber(readings.first().confirmedPressureBar - allowedDrop)} bar", color = HydorRed, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF5F6368))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = HydorBlue)
        }
    }
}

@Composable
private fun ReadingRow(number: Int, reading: PressureReadingEntity, firstTime: Long) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#$number", fontWeight = FontWeight.Bold, color = HydorBlue, modifier = Modifier.width(38.dp))
            Column(Modifier.weight(1f)) {
                Text("${formatNumber(reading.confirmedPressureBar)} bar", fontWeight = FontWeight.Bold)
                Text("${formatTime(reading.capturedAt)} · +${formatElapsed(reading.capturedAt - firstTime)}", fontSize = 12.sp)
            }
            Text(if (reading.source == "MANUAL") "Manual" else "Cámara", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProjectsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var projects by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    var sections by remember { mutableStateOf<Map<Long, List<SectionEntity>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        projects = dao.getProjects()
        sections = projects.associate { it.id to dao.getSectionsForProject(it.id) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Proyectos y tramos", "Archivo local de trabajos de campo.")
        if (projects.isEmpty()) {
            Text("No existen proyectos registrados todavía.")
        } else {
            projects.forEach { project ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(project.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HydorBlue)
                        Text(project.location)
                        (sections[project.id] ?: emptyList()).forEach { section ->
                            Divider()
                            Text("${section.startValve} → ${section.endValve}", fontWeight = FontWeight.Bold)
                            Text("${section.neighborhood} · ${formatNumber(section.lengthMeters)} m · Ø ${formatDiameter(section.diameterInches)}")
                        }
                    }
                }
            }
        }
        Button(onClick = { navController.navigate(Routes.NEW_TEST) }, modifier = Modifier.fillMaxWidth()) { Text("Nueva prueba") }
        TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}

@Composable
private fun ReportsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    var tests by remember { mutableStateOf<List<HydraulicTestEntity>>(emptyList()) }
    var sections by remember { mutableStateOf<Map<Long, SectionEntity>>(emptyMap()) }

    LaunchedEffect(Unit) {
        tests = dao.getAllTests()
        sections = tests.mapNotNull { test -> dao.getSection(test.sectionId)?.let { test.id to it } }.toMap()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Informes y resultados", "Pruebas guardadas y estado técnico.")
        if (tests.isEmpty()) {
            Text("Todavía no existen ensayos registrados.")
        } else {
            tests.forEach { test ->
                val section = sections[test.id]
                val statusColor = when (test.status) {
                    "PASSED" -> HydorGreen
                    "REVIEW" -> HydorRed
                    "IN_PROGRESS" -> HydorAmber
                    else -> HydorBlue
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section?.let { "${it.startValve} → ${it.endValve}" } ?: "Prueba #${test.id}", fontWeight = FontWeight.Bold, color = HydorBlue)
                        Text(section?.let { "${formatNumber(it.lengthMeters)} m · Ø ${formatDiameter(it.diameterInches)}" } ?: "")
                        Text(
                            when (test.status) {
                                "PASSED" -> "ACEPTABLE"
                                "REVIEW" -> "REQUIERE REVISIÓN"
                                "IN_PROGRESS" -> "EN CURSO"
                                else -> "PREPARADA"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                        Text("Límite configurado: ${formatNumber(test.maxAllowedDropBar)} bar", fontSize = 12.sp)
                    }
                }
            }
        }
        Text("La generación automática de PDF se incorporará después de integrar cámara y evidencias fotográficas.", fontSize = 12.sp)
        TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}

private fun formatClock(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatElapsed(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    return if (totalMinutes < 60) "${totalMinutes} min" else "${totalMinutes / 60} h ${totalMinutes % 60} min"
}

private fun formatDiameter(value: String): String {
    val clean = value.trim()
    return if (clean.endsWith("\"")) clean else "$clean\""
}

private fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun formatDuration(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"
private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
