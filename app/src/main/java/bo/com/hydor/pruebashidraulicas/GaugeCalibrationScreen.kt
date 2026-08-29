package bo.com.hydor.pruebashidraulicas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val CalBlue = Color(0xFF123A63)
private val CalGreen = Color(0xFF26734D)
private val CalRed = Color(0xFFB3261E)

@Composable
fun GaugeCalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var calibration by remember { mutableStateOf(GaugeCalibrationStore.load(context)) }
    var name by remember { mutableStateOf(calibration.name) }
    var maxBarText by remember { mutableStateOf(calibration.maxBar.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    var cameraMode by remember { mutableStateOf(false) }
    var detectedAngle by remember { mutableStateOf<Double?>(null) }
    var confidence by remember { mutableStateOf<Double?>(null) }
    var realPressureText by remember { mutableStateOf("") }

    fun reload() { calibration = GaugeCalibrationStore.load(context) }

    if (cameraMode) {
        CalibrationCameraPanel(
            gaugeMaxBar = calibration.maxBar,
            onDetected = { angle, conf ->
                detectedAngle = angle
                confidence = conf
                realPressureText = ""
                cameraMode = false
            },
            onCancel = { cameraMode = false }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Calibración del manómetro", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = CalBlue)
        Text("Primero registra el instrumento. Luego toma fotografías en distintas posiciones de la aguja y confirma el valor real que marca el manómetro.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MANÓMETRO ACTIVO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(calibration.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Escala: 0–${String.format(java.util.Locale.US, "%.0f", calibration.maxBar)} bar")
                Text("Puntos de calibración: ${calibration.samples.size}")
                Text(if (calibration.isCalibrated) "CALIBRADO" else "EN APRENDIZAJE", fontWeight = FontWeight.ExtraBold, color = if (calibration.isCalibrated) CalGreen else CalBlue)
                Text(if (calibration.isCalibrated) "La relación ángulo → presión ya está activa." else "Recomendado: toma al menos 3 puntos separados, por ejemplo 1, 5 y 9 bar.", fontSize = 12.sp)
            }
        }

        OutlinedTextField(name, { name = it }, label = { Text("Nombre / identificación del manómetro") }, supportingText = { Text("Ej.: Manómetro WIKA 0–10 bar #01") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maxBarText, { maxBarText = it }, label = { Text("Escala máxima (bar)") }, supportingText = { Text("Ej.: 10 para un manómetro de 0 a 10 bar") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                val maxBar = maxBarText.toDoubleOrNull() ?: return@Button
                GaugeCalibrationStore.saveProfile(context, name, maxBar, resetSamples = false)
                reload(); message = "Datos del manómetro guardados"
            },
            enabled = maxBarText.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("GUARDAR DATOS DEL MANÓMETRO") }

        OutlinedButton(
            onClick = {
                val maxBar = maxBarText.toDoubleOrNull() ?: return@OutlinedButton
                GaugeCalibrationStore.saveProfile(context, name, maxBar, resetSamples = true)
                reload(); detectedAngle = null; confidence = null; realPressureText = ""
                message = "Nuevo manómetro registrado. Ahora toma fotografías para calibrarlo."
            },
            enabled = maxBarText.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("REGISTRAR / CALIBRAR NUEVO MANÓMETRO") }

        Divider()
        Text("Calibración con cámara", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CalBlue)
        Text("Pon la aguja en una posición conocida, fotografía el manómetro y luego escribe el valor real que ves.", fontSize = 13.sp)

        Button(
            onClick = { cameraMode = true },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("📷  TOMAR FOTO PARA CALIBRAR", fontWeight = FontWeight.Bold) }

        detectedAngle?.let { angle ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2F8)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AGUJA DETECTADA", fontWeight = FontWeight.Bold, color = CalBlue)
                    Text("Ángulo detectado: ${String.format(java.util.Locale.US, "%.1f", angle)}°")
                    confidence?.let { Text("Confianza: ${(it * 100).toInt()}%") }
                    OutlinedTextField(
                        value = realPressureText,
                        onValueChange = { realPressureText = it },
                        label = { Text("Valor REAL que marca el manómetro (bar)") },
                        supportingText = { Text("Ej.: si la imagen es la prueba de 5 bar, escribe 5.00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val real = realPressureText.toDoubleOrNull() ?: return@Button
                            GaugeCalibrationStore.addConfirmedSample(context, angle, real)
                            reload(); message = "Punto de calibración guardado: ${String.format(java.util.Locale.US, "%.2f", real)} bar"
                            detectedAngle = null; confidence = null; realPressureText = ""
                        },
                        enabled = realPressureText.toDoubleOrNull() != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("GUARDAR ESTE PUNTO DE CALIBRACIÓN") }
                    OutlinedButton(onClick = { cameraMode = true }, modifier = Modifier.fillMaxWidth()) { Text("REPETIR FOTO") }
                }
            }
        }

        if (calibration.samples.isNotEmpty()) {
            Text("Puntos aprendidos", fontWeight = FontWeight.Bold)
            calibration.samples.forEachIndexed { index, sample ->
                Text("${index + 1}. Ángulo ${String.format(java.util.Locale.US, "%.1f", sample.angleDeg)}° → ${String.format(java.util.Locale.US, "%.2f", sample.pressureBar)} bar")
            }
            TextButton(onClick = { GaugeCalibrationStore.clearSamples(context); reload(); message = "Muestras eliminadas" }) { Text("Borrar puntos y volver a calibrar") }
        }

        message?.let { Text(it, color = CalGreen) }
        TextButton(onClick = onBack) { Text("Volver") }
    }
}

