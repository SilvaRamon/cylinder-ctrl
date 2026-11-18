package com.silvaramon.cylinders_ctrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.silvaramon.cylinders_ctrl.ui.AppViewModel
import com.silvaramon.cylinders_ctrl.ui.Screen
import com.silvaramon.cylinders_ctrl.data.Cylinder
import com.silvaramon.cylinders_ctrl.data.ShipmentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentFormScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = viewModel.clientName,
                onValueChange = viewModel::onClientNameChange,
                label = { Text("Nome do cliente") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.clientEmployeeName,
                onValueChange = viewModel::onClientEmployeeNameChange,
                label = { Text("Funcionário responsável (cliente)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.invoiceNumber,
                onValueChange = viewModel::onInvoiceNumberChange,
                label = { Text("Número da nota") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ShipmentTypeDropdown(
                selected = viewModel.shipmentType,
                onSelected = viewModel::onShipmentTypeChange,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cilindros (${viewModel.cylinders.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = viewModel::addCylinder) {
                    Text("Adicionar cilindro")
                }
            }
        }
        if (viewModel.cylinders.isEmpty()) {
            item {
                Text(
                    "Nenhum cilindro capturado ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(viewModel.cylinders, key = { it.id }) { cylinder ->
                CylinderCard(
                    cylinder = cylinder,
                    onRemove = { viewModel.removeCylinder(cylinder) },
                    onCaptureIdentification = { viewModel.navigate(Screen.CaptureOcr(cylinder.id)) },
                    onCaptureLot = { viewModel.navigate(Screen.CaptureLot(cylinder.id)) },
                )
            }
            item {
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = viewModel::finish,
                    enabled = viewModel.canFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isFinishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Obtendo localização…")
                    } else {
                        Text("Finalizar")
                    }
                }
                if (!viewModel.canFinish && !viewModel.isFinishing) {
                    val missingIds = viewModel.cylinders.count { !it.hasIdentification }
                    val missingLots = viewModel.cylinders.count { !it.hasLot }
                    Text(
                        buildString {
                            append("Para finalizar, preencha cliente, funcionário responsável, número da nota e tipo")
                            if (missingIds > 0) append(", capture a identificação de $missingIds cilindro(s)")
                            if (missingLots > 0) append(" e capture o lote de $missingLots cilindro(s)")
                            append(".")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShipmentTypeDropdown(
    selected: ShipmentType?,
    onSelected: (ShipmentType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Entrega ou coleta") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ShipmentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CylinderCard(
    cylinder: Cylinder,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onCaptureIdentification: (() -> Unit)? = null,
    onCaptureLot: (() -> Unit)? = null,
) {
    val idColor = if (cylinder.hasIdentification) Color.Unspecified else MaterialTheme.colorScheme.error
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "N/S: ${cylinder.serialNumber.ifBlank { "pendente" }}",
                style = MaterialTheme.typography.titleSmall,
                color = idColor,
            )
            Text(
                "CAP_M3: ${cylinder.capacityM3.ifBlank { "pendente" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = idColor,
            )
            Text(
                "Lote: ${cylinder.lot ?: "pendente"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (cylinder.hasLot) Color.Unspecified else MaterialTheme.colorScheme.error,
            )
            if (onRemove != null || onCaptureIdentification != null || onCaptureLot != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (onRemove != null) {
                        TextButton(onClick = onRemove) { Text("Remover") }
                    }
                    if (onCaptureIdentification != null) {
                        OutlinedButton(onClick = onCaptureIdentification) {
                            Text(
                                if (cylinder.hasIdentification) "Recapturar identificação"
                                else "Capturar identificação"
                            )
                        }
                    }
                    if (onCaptureLot != null) {
                        OutlinedButton(onClick = onCaptureLot) {
                            Text(if (cylinder.hasLot) "Recapturar lote" else "Capturar lote")
                        }
                    }
                }
            }
        }
    }
}
