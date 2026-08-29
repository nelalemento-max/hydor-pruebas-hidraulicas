package bo.com.hydor.pruebashidraulicas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PressureGauge(
    pressureBar: Double,
    maxBar: Double,
    modifier: Modifier = Modifier
) {
    val safeMax = maxBar.coerceAtLeast(1.0)
    val normalized = (pressureBar / safeMax).coerceIn(0.0, 1.0)
    val startAngleDeg = 135.0
    val sweepDeg = 270.0
    val needleAngleDeg = startAngleDeg + sweepDeg * normalized

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(78.dp)) {
            val radius = size.minDimension * 0.40f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawArc(
                color = Color(0xFFD8E0E7),
                startAngle = startAngleDeg.toFloat(),
                sweepAngle = sweepDeg.toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 6f)
            )

            for (i in 0..10) {
                val angle = Math.toRadians(startAngleDeg + sweepDeg * (i / 10.0))
                val outer = radius
                val inner = if (i % 5 == 0) radius * 0.73f else radius * 0.82f
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(
                        center.x + (cos(angle) * inner).toFloat(),
                        center.y + (sin(angle) * inner).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(angle) * outer).toFloat(),
                        center.y + (sin(angle) * outer).toFloat()
                    ),
                    strokeWidth = if (i % 5 == 0) 3f else 2f
                )
            }

            val needleAngle = needleAngleDeg * PI / 180.0
            val needleLength = radius * 0.72f
            drawLine(
                color = Color(0xFF123A63),
                start = center,
                end = Offset(
                    center.x + (cos(needleAngle) * needleLength).toFloat(),
                    center.y + (sin(needleAngle) * needleLength).toFloat()
                ),
                strokeWidth = 5f
            )
            drawCircle(Color(0xFF123A63), radius = 6f, center = center)
        }
    }
}