@Composable
private fun CalibrationCameraPanel(
    gaugeMaxBar: Double,
    onDetected: (Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(Unit) { if (!allowed) launcher.launch(Manifest.permission.CAMERA) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text("Calibrar con cámara", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Text("Centra el manómetro dentro del círculo y toma la foto.", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (!allowed) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(16.dp)) { Text("PERMITIR CÁMARA") }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                            imageCapture = capture
                            try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }
                            catch (e: Exception) { error = "No se pudo iniciar la cámara: ${e.message}" }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val radius = min(size.width, size.height) * 0.34f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color.White.copy(alpha = 0.95f), radius, center, style = Stroke(width = 4f))
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x - 22f, center.y), Offset(center.x + 22f, center.y), 2f)
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x, center.y - 22f), Offset(center.x, center.y + 22f), 2f)
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.60f)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Manómetro 0–${String.format(java.util.Locale.US, "%.0f", gaugeMaxBar)} bar", color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            val dir = File(context.filesDir, "hydor_calibration").apply { mkdirs() }
                            val file = File(dir, "cal_${System.currentTimeMillis()}.jpg")
                            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    val bitmap = loadCalibrationBitmap(file)
                                    val estimate = bitmap?.let { CalibrationNeedleEstimator.estimate(it) }
                                    ContextCompat.getMainExecutor(context).execute {
                                        if (estimate != null) onDetected(estimate.first, estimate.second)
                                        else error = "No se pudo analizar la fotografía. Repite intentando centrar mejor el manómetro."
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) { error = "Error al tomar fotografía: ${exception.message}" }
                            })
                        },
                        modifier = Modifier.size(74.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("●", fontSize = 30.sp) }
                    error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
                    TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White) }
                }
            }
        }
    }
}

private object CalibrationNeedleEstimator {
    fun estimate(bitmap: Bitmap): Pair<Double, Double>? {
        val scaled = scaleDown(bitmap, 720)
        val cx = scaled.width / 2.0
        val cy = scaled.height / 2.0
        val radius = min(scaled.width, scaled.height) * 0.34
        if (radius < 40) return null
        val startDeg = 135.0
        val sweepDeg = 270.0
        var bestAngle = 0.0
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY
        var angle = startDeg
        while (angle <= startDeg + sweepDeg) {
            val rad = angle * PI / 180.0
            var score = 0.0
            var samples = 0
            var r = radius * 0.18
            while (r <= radius * 0.70) {
                val x = (cx + cos(rad) * r).toInt()
                val y = (cy + sin(rad) * r).toInt()
                if (x in 0 until scaled.width && y in 0 until scaled.height) {
                    val pixel = scaled.getPixel(x, y)
                    val rr = (pixel shr 16) and 0xFF
                    val gg = (pixel shr 8) and 0xFF
                    val bb = pixel and 0xFF
                    score += 255.0 - (0.299 * rr + 0.587 * gg + 0.114 * bb)
                    samples++
                }
                r += max(2.0, radius / 90.0)
            }
            if (samples > 0) score /= samples
            if (score > bestScore) { secondScore = bestScore; bestScore = score; bestAngle = angle }
            else if (score > secondScore) secondScore = score
            angle += 0.5
        }
        val confidence = (0.35 + (bestScore - secondScore).coerceAtLeast(0.0) / 35.0).coerceIn(0.20, 0.92)
        return bestAngle to confidence
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val factor = maxSide.toFloat() / side.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
    }
}

private fun loadCalibrationBitmap(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return bitmap
    val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
}
