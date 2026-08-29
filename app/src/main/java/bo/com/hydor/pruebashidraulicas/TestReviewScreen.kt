package bo.com.hydor.pruebashidraulicas

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.HydraulicTestEntity
import bo.com.hydor.pruebashidraulicas.data.PressureReadingEntity
import bo.com.hydor.pruebashidraulicas.data.ProjectEntity
import bo.com.hydor.pruebashidraulicas.data.SectionEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val ReviewBlue = Color(0xFF123A63)
private val ReviewGreen = Color(0xFF26734D)
private val ReviewAmber = Color(0xFFA96400)
private val ReviewRed = Color(0xFFB3261E)
private val ReviewBg = Color(0xFFF5F7FA)

@Composable
fun TestReviewScreen(
    testId: Long,
    onBackToTest: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()

    var test by remember { mutableStateOf<HydraulicTestEntity?>(null) }
    var section by remember { mutableStateOf<SectionEntity?>(null) }
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var readings by remember { mutableStateOf<List<PressureReadingEntity>>(emptyList()) }
    var confirmFinish by remember { mutableStateOf(false) }

    suspend fun reload() {
        test = dao.getTest(testId)
        section = test?.let { dao.getSection(it.sectionId) }
        project = section?.let { dao.getProject(it.projectId) }
        readings = dao.getReadings(testId)
    }

    LaunchedEffect(testId) { reload() }

    val currentTest = test
    val startPressure = currentTest?.targetPressureBar ?: 0.0
    val allowedDrop = currentTest?.maxAllowedDropBar ?: 0.0
    val currentPressure = readings.lastOrNull()?.confirmedPressureBar ?: startPressure
    val drop = max(0.0, startPressure - currentPressure)
    val margin = allowedDrop - drop
    val limit = startPressure - allowedDrop
    val actualReadings = readings.filter { it.source != "PROGRAMMED" }
    val photoCount = actualReadings.count { !it.imagePath.isNullOrBlank() }
    val outOfRangeCount = actualReadings.count { it.confirmedPressureBar < limit }
    val status = when {
        actualReadings.isEmpty() -> "SIN EVIDENCIAS"
        drop > allowedDrop -> "FUERA DE RANGO"
        drop >= allowedDrop * 0.75 -> "ATENCIÓN"
        else -> "ACEPTABLE"
    }
    val statusColor = when (status) {
        "ACEPTABLE" -> ReviewGreen
        "ATENCIÓN" -> ReviewAmber
        else -> ReviewRed
    }

    Column(
        Modifier.fillMaxSize().background(ReviewBg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Revisión final de la prueba", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = ReviewBlue)
        Text("Revisa datos, lecturas y fotografías antes de cerrar definitivamente el ensayo.")

        Card(colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.10f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("RESULTADO TÉCNICO PRELIMINAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(status, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
                Text(
                    when (status) {
                        "ACEPTABLE" -> "La presión final permanece dentro del límite configurado para esta prueba."
                        "ATENCIÓN" -> "La presión sigue dentro del límite, pero la caída se encuentra próxima al máximo permitido."
                        "FUERA DE RANGO" -> "La caída de presión supera el límite configurado. El tramo requiere revisión técnica."
                        else -> "No existen lecturas reales suficientes para cerrar técnicamente la prueba."
                    }
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(project?.name ?: "Proyecto", fontWeight = FontWeight.Bold, color = ReviewBlue)
                section?.let {
                    Text("Ubicación: ${it.neighborhood}")
                    Text("Tramo: ${it.startValve} → ${it.endValve}")
                    Text("Longitud: ${reviewNum(it.lengthMeters)} m · Ø ${reviewDiameter(it.diameterInches)}")
                }
                HorizontalDivider()
                Text("Presión programada: ${reviewNum(startPressure)} bar")
                Text("Límite mínimo: ${reviewNum(limit)} bar")
                Text("Presión final actual: ${reviewNum(currentPressure)} bar", fontWeight = FontWeight.Bold)
                Text("Caída acumulada: ${reviewNum(drop)} bar · Margen: ${reviewNum(margin)} bar")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewStat("Lecturas", actualReadings.size.toString(), Modifier.weight(1f))
            ReviewStat("Fotos", photoCount.toString(), Modifier.weight(1f))
            ReviewStat("Fuera límite", outOfRangeCount.toString(), Modifier.weight(1f))
        }

        Text("Curva consolidada", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ReviewBlue)
        LabeledPressureChart(
            readings = readings,
            startPressure = startPressure,
            allowedDrop = allowedDrop,
            startedAt = currentTest?.startedAt ?: System.currentTimeMillis(),
            totalMillis = (((currentTest?.durationMinutes ?: 1) + TestExtraTimeStore.minutes(context, testId)) * 60_000L)
        )

        Text("Evidencias y lecturas", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ReviewBlue)
        Text("Puedes corregir una lectura confirmada o eliminar una lectura equivocada. El punto programado no se puede modificar.", fontSize = 12.sp)

        readings.forEachIndexed { index, reading ->
            ReviewReadingCard(
                number = index + 1,
                reading = reading,
                startTime = currentTest?.startedAt ?: readings.firstOrNull()?.capturedAt ?: reading.capturedAt,
                gaugeMax = currentTest?.gaugeMaxBar ?: 10.0,
                onSave = { newValue ->
                    scope.launch {
                        dao.updateConfirmedReading(reading.id, newValue)
                        reload()
                    }
                },
                onDelete = {
                    scope.launch {
                        dao.deleteEditableReading(reading.id)
                        reading.imagePath?.let { runCatching { File(it).delete() } }
                        reload()
                    }
                }
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2F8)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Antes de cerrar", fontWeight = FontWeight.Bold, color = ReviewBlue)
                Text("• Confirma que el tramo y diámetro sean correctos.")
                Text("• Revisa especialmente la última lectura.")
                Text("• Verifica las fotografías asociadas a las lecturas de cámara.")
                Text("• Si una lectura fue ingresada por error, corrígela o elimínala aquí.")
            }
        }

        Button(
            onClick = { confirmFinish = true },
            enabled = actualReadings.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("CONFIRMAR RESULTADO Y CERRAR PRUEBA", fontWeight = FontWeight.Bold) }

        OutlinedButton(onClick = onBackToTest, modifier = Modifier.fillMaxWidth()) {
            Text("VOLVER A LA PRUEBA")
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Cerrar prueba definitivamente") },
            text = {
                Text("El resultado quedará registrado como $status. Después podremos generar el informe técnico con esta información y sus evidencias.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmFinish = false
                    scope.launch {
                        val finalStatus = if (drop <= allowedDrop && actualReadings.isNotEmpty()) "PASSED" else "REVIEW"
                        dao.finishTest(testId, System.currentTimeMillis(), finalStatus)
                        onFinished()
                    }
                }) { Text("CERRAR PRUEBA") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("SEGUIR REVISANDO") }
            }
        )
    }
}

@Composable
private fun ReviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ReviewBlue)
            Text(label, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReviewReadingCard(
    number: Int,
    reading: PressureReadingEntity,
    startTime: Long,
    gaugeMax: Double,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var editValue by remember(reading.id, reading.confirmedPressureBar) {
        mutableStateOf(reviewNum(reading.confirmedPressureBar))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val editable = reading.source != "PROGRAMMED"
    val imageBitmap = remember(reading.imagePath) {
        reading.imagePath?.takeIf { File(it).exists() }?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PressureGauge(reading.confirmedPressureBar, gaugeMax, Modifier.size(76.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (reading.source == "PROGRAMMED") "PUNTO INICIAL PROGRAMADO" else "Lectura #$number",
                        fontWeight = FontWeight.Bold,
                        color = ReviewBlue
                    )
                    Text("${reviewNum(reading.confirmedPressureBar)} bar", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${reviewTime(reading.capturedAt)} · +${reviewElapsed(reading.capturedAt - startTime)}", fontSize = 11.sp)
                    Text(
                        when (reading.source) {
                            "PROGRAMMED" -> "PROGRAMADO"
                            "MANUAL" -> "MANUAL"
                            else -> "CÁMARA"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Fotografía del manómetro de la lectura $number",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Fit
                )
                reading.detectedPressureBar?.let {
                    Text("Detección automática: ${reviewNum(it)} bar · valor confirmado: ${reviewNum(reading.confirmedPressureBar)} bar", fontSize = 11.sp)
                }
            }

            if (editable) {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text("Valor confirmado (bar)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { editValue.toDoubleOrNull()?.let(onSave) },
                        enabled = editValue.toDoubleOrNull() != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("GUARDAR") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Text("ELIMINAR")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar lectura") },
            text = { Text("¿Eliminar esta lectura y su fotografía asociada? Esta acción no afecta las demás evidencias.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDelete() }) { Text("ELIMINAR") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("CANCELAR") }
            }
        )
    }
}

private fun reviewNum(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun reviewDiameter(value: String): String = if (value.trim().endsWith("\"")) value.trim() else "${value.trim()}\""
private fun reviewTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
private fun reviewElapsed(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    return if (minutes < 60) "${minutes} min" else "${minutes / 60} h ${minutes % 60} min"
}
