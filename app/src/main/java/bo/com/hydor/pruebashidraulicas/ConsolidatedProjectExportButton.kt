package bo.com.hydor.pruebashidraulicas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsolidatedProjectExportButton(projectId: Long) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun generateAndWrite(uri: Uri) {
        withContext(Dispatchers.IO) {
            val project = dao.getProject(projectId) ?: error("Proyecto no encontrado")
            val sections = dao.getSectionsForProject(projectId)
            val layout = NetworkLayoutStore.load(context, projectId, sections.map { it.id }.toSet())
            val included = sections.filter { it.id in layout.includedSectionIds }
            val allTests = dao.getAllTests()
            val latest = allTests
                .filter { test -> included.any { it.id == test.sectionId } }
                .groupBy { it.sectionId }
                .mapValues { (_, list) -> list.maxByOrNull { it.id }!! }

            val bundles = included.map { section ->
                val test = latest[section.id]
                val readings = if (test != null) dao.getReadings(test.id) else emptyList()
                var photoKept = false
                val lightweightReadings = readings.map { reading ->
                    if (reading.imagePath != null && !photoKept) {
                        photoKept = true
                        reading
                    } else if (reading.imagePath != null) {
                        reading.copy(imagePath = null)
                    } else reading
                }
                FinalProjectPdf.TestBundle(section, test, lightweightReadings)
            }

            val extras = bundles.mapNotNull { it.test?.id }
                .associateWith { TestExtraTimeStore.entries(context, it) }
            val bytes = FinalProjectPdf.build(
                context = context,
                project = project,
                bundles = bundles,
                topology = layout.topologyCodes,
                bends = layout.bends,
                extraTimeByTest = extras
            )
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("No se pudo abrir el archivo de destino")
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = "Generando y guardando PDF…"
            try {
                generateAndWrite(uri)
                message = "PDF guardado correctamente en el dispositivo."
            } catch (e: Exception) {
                message = "No se pudo generar el PDF: ${e.message ?: "error desconocido"}"
            } finally {
                busy = false
            }
        }
    }

    Button(
        onClick = {
            message = null
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            launcher.launch("HYDOR_Informe_Proyecto_${projectId}_$stamp.pdf")
        },
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123A63)),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Text(if (busy) "GENERANDO PDF…" else "GENERAR / GUARDAR PDF", fontWeight = FontWeight.Bold)
    }

    message?.let { Text(it) }
}
