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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    const val TEST_READY = "test_ready"
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
                composable(Routes.TEST_READY) { TestReadyScreen(navController) }
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
        Text("Primero registra el tramo que será sometido a presión.")

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
                Text("4. Lectura inicial con fotografía")
                Text("5. Lecturas intermedias y final")
            }
        }

        Button(
            onClick = { navController.navigate(Routes.TEST_READY) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Guardar tramo y preparar prueba") }

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
private fun TestReadyScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Preparación de la prueba", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Antes de iniciar el cronómetro confirma las condiciones del tramo.")

        PreparationItem("Tubería completamente llena")
        PreparationItem("Aire purgado por el punto más alto")
        PreparationItem("Válvulas y accesorios asegurados")
        PreparationItem("Manómetro instalado y visible")

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Iniciar ensayo (siguiente etapa)") }

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Volver al tramo") }
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
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Proyectos y tramos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Aquí se almacenarán localmente todos los proyectos, baterías y tramos registrados.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sin proyectos registrados todavía", fontWeight = FontWeight.Bold)
                Text("Crea la primera prueba para iniciar el archivo de campo.")
            }
        }
        Button(onClick = { navController.navigate(Routes.NEW_TEST) }, modifier = Modifier.fillMaxWidth()) {
            Text("Crear primer tramo")
        }
        TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}

@Composable
private fun ReportsScreen(navController: NavHostController) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Informes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Los informes podrán generarse por una prueba individual o consolidando varios tramos.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Todavía no existen ensayos finalizados", fontWeight = FontWeight.Bold)
                Text("Cuando terminemos una prueba aparecerá aquí para seleccionar y exportar a PDF.")
            }
        }
        TextButton(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}
