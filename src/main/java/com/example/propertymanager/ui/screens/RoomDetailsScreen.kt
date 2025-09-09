package com.example.propertymanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// formatDate function removed from here, will use the one from RoomScreen.kt (same package)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailsScreen(
    roomId: Int,
    roomViewModel: RoomViewModel,
    navController: NavController
) {
    val roomsWithTenants by roomViewModel.roomsWithTenants.collectAsState()
    val currentRoomWithTenant = remember(roomsWithTenants, roomId) {
        roomsWithTenants.find { it.room.id == roomId }
    }

    var showAddEditBillDialog by remember { mutableStateOf(false) }
    var currentBillToEdit by remember { mutableStateOf<MonthlyBillEntity?>(null) }
    
    var showBillHistoryDialog by remember { mutableStateOf(false) }
    var billHistoryList by remember { mutableStateOf<List<MonthlyBillEntity>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentRoomWithTenant?.room?.name ?: "Room Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            if (currentRoomWithTenant == null) {
                Text("Room not found.")
            } else {
                Text("Details for Room: ${currentRoomWithTenant.room.name}", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val calendar = Calendar.getInstance()
                            val year = calendar.get(Calendar.YEAR)
                            val month = calendar.get(Calendar.MONTH) + 1
                            currentBillToEdit = roomViewModel.getOrCreateBillForRoom(
                                roomId = currentRoomWithTenant.room.id,
                                year = year,
                                month = month,
                                currentRoomRent = currentRoomWithTenant.room.rent
                            )
                            showAddEditBillDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = "Manage Current Bill", modifier = Modifier.padding(end = 4.dp))
                    Text("Manage Current Month\'s Bill")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            billHistoryList = roomViewModel.getAllBillsForRoom(roomId)
                            showBillHistoryDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.History, contentDescription = "View Bill History", modifier = Modifier.padding(end = 4.dp))
                    Text("View Bill History")
                }
            }
        }

        if (showAddEditBillDialog && currentBillToEdit != null && currentRoomWithTenant != null) {
            var billInDialog by remember(currentBillToEdit) { mutableStateOf(currentBillToEdit!!) }
            AddEditBillDialog(
                bill = billInDialog,
                roomName = currentRoomWithTenant.room.name,
                onDismiss = {
                    showAddEditBillDialog = false
                    currentBillToEdit = null
                },
                onBillUpdated = { updatedBill ->
                    billInDialog = updatedBill
                },
                onSave = {
                    coroutineScope.launch {
                        roomViewModel.saveBill(it)
                        showAddEditBillDialog = false
                        currentBillToEdit = null
                    }
                }
            )
        }

        if (showBillHistoryDialog && currentRoomWithTenant != null) {
            BillHistoryDialog(
                roomName = currentRoomWithTenant.room.name,
                bills = billHistoryList,
                onDismiss = { showBillHistoryDialog = false }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillDialog(
    bill: MonthlyBillEntity,
    roomName: String,
    onDismiss: () -> Unit,
    onBillUpdated: (MonthlyBillEntity) -> Unit,
    onSave: (MonthlyBillEntity) -> Unit
) {
    val currentBillState = remember(bill) { mutableStateOf(bill) }
    val isRoomOccupied = currentBillState.value.tenantNameAtBillingTime != "Not Occupied" && currentBillState.value.rentAtBillingTime > 0.0

    var electricityBill by remember(currentBillState.value.id, currentBillState.value.electricityBill) { mutableStateOf(currentBillState.value.electricityBill.takeIf { it > 0 }?.toString() ?: "") }
    var waterBill by remember(currentBillState.value.id, currentBillState.value.waterBill) { mutableStateOf(currentBillState.value.waterBill.takeIf { it > 0 }?.toString() ?: "") }
    var otherCharges by remember(currentBillState.value.id, currentBillState.value.otherCharges) { mutableStateOf(currentBillState.value.otherCharges.takeIf { it > 0 }?.toString() ?: "") }
    var otherChargesDescription by remember(currentBillState.value.id, currentBillState.value.otherChargesDescription) { mutableStateOf(currentBillState.value.otherChargesDescription ?: "") }

    var paymentAmountToRecord by remember { mutableStateOf("") }
    var paymentError by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(electricityBill, waterBill, otherCharges, otherChargesDescription, isRoomOccupied) {
        val elec = if (isRoomOccupied) electricityBill.toDoubleOrNull() ?: 0.0 else 0.0
        val water = if (isRoomOccupied) waterBill.toDoubleOrNull() ?: 0.0 else 0.0
        val other = if (isRoomOccupied) otherCharges.toDoubleOrNull() ?: 0.0 else 0.0
        val desc = if (isRoomOccupied) otherChargesDescription.ifBlank { null } else null

        val updatedBillFields = currentBillState.value.copy(
            electricityBill = elec,
            waterBill = water,
            otherCharges = other,
            otherChargesDescription = desc
        )
        updatedBillFields.calculateTotalDue()
        currentBillState.value = updatedBillFields
        onBillUpdated(updatedBillFields)
    }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val billPeriod = remember(currentBillState.value.year, currentBillState.value.month) {
         monthYearFormat.format(Calendar.getInstance().apply { set(currentBillState.value.year, currentBillState.value.month - 1, 1) }.time)
    }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "US")) }

    val amountDue = currentBillState.value.totalAmountDue
    val paid = currentBillState.value.amountPaid
    val remainingDue = remember(amountDue, paid) { max(0.0, amountDue - paid) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bill for $roomName - $billPeriod") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tenant: ${currentBillState.value.tenantNameAtBillingTime ?: "N/A"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text("Base Rent: ${currencyFormat.format(currentBillState.value.rentAtBillingTime)}", style = MaterialTheme.typography.bodyLarge)
                
                if (currentBillState.value.previousMonthDues > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Previous Month\'s Dues: ${currencyFormat.format(currentBillState.value.previousMonthDues)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = electricityBill, onValueChange = { electricityBill = it }, label = { Text("Electricity Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = waterBill, onValueChange = { waterBill = it }, label = { Text("Water Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = otherCharges, onValueChange = { otherCharges = it }, label = { Text("Other Charges") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = otherChargesDescription, onValueChange = { otherChargesDescription = it }, label = { Text("Description for Other Charges") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = {keyboardController?.hide()}), singleLine = false, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Total Due: ${currencyFormat.format(amountDue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Payment Status:", style = MaterialTheme.typography.titleSmall)
                Text("Amount Paid: ${currencyFormat.format(paid)}", style = MaterialTheme.typography.bodyMedium)

                if (currentBillState.value.isFullyPaid) {
                    Text("Status: Fully Paid on ${formatDate(currentBillState.value.paymentDate)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Status: ${currencyFormat.format(remainingDue)} remaining", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = paymentAmountToRecord,
                        onValueChange = { paymentAmountToRecord = it; paymentError = null },
                        label = { Text("Record Payment Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        isError = paymentError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = remainingDue > 0 // Only enable if there's something to pay
                    )
                    if (paymentError != null) { Text(paymentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    
                    Button(
                        onClick = {
                            val paymentValue = paymentAmountToRecord.toDoubleOrNull()
                            if (paymentValue == null || paymentValue <= 0) {
                                paymentError = "Enter a valid amount"
                            } else if (paymentValue > remainingDue + 0.001) { 
                                paymentError = "Cannot pay more than remaining due"
                            } else {
                                val newAmountPaid = paid + paymentValue
                                val isNowFullyPaid = newAmountPaid >= amountDue - 0.001 
                                val billWithPayment = currentBillState.value.copy(
                                    amountPaid = newAmountPaid,
                                    isFullyPaid = isNowFullyPaid,
                                    paymentDate = if (isNowFullyPaid) System.currentTimeMillis() else currentBillState.value.paymentDate
                                )
                                currentBillState.value = billWithPayment
                                onBillUpdated(billWithPayment)
                                paymentAmountToRecord = ""
                                paymentError = null
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = remainingDue > 0
                    ) {
                        Icon(Icons.Filled.Payment, contentDescription = "Record Payment", modifier = Modifier.padding(end = 4.dp))
                        Text("Record This Payment")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(currentBillState.value) }) { Text("Save Bill Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BillHistoryDialog(
    roomName: String,
    bills: List<MonthlyBillEntity>,
    onDismiss: () -> Unit
) {
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bill History for $roomName") },
        text = {
            if (bills.isEmpty()) {
                Text("No bill history found for this room.")
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(bills) { bill ->
                        BillHistoryItem(bill = bill, monthYearFormat = monthYearFormat)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BillHistoryItem(bill: MonthlyBillEntity, monthYearFormat: SimpleDateFormat) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "US")) }
    val billPeriod = remember(bill.year, bill.month) {
        monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
    }
    val remainingDue = remember(bill.totalAmountDue, bill.amountPaid) {
        max(0.0, bill.totalAmountDue - bill.amountPaid)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(billPeriod, style = MaterialTheme.typography.titleMedium)
            Text(
                "Billed to: ${bill.tenantNameAtBillingTime ?: "N/A"}", // Added tenant name
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Base Rent: ${currencyFormat.format(bill.rentAtBillingTime)}")
            if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") { // Show if > 0 or if room was not occupied (will be 0)
                 Text("Electricity: ${currencyFormat.format(bill.electricityBill)}")
            }
            if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                Text("Water: ${currencyFormat.format(bill.waterBill)}")
            }
            if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                Text("Other Charges: ${currencyFormat.format(bill.otherCharges)}$description")
            }
            if (bill.previousMonthDues > 0) {
                Text("Previous Dues Carried Over: ${currencyFormat.format(bill.previousMonthDues)}", color = MaterialTheme.colorScheme.error)
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Total Bill Amount: ${currencyFormat.format(bill.totalAmountDue)}", fontWeight = FontWeight.Bold)
            Text("Amount Paid: ${currencyFormat.format(bill.amountPaid)}")
            
            Spacer(modifier = Modifier.height(4.dp))
            if (bill.isFullyPaid) {
                Text("Status: Fully Paid", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("Paid on: ${formatDate(bill.paymentDate)}", style = MaterialTheme.typography.bodySmall)
            } else {
                if (remainingDue > 0) {
                    Text("Status: ${currencyFormat.format(remainingDue)} Due", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                } else { 
                    Text("Status: Partially Paid", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                if(bill.paymentDate != null && bill.paymentDate != 0L) {
                    Text("Last Payment: ${formatDate(bill.paymentDate)}", style = MaterialTheme.typography.bodySmall)
                } else if (bill.amountPaid > 0) { 
                     Text("Last Payment: Date N/A", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
