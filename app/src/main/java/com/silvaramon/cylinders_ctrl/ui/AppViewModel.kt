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
import com.silvaramon.cylinders_ctrl.data.DeviceInfo
import com.silvaramon.cylinders_ctrl.data.GoogleSheetsClient
import com.silvaramon.cylinders_ctrl.data.LocationProvider
import com.silvaramon.cylinders_ctrl.data.NetworkMonitor
import com.silvaramon.cylinders_ctrl.data.formatDateTime
import com.silvaramon.cylinders_ctrl.data.PendingShipment
import com.silvaramon.cylinders_ctrl.data.PendingShipmentRepository
import com.silvaramon.cylinders_ctrl.data.ShipmentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface Screen {
    data object Form : Screen
    /** 1ª captura: OCR da identificação (CAP_M3 + N/S) do cilindro informado. */
    data class CaptureOcr(val cylinderId: String) : Screen

    /** 2ª captura: QR Code com o lote do cilindro informado. */
    data class CaptureLot(val cylinderId: String) : Screen
    data object Pending : Screen
    data class PendingDetail(val id: String) : Screen
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PendingShipmentRepository(application)
    private val locationProvider = LocationProvider(application)
    private val networkMonitor = NetworkMonitor(application)
    private val addressResolutionMutex = Mutex()
    private val sheetsClient = GoogleSheetsClient()
    val pendingShipments = repository.shipments

    /** Ids dos envios sendo enviados à planilha no momento. */
    val sendingShipmentIds = mutableStateListOf<String>()

    /** Mensagem para exibir ao usuário (Snackbar); consumida via [onUserMessageShown]. */
    var userMessage by mutableStateOf<String?>(null)
        private set

    fun onUserMessageShown() {
        userMessage = null
    }

    init {
        // Sempre que a internet voltar, calcula os endereços que ficaram pendentes.
        viewModelScope.launch {
            networkMonitor.isOnlineFlow
                .filter { it }
                .collect { resolvePendingAddresses() }
        }
    }

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
    var clientEmployeeName by mutableStateOf("")
        private set
    var invoiceNumber by mutableStateOf("")
        private set
    var shipmentType by mutableStateOf<ShipmentType?>(null)
        private set
    val cylinders = mutableStateListOf<Cylinder>()

    /** `true` enquanto a localização/endereço estão sendo capturados na finalização. */
    var isFinishing by mutableStateOf(false)
        private set

    val canFinish: Boolean
        get() = !isFinishing && clientName.isNotBlank() && clientEmployeeName.isNotBlank() &&
            invoiceNumber.isNotBlank() &&
            shipmentType != null && cylinders.isNotEmpty() &&
            cylinders.all { it.hasIdentification && it.hasLot }

    fun onClientNameChange(value: String) {
        clientName = value
    }

    fun onClientEmployeeNameChange(value: String) {
        clientEmployeeName = value
    }

    fun onInvoiceNumberChange(value: String) {
        invoiceNumber = value.filter(Char::isDigit)
    }

    fun onShipmentTypeChange(value: ShipmentType) {
        shipmentType = value
    }

    /** Adiciona um novo cilindro vazio; identificação e lote são capturados depois pelo item. */
    fun addCylinder() {
        cylinders.add(
            Cylinder(
                id = UUID.randomUUID().toString(),
                capacityM3 = "",
                serialNumber = "",
            )
        )
    }

    /** Grava CAP_M3 e N/S (lidos via OCR) no cilindro indicado, se ambos estiverem presentes. */
    fun setIdentification(cylinderId: String, ocr: CylinderOcrData): Boolean {
        val index = cylinders.indexOfFirst { it.id == cylinderId }
        if (index < 0 || ocr.capacityM3.isBlank() || ocr.serialNumber.isBlank()) return false
        cylinders[index] = cylinders[index].copy(
            capacityM3 = ocr.capacityM3,
            serialNumber = ocr.serialNumber,
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

    /**
     * Captura data/hora, localização e endereço e salva o envio como pendente.
     * Se a permissão for negada ou a posição não puder ser obtida, salva sem localização.
     */
    fun finish() {
        val type = shipmentType ?: return
        if (!canFinish) return
        val dateTime = formatDateTime()
        // Id do envio, gerado ao clicar em Finalizar e gravado também no JSON de cada cilindro.
        val shipmentId = UUID.randomUUID().toString()
        isFinishing = true
        viewModelScope.launch {
            try {
                val location = runCatching { locationProvider.capturePosition() }.getOrNull()
                // Sem internet, salva sem endereço; ele será calculado quando a rede voltar.
                val address = if (location != null && networkMonitor.isOnline()) {
                    runCatching {
                        locationProvider.reverseGeocode(location.latitude, location.longitude)
                    }.getOrNull()
                } else {
                    null
                }
                val shipment = PendingShipment(
                    id = shipmentId,
                    clientName = clientName.trim(),
                    clientEmployeeName = clientEmployeeName.trim(),
                    invoiceNumber = invoiceNumber,
                    type = type,
                    cylinders = cylinders.map { it.copy(shipmentId = shipmentId) },
                    dateTime = dateTime,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    address = address,
                    deviceId = DeviceInfo.deviceId(getApplication()),
                )
                repository.add(shipment)
                clientName = ""
                clientEmployeeName = ""
                invoiceNumber = ""
                shipmentType = null
                cylinders.clear()
                navigate(Screen.Pending)
            } finally {
                isFinishing = false
            }
        }
    }

    /** Calcula o endereço dos envios pendentes que têm coordenadas mas ainda não têm endereço. */
    private suspend fun resolvePendingAddresses() = addressResolutionMutex.withLock {
        repository.shipments.value
            .filter { it.isAddressPending }
            .forEach { shipment ->
                if (!networkMonitor.isOnline()) return@withLock
                val lat = shipment.latitude ?: return@forEach
                val lng = shipment.longitude ?: return@forEach
                val address = runCatching { locationProvider.reverseGeocode(lat, lng) }.getOrNull()
                if (!address.isNullOrBlank()) {
                    repository.update(shipment.id) { it.copy(address = address) }
                }
            }
    }

    /**
     * Envia o registro para a aba da planilha correspondente ao tipo (entregas/coletas).
     * Em caso de sucesso remove dos pendentes; em caso de falha, mantém na lista.
     */
    fun send(shipment: PendingShipment) {
        if (shipment.id in sendingShipmentIds) return
        if (!networkMonitor.isOnline()) {
            userMessage = "Sem conexão com a internet. O envio permanece pendente."
            return
        }
        sendingShipmentIds += shipment.id
        viewModelScope.launch {
            try {
                // Garante o endereço antes do envio, caso tenha sido salvo offline.
                if (shipment.isAddressPending) resolvePendingAddresses()
                val current = repository.shipments.value.firstOrNull { it.id == shipment.id }
                    ?: return@launch
                sheetsClient.appendRows(current.type.sheetName, current.toSheetRows())
                repository.remove(current.id)
                userMessage = "Nota ${current.invoiceNumber} enviada para \"${current.type.sheetName}\"."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessage = "Falha ao enviar nota ${shipment.invoiceNumber}: ${e.message}"
            } finally {
                sendingShipmentIds -= shipment.id
            }
        }
    }
}
