package bo.com.hydor.pruebashidraulicas

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.com.hydor.pruebashidraulicas.data.PressureReadingEntity
import java.util.Locale
import kotlin.math.max

object TestExtraTimeStore {
    private const val PREFS = "hydor_test_extra_time"
    fun minutes(context: Context, testId: Long): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("extra_$testId", 0)
    fun add(context: Context, testId: Long, minutes: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt("extra_$testId", (p.getInt("extra_$testId", 0) + minutes).coerceAtLeast(0)).apply()
    }
}

@Composable
fun TestAlertBell(status: String, detail: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔔", fontSize = 25.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(status, fontWeight = FontWeight.ExtraBold, color = color)
                Text(detail, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TestTimelineCard(elapsedMillis: Long, totalMillis: Long, extraMinutes: Int) {
    val progress = if (totalMillis > 0) (elapsedMillis.toFloat() / totalMillis).coerceIn(0f, 1f) else 0f
    val color = when {
        progress >= 1f -> Color(0xFFB3261E)
        progress >= 0.85f -> Color(0xFFA96400)
        progress >= 0.60f -> Color(0xFFB58B00)
        else -> Color(0xFF26734D)
    }
    val remaining = max(0L, totalMillis - elapsedMillis)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Línea de tiempo", fontWeight = FontWeight.Bold)
                Text(if (remaining > 0) formatTimelineClock(remaining) else "TIEMPO CUMPLIDO", fontWeight = FontWeight.ExtraBold, color = color)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp), color = color, trackColor = Color(0xFFE3E8ED))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Inicio", fontSize = 10.sp, color = Color.Gray)
                Text("60%", fontSize = 10.sp, color = Color.Gray)
                Text("85%", fontSize = 10.sp, color = Color.Gray)
                Text("Fin", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
            }
            if (extraMinutes > 0) Text("Tiempo extra acumulado: $extraMinutes min", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LabeledPressureChart(
    readings: List<PressureReadingEntity>,
    startPressure: Double,
    allowedDrop: Double,
    startedAt: Long,
    totalMillis: Long
) {
    val limitPressure = startPressure - allowedDrop
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (readings.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) { Text("La curva aparecerá al registrar lecturas.") }
            } else {
                val values = readings.map { it.confirmedPressureBar } + startPressure + limitPressure
                var yMin = values.minOrNull() ?: 0.0
                var yMax = values.maxOrNull() ?: 1.0
                val pad = max(0.15, (yMax - yMin) * 0.18)
                yMin -= pad; yMax += pad
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.width(38.dp).height(220.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                        for (i in 0..5) {
                            val v = yMax - (yMax - yMin) * i / 5.0
                            Text(String.format(Locale.US, "%.1f", v), fontSize = 9.sp, color = Color(0xFF66707A))
                        }
                    }
                    Canvas(Modifier.weight(1f).height(220.dp).background(Color.White)) {
                        val left = 4f; val right = size.width - 4f; val top = 5f; val bottom = size.height - 5f
                        val w = right-left; val h = bottom-top
                        val grid = Color(0xFFDCE3E9).copy(alpha = 0.75f)
                        for (i in 0..5) { val y = top + h*i/5f; drawLine(grid, Offset(left,y), Offset(right,y),1f) }
                        for (i in 0..6) { val x = left + w*i/6f; drawLine(grid, Offset(x,top), Offset(x,bottom),1f) }
                        fun yFor(v: Double)= bottom - (((v-yMin)/(yMax-yMin)).toFloat()*h)
                        fun xFor(t: Long): Float {
                            val denom = totalMillis.coerceAtLeast(1L)
                            return left + (((t-startedAt).coerceAtLeast(0L).toFloat()/denom.toFloat()).coerceIn(0f,1f)*w)
                        }
                        drawLine(Color(0xFF123A63).copy(alpha=.35f), Offset(left,yFor(startPressure)), Offset(right,yFor(startPressure)),2f)
                        drawLine(Color(0xFFB3261E).copy(alpha=.8f), Offset(left,yFor(limitPressure)), Offset(right,yFor(limitPressure)),3f)
                        val path=Path()
                        readings.forEachIndexed { idx,r -> val x=xFor(r.capturedAt); val y=yFor(r.confirmedPressureBar); if(idx==0) path.moveTo(x,y) else path.lineTo(x,y) }
                        if(readings.size>1) drawPath(path, Color(0xFF123A63), style=androidx.compose.ui.graphics.drawscope.Stroke(4f))
                        readings.forEach { r -> drawCircle(Color(0xFF123A63),6f,Offset(xFor(r.capturedAt),yFor(r.confirmedPressureBar))) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start=38.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0..4) Text(formatAxisTime(totalMillis*i/4), fontSize=9.sp, color=Color(0xFF66707A))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Presión real", color=Color(0xFF123A63), fontSize=11.sp, fontWeight=FontWeight.Bold)
                    Text("Límite ${String.format(Locale.US,"%.2f",limitPressure)} bar", color=Color(0xFFB3261E), fontSize=11.sp)
                }
            }
        }
    }
}

private fun formatTimelineClock(ms: Long): String { val s=ms/1000; return "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) }
private fun formatAxisTime(ms: Long): String { val m=ms/60000; return if(m<60) "${m}m" else "${m/60}h${if(m%60>0) "${m%60}" else ""}" }
