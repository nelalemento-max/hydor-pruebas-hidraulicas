package bo.com.hydor.pruebashidraulicas

import android.content.Intent
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
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }
    var projectName by remember { mutableStateOf("Proyecto_$projectId") }

    LaunchedEffect(projectId) {
        projectName = dao.getProject(projectId)?.name?.ifBlank { "Proyecto_$projectId" } ?: "Proyecto_$projectId"
    }

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
                FinalProjectPdf.TestBundle(section, test, readings)
            }

            val extras = bundles.mapNotNull { it.test?.id }
                .associateWith { TestExtraTimeStore.entries(context, it) }

            val bytes = PbcCompactReportPdf.build(
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

    fun openPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Abrir informe PBC")) }
            .onFailure { message = "PDF guardado, pero no se encontró una aplicación para abrirlo." }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = "Generando informe PBC compacto…"
            try {
                generateAndWrite(uri)
                lastSavedUri = uri
                message = "Informe PBC guardado correctamente. Abriendo…"
                openPdf(uri)
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
            val safeName = projectName.replace(Regex("[^A-Za-z0-9ÁÉÍÓÚáéíóúÑñ_-]+"), "_").take(45)
            launcher.launch("PBC_Informe_Prueba_Hidraulica_${safeName}_$stamp.pdf")
        },
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E2E4D)),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Text(if (busy) "GENERANDO INFORME PBC…" else "GENERAR / GUARDAR PDF PBC", fontWeight = FontWeight.Bold)
    }

    lastSavedUri?.let { uri ->
        Button(
            onClick = { openPdf(uri) },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26914C)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("ABRIR ÚLTIMO PDF", fontWeight = FontWeight.Bold)
        }
    }

    message?.let { Text(it) }
}
