package com.example.propertymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.utils.formatDate // Corrected import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTenantFieldDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDismiss: () -> Unit,
    onSave: (updatedValue: String) -> Unit
) {
    var textValue by remember(initialValue) { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current
    // State to manage if the input is valid for saving
    val isValidForSave = remember(textValue, initialValue) {
        textValue.isNotBlank() && textValue != initialValue
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("New Value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (isValidForSave) {
                        onSave(textValue)
                        // onDismiss() will be called by the confirm button's onClick
                    }
                    keyboardController?.hide()
                 }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(textValue)
                    onDismiss() // Dismiss after save
                },
                enabled = isValidForSave
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantInfoDialog(
    roomViewModel: RoomViewModel,
    roomName: String,
    currentTenant: TenantEntity?,
    allTenantsInRoom: List<TenantEntity>,
    onDismiss: () -> Unit
) {
    val pastTenants = remember(currentTenant, allTenantsInRoom) {
        allTenantsInRoom.filter { it.id != currentTenant?.id }.sortedByDescending { it.moveInDate }
    }

    // State for the editing sub-dialog
    var showEditTenantFieldDialog by remember { mutableStateOf(false) }
    var tenantToEditState by remember { mutableStateOf<TenantEntity?>(null) }
    var fieldLabelToEditState by remember { mutableStateOf("") }
    var fieldValueToEditState by remember { mutableStateOf("") }
    var fieldKeyboardTypeState by remember { mutableStateOf(KeyboardType.Text) }
    var fieldUpdateActionState by remember { mutableStateOf<(TenantEntity, String) -> TenantEntity>({ t, _ -> t }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tenant Information for $roomName") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Current Tenant",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (currentTenant != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Tenant Image",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .padding(end = 16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = currentTenant.name,
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        IconButton(onClick = {
                                            tenantToEditState = currentTenant
                                            fieldLabelToEditState = "Tenant Name"
                                            fieldValueToEditState = currentTenant.name
                                            fieldKeyboardTypeState = KeyboardType.Text
                                            fieldUpdateActionState = { tenant, newValue -> tenant.copy(name = newValue) }
                                            showEditTenantFieldDialog = true
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Name", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Mobile: ${currentTenant.mobile}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        IconButton(onClick = {
                                            tenantToEditState = currentTenant
                                            fieldLabelToEditState = "Mobile Number"
                                            fieldValueToEditState = currentTenant.mobile
                                            fieldKeyboardTypeState = KeyboardType.Phone
                                            fieldUpdateActionState = { tenant, newValue -> tenant.copy(mobile = newValue) }
                                            showEditTenantFieldDialog = true
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Mobile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Text("Move-in: ${formatDate(currentTenant.moveInDate)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Tenant Documents & Assets", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { /* TODO: Implement asset upload */ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Upload Assets", modifier = Modifier.padding(end = 8.dp))
                                Text("Upload ID/Documents")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Asset Gallery", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp)
                                    .padding(vertical = 8.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No assets uploaded yet.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    Text(
                        "Currently Unoccupied",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Past Tenants", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                if (pastTenants.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        items(pastTenants) { tenant ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(tenant.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Mobile: ${tenant.mobile}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Moved In: ${formatDate(tenant.moveInDate)}", style = MaterialTheme.typography.bodySmall)
                                    tenant.moveOutDate?.let {
                                        Text("Moved Out: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("No past tenant history for this room.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showEditTenantFieldDialog && tenantToEditState != null) {
        EditTenantFieldDialog(
            title = "Edit ${fieldLabelToEditState}",
            initialValue = fieldValueToEditState,
            keyboardType = fieldKeyboardTypeState,
            onDismiss = { showEditTenantFieldDialog = false },
            onSave = { updatedValue ->
                val updatedTenant = fieldUpdateActionState(tenantToEditState!!, updatedValue)
                roomViewModel.updateTenant(updatedTenant) // Make sure RoomViewModel is accessible and this method exists
                showEditTenantFieldDialog = false
            }
        )
    }
}