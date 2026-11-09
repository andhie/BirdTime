package com.andhiewong.birdtime

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LiveData
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.Executors

private val GAME_SCREEN = RectF(0.04f, 0.04f, 0.96f, 0.96f)
private val QUESTION_ZONE = RectF(0.15f, 0.70f, 0.85f, 0.95f)
private const val CAMERA_UNAVAILABLE = "Camera unavailable. Check camera access, close other camera apps, and retry."

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var controller: LifecycleCameraController? = null
    private var cameraState: LiveData<CameraState>? = null
    private var preview: PreviewView? = null
    private var zoomRatio = 1f
    private var matcher: QuestionMatcher? = null
    private var requestId = 0
    private var timeout: Runnable? = null
    private var permitted by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var cameraReady by mutableStateOf(false)
    private var cameraError by mutableStateOf<String?>(null)
    private var dataError by mutableStateOf<String?>(null)
    private var dataReady by mutableStateOf(false)
    private var scanning by mutableStateOf(false)
    private var attempted by mutableStateOf(false)
    private var message by mutableStateOf("Fit the game screen in the white guide and the full question in the green box.")
    private var match by mutableStateOf<Match?>(null)

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
        permissionDenied = !it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permitted = hasCameraPermission()
        attempted = savedInstanceState?.getBoolean("attempted") ?: false
        permissionDenied = savedInstanceState?.getBoolean("permissionDenied") ?: false
        zoomRatio = savedInstanceState?.getFloat("zoomRatio", 1f) ?: 1f
        message = savedInstanceState?.getString("message") ?: message
        savedInstanceState?.getString("remark")?.let { remark ->
            match = Match(
                Question(remark, savedInstanceState.getString("answer").orEmpty()),
                savedInstanceState.getDouble("score"),
                savedInstanceState.getBoolean("strong"),
            )
        }
        worker.execute {
            val loaded = runCatching {
                val questions = assets.open("questions.json").bufferedReader().use {
                    Json.decodeFromString<List<Question>>(it.readText())
                }
                require(questions.size == 496 && questions.map { it.remark }.distinct().size == 496)
                require(questions.all { it.remark.isNotBlank() && it.answer.isNotBlank() })
                QuestionMatcher(questions)
            }
            handler.post {
                if (!isDestroyed) {
                    loaded.onSuccess { matcher = it; dataReady = true }
                        .onFailure { dataError = "Question data could not be loaded. Reinstall the app and try again." }
                }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFA5D6B5),
                onPrimary = Color(0xFF103824),
                surface = Color(0xFF101713),
                surfaceVariant = Color(0xFF26352C),
                onSurfaceVariant = Color(0xFFC0CEC3),
            )) {
                Surface(Modifier.fillMaxSize()) {
                    if (!permitted) {
                        Column(
                            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Bird Time", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(16.dp))
                            Text(if (permissionDenied) {
                                "Camera permission was denied. Allow camera access to scan. If Android no longer asks, enable Camera in the app's system settings and return."
                            } else {
                                "Bird Time needs the camera to read a game question. Images and text stay on this phone."
                            })
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                                Text("Allow camera")
                            }
                        }
                    } else {
                        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                            val stacked = maxHeight > maxWidth
                            val previewPane: @Composable (Modifier) -> Unit = { modifier ->
                                Box(modifier.clip(MaterialTheme.shapes.large)) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize().semantics {
                                            contentDescription = "Camera preview. Align the game screen with the white guide and question with the green guide."
                                        },
                                        factory = { context ->
                                            PreviewView(context).also { view ->
                                                preview?.let(::releasePreview)
                                                preview = view
                                                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                                view.previewStreamState.observe(this@MainActivity) {
                                                    cameraReady = it == PreviewView.StreamState.STREAMING
                                                }
                                                view.doOnLayout { if (preview === view) bindCamera() }
                                            }
                                        },
                                        onRelease = ::releasePreview,
                                    )
                                    Canvas(Modifier.fillMaxSize()) {
                                        for ((region, color) in listOf(GAME_SCREEN to Color.White, QUESTION_ZONE to Color.Green)) {
                                            drawRect(
                                                color = color,
                                                topLeft = Offset(region.left * size.width, region.top * size.height),
                                                size = Size(region.width() * size.width, region.height() * size.height),
                                                style = Stroke(2.dp.toPx()),
                                            )
                                        }
                                    }
                                }
                            }
                            val controlsPane: @Composable (Modifier) -> Unit = { modifier ->
                                Column(
                                    modifier,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    val scanControls: @Composable () -> Unit = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (!dataReady && dataError == null) Text("Loading questions…")
                                            if (!cameraReady && cameraError == null) Text("Starting camera…")
                                            Button(
                                                onClick = ::scan,
                                                enabled = cameraReady && dataReady && !scanning && cameraError == null,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            ) {
                                                Text(if (scanning) "Scanning…" else if (attempted) "Scan again" else "Scan question")
                                            }
                                            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                                            cameraError?.let {
                                                Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                                Button(onClick = ::bindCamera) { Text("Retry camera") }
                                            }
                                        }
                                    }
                                    if (stacked) scanControls()
                                    val result = match
                                    Column(
                                        Modifier.fillMaxWidth().weight(1f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
                                            .verticalScroll(rememberScrollState())
                                            .padding(20.dp)
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (result != null) {
                                            val confidenceColor = if (result.strong) MaterialTheme.colorScheme.primary else Color(0xFFFFD180)
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    if (result.strong) "Answer" else "Possible answer",
                                                    modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = confidenceColor,
                                                )
                                                Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = confidenceColor.copy(alpha = 0.12f),
                                                    contentColor = confidenceColor,
                                                ) {
                                                    Text(
                                                        String.format(Locale.ROOT, "%.0f%% text similarity", result.score * 100),
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                }
                                            }
                                            Text(result.question.answer, style = MaterialTheme.typography.headlineMedium)
                                            if (!result.strong) {
                                                Text(
                                                    "Compare the question below with your screen before using this answer.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFFFFD180),
                                                )
                                            }
                                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                            Text(
                                                "Question reference",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                result.question.remark,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Text(
                                                if (scanning) "Reading question…" else if (attempted) "Ready to try again" else "Find your answer",
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            if (cameraError == null || dataError != null) {
                                                Text(dataError ?: message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (!stacked) scanControls()
                                }
                            }
                            if (stacked) {
                                val previewHeight = minOf(maxWidth, maxHeight / 2)
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    previewPane(Modifier.fillMaxWidth().height(previewHeight))
                                    controlsPane(Modifier.fillMaxWidth().weight(1f))
                                }
                            } else {
                                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    previewPane(Modifier.fillMaxHeight().weight(2f))
                                    controlsPane(Modifier.fillMaxHeight().weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        if (!hasCameraPermission()) {
            permitted = false
            return
        }
        val view = preview ?: return
        unbindCamera()
        cameraError = null
        try {
            val camera = LifecycleCameraController(this)
            controller = camera
            camera.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            camera.imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(
                    android.util.Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ))
                .build()
            camera.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            camera.setZoomRatio(zoomRatio)
            view.controller = camera
            camera.bindToLifecycle(this)
            camera.initializationFuture.addListener({
                if (controller === camera && !isDestroyed) {
                    try {
                        camera.initializationFuture.get()
                        val state = checkNotNull(camera.cameraInfo).cameraState
                        cameraState = state
                        state.observe(this) {
                            if (it.error != null) {
                                cameraError = CAMERA_UNAVAILABLE
                                if (scanning) finishScan(requestId, "Camera interrupted the scan. Try again when ready.")
                            } else if (it.type == CameraState.Type.OPEN) {
                                cameraError = null
                            }
                        }
                    } catch (_: Exception) {
                        cameraError = CAMERA_UNAVAILABLE
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (_: Exception) {
            cameraError = CAMERA_UNAVAILABLE
        }
    }

    private fun releasePreview(view: PreviewView) {
        view.previewStreamState.removeObservers(this)
        if (preview !== view) return
        if (scanning) finishScan(requestId, "Scan paused. Tap Scan again when ready.")
        unbindCamera()
        preview = null
    }

    private fun unbindCamera() {
        cameraState?.removeObservers(this)
        cameraState = null
        controller?.let {
            zoomRatio = it.zoomState.value?.zoomRatio ?: zoomRatio
            it.clearImageAnalysisAnalyzer()
            preview?.controller = null
            it.unbind()
        }
        controller = null
        cameraReady = false
    }

    private fun scan() {
        if (!hasCameraPermission()) {
            permitted = false
            return
        }
        val camera = controller ?: return
        val view = preview ?: return
        val localMatcher = matcher ?: return
        if (scanning || !cameraReady || cameraError != null || view.width == 0 || view.height == 0) return
        scanning = true
        attempted = true
        match = null
        message = "Reading question…"
        val id = ++requestId
        val zone = RectF(
            QUESTION_ZONE.left * view.width, QUESTION_ZONE.top * view.height,
            QUESTION_ZONE.right * view.width, QUESTION_ZONE.bottom * view.height,
        )
        timeout = Runnable {
            if (id == requestId && scanning) {
                finishScan(id, "The scan timed out. Keep the question steady and try again.")
            }
        }.also { handler.postDelayed(it, 15_000) }
        var received = false
        try {
            val analyzer = MlKitAnalyzer(
                listOf(recognizer),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(this),
            ) { result ->
                if (id != requestId || !scanning || received) return@MlKitAnalyzer
                received = true
                try {
                    camera.clearImageAnalysisAnalyzer()
                    result.getThrowable(recognizer)?.let { throw it }
                    val text = result.getValue(recognizer) ?: error("No OCR result")
                    val selected = text.textBlocks.filter { block ->
                        block.boundingBox?.let { zone.contains(it.exactCenterX(), it.exactCenterY()) } == true
                    }.sortedWith(compareBy({ it.boundingBox?.top }, { it.boundingBox?.left }))
                        .joinToString("\n") { it.text }
                    worker.execute {
                        val found = runCatching { localMatcher.match(selected) }
                        handler.post {
                            found.onSuccess {
                                finishScan(id, "No matching question. Keep the full question in the green box and try again.", it)
                            }.onFailure {
                                finishScan(id, "Could not read the question. Try scanning again.")
                            }
                        }
                    }
                } catch (_: Exception) {
                    finishScan(id, "Could not read the question. Try scanning again.")
                }
            }
            // CameraX forwards preview transform changes but doesn't replay the current transform.
            analyzer.updateTransform(view.sensorToViewTransform)
            // Resolution is fixed before binding; analyzer changes must not restart zoom/focus.
            camera.setImageAnalysisAnalyzer(worker, object : ImageAnalysis.Analyzer {
                override fun analyze(image: ImageProxy) = analyzer.analyze(image)
                override fun getDefaultTargetResolution(): android.util.Size? = null
                override fun getTargetCoordinateSystem() = analyzer.targetCoordinateSystem
                override fun updateTransform(matrix: Matrix?) = analyzer.updateTransform(matrix)
            })
        } catch (_: Exception) {
            finishScan(id, "Could not start scanning. Try again.")
        }
    }

    private fun finishScan(id: Int, text: String, result: Match? = null) {
        if (id != requestId || isDestroyed) return
        try {
            controller?.clearImageAnalysisAnalyzer()
        } finally {
            timeout?.let(handler::removeCallbacks)
            timeout = null
            requestId++
            scanning = false
            match = result
            message = text
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("attempted", attempted)
        outState.putBoolean("permissionDenied", permissionDenied)
        outState.putString("message", if (scanning) "Scan paused. Tap Scan again when ready." else message)
        outState.putFloat("zoomRatio", controller?.zoomState?.value?.zoomRatio ?: zoomRatio)
        match?.let { result ->
            outState.putString("remark", result.question.remark)
            outState.putString("answer", result.question.answer)
            outState.putDouble("score", result.score)
            outState.putBoolean("strong", result.strong)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        permitted = hasCameraPermission()
    }

    override fun onStop() {
        if (scanning) finishScan(requestId, "Scan paused. Tap Scan again when ready.")
        super.onStop()
    }

    override fun onDestroy() {
        requestId++
        timeout?.let(handler::removeCallbacks)
        preview?.let(::releasePreview)
        recognizer.close()
        worker.shutdown()
        super.onDestroy()
    }
}
