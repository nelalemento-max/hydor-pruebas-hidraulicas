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

@Composable
fun GaugeCalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var calibration by remember { mutableStateOf(GaugeCalibrationStore.load(context)) }
    var name by remember { mutableStateOf(calibration.name) }
    var maxBarText by remember { mutableStateOf(calibration.maxBar.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() { calibration = GaugeCalibrationStore.load(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Calibración del manómetro", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("HYDOR conserva la calibración mientras sigas usando el mismo manómetro. Si cambias de instrumento, crea una calibración nueva.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MANÓMETRO ACTIVO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(calibration.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Escala: 0–${String.format(java.util.Locale.US, "%.0f", calibration.maxBar)} bar")
                Text("Muestras confirmadas: ${calibration.samples.size}")
                Text(
                    if (calibration.isCalibrated) "CALIBRADO" else "CALIBRACIÓN EN APRENDIZAJE",
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (calibration.isCalibrated)
                        "Las lecturas automáticas usan la relación aprendida entre ángulo de aguja y presión."
                    else
                        "Confirma al menos dos fotografías con la aguja en posiciones diferentes. HYDOR aprenderá del valor corregido por el técnico.",
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre / identificación del manómetro") },
            supportingText = { Text("Ej.: Manómetro WIKA 0–10 bar #01") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = maxBarText,
            onValueChange = { maxBarText = it },
            label = { Text("Escala máxima (bar)") },
            supportingText = { Text("Ej.: 10 para un manómetro de 0 a 10 bar") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val maxBar = maxBarText.toDoubleOrNull() ?: return@Button
                GaugeCalibrationStore.saveProfile(context, name, maxBar, resetSamples = false)
                reload(); message = "Datos del manómetro actualizados"
            },
            enabled = maxBarText.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("GUARDAR MANÓMETRO ACTUAL") }

        OutlinedButton(
            onClick = {
                val maxBar = maxBarText.toDoubleOrNull() ?: return@OutlinedButton
                GaugeCalibrationStore.saveProfile(context, name, maxBar, resetSamples = true)
                reload(); message = "Nueva calibración iniciada. Toma y confirma al menos dos lecturas diferentes."
            },
            enabled = maxBarText.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("CALIBRAR NUEVO MANÓMETRO") }

        if (calibration.samples.isNotEmpty()) {
            Text("Puntos aprendidos", fontWeight = FontWeight.Bold)
            calibration.samples.forEachIndexed { index, sample ->
                Text("${index + 1}. Ángulo ${String.format(java.util.Locale.US, "%.1f", sample.angleDeg)}° → ${String.format(java.util.Locale.US, "%.2f", sample.pressureBar)} bar")
            }
            TextButton(onClick = { GaugeCalibrationStore.clearSamples(context); reload(); message = "Muestras de calibración eliminadas" }) {
                Text("Borrar muestras y volver a aprender")
            }
        }

        message?.let { Text(it) }
        TextButton(onClick = onBack) { Text("Volver") }
    }
}
