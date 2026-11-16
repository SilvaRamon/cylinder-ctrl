package com.silvaramon.cylinders_ctrl.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silvaramon.cylinders_ctrl.data.Cylinder
import com.silvaramon.cylinders_ctrl.data.CylinderOcrData
import com.silvaramon.cylinders_ctrl.data.PendingShipment
import com.silvaramon.cylinders_ctrl.data.PendingShipmentRepository
import com.silvaramon.cylinders_ctrl.data.ShipmentType
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Screen {
    data object Form : Screen
    /** 1ª captura: OCR da identificação (CAP_M3 + N/S). */
    data object CaptureOcr : Screen

    /** 2ª captura: QR Code com o lote do cilindro informado. */
    data class CaptureLot(val cylinderId: String) : Screen
    data object Pending : Screen
    data class PendingDetail(val id: String) : Screen
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PendingShipmentRepository(application)
    val pendingShipments = repository.shipments

    // --- Navegação simples baseada em pilha ---
    private val backStack = mutableStateListOf<Screen>(Screen.Form)
    val currentScreen: Screen get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun goBack() {
        if (canGoBack) backStack.removeAt(backStack.lastIndex)
    }

    // --- Estado do formulário ---
    var clientName by mutableStateOf("")
        private set
    var invoiceNumber by mutableStateOf("")
        private set
    var shipmentType by mutableStateOf<ShipmentType?>(null)
        private set
    val cylinders = mutableStateListOf<Cylinder>()

    val canFinish: Boolean
        get() = clientName.isNotBlank() && invoiceNumber.isNotBlank() &&
            shipmentType != null && cylinders.isNotEmpty() && cylinders.all { it.hasLot }

    fun onClientNameChange(value: String) {
        clientName = value
    }

    fun onInvoiceNumberChange(value: String) {
        invoiceNumber = value.filter(Char::isDigit)
    }

    fun onShipmentTypeChange(value: ShipmentType) {
        shipmentType = value
    }

    /** Adiciona o cilindro (sem lote) apenas se CAP_M3 e N/S estiverem presentes. */
    fun addCylinder(ocr: CylinderOcrData): Boolean {
        if (ocr.capacityM3.isBlank() || ocr.serialNumber.isBlank()) return false
        cylinders.add(
            Cylinder(
                id = UUID.randomUUID().toString(),
                capacityM3 = ocr.capacityM3,
                serialNumber = ocr.serialNumber,
            )
        )
        return true
    }

    /** Grava o lote (string lida do QR Code) no cilindro indicado. */
    fun setLot(cylinderId: String, lot: String): Boolean {
        val index = cylinders.indexOfFirst { it.id == cylinderId }
        if (index < 0 || lot.isBlank()) return false
        cylinders[index] = cylinders[index].copy(lot = lot.trim())
        return true
    }

    fun removeCylinder(cylinder: Cylinder) {
        cylinders.remove(cylinder)
    }

    fun finish() {
        val type = shipmentType ?: return
        if (!canFinish) return
        val shipment = PendingShipment(
            id = UUID.randomUUID().toString(),
            clientName = clientName.trim(),
            invoiceNumber = invoiceNumber,
            type = type,
            cylinders = cylinders.toList(),
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { repository.add(shipment) }
        clientName = ""
        invoiceNumber = ""
        shipmentType = null
        cylinders.clear()
        navigate(Screen.Pending)
    }

    fun send(@Suppress("UNUSED_PARAMETER") shipment: PendingShipment) {
        // TODO: implementar o envio.
    }
}
