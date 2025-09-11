package com.example.propertymanager.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues 
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
import androidx.compose.material.icons.outlined.ElectricBolt // ADDED
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
import androidx.compose.foundation.pager.HorizontalPager 
import androidx.compose.foundation.pager.rememberPagerState 
import androidx.compose.foundation.ExperimentalFoundationApi 

// Helper function to format a single bill for sharing
fun formatSingleBillForSharing(
    bill: MonthlyBillEntity,
    roomName: String,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    shortDateFormat: SimpleDateFormat
): String {
    val billPeriodCal = Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }
    val billPeriodText = monthYearFormat.format(billPeriodCal.time)
    val sb = StringBuilder()

    sb.appendLine("Bill Details for Room: $roomName")
    sb.appendLine("Period: $billPeriodText")
    sb.appendLine("------------------------------")
    sb.appendLine("Tenant: ${bill.tenantNameAtBillingTime}")
    sb.appendLine("Rent: ${currencyFormat.format(bill.rentAtBillingTime)}")

    if ((bill.electricityUnits ?: 0.0) > 0) {
        sb.appendLine("Electricity Units: ${String.format(Locale.US, "%.1f", bill.electricityUnits)} units")
        sb.appendLine("Elec. Rate: ${currencyFormat.format(bill.electricityRateAtBillingTime ?: 0.0)}/unit")
        sb.appendLine("Electricity Charges: ${currencyFormat.format(bill.electricityBill)}")
    }
    if ((bill.waterBill ?: 0.0) > 0) {
        sb.appendLine("Water Bill: ${currencyFormat.format(bill.waterBill)}")
    }
    if ((bill.otherCharges ?: 0.0) > 0) {
        sb.appendLine("Other Charges (${bill.otherChargesDescription ?: ""}): ${currencyFormat.format(bill.otherCharges)}")
    }
    if ((bill.previousMonthDues ?: 0.0) > 0) {
        sb.appendLine("Previous Dues: ${currencyFormat.format(bill.previousMonthDues)}")
    }
    sb.appendLine("------------------------------")
    sb.appendLine("Total Amount Due: ${currencyFormat.format(bill.totalAmountDue)}")
    sb.appendLine("Amount Paid: ${currencyFormat.format(bill.amountPaid)}")

    val balance = bill.totalAmountDue - bill.amountPaid
    if (bill.isFullyPaid) {
        sb.appendLine("Status: Fully Paid on ${bill.paymentDate?.let { shortDateFormat.format(Date(it)) } ?: "N/A"}")
    } else if (balance > 0.001) {
        sb.appendLine("Balance Due: ${currencyFormat.format(balance)}")
    } else if (balance < -0.001) {
        sb.appendLine("Advance Paid: ${currencyFormat.format(-balance)}")
    } else {
        sb.appendLine("Status: Settled")
    }

    if (bill.installments.isNotEmpty()) {
        sb.appendLine("------------------------------")
        sb.appendLine("Payment History:")
        bill.installments.forEach {
            sb.appendLine("- ${currencyFormat.format(it.amount)} on ${shortDateFormat.format(Date(it.date))}")
        }
    }
    sb.appendLine("------------------------------")
    bill.dueDate?.let {
        sb.appendLine("Due Date: ${shortDateFormat.format(Date(it))}")
    }
    return sb.toString()
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class) 
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

    // --- Pager State ---
    val pagerState = rememberPagerState(initialPage = 0) { // Page count for e.g. 20 years
        240 // 20 years * 12 months
    }
    // --- End Pager State ---

    val displayedBillDetails by roomViewModel.displayedBillDetails.collectAsState() 

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

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    val shortDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault())}

    LaunchedEffect(pagerState.settledPage, roomId) {
        if (currentRoomWithTenant != null) { 
            val targetCalendar = Calendar.getInstance()
            targetCalendar.add(Calendar.MONTH, -pagerState.settledPage)
            val year = targetCalendar.get(Calendar.YEAR)
            val month = targetCalendar.get(Calendar.MONTH) + 1
            roomViewModel.loadBillDetailsForMonth(roomId, year, month)
        }
    }

    LaunchedEffect(currentRoomWithTenant, showTenantInfoDialog, showBillHistoryDialog) {
        if (currentRoomWithTenant != null && (showTenantInfoDialog || showBillHistoryDialog) && allTenantsForRoom.isEmpty()) {
            allTenantsForRoom = roomViewModel.getAllTenantsForRoomFlow(roomId).firstOrNull() ?: emptyList()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            roomViewModel.clearDisplayedBillDetailsState() 
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
                .padding(horizontal = 16.dp) 
                .fillMaxWidth()
        ) {
            if (currentRoomWithTenant == null) {
                Text("Room not found.")
            } else {
                Text(
                    "Details for Room: ${currentRoomWithTenant.room.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // --- HORIZONTAL PAGER FOR BILLS ---
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp), 
                    pageSpacing = 8.dp 
                ) { pageIndex -> 
                    val billForThisPage = if (pagerState.settledPage == pageIndex) displayedBillDetails else null

                    CombinedBillActionsCard(
                        displayedBill = billForThisPage,
                        monthYearFormat = monthYearFormat,
                        currencyFormat = currencyFormat,
                        shortDateFormat = shortDateFormat,
                        onManageBillClick = { billToManage ->
                            if (billToManage.id != 0) {
                                billIdForDialog = billToManage.id
                                directBillObjectForDialog = null
                            } else {
                                directBillObjectForDialog = billToManage
                                billIdForDialog = null
                            }
                        },
                        onShareBillClick = { billToShare -> 
                            currentRoomWithTenant.room.name.let { roomName ->
                                textToShare = formatSingleBillForSharing(
                                    bill = billToShare,
                                    roomName = roomName,
                                    monthYearFormat = monthYearFormat,
                                    currencyFormat = currencyFormat,
                                    shortDateFormat = shortDateFormat
                                )
                            }
                        }
                    )
                }
                // --- END HORIZONTAL PAGER ---

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
            val calendar = Calendar.getInstance()
            val systemCurrentYear = calendar.get(Calendar.YEAR)
            val systemCurrentMonth = calendar.get(Calendar.MONTH) + 1

            AddEditBillDialog(
                bill = finalBillForDialog!!,
                roomViewModel = roomViewModel,
                roomName = currentRoomWithTenant.room.name,
                isCurrentActiveBill = finalBillForDialog!!.year == systemCurrentYear && finalBillForDialog!!.month == systemCurrentMonth,
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
    displayedBill: MonthlyBillEntity?,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    shortDateFormat: SimpleDateFormat,
    onManageBillClick: (MonthlyBillEntity) -> Unit,
    onShareBillClick: (MonthlyBillEntity) -> Unit 
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp), 
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row( 
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val titleText = if (displayedBill != null) {
                    val billCalendar = Calendar.getInstance().apply { set(displayedBill.year, displayedBill.month - 1, 1) }
                    "Bill for ${monthYearFormat.format(billCalendar.time)}"
                } else {
                    "Bill Status"
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f) 
                )
                if (displayedBill != null && displayedBill.id != 0) { 
                    IconButton(onClick = { onShareBillClick(displayedBill) }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Bill",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) 

            if (displayedBill != null) {
                val billPeriodCal = Calendar.getInstance().apply { set(displayedBill.year, displayedBill.month - 1, 1) }
                val billPeriodText = monthYearFormat.format(billPeriodCal.time)

                InfoRow(icon = Icons.Filled.History, label = "Period", value = billPeriodText)

                val isPlaceholderForItsMonth = displayedBill.id == 0 &&
                                               displayedBill.tenantNameAtBillingTime == "Not Occupied"

                if (isPlaceholderForItsMonth) {
                     InfoRow(icon = Icons.Filled.Info, label = "Status", value = "Not yet generated", valueColor = MaterialTheme.colorScheme.secondary)
                } else if (displayedBill.id != 0) { 
                    InfoRow(icon = Icons.Filled.Info, label = "Status", value = "Generated", valueColor = MaterialTheme.colorScheme.primary)
                    
                    if (displayedBill.electricityBill > 0.0) {
                        InfoRow(icon = Icons.Outlined.ElectricBolt, label = "Charges", value = currencyFormat.format(displayedBill.electricityBill)) // MODIFIED
                    }

                    InfoRow(icon = Icons.Filled.AttachMoney, label = "Total Due", value = currencyFormat.format(displayedBill.totalAmountDue))
                    InfoRow(icon = Icons.Filled.Payments, label = "Amount Paid", value = currencyFormat.format(displayedBill.amountPaid))
                    
                    val balanceDue = displayedBill.totalAmountDue - displayedBill.amountPaid
                    if (displayedBill.isFullyPaid) {
                        InfoRow(icon = Icons.Filled.CheckCircleOutline, label = "Payment", value = "Fully Paid on ${displayedBill.paymentDate?.let { shortDateFormat.format(Date(it)) } ?: "N/A"}", valueColor = Color(0xFF008000))
                    } else if (balanceDue > 0.001) { 
                        InfoRow(icon = Icons.Filled.ErrorOutline, label = "Balance Due", value = currencyFormat.format(balanceDue), valueColor = MaterialTheme.colorScheme.error)
                    } else if (balanceDue < -0.001) { 
                        InfoRow(icon = Icons.Filled.CheckCircleOutline, label = "Advance", value = currencyFormat.format(-balanceDue), valueColor = Color(0xFF38761D))
                    } else { 
                         InfoRow(icon = Icons.Filled.CheckCircleOutline, label = "Balance", value = "Settled", valueColor = Color(0xFF008000))
                    }

                    if (displayedBill.installments.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                        Text("Payment History:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        displayedBill.installments.forEach {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                                Text("${currencyFormat.format(it.amount)} on ${shortDateFormat.format(Date(it.date))}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                         HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                         Text("No payments recorded for this bill period.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp) )
                    }

                } else { 
                     Text("Status: Details for $billPeriodText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onManageBillClick(displayedBill) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Manage Bill Icon", modifier = Modifier.padding(end = 8.dp))
                    Text(if (displayedBill.id == 0) "Generate & Manage Bill" else "Manage This Bill", fontSize = 16.sp)
                }

            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Loading bill details for this month...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
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
