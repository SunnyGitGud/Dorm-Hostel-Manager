package com.example.propertymanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.text.input.KeyboardType // No longer needed for phone
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import java.text.SimpleDateFormat
import java.util.Locale

fun formatDate(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "N/A"
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(timestamp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(propertyId: Int, roomViewModel: RoomViewModel, navController: NavController) {
    val roomsWithTenants by roomViewModel.roomsWithTenants.collectAsState()
    var showAddRoomDialog by remember { mutableStateOf(false) }

    var showAddEditTenantDialog by remember { mutableStateOf(false) }
    var selectedRoomForTenantAction by remember { mutableStateOf<RoomWithTenant?>(null) }

    var showConfirmRemoveTenantDialog by remember { mutableStateOf(false) }
    var tenantPendingRemoval by remember { mutableStateOf<RoomWithTenant?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Rooms") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRoomDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Room")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (roomsWithTenants.isEmpty()) {
                item { Text("No rooms found. Click the '+' button to add one.") }
            } else {
                items(roomsWithTenants) { roomWithTenant ->
                    RoomItem(
                        roomWithTenant = roomWithTenant,
                        onAddEditTenant = {
                            selectedRoomForTenantAction = it
                            showAddEditTenantDialog = true
                        },
                        onRemoveTenant = { roomWithTenantToRemove ->
                            tenantPendingRemoval = roomWithTenantToRemove
                            showConfirmRemoveTenantDialog = true
                        },
                        onViewDetails = { pId, rId ->
                            navController.navigate("room_details/$pId/$rId")
                        },
                        propertyId = propertyId
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (showAddRoomDialog) {
            AddRoomDialog(
                onDismiss = { showAddRoomDialog = false },
                onAddRoom = { name, rentString ->
                    val rent = rentString.toDoubleOrNull()
                    if (rent != null && rent > 0) {
                        roomViewModel.addRoom(name, rent)
                        showAddRoomDialog = false
                    }
                }
            )
        }

        if (showAddEditTenantDialog && selectedRoomForTenantAction != null) {
            AddEditTenantDialog(
                roomWithTenant = selectedRoomForTenantAction!!,
                onDismiss = {
                    showAddEditTenantDialog = false
                    selectedRoomForTenantAction = null
                },
                onConfirm = { roomId, name, moveInDate, existingTenantId -> // Updated signature
                    roomViewModel.addOrUpdateTenantDetails(roomId, name, moveInDate, existingTenantId) // Updated call
                    showAddEditTenantDialog = false
                    selectedRoomForTenantAction = null
                }
            )
        }

        if (showConfirmRemoveTenantDialog && tenantPendingRemoval != null) {
            val tenantIdToRemove = tenantPendingRemoval?.tenant?.id
            ConfirmRemoveTenantDialog(
                tenantName = tenantPendingRemoval!!.tenant?.name ?: "Unknown Tenant",
                roomName = tenantPendingRemoval!!.room.name,
                onDismiss = {
                    showConfirmRemoveTenantDialog = false
                    tenantPendingRemoval = null
                },
                onConfirm = {
                    if (tenantIdToRemove != null) {
                        roomViewModel.recordTenantMoveOutDate(tenantIdToRemove, System.currentTimeMillis()) // Updated call
                    }
                    showConfirmRemoveTenantDialog = false
                    tenantPendingRemoval = null
                }
            )
        }
    }
}

@Composable
fun RoomItem(
    roomWithTenant: RoomWithTenant,
    onAddEditTenant: (RoomWithTenant) -> Unit,
    onRemoveTenant: (RoomWithTenant) -> Unit,
    onViewDetails: (propertyId: Int, roomId: Int) -> Unit,
    propertyId: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Room: ${roomWithTenant.room.name}", style = MaterialTheme.typography.titleMedium)
            Text(text = "Rent: $${String.format("%.2f", roomWithTenant.room.rent)}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))
            Text("Tenant Info:", style = MaterialTheme.typography.titleSmall)
            if (roomWithTenant.tenant != null) {
                Text("Name: ${roomWithTenant.tenant.name}", style = MaterialTheme.typography.bodyMedium)
                // Text("Phone: ${roomWithTenant.tenant.phone}", style = MaterialTheme.typography.bodyMedium) // Removed phone
                Text("Move-in: ${formatDate(roomWithTenant.tenant.moveInDate)}", style = MaterialTheme.typography.bodyMedium)
                if (roomWithTenant.tenant.moveOutDate != null) {
                    Text("Move-out: ${formatDate(roomWithTenant.tenant.moveOutDate)}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { onAddEditTenant(roomWithTenant) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Tenant", modifier = Modifier.padding(end = 4.dp))
                        Text("Edit Tenant")
                    }
                    // Only show remove button if tenant is active (no moveOutDate)
                    if (roomWithTenant.tenant.moveOutDate == null) {
                        IconButton(onClick = { onRemoveTenant(roomWithTenant) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Record Move-out", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Text("No tenant assigned.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onAddEditTenant(roomWithTenant) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Tenant", modifier = Modifier.padding(end = 4.dp))
                    Text("Add Tenant")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onViewDetails(propertyId, roomWithTenant.room.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "View Details", modifier = Modifier.padding(end = 4.dp))
                Text("View Details / Manage Bills")
            }
        }
    }
}

@Composable
fun ConfirmRemoveTenantDialog( // Renamed from ConfirmRemoveTenantDialog to reflect it's now a "move-out"
    tenantName: String,
    roomName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Move-out") },
        text = { Text("Are you sure you want to record tenant '$tenantName' as moved out from room '$roomName' as of today?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm Move-out")
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
fun AddEditTenantDialog(
    roomWithTenant: RoomWithTenant,
    onDismiss: () -> Unit,
    onConfirm: (roomId: Int, name: String, moveInDate: Long, existingTenantId: Int?) -> Unit // Updated signature
) {
    var name by remember(roomWithTenant.tenant) { mutableStateOf(roomWithTenant.tenant?.name ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    // Add state for moveInDate if you want to allow editing it. For now, new tenants get current time.
    // var moveInDate by remember { mutableStateOf(roomWithTenant.tenant?.moveInDate ?: System.currentTimeMillis()) }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (roomWithTenant.tenant == null || roomWithTenant.tenant.moveOutDate != null) "Add New Tenant" else "Edit Tenant Details") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Tenant Name") },
                    isError = nameError != null,
                    singleLine = true
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                //Spacer(modifier = Modifier.height(8.dp))
                // OutlinedTextField for phone removed
                // Consider adding an OutlinedTextField for moveInDate if it should be editable
                // For now, moveInDate for new tenants is System.currentTimeMillis()
                // For editing existing tenants, their original moveInDate is preserved by default if not explicitly changed.
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var valid = true
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        valid = false
                    }
                    // phone validation removed
                    if (valid) {
                        val existingTenantId = if (roomWithTenant.tenant?.moveOutDate == null) roomWithTenant.tenant?.id else null
                        val moveInDateToUse = roomWithTenant.tenant?.moveInDate ?: System.currentTimeMillis() // Use existing or new
                        onConfirm(roomWithTenant.room.id, name, moveInDateToUse, existingTenantId)
                    }
                }
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
fun AddRoomDialog(
    onDismiss: () -> Unit,
    onAddRoom: (name: String, rent: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") } // Corrected typo here from mutableStateOF
    var nameError by remember { mutableStateOf<String?>(null) }
    var rentError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Room") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Room Name/Number") },
                    isError = nameError != null,
                    singleLine = true
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rent,
                    onValueChange = { rent = it; rentError = null },
                    label = { Text("Monthly Rent") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    isError = rentError != null,
                    singleLine = true
                )
                if (rentError != null) {
                    Text(rentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var valid = true
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        valid = false
                    }
                    val rentDouble = rent.toDoubleOrNull()
                    if (rent.isBlank()) {
                        rentError = "Rent cannot be empty"
                        valid = false
                    } else if (rentDouble == null || rentDouble <= 0) {
                        rentError = "Please enter a valid positive rent amount"
                        valid = false
                    }

                    if (valid) {
                        onAddRoom(name, rent)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
