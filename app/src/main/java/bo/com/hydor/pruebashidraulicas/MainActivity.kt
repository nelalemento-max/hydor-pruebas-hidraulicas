package bo.com.hydor.pruebashidraulicas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HydorApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydorApp() {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("HYDOR", fontWeight = FontWeight.Bold)
                        Text("Pruebas Hidráulicas", fontSize = 12.sp)
                    }
                })
            }
        ) { padding ->
            Dashboard(Modifier.padding(padding))
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Trabajo de campo", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Registro offline, lecturas de presión e informes técnicos.")

        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Nueva prueba hidráulica") }

        OutlinedButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Proyectos y tramos") }

        OutlinedButton(
            onClick = { },
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
