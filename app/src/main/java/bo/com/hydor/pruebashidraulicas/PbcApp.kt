package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PbcBlue = Color(0xFF0E2E4D)
private val PbcYellow = Color(0xFFFFD21F)

/**
 * Capa visual institucional de Laboratorio PBC Bolivia.
 * Mantiene intacto el motor interno existente y cubre el encabezado anterior
 * con la identidad PBC para no alterar la navegación ni los datos ya guardados.
 */
@Composable
fun PbcApp() {
    Box(Modifier.fillMaxSize()) {
        HydorApp()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(PbcBlue)
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.pbc_logo),
                contentDescription = "Logo PBC",
                modifier = Modifier.size(50.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("PBC", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("LABORATORIO PBC BOLIVIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("PRUEBAS HIDRÁULICAS", color = PbcYellow, fontSize = 9.sp)
            }
        }
    }
}
