package com.silvaramon.cylinders_ctrl.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Armazena os envios pendentes localmente (SharedPreferences + JSON).
 * Implementação simples para a primeira versão; pode ser trocada por Room depois.
 */
class PendingShipmentRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pending_shipments", Context.MODE_PRIVATE)

    private val _shipments = MutableStateFlow(load())
    val shipments: StateFlow<List<PendingShipment>> = _shipments.asStateFlow()

    suspend fun add(shipment: PendingShipment) {
        _shipments.update { it + shipment }
        persist(_shipments.value)
    }

    private suspend fun persist(list: List<PendingShipment>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<PendingShipment> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getJSONObject(it).toShipment() }
        }.getOrDefault(emptyList())
    }

    private fun PendingShipment.toJson() = JSONObject().apply {
        put("id", id)
        put("clientName", clientName)
        put("invoiceNumber", invoiceNumber)
        put("type", type.name)
        put("createdAt", createdAt)
        put("cylinders", JSONArray().apply {
            cylinders.forEach { put(it.toJson()) }
        })
    }

    private fun JSONObject.toShipment(): PendingShipment {
        val cyl = getJSONArray("cylinders")
        return PendingShipment(
            id = getString("id"),
            clientName = getString("clientName"),
            invoiceNumber = getString("invoiceNumber"),
            type = ShipmentType.valueOf(getString("type")),
            createdAt = getLong("createdAt"),
            cylinders = List(cyl.length()) { i -> Cylinder.fromJson(cyl.getJSONObject(i)) },
        )
    }

    private companion object {
        const val KEY = "shipments"
    }
}
