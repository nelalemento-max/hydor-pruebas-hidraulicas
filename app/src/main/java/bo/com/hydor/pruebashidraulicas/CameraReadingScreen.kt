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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import bo.com.hydor.pruebashidraulicas.data.HydorDatabase
import bo.com.hydor.pruebashidraulicas.data.PressureReadingEntity
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val CameraBlue = Color(0xFF123A63)
private val CameraRed = Color(0xFFB3261E)

@Composable
fun CameraReadingScreen(testId: Long, onSaved: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { HydorDatabase.getInstance(context).hydorDao() }
    val scope = rememberCoroutineScope()
    var gaugeMax by remember { mutableDoubleStateOf(10.0) }
    var calibration by remember { mutableStateOf(GaugeCalibrationStore.load(context)) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var detected by remember { mutableStateOf<Double?>(null) }
    var detectedAngle by remember { mutableStateOf<Double?>(null) }
    var confidence by remember { mutableStateOf<Double?>(null) }
    var confirmedText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }

    LaunchedEffect(testId) {
        calibration = GaugeCalibrationStore.load(context)
        dao.getTest(testId)?.let { gaugeMax = it.gaugeMaxBar.coerceAtLeast(1.0) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraAllowed = it
        if (!it) error = "Se necesita permiso de cámara para fotografiar el manómetro."
    }
    LaunchedEffect(Unit) { if (!cameraAllowed) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lectura con cámara", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CameraBlue)
        Text("Alinea el centro del manómetro con la guía circular. La lectura automática es una ayuda: debes confirmarla antes de guardarla.")
        val sameGaugeScale = abs(calibration.maxBar - gaugeMax) < 0.11
        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (sameGaugeScale && calibration.isCalibrated) "${calibration.name} · CALIBRADO (${calibration.samples.size} puntos)"
                    else if (sameGaugeScale) "${calibration.name} · aprendiendo (${calibration.samples.size}/2 mínimo)"
                    else "Escala de prueba distinta al manómetro calibrado"
                )
            }
        )

        if (!cameraAllowed) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Permiso de cámara pendiente", fontWeight = FontWeight.Bold)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("PERMITIR CÁMARA") }
            } }
        } else if (capturedFile == null) {
            GaugeCamera(onCaptured = { file, estimate ->
                capturedFile = file
                detected = estimate.valueBar
                detectedAngle = estimate.angleDeg
                confidence = estimate.confidence
                confirmedText = estimate.valueBar?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: ""
                error = estimate.message
            }, gaugeMaxBar = gaugeMax, calibration = calibration, onCancel = onCancel)
        } else {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PressureGauge(confirmedText.toDoubleOrNull() ?: detected ?: 0.0, gaugeMax, Modifier.size(90.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LECTURA DETECTADA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(detected?.let { "${String.format(java.util.Locale.US, "%.2f", it)} bar" } ?: "No determinada", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = CameraBlue)
                        confidence?.let { Text("Confianza estimada: ${(it * 100).toInt()}%", fontSize = 12.sp) }
                        detectedAngle?.let { Text("Ángulo detectado: ${String.format(java.util.Locale.US, "%.1f", it)}°", fontSize = 11.sp) }
                    }
                }
                OutlinedTextField(confirmedText, { confirmedText = it }, label = { Text("Presión confirmada por el técnico (bar)") }, supportingText = { Text("Tu corrección también sirve como punto de aprendizaje para este manómetro.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = CameraRed, fontSize = 12.sp) }
                Button(onClick = {
                    val confirmed = confirmedText.toDoubleOrNull() ?: return@Button
                    val file = capturedFile ?: return@Button
                    val angle = detectedAngle
                    scope.launch {
                        if (angle != null && abs(calibration.maxBar - gaugeMax) < 0.11) {
                            GaugeCalibrationStore.addConfirmedSample(context, angle, confirmed)
                        }
                        dao.insertReading(PressureReadingEntity(testId = testId, capturedAt = System.currentTimeMillis(), detectedPressureBar = detected, confirmedPressureBar = confirmed, imagePath = file.absolutePath, detectionConfidence = confidence, source = "CAMERA_CONFIRMED"))
                        onSaved()
                    }
                }, enabled = confirmedText.toDoubleOrNull() != null, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("CONFIRMAR Y GUARDAR LECTURA", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { capturedFile?.delete(); capturedFile = null; detected = null; detectedAngle = null; confidence = null; confirmedText = ""; error = null }, modifier = Modifier.fillMaxWidth()) { Text("REPETIR FOTO") }
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancelar") }
            } }
        }
    }
}

