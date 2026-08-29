package bo.com.hydor.pruebashidraulicas

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun FinalReportExportButton(projectId: Long, enabled: Boolean) {
    if (enabled) {
        Text(
            "Informe consolidado listo. Genera y guarda el PDF desde Informes y resultados.",
            color = Color(0xFF123A63),
            fontWeight = FontWeight.Bold
        )
    }
}
