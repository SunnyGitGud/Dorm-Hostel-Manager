package com.example.propertymanager.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.utils.formatDate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max


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

    // For AddEditBillDialog invocation
    var billIdForDialog by remember { mutableStateOf<Int?>(null) }
    var directBillObjectForDialog by remember { mutableStateOf<MonthlyBillEntity?>(null) }
    val billToEditInDialogViaId: MonthlyBillEntity? by key(billIdForDialog) {
        if (billIdForDialog != null) {
            roomViewModel.getBillByIdFlow(billIdForDialog!!).collectAsState(initial = null)
        } else {
            remember { mutableStateOf(null) }
        }
    }
    val finalBillForDialog = billToEditInDialogViaId ?: directBillObjectForDialog

    var showBillHistoryDialog by remember { mutableStateOf(false) }
    var billHistoryList by remember { mutableStateOf<List<MonthlyBillEntity>>(emptyList()) }
    var allTenantsForRoom by remember { mutableStateOf<List<TenantEntity>>(emptyList()) }
    var filterYear by remember { mutableStateOf("") }
    var selectedTenantIdFilter by remember { mutableStateOf<Int?>(null) }
    var showTenantFilterDropdown by remember { mutableStateOf(false) }

    var showTenantInfoDialog by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var currentMonthEndReadingForDialog by remember { mutableStateOf<Double?>(null) }

    var textToShare by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentCalendarMonthBill by roomViewModel.currentCalendarMonthBill.collectAsState()
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    val shortDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault())}

    LaunchedEffect(roomId) {
        roomViewModel.loadCurrentCalendarMonthBill(roomId)
    }

    LaunchedEffect(currentRoomWithTenant, showTenantInfoDialog, showBillHistoryDialog) {
        if (currentRoomWithTenant != null && (showTenantInfoDialog || showBillHistoryDialog) && allTenantsForRoom.isEmpty()) {
            allTenantsForRoom = roomViewModel.getAllTenantsForRoomFlow(roomId).firstOrNull() ?: emptyList()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            roomViewModel.clearCurrentCalendarMonthBillState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentRoomWithTenant?.room?.name ?: "Room Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    currentRoomWithTenant?.room?.let {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val calendar = Calendar.getInstance()
                                val year = calendar.get(Calendar.YEAR)
                                val month = calendar.get(Calendar.MONTH) + 1
                                val billForCurrentMonth = roomViewModel.getOrCreateBillForRoom(
                                    roomId = currentRoomWithTenant.room.id,
                                    billYear = year,
                                    billMonth = month,
                                    currentRoomFullRent = currentRoomWithTenant.room.rent
                                )
                                currentMonthEndReadingForDialog = if (billForCurrentMonth.id != 0 && billForCurrentMonth.year == year && billForCurrentMonth.month == month) {
                                    billForCurrentMonth.monthEndMeterReading
                                } else {
                                    null
                                }
                                showEditRoomDialog = true
                            }
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Room Details")
                        }
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
                .verticalScroll(rememberScrollState())
        ) {
            if (currentRoomWithTenant == null) {
                Text("Room not found.")
            } else {
                Text("Details for Room: ${currentRoomWithTenant.room.name}", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                CombinedBillActionsCard(
                    currentMonthBill = currentCalendarMonthBill,
                    monthYearFormat = monthYearFormat,
                    currencyFormat = currencyFormat,
                    shortDateFormat = shortDateFormat,
                    onManageCurrentMonthBillClick = {
                        currentCalendarMonthBill?.let { bill ->
                            if (bill.id != 0) {
                                billIdForDialog = bill.id
                                directBillObjectForDialog = null
                            } else {
                                directBillObjectForDialog = bill
                                billIdForDialog = null
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            billHistoryList = roomViewModel.getAllBillsForRoom(roomId)
                            if (allTenantsForRoom.isEmpty()) {
                                allTenantsForRoom = roomViewModel.getAllTenantsForRoomFlow(roomId).firstOrNull() ?: emptyList()
                            }
                            filterYear = ""
                            selectedTenantIdFilter = null
                            showBillHistoryDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.History, contentDescription = "View Bill History", modifier = Modifier.padding(end = 4.dp))
                    Text("View Bill History")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        if (allTenantsForRoom.isEmpty() && currentRoomWithTenant != null) {
                             coroutineScope.launch {
                                allTenantsForRoom = roomViewModel.getAllTenantsForRoomFlow(roomId).firstOrNull() ?: emptyList()
                             }
                        }
                        showTenantInfoDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Tenant Info", modifier = Modifier.padding(end = 4.dp))
                    Text("Tenant Info")
                }
            }
        }

        if (finalBillForDialog != null && currentRoomWithTenant != null) {
            AddEditBillDialog(
                bill = finalBillForDialog!!,
                roomViewModel = roomViewModel,
                roomName = currentRoomWithTenant.room.name,
                isCurrentActiveBill = currentCalendarMonthBill?.let {
                    it.year == finalBillForDialog!!.year && it.month == finalBillForDialog!!.month
                } ?: false,
                currentRoomInitialMeterReading = currentRoomWithTenant.room.initialMeterReading,
                currentRoomElectricityRate = currentRoomWithTenant.room.electricityRatePerUnit,
                fullRoomRent = currentRoomWithTenant.room.rent,
                onDismiss = {
                    billIdForDialog = null
                    directBillObjectForDialog = null
                },
                onSave = {
                    coroutineScope.launch {
                        roomViewModel.saveBill(it)
                        billIdForDialog = null
                        directBillObjectForDialog = null
                    }
                }
            )
        }

        if (textToShare.isNotBlank()) {
            ShareBillHistoryAction(historyText = textToShare)
            LaunchedEffect(textToShare) {
                if (textToShare.isNotBlank()) {
                    textToShare = ""
                }
            }
        }

        if (showBillHistoryDialog && currentRoomWithTenant != null) {
            BillHistoryDialog(
                roomName = currentRoomWithTenant.room.name,
                bills = billHistoryList,
                allTenants = allTenantsForRoom,
                filterYear = filterYear,
                onFilterYearChange = { filterYear = it },
                selectedTenantId = selectedTenantIdFilter,
                onTenantSelected = { selectedTenantIdFilter = it },
                showTenantFilterDropdown = showTenantFilterDropdown,
                onToggleTenantFilterDropdown = { showTenantFilterDropdown = !showTenantFilterDropdown },
                onDismiss = { showBillHistoryDialog = false },
                onClearFilters = {
                    filterYear = ""
                    selectedTenantIdFilter = null
                },
                onShareClicked = { formattedText ->
                    textToShare = formattedText
                },
                onBillSelected = { bill ->
                    if (bill.id != 0) {
                        billIdForDialog = bill.id
                        directBillObjectForDialog = null
                    } else {
                        directBillObjectForDialog = bill
                        billIdForDialog = null
                    }
                    showBillHistoryDialog = false
                }
            )
        }

        if (showTenantInfoDialog && currentRoomWithTenant != null) {
            TenantInfoDialog(
                roomViewModel = roomViewModel,
                roomName = currentRoomWithTenant.room.name,
                currentTenant = currentRoomWithTenant.tenant,
                allTenantsInRoom = allTenantsForRoom,
                onDismiss = { showTenantInfoDialog = false }
            )
        }

        if (showEditRoomDialog && currentRoomWithTenant?.room != null) {
            EditRoomDetailsDialog(
                room = currentRoomWithTenant.room,
                currentMonthEndReadingForDisplay = currentMonthEndReadingForDialog, 
                onDismiss = { showEditRoomDialog = false },
                onSave = { updatedRoom ->
                    roomViewModel.updateRoomDetails(updatedRoom)
                    showEditRoomDialog = false
                }
            )
        }
    }
}

@Composable
fun CombinedBillActionsCard(
    currentMonthBill: MonthlyBillEntity?,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    shortDateFormat: SimpleDateFormat, // Added for installment dates
    onManageCurrentMonthBillClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current Month Bill Status", 
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 8.dp) // Adjusted padding
                    .align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp)) // Added Divider and its padding

            if (currentMonthBill != null) {
                val currentCal = Calendar.getInstance()
                val billPeriod = monthYearFormat.format(Calendar.getInstance().apply { set(currentMonthBill.year, currentMonthBill.month - 1, 1) }.time)
                
                InfoRow(icon = Icons.Filled.History, label = "Period", value = billPeriod)

                if (currentMonthBill.id == 0 && 
                    currentMonthBill.year == currentCal.get(Calendar.YEAR) && 
                    currentMonthBill.month == currentCal.get(Calendar.MONTH) +1) {
                    InfoRow(icon = Icons.Filled.Info, label = "Status", value = "Not yet generated", valueColor = MaterialTheme.colorScheme.secondary)
                } else if (currentMonthBill.id != 0) { // Bill is generated
                    InfoRow(icon = Icons.Filled.Info, label = "Status", value = "Generated", valueColor = MaterialTheme.colorScheme.primary)
                    InfoRow(icon = Icons.Filled.AttachMoney, label = "Total Due", value = currencyFormat.format(currentMonthBill.totalAmountDue))
                    InfoRow(icon = Icons.Filled.Payments, label = "Amount Paid", value = currencyFormat.format(currentMonthBill.amountPaid))
                    
                    val balanceDue = currentMonthBill.totalAmountDue - currentMonthBill.amountPaid
                    if (currentMonthBill.isFullyPaid) {
                        InfoRow(icon = Icons.Filled.CheckCircleOutline, label = "Payment", value = "Fully Paid on ${currentMonthBill.paymentDate?.let { shortDateFormat.format(Date(it)) } ?: "N/A"}", valueColor = Color(0xFF008000) /* Green */)
                    } else if (balanceDue > 0) {
                        InfoRow(icon = Icons.Filled.ErrorOutline, label = "Balance Due", value = currencyFormat.format(balanceDue), valueColor = MaterialTheme.colorScheme.error)
                    } else {
                         InfoRow(icon = Icons.Filled.CheckCircleOutline, label = "Balance", value = "Settled", valueColor = Color(0xFF008000) /* Green */)
                    }

                    // Payment History Section
                    if (currentMonthBill.installments.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                        Text("Payment History:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        currentMonthBill.installments.forEach {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                                Text("${currencyFormat.format(it.amount)} on ${shortDateFormat.format(Date(it.date))}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                         HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                         Text("No payments recorded for this month.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp) )
                    }

                } else {
                     Text("Status: (Data for a different period: $billPeriod)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onManageCurrentMonthBillClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Manage Bill Icon", modifier = Modifier.padding(end = 8.dp))
                    Text(if (currentMonthBill.id == 0) "Generate & Manage Bill" else "Manage This Bill", fontSize = 16.sp)
                }

            } else {
                Text("Current month's bill status is loading or unavailable.", modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row( // Outer Row for Icon and Text content
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp) 
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp)) 

        // Inner Row for label and value, to use weights for alignment
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically 
        ) {
            Text(
                text = "$label:", 
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(0.4f) 
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else valueColor,
                modifier = Modifier.weight(0.6f) 
            )
        }
    }
}
