package com.silvaramon.cylinders_ctrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.silvaramon.cylinders_ctrl.data.PendingShipment
import java.util.Locale

@Composable
fun PendingShipmentsScreen(
    shipments: List<PendingShipment>,
    sendingIds: List<String>,
    onSend: (PendingShipment) -> Unit,
    onDetails: (PendingShipment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shipments.isEmpty()) {
        Text(
            "Nenhum envio pendente.",
            modifier = modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shipments, key = { it.id }) { shipment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(shipment.clientName, style = MaterialTheme.typography.titleMedium)
                    Text("Nota: ${shipment.invoiceNumber} • ${shipment.type.label}")
                    Text("Responsável: ${shipment.clientEmployeeName}")
                    Text("Cilindros: ${shipment.cylinders.size}")
                    Text(
                        "Data e Hora: ${shipment.dateTime}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        addressLabel(shipment),
                        color = if (shipment.isAddressPending) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onDetails(shipment) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Detalhes") }
                        val isSending = shipment.id in sendingIds
                        Button(
                            onClick = { onSend(shipment) },
                            enabled = !isSending,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Enviando…")
                            } else {
                                Text("Enviar")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun addressLabel(shipment: PendingShipment): String = when {
    !shipment.address.isNullOrBlank() -> "Endereço: ${shipment.address}"
    shipment.isAddressPending -> "Endereço: pendente (será calculado quando houver internet)"
    else -> "localização indisponível"
}

@Composable
fun PendingShipmentDetailScreen(
    shipment: PendingShipment?,
    modifier: Modifier = Modifier,
) {
    if (shipment == null) {
        Text("Envio não encontrado.", modifier = modifier.padding(16.dp))
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(shipment.clientName, style = MaterialTheme.typography.headlineSmall)
            Text("Número da nota: ${shipment.invoiceNumber}")
            Text("Tipo: ${shipment.type.label}")
            Text("Funcionário responsável: ${shipment.clientEmployeeName}")
            Text("Data e hora: ${shipment.dateTime}")
            Text("Endereço: ${addressLabel(shipment)}")
            if (shipment.latitude != null && shipment.longitude != null) {
                Text(
                    "Coordenadas: %.6f, %.6f".format(Locale.US, shipment.latitude, shipment.longitude),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Cilindros (${shipment.cylinders.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(shipment.cylinders, key = { it.id }) { cylinder ->
            CylinderCard(cylinder = cylinder)
        }
    }
}
