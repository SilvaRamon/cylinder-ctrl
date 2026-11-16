package com.silvaramon.cylinders_ctrl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silvaramon.cylinders_ctrl.ui.AppViewModel
import com.silvaramon.cylinders_ctrl.ui.CylinderOcrCaptureScreen
import com.silvaramon.cylinders_ctrl.ui.LotQrCaptureScreen
import com.silvaramon.cylinders_ctrl.ui.PendingShipmentDetailScreen
import com.silvaramon.cylinders_ctrl.ui.PendingShipmentsScreen
import com.silvaramon.cylinders_ctrl.ui.Screen
import com.silvaramon.cylinders_ctrl.ui.ShipmentFormScreen
import com.silvaramon.cylinders_ctrl.ui.theme.CylindersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CylindersTheme {
                CylindersApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CylindersApp(viewModel: AppViewModel = viewModel()) {
    val screen = viewModel.currentScreen
    val pending by viewModel.pendingShipments.collectAsStateWithLifecycle()

    BackHandler(enabled = viewModel.canGoBack) { viewModel.goBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.Form -> "Nova nota"
                            Screen.CaptureOcr -> "Capturar cilindro"
                            is Screen.CaptureLot -> "Capturar lote"
                            Screen.Pending -> "Envios pendentes"
                            is Screen.PendingDetail -> "Detalhes do envio"
                        }
                    )
                },
                navigationIcon = {
                    if (viewModel.canGoBack) {
                        TextButton(onClick = viewModel::goBack) { Text("Voltar") }
                    }
                },
                actions = {
                    if (screen == Screen.Form) {
                        TextButton(onClick = { viewModel.navigate(Screen.Pending) }) {
                            Text("Pendentes (${pending.size})")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (screen) {
            Screen.Form -> ShipmentFormScreen(viewModel, contentModifier)

            Screen.CaptureOcr -> CylinderOcrCaptureScreen(
                onConfirm = { ocr -> if (viewModel.addCylinder(ocr)) viewModel.goBack() },
                onCancel = viewModel::goBack,
                modifier = contentModifier,
            )

            is Screen.CaptureLot -> LotQrCaptureScreen(
                cylinder = viewModel.cylinders.firstOrNull { it.id == screen.cylinderId },
                onConfirm = { lot -> if (viewModel.setLot(screen.cylinderId, lot)) viewModel.goBack() },
                onCancel = viewModel::goBack,
                modifier = contentModifier,
            )

            Screen.Pending -> PendingShipmentsScreen(
                shipments = pending,
                onSend = viewModel::send,
                onDetails = { viewModel.navigate(Screen.PendingDetail(it.id)) },
                modifier = contentModifier,
            )

            is Screen.PendingDetail -> PendingShipmentDetailScreen(
                shipment = pending.firstOrNull { it.id == screen.id },
                modifier = contentModifier,
            )
        }
    }
}
