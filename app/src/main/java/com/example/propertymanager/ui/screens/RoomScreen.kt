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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
// import androidx.compose.material.icons.filled.Edit // No longer solely for Edit Tenant
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
// import androidx.compose.material3.IconButton // IconButton for delete will be removed
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.utils.formatDate // Corrected import

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
                        onAddEditTenant = { // This will be used for "Add Tenant" or if dialog is called for editing from elsewhere
                            selectedRoomForTenantAction = it
                            showAddEditTenantDialog = true
                        },
                        onRemoveTenant = { roomWithTenantToRemove -> // This is for "Record Move-Out"
                            tenantPendingRemoval = roomWithTenantToRemove
                            showConfirmRemoveTenantDialog = true
                        },
                        onViewDetails = { pId, rId ->
                            navController.navigate("room_details/${roomWithTenant.room.propertyId}/$rId")
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
                onAddRoom = { name, rentString, electricityRateString, initialMeterReadingString ->
                    val rent = rentString.toDoubleOrNull()
                    val electricityRate = electricityRateString.toDoubleOrNull()
                    val initialMeterReading = initialMeterReadingString.toDoubleOrNull()
                    
                    if (rent != null && electricityRate != null) { 
                        roomViewModel.addRoom(name, rent, electricityRate, initialMeterReading)
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
                onConfirm = { roomId, name, mobile, moveInDate, existingTenantId ->
                    roomViewModel.addOrUpdateTenantDetails(roomId, name, mobile, moveInDate, existingTenantId)
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
                        roomViewModel.recordTenantMoveOutDate(tenantIdToRemove, System.currentTimeMillis())
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
            Text(
                text = "Elec. Rate: $${String.format("%.2f", roomWithTenant.room.electricityRatePerUnit)}/unit", 
                style = MaterialTheme.typography.bodySmall
            )
            roomWithTenant.room.initialMeterReading?.let {
                Text(
                    text = "Initial Reading: $it units on ${formatDate(roomWithTenant.room.initialMeterReadingDate)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Tenant Info:", style = MaterialTheme.typography.titleSmall)
            if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate == null) { // Active tenant
                Text("Name: ${roomWithTenant.tenant.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Mobile: ${roomWithTenant.tenant.mobile}", style = MaterialTheme.typography.bodyMedium)
                Text("Move-in: ${formatDate(roomWithTenant.tenant.moveInDate)}", style = MaterialTheme.typography.bodyMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { onRemoveTenant(roomWithTenant) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Move Out", modifier = Modifier.padding(end = 4.dp)) // Updated contentDescription
                    Text("Move Out") // Updated Text
                }

            } else if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate != null) { // Tenant has moved out
                Text("Name: ${roomWithTenant.tenant.name} (Moved Out)", style = MaterialTheme.typography.bodyMedium)
                Text("Move-out: ${formatDate(roomWithTenant.tenant.moveOutDate)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onAddEditTenant(roomWithTenant) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add New Tenant", modifier = Modifier.padding(end = 4.dp))
                    Text("Add New Tenant")
                }
            } else { // No tenant ever assigned or previous tenant record cleared
                Text("No tenant assigned.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onAddEditTenant(roomWithTenant) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Tenant", modifier = Modifier.padding(end = 4.dp))
                    Text("Add Tenant")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onViewDetails(roomWithTenant.room.propertyId, roomWithTenant.room.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "View Details", modifier = Modifier.padding(end = 4.dp))
                Text("View Details / Manage Bills")
            }
        }
    }
}

@Composable
fun ConfirmRemoveTenantDialog(
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
    onConfirm: (roomId: Int, name: String, mobile: String, moveInDate: Long, existingTenantId: Int?) -> Unit
) {
    var name by remember(roomWithTenant.tenant) { mutableStateOf(roomWithTenant.tenant?.name ?: "") }
    var mobile by remember(roomWithTenant.tenant) { mutableStateOf(roomWithTenant.tenant?.mobile ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }

    val dialogTitle = if (roomWithTenant.tenant == null || roomWithTenant.tenant.moveOutDate != null) {
        "Add New Tenant"
    } else {
        "Edit Tenant Details"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Tenant Name") },
                    isError = nameError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it; mobileError = null },
                    label = { Text("Tenant Mobile") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = mobileError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mobileError != null) {
                    Text(mobileError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    if (mobile.isBlank()) {
                        mobileError = "Mobile cannot be empty"
                        valid = false
                    }

                    if (valid) {
                        val existingTenantIdToPass = if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate == null) {
                            roomWithTenant.tenant.id
                        } else {
                            null
                        }
                        
                        val moveInDateToUse = if (existingTenantIdToPass == null) {
                            System.currentTimeMillis()
                        } else {
                            roomWithTenant.tenant!!.moveInDate
                        }

                        onConfirm(roomWithTenant.room.id, name, mobile, moveInDateToUse, existingTenantIdToPass)
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
    onAddRoom: (name: String, rent: String, electricityRate: String, initialMeterReading: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") }
    var electricityRateString by remember { mutableStateOf("10.0") } 
    var initialMeterReadingString by remember { mutableStateOf("") } 

    var nameError by remember { mutableStateOf<String?>(null) }
    var rentError by remember { mutableStateOf<String?>(null) }
    var electricityRateError by remember { mutableStateOf<String?>(null) }
    var initialMeterReadingError by remember { mutableStateOf<String?>(null) }

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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rent,
                    onValueChange = { rent = it; rentError = null },
                    label = { Text("Monthly Rent (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = rentError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (rentError != null) {
                    Text(rentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = electricityRateString,
                    onValueChange = { electricityRateString = it; electricityRateError = null },
                    label = { Text("Electricity Rate per Unit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = electricityRateError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (electricityRateError != null) {
                    Text(electricityRateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField( 
                    value = initialMeterReadingString,
                    onValueChange = { initialMeterReadingString = it; initialMeterReadingError = null },
                    label = { Text("Initial Meter Reading (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = initialMeterReadingError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (initialMeterReadingError != null) {
                    Text(initialMeterReadingError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

                    val electricityRateDouble = electricityRateString.toDoubleOrNull()
                    if (electricityRateString.isBlank()) {
                        electricityRateError = "Rate cannot be empty"
                        valid = false
                    } else if (electricityRateDouble == null || electricityRateDouble < 0) {
                        electricityRateError = "Please enter a valid rate (e.g., 10.0)"
                        valid = false
                    }

                    val initialMeterReadingDouble = initialMeterReadingString.toDoubleOrNull()
                    if (initialMeterReadingString.isNotBlank() && (initialMeterReadingDouble == null || initialMeterReadingDouble < 0)) {
                        initialMeterReadingError = "Please enter a valid non-negative reading or leave blank"
                        valid = false
                    }

                    if (valid) {
                        onAddRoom(name, rent, electricityRateString, initialMeterReadingString)
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