@Composable
private fun ColumnScope.GaugeCamera(gaugeMaxBar: Double, calibration: GaugeCalibration, onCaptured: (File, GaugeEstimate) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var restartKey by remember { mutableIntStateOf(0) }
    var providerForCleanup by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerForCleanup?.unbindAll() }
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(18.dp))) {
        key(restartKey) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val previewView = this
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                providerForCleanup = provider
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                imageCapture = capture
                                captureError = null
                            } catch (e: Exception) {
                                imageCapture = null
                                captureError = "La cámara tardó demasiado o está ocupada. Cierra otras apps que usen la cámara y toca REINTENTAR."
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) * 0.34f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White.copy(alpha = 0.92f), radius, center, style = Stroke(width = 4f))
            drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x - 22f, center.y), Offset(center.x + 22f, center.y), 2f)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x, center.y - 22f), Offset(center.x, center.y + 22f), 2f)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.58f)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Escala configurada: 0–${String.format(java.util.Locale.US, "%.0f", gaugeMaxBar)} bar", color = Color.White, fontSize = 12.sp)
            Text(if (calibration.isCalibrated) "Usando calibración aprendida" else "Modo aprendizaje: confirma el valor después de la foto", color = Color.White, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))

            if (captureError != null) {
                Text(captureError!!, color = Color(0xFFFFB4AB), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching { providerForCleanup?.unbindAll() }
                        imageCapture = null
                        captureError = null
                        restartKey++
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("REINTENTAR CÁMARA") }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val capture = imageCapture ?: run {
                        captureError = "La cámara todavía no está lista. Espera unos segundos o toca REINTENTAR CÁMARA."
                        return@Button
                    }
                    val dir = File(context.filesDir, "hydor_photos").apply { mkdirs() }
                    val file = File(dir, "test_${System.currentTimeMillis()}.jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                val bitmap = loadOrientedBitmap(file)
                                val estimate = if (bitmap != null) GaugeNeedleEstimator.estimate(bitmap, gaugeMaxBar, calibration)
                                else GaugeEstimate(null, null, 0.0, "No se pudo analizar la fotografía; confirma la lectura manualmente.")
                                ContextCompat.getMainExecutor(context).execute { onCaptured(file, estimate) }
                            }

                            override fun onError(e: ImageCaptureException) {
                                ContextCompat.getMainExecutor(context).execute {
                                    captureError = "Error al tomar fotografía. Reintenta la cámara."
                                }
                            }
                        }
                    )
                },
                enabled = imageCapture != null,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) { Text("●", fontSize = 30.sp) }

            TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White) }
        }
    }
}

data class GaugeEstimate(val valueBar: Double?, val angleDeg: Double?, val confidence: Double, val message: String?)

private object GaugeNeedleEstimator {
    fun estimate(bitmap: Bitmap, maxBar: Double, calibration: GaugeCalibration): GaugeEstimate {
        val scaled = scaleDown(bitmap, 720)
        val cx = scaled.width / 2.0
        val cy = scaled.height / 2.0
        val radius = min(scaled.width, scaled.height) * 0.34
        if (radius < 40) return GaugeEstimate(null, null, 0.0, "Imagen demasiado pequeña para estimar la aguja.")
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
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestAngle = angle
            } else if (score > secondScore) secondScore = score
            angle += 0.5
        }
        val sameScale = abs(calibration.maxBar - maxBar) < 0.11
        val value = if (sameScale) calibration.pressureForAngle(bestAngle)
        else ((bestAngle - startDeg) / sweepDeg).coerceIn(0.0, 1.0) * maxBar
        val confidence = (0.35 + (bestScore - secondScore).coerceAtLeast(0.0) / 35.0).coerceIn(0.20, 0.92)
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        val note = when {
            !sameScale -> "La escala de esta prueba no coincide con el manómetro activo. Se usó la escala geométrica sin calibración."
            confidence < 0.50 -> "Detección con baja confianza. Verifica visualmente la aguja y corrige el valor; la corrección ayudará a calibrar."
            !calibration.isCalibrated -> "Calibración en aprendizaje. Confirma el valor real para añadir este punto."
            else -> null
        }
        return GaugeEstimate(rounded, bestAngle, confidence, note)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val factor = maxSide.toFloat() / side.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
    }
}

private fun loadOrientedBitmap(file: File): Bitmap? {
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
