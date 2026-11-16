package com.silvaramon.cylinders_ctrl.data

import org.json.JSONObject

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

data class PendingShipment(
    val id: String,
    val clientName: String,
    val invoiceNumber: String,
    val type: ShipmentType,
    val cylinders: List<Cylinder>,
    val createdAt: Long,
)
