package com.silvaramon.cylinders_ctrl.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.silvaramon.cylinders_ctrl.data.Cylinder
import com.silvaramon.cylinders_ctrl.data.CylinderOcrData
import com.silvaramon.cylinders_ctrl.data.CylinderOcrParser
import com.silvaramon.cylinders_ctrl.data.OcrParseResult

private const val TAG = "CylinderCapture"

private enum class AnalyzerMode { TEXT, QR_CODE }

/**
 * 1ª captura: OCR da etiqueta do cilindro. Extrai CAP_M3 e N/S; se algum
 * estiver ausente, exibe erro e não confirma.
 */
@Composable
fun CylinderOcrCaptureScreen(
    onConfirm: (CylinderOcrData) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var liveText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<CylinderOcrData?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    CameraPermissionGate(onCancel = onCancel, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            StepTitle(
                if (parsed == null) "Aponte para a identificação do cilindro (CAP_M3 e N/S)"
                else "Revise os dados do cilindro"
            )
            ErrorBanner(errorMessage)

            val data = parsed
            if (data == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CameraAnalyzerPreview(
                        mode = AnalyzerMode.TEXT,
                        onResult = { liveText = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ActionRow(onCancel) {
                    Button(
                        onClick = {
                            Log.d(TAG, "OCR capturado:\n$liveText")
                            when (val result = CylinderOcrParser.parse(liveText)) {
                                is OcrParseResult.Success -> {
                                    parsed = result.data
                                    errorMessage = null
                                }

                                is OcrParseResult.Error -> errorMessage = result.message
                            }
                        },
                        enabled = liveText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Capturar texto") }
                }
            } else {
                JsonPreview(
                    Cylinder(id = "-", capacityM3 = data.capacityM3, serialNumber = data.serialNumber)
                )
                ActionRow(onCancel) {
                    OutlinedButton(
                        onClick = { parsed = null },
                        modifier = Modifier.weight(1f),
                    ) { Text("Refazer") }
                    Button(
                        onClick = { onConfirm(data) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Salvar identificação") }
                }
            }
        }
    }
}

/**
 * 2ª captura: leitura do QR Code com o lote do cilindro. O conteúdo do QR
 * é usado como string do lote.
 */
@Composable
fun LotQrCaptureScreen(
    cylinder: Cylinder?,
    onConfirm: (lot: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cylinder == null) {
        Column(modifier.padding(16.dp)) {
            Text("Cilindro não encontrado.")
            OutlinedButton(onClick = onCancel) { Text("Voltar") }
        }
        return
    }
    var lot by rememberSaveable { mutableStateOf<String?>(null) }

    CameraPermissionGate(onCancel = onCancel, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            StepTitle(
                if (lot == null) {
                    "Aponte para o QR Code do lote" +
                        cylinder.serialNumber.ifBlank { null }?.let { " — N/S $it" }.orEmpty()
                }
                else "Revise o lote do cilindro"
            )
            val read = lot
            if (read == null) {
                CameraAnalyzerPreview(
                    mode = AnalyzerMode.QR_CODE,
                    onResult = { value ->
                        if (lot == null && value.isNotBlank()) {
                            Log.d(TAG, "QR Code lido: $value")
                            lot = value.trim()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                ActionRow(onCancel)
            } else {
                JsonPreview(cylinder.copy(lot = read))
                ActionRow(onCancel) {
                    OutlinedButton(
                        onClick = { lot = null },
                        modifier = Modifier.weight(1f),
                    ) { Text("Ler novamente") }
                    Button(
                        onClick = { onConfirm(read) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Salvar lote") }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Componentes compartilhados
// ----------------------------------------------------------------------------

@Composable
private fun CameraPermissionGate(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.fillMaxSize()) {
        if (hasPermission) {
            content()
        } else {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("É necessário permitir o acesso à câmera para capturar os cilindros.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun ErrorBanner(message: String?) {
    if (message == null) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(message, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ColumnScope.JsonPreview(cylinder: Cylinder) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Dados do cilindro (JSON):", style = MaterialTheme.typography.labelLarge)
        Text(
            cylinder.toJson().apply {
                remove("id")
                remove("id_envio") // só é gerado ao finalizar
            }.toString(2),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ActionRow(
    onCancel: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("Cancelar")
        }
        actions()
    }
}

@Composable
private fun CameraAnalyzerPreview(
    mode: AnalyzerMode,
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    DisposableEffect(mode) {
        val executor = ContextCompat.getMainExecutor(context)
        val detector = when (mode) {
            AnalyzerMode.TEXT -> {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                controller.setImageAnalysisAnalyzer(
                    executor,
                    MlKitAnalyzer(
                        listOf(recognizer),
                        ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                        executor,
                    ) { result ->
                        result.getValue(recognizer)?.let { currentOnResult(it.text) }
                    },
                )
                recognizer
            }

            AnalyzerMode.QR_CODE -> {
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )
                controller.setImageAnalysisAnalyzer(
                    executor,
                    MlKitAnalyzer(
                        listOf(scanner),
                        ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                        executor,
                    ) { result ->
                        result.getValue(scanner)
                            ?.firstNotNullOfOrNull { it.rawValue }
                            ?.let { currentOnResult(it) }
                    },
                )
                scanner
            }
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            detector.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}
