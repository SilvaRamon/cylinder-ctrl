package com.silvaramon.cylinders_ctrl.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss"

/** Formata millis como `dd/MM/yyyy HH:mm:ss` (fuso local). */
fun formatDateTime(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).format(Date(millis))

/** @param sheetName aba da planilha onde os envios deste tipo são inseridos. */
enum class ShipmentType(val label: String, val sheetName: String) {
    DELIVERY("Entrega", "entregas"),
    PICKUP("Coleta", "coletas"),
}

/**
 * Dados de um cilindro capturado. Serializado no padrão:
 * `{"id": "...", "id_envio": "...", "cap_m3": "10.0", "n_s": "ABC123", "lote": "..."}`
 * (`lote` fica `null` até a segunda captura via QR Code; `id_envio` é preenchido ao finalizar).
 *
 * @param shipmentId id do envio, gerado ao clicar em Finalizar; `null` antes disso.
 * @param capacityM3 capacidade (CAP_M3) lida via OCR.
 * @param serialNumber número de identificação (N/S) lido via OCR.
 * @param lot lote lido do QR Code; `null` enquanto não capturado.
 */
data class Cylinder(
    val id: String,
    val capacityM3: String,
    val serialNumber: String,
    val lot: String? = null,
    val shipmentId: String? = null,
) {
    val hasLot: Boolean get() = !lot.isNullOrBlank()

    /** `true` quando CAP_M3 e N/S já foram capturados via OCR. */
    val hasIdentification: Boolean get() = capacityM3.isNotBlank() && serialNumber.isNotBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_SHIPMENT_ID, shipmentId ?: "")
        put(KEY_CAP_M3, capacityM3)
        put(KEY_SERIAL, serialNumber)
        put(KEY_LOT, lot ?: "")
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_SHIPMENT_ID = "id_envio"
        private const val KEY_CAP_M3 = "cap_m3"
        private const val KEY_SERIAL = "n_s"
        private const val KEY_LOT = "lote"

        fun fromJson(json: JSONObject) = Cylinder(
            id = json.getString(KEY_ID),
            capacityM3 = json.optString(KEY_CAP_M3),
            serialNumber = json.optString(KEY_SERIAL),
            lot = if (json.isNull(KEY_LOT)) "" else json.optString(KEY_LOT).ifBlank { "" },
            shipmentId = json.optString(KEY_SHIPMENT_ID).ifBlank { null },
        )
    }
}

/**
 * @param clientEmployeeName nome do funcionário responsável no cliente.
 * @param dateTime data e hora da finalização, no formato `dd/MM/yyyy HH:mm:ss`.
 * @param latitude/longitude posição capturada ao finalizar; `null` se indisponível.
 * @param address endereço (geocodificação reversa) da posição; `null` se indisponível.
 * @param deviceId id do aparelho que finalizou o envio (`ANDROID_ID`).
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
    val deviceId: String = "",
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    /** Tem coordenadas, mas o endereço ainda não pôde ser calculado (ex.: sem internet). */
    val isAddressPending: Boolean get() = hasLocation && address.isNullOrBlank()

    /**
     * Linhas para a planilha: uma por cilindro, na ordem de [SHEET_COLUMNS].
     */
    fun toSheetRows(): List<List<Any?>> = cylinders.map { cylinder ->
        listOf(
            id,
            dateTime,
            clientName,
            clientEmployeeName,
            invoiceNumber,
            type.label,
            cylinder.serialNumber,
            cylinder.capacityM3,
            cylinder.lot.orEmpty(),
            latitude,
            longitude,
            address.orEmpty(),
            deviceId,
        )
    }

    companion object {
        /** Cabeçalho sugerido para as abas `entregas` e `coletas`. */
        val SHEET_COLUMNS = listOf(
            "id_envio", "data_hora", "cliente", "funcionario_responsavel", "numero_nota",
            "tipo", "n_s", "cap_m3", "lote", "latitude", "longitude",
            "endereco", "id_aparelho",
        )
    }
}
