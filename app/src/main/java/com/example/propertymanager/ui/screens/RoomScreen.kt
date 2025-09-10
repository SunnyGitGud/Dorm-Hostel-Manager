package com.example.propertymanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.ui.viewmodel.BillStatusSummary // Import the data class
import com.example.propertymanager.utils.formatDate
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(propertyId: Int, roomViewModel: RoomViewModel, navController: NavController) {
    val roomsWithTenants by roomViewModel.roomsWithTenants.collectAsState()
    val roomBillSummaries by roomViewModel.roomBillSummaries.collectAsState()
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (roomsWithTenants.isEmpty()) {
                item {
                    Text(
                        "No rooms found. Click the '+' button to add one.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(roomsWithTenants, key = { it.room.id }) { roomWithTenant ->
                    RoomItem(
                        roomWithTenant = roomWithTenant,
                        billStatusSummary = roomBillSummaries[roomWithTenant.room.id],
                        onAddEditTenant = { 
                            selectedRoomForTenantAction = it
                            showAddEditTenantDialog = true
                        },
                        onRemoveTenant = { roomWithTenantToRemove ->
                            tenantPendingRemoval = roomWithTenantToRemove
                            showConfirmRemoveTenantDialog = true
                        },
                        onViewDetails = { _, rId ->
                            navController.navigate("room_details/${roomWithTenant.room.propertyId}/$rId")
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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

@OptIn(ExperimentalMaterial3Api::class) // Added for Card onClick
@Composable
fun RoomItem(
    roomWithTenant: RoomWithTenant,
    billStatusSummary: BillStatusSummary?,
    onAddEditTenant: (RoomWithTenant) -> Unit,
    onRemoveTenant: (RoomWithTenant) -> Unit,
    onViewDetails: (propertyId: Int, roomId: Int) -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = { isExpanded = !isExpanded } // Make card clickable
    ) {
        Box(modifier = Modifier.fillMaxWidth()) { 
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Room: ${roomWithTenant.room.name}", 
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f) // Allow text to take available space
                    )
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Rent: ${currencyFormat.format(roomWithTenant.room.rent)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Elec. Rate: ${currencyFormat.format(roomWithTenant.room.electricityRatePerUnit)}/unit", 
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
                        if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate == null) { 
                            Text("Name: ${roomWithTenant.tenant.name}", style = MaterialTheme.typography.bodyMedium)
                            Text("Mobile: ${roomWithTenant.tenant.mobile}", style = MaterialTheme.typography.bodyMedium)
                            Text("Move-in: ${formatDate(roomWithTenant.tenant.moveInDate)}", style = MaterialTheme.typography.bodyMedium)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { onRemoveTenant(roomWithTenant) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Move Out", modifier = Modifier.padding(end = 4.dp))
                                Text("Move Out")
                            }

                        } else if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate != null) {
                            Text("Name: ${roomWithTenant.tenant.name} (Moved Out)", style = MaterialTheme.typography.bodyMedium)
                            Text("Move-out: ${formatDate(roomWithTenant.tenant.moveOutDate)}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onAddEditTenant(roomWithTenant) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add New Tenant", modifier = Modifier.padding(end = 4.dp))
                                Text("Add New Tenant")
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
                            onClick = { onViewDetails(roomWithTenant.room.propertyId, roomWithTenant.room.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = "View Details", modifier = Modifier.padding(end = 4.dp))
                            Text("View Details / Manage Bills")
                        }
                    }
                }
            }

            // Bill Status Badge (Due/Advance) - Stays in the Box, aligned TopEnd
            if (billStatusSummary != null && (billStatusSummary.isDue || billStatusSummary.isAdvance)) {
                val statusText = currencyFormat.format(billStatusSummary.displayAmount)
                val backgroundColor = if (billStatusSummary.isDue) Color(0xFFB00020) else Color(0xFF00C853)
                val textColor = Color.White

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp) // Adjusted padding to not overlap icon too much
                        .clip(RoundedCornerShape(4.dp))
                        .background(backgroundColor)
                        .padding(horizontal = 6.dp, vertical = 3.dp) 
                ) {
                    Text(
                        text = if (billStatusSummary.isDue) "Due: $statusText" else "Adv: $statusText",
                        color = textColor,
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
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
                val currentNameError = nameError
                if (currentNameError != null) {
                    Text(currentNameError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                val currentMobileError = mobileError
                if (currentMobileError != null) {
                    Text(currentMobileError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                val currentNameError = nameError 
                if (currentNameError != null) {
                    Text(currentNameError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                val currentRentError = rentError 
                if (currentRentError != null) {
                    Text(currentRentError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                val currentElectricityRateError = electricityRateError
                if (currentElectricityRateError != null) {
                    Text(currentElectricityRateError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                val currentInitialMeterReadingError = initialMeterReadingError 
                if (currentInitialMeterReadingError != null) {
                    Text(currentInitialMeterReadingError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
