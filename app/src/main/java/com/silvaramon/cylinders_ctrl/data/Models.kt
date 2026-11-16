package com.silvaramon.cylinders_ctrl.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss"

/** Formata millis como `dd/MM/yyyy HH:mm:ss` (fuso local). */
fun formatDateTime(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).format(Date(millis))

enum class ShipmentType(val label: String) {
    DELIVERY("Entrega"),
    PICKUP("Coleta"),
}

/**
 * Dados de um cilindro capturado. Serializado no padrão:
 * `{"id": "...", "cap_m3": "10.0", "n_s": "ABC123", "lote": "..."}`
 * (`lote` fica `null` até a segunda captura via QR Code).
 *
 * @param capacityM3 capacidade (CAP_M3) lida via OCR.
 * @param serialNumber número de identificação (N/S) lido via OCR.
 * @param lot lote lido do QR Code; `null` enquanto não capturado.
 */
data class Cylinder(
    val id: String,
    val capacityM3: String,
    val serialNumber: String,
    val lot: String? = null,
) {
    val hasLot: Boolean get() = !lot.isNullOrBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_CAP_M3, capacityM3)
        put(KEY_SERIAL, serialNumber)
        put(KEY_LOT, lot ?: "")
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_CAP_M3 = "cap_m3"
        private const val KEY_SERIAL = "n_s"
        private const val KEY_LOT = "lote"

        fun fromJson(json: JSONObject) = Cylinder(
            id = json.getString(KEY_ID),
            capacityM3 = json.optString(KEY_CAP_M3),
            serialNumber = json.optString(KEY_SERIAL),
            lot = if (json.isNull(KEY_LOT)) "" else json.optString(KEY_LOT).ifBlank { "" },
        )
    }
}

/**
 * @param clientEmployeeName nome do funcionário responsável no cliente.
 * @param dateTime data e hora da finalização, no formato `dd/MM/yyyy HH:mm:ss`.
 * @param latitude/longitude posição capturada ao finalizar; `null` se indisponível.
 * @param address endereço (geocodificação reversa) da posição; `null` se indisponível.
 */
data class PendingShipment(
    val id: String,
    val clientName: String,
    val clientEmployeeName: String,
    val invoiceNumber: String,
    val type: ShipmentType,
    val cylinders: List<Cylinder>,
    val dateTime: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    /** Tem coordenadas, mas o endereço ainda não pôde ser calculado (ex.: sem internet). */
    val isAddressPending: Boolean get() = hasLocation && address.isNullOrBlank()
}
