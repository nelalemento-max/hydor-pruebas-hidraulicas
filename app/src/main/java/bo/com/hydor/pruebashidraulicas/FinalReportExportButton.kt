package bo.com.hydor.pruebashidraulicas

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
fun FinalReportExportButton(projectId: Long, enabled: Boolean) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val data = pendingBytes
        if (uri != null && data != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } }
                .onSuccess { message = "Informe PDF guardado correctamente." }
                .onFailure { message = "No se pudo guardar el PDF: ${it.message}" }
        }
        pendingBytes = null
    }

    Button(
        onClick = {
            scope.launch {
                busy = true
                message = null
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val project = dao.getProject(projectId) ?: error("Proyecto no encontrado")
                        val sections = dao.getSectionsForProject(projectId)
                        val allTests = dao.getAllTests()
                        val latest = allTests.filter { test -> sections.any { it.id == test.sectionId } }
                            .groupBy { it.sectionId }
                            .mapValues { (_, list) -> list.maxByOrNull { it.id }!! }
                        val layout = NetworkLayoutStore.load(context, projectId, sections.map { it.id }.toSet())
                        val includedSections = sections.filter { it.id in layout.includedSectionIds }
                        val bundles = includedSections.map { section ->
                            val test = latest[section.id]
                            FinalProjectPdf.TestBundle(section, test, if (test != null) dao.getReadings(test.id) else emptyList())
                        }
                        val extras = bundles.mapNotNull { it.test?.id }.associateWith { TestExtraTimeStore.entries(context, it) }
                        FinalProjectPdf.build(context, project, bundles, layout.topologyCodes, layout.bends, extras)
                    }
                    pendingBytes = bytes
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    launcher.launch("HYDOR_Informe_${projectId}_$stamp.pdf")
                } catch (e: Exception) {
                    message = "No se pudo generar el informe: ${e.message}"
                } finally {
                    busy = false
                }
            }
        },
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123A63)),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(if (busy) "GENERANDO INFORME..." else "GENERAR INFORME FINAL PDF", fontWeight = FontWeight.Bold)
    }

    message?.let { Text(it) }
}
