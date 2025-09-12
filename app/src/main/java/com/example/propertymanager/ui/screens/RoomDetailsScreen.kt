package com.example.propertymanager.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.propertymanager.R
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.utils.formatDate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

fun formatSingleBillForSharing(
    context: Context,
    bill: MonthlyBillEntity,
    roomName: String,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    shortDateFormat: SimpleDateFormat
): String {
    val billPeriodCal = Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }
    val billPeriodText = monthYearFormat.format(billPeriodCal.time)
    val sb = StringBuilder()
    val textNa = context.getString(R.string.text_na)

    sb.appendLine(context.getString(R.string.bill_share_details_for_room, roomName))
    sb.appendLine(context.getString(R.string.bill_share_period, billPeriodText))
    sb.appendLine("------------------------------")
    sb.appendLine(context.getString(R.string.bill_share_tenant, bill.tenantNameAtBillingTime ?: textNa))
    sb.appendLine(context.getString(R.string.bill_share_rent, currencyFormat.format(bill.rentAtBillingTime)))

    if ((bill.electricityUnits ?: 0.0) > 0) {
        sb.appendLine(context.getString(R.string.bill_share_electricity_units, bill.electricityUnits!!))
        sb.appendLine(context.getString(R.string.bill_share_electricity_rate, currencyFormat.format(bill.electricityRateAtBillingTime ?: 0.0)))
        sb.appendLine(context.getString(R.string.bill_share_electricity_charges, currencyFormat.format(bill.electricityBill)))
    }
    if ((bill.waterBill ?: 0.0) > 0) {
        sb.appendLine(context.getString(R.string.bill_share_water_bill, currencyFormat.format(bill.waterBill!!)))
    }
    if ((bill.otherCharges ?: 0.0) > 0) {
        sb.appendLine(context.getString(R.string.bill_share_other_charges, bill.otherChargesDescription ?: "", currencyFormat.format(bill.otherCharges!!)))
    }
    if ((bill.previousMonthDues ?: 0.0) > 0) {
        sb.appendLine(context.getString(R.string.bill_share_previous_dues, currencyFormat.format(bill.previousMonthDues!!)))
    }
    sb.appendLine("------------------------------")
    sb.appendLine(context.getString(R.string.bill_share_total_amount_due, currencyFormat.format(bill.totalAmountDue)))
    sb.appendLine(context.getString(R.string.bill_share_amount_paid, currencyFormat.format(bill.amountPaid)))

    val balance = bill.totalAmountDue - bill.amountPaid
    if (bill.isFullyPaid) {
        sb.appendLine(context.getString(R.string.bill_share_status_fully_paid, bill.paymentDate?.let { shortDateFormat.format(Date(it)) } ?: textNa))
    } else if (balance > 0.001) {
        sb.appendLine(context.getString(R.string.bill_share_balance_due, currencyFormat.format(balance)))
    } else if (balance < -0.001) {
        sb.appendLine(context.getString(R.string.bill_share_advance_paid, currencyFormat.format(-balance)))
    } else {
        sb.appendLine(context.getString(R.string.bill_share_status_settled))
    }

    if (bill.installments.isNotEmpty()) {
        sb.appendLine("------------------------------")
        sb.appendLine(context.getString(R.string.bill_share_payment_history))
        bill.installments.forEach {
            sb.appendLine(context.getString(R.string.bill_share_payment_history_item, currencyFormat.format(it.amount), shortDateFormat.format(Date(it.date))))
        }
    }
    sb.appendLine("------------------------------")
    bill.dueDate?.let {
        sb.appendLine(context.getString(R.string.bill_share_due_date, shortDateFormat.format(Date(it))))
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

    val pagerState = rememberPagerState(initialPage = 0) {
        240
    }

    val displayedBillDetails by roomViewModel.displayedBillDetails.collectAsState()

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
                title = { Text(currentRoomWithTenant?.room?.name ?: stringResource(R.string.room_details_screen_title_default)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
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
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.content_description_edit_room_details))
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
                Text(stringResource(R.string.room_details_not_found))
            } else {
                Text(
                    stringResource(R.string.room_details_title, currentRoomWithTenant.room.name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

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
                                    context = context,
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
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.content_description_view_bill_history), modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.button_view_bill_history))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        if (allTenantsForRoom.isEmpty() && currentRoomWithTenant != null) { // roomId is available directly
                             coroutineScope.launch {
                                allTenantsForRoom = roomViewModel.getAllTenantsForRoomFlow(roomId).firstOrNull() ?: emptyList()
                             }
                        }
                        showTenantInfoDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.content_description_tenant_info), modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.button_tenant_info))
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

        if (showTenantInfoDialog && currentRoomWithTenant != null) { // Pass roomId here
            TenantInfoDialog(
                roomId = roomId, // Pass the roomId from RoomDetailsScreen
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
                    stringResource(R.string.bill_card_title_for_month, monthYearFormat.format(billCalendar.time))
                } else {
                    stringResource(R.string.bill_card_title_status_default)
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
                            contentDescription = stringResource(R.string.content_description_share_bill),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (displayedBill != null) {
                val billPeriodCal = Calendar.getInstance().apply { set(displayedBill.year, displayedBill.month - 1, 1) }
                val billPeriodText = monthYearFormat.format(billPeriodCal.time)
                val unknownTenantText = stringResource(R.string.unknown_tenant)

                InfoRow(icon = Icons.Filled.History, label = stringResource(R.string.bill_card_label_period), value = billPeriodText)

                val isPlaceholderForItsMonth = displayedBill.id == 0 &&
                                               displayedBill.tenantNameAtBillingTime == unknownTenantText

                if (isPlaceholderForItsMonth) {
                     InfoRow(icon = Icons.Filled.Info, label = stringResource(R.string.status_label), value = stringResource(R.string.bill_card_status_not_yet_generated), valueColor = MaterialTheme.colorScheme.secondary)
                } else if (displayedBill.id != 0) {
                    InfoRow(icon = Icons.Filled.Info, label = stringResource(R.string.status_label), value = stringResource(R.string.bill_card_status_generated), valueColor = MaterialTheme.colorScheme.primary)

                    if (displayedBill.electricityBill > 0.0) {
                        InfoRow(icon = Icons.Outlined.ElectricBolt, label = stringResource(R.string.bill_card_label_charges), value = currencyFormat.format(displayedBill.electricityBill))
                    }

                    InfoRow(icon = Icons.Filled.AttachMoney, label = stringResource(R.string.bill_card_label_total_due), value = currencyFormat.format(displayedBill.totalAmountDue))
                    InfoRow(icon = Icons.Filled.Payments, label = stringResource(R.string.bill_card_label_amount_paid), value = currencyFormat.format(displayedBill.amountPaid))

                    val balanceDue = displayedBill.totalAmountDue - displayedBill.amountPaid
                    val textNa = stringResource(R.string.text_na)
                    if (displayedBill.isFullyPaid) {
                        InfoRow(icon = Icons.Filled.CheckCircleOutline, label = stringResource(R.string.bill_card_label_payment_status), value = stringResource(R.string.bill_card_value_fully_paid_on, displayedBill.paymentDate?.let { shortDateFormat.format(Date(it)) } ?: textNa), valueColor = Color(0xFF008000))
                    } else if (balanceDue > 0.001) {
                        InfoRow(icon = Icons.Filled.ErrorOutline, label = stringResource(R.string.bill_card_label_balance_due), value = currencyFormat.format(balanceDue), valueColor = MaterialTheme.colorScheme.error)
                    } else if (balanceDue < -0.001) {
                        InfoRow(icon = Icons.Filled.CheckCircleOutline, label = stringResource(R.string.bill_card_label_advance), value = currencyFormat.format(-balanceDue), valueColor = Color(0xFF38761D))
                    } else {
                         InfoRow(icon = Icons.Filled.CheckCircleOutline, label = stringResource(R.string.bill_card_label_balance), value = stringResource(R.string.bill_card_value_settled), valueColor = Color(0xFF008000))
                    }

                    if (displayedBill.installments.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                        Text(stringResource(R.string.bill_card_payment_history_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        displayedBill.installments.forEach {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.bill_card_payment_history_item, currencyFormat.format(it.amount), shortDateFormat.format(Date(it.date))), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                         HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                         Text(stringResource(R.string.bill_card_no_payments_recorded), style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp) )
                    }

                } else {
                     Text(stringResource(R.string.bill_card_status_details_for_period, billPeriodText), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onManageBillClick(displayedBill) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.content_description_manage_bill_icon), modifier = Modifier.padding(end = 8.dp))
                    Text(if (displayedBill.id == 0) stringResource(R.string.button_generate_manage_bill) else stringResource(R.string.button_manage_this_bill), fontSize = 16.sp)
                }

            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.bill_card_loading_details), style = MaterialTheme.typography.bodyMedium)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantInfoDialog(
    roomId: Int, // Added roomId parameter
    roomViewModel: RoomViewModel,
    roomName: String,
    currentTenant: TenantEntity?,
    allTenantsInRoom: List<TenantEntity>,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    // Initialize with the passed roomId if creating a new tenant
    var tenantToEdit by remember { mutableStateOf(currentTenant ?: TenantEntity(roomId = roomId, name = "", mobile = "", moveInDate = System.currentTimeMillis())) }
    var isEditing by remember { mutableStateOf(currentTenant == null) }

    val simpleDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val textNa = stringResource(R.string.text_na)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tenant_information_for_room, roomName)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isEditing) {
                    OutlinedTextField(
                        value = tenantToEdit.name,
                        onValueChange = { tenantToEdit = tenantToEdit.copy(name = it) },
                        label = { Text(stringResource(R.string.tenant_name_label)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tenantToEdit.mobile,
                        onValueChange = { tenantToEdit = tenantToEdit.copy(mobile = it) },
                        label = { Text(stringResource(R.string.contact_number_label)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    // Removed OutlinedTextField for ID Document as it's not in TenantEntity
                     Text(
                        text = stringResource(R.string.move_in_date_colon, tenantToEdit.moveInDate?.let { simpleDateFormat.format(Date(it)) } ?: textNa),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (tenantToEdit.moveOutDate != null) {
                        Text(
                            text = stringResource(R.string.move_out_date_colon, simpleDateFormat.format(Date(tenantToEdit.moveOutDate!!))),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                } else {
                    currentTenant?.let {
                        Text(stringResource(R.string.tenant_name_colon, it.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                        Text(stringResource(R.string.contact_colon, it.mobile.ifBlank { textNa }), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                        // Removed Text for ID Document as it's not in TenantEntity
                        Text(stringResource(R.string.move_in_date_colon, it.moveInDate?.let { date -> simpleDateFormat.format(Date(date)) } ?: textNa), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                        it.moveOutDate?.let { date ->
                            Text(stringResource(R.string.move_out_date_colon, simpleDateFormat.format(Date(date))), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    } ?: Text(stringResource(R.string.no_current_tenant_assigned))
                }

                if (!isEditing && currentTenant != null && currentTenant.moveOutDate == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Ensure roomId is from the currentTenant when marking as moved out
                            tenantToEdit = currentTenant.copy(moveOutDate = System.currentTimeMillis())
                            coroutineScope.launch {
                                roomViewModel.updateTenant(tenantToEdit)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.mark_as_moved_out_button))
                    }
                }

                val pastTenants = allTenantsInRoom.filter { it.id != currentTenant?.id && it.moveOutDate != null }
                if (pastTenants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.past_tenants_title), style = MaterialTheme.typography.titleMedium)
                    pastTenants.forEach { pastTenant ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.tenant_name_colon, pastTenant.name), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.contact_colon, pastTenant.mobile.ifBlank { textNa }), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.moved_in_colon, pastTenant.moveInDate?.let { simpleDateFormat.format(Date(it)) } ?: textNa), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.moved_out_colon, pastTenant.moveOutDate?.let { simpleDateFormat.format(Date(it)) } ?: textNa), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val tenantToSave = tenantToEdit.copy(roomId = if (tenantToEdit.id == 0) roomId else tenantToEdit.roomId)
                        val moveInDateValue = tenantToSave.moveInDate // Will be non-null for new or existing
                        if (moveInDateValue != null) { // Ensure moveInDate is not null before calling
                            if (tenantToSave.id != 0) {
                                // Updating existing tenant
                                roomViewModel.addOrUpdateTenantDetails(
                                    roomId = tenantToSave.roomId,
                                    name = tenantToSave.name,
                                    mobile = tenantToSave.mobile,
                                    moveInDate = moveInDateValue,
                                    existingTenantId = tenantToSave.id
                                )
                            } else {
                                // Adding new tenant
                                roomViewModel.addOrUpdateTenantDetails(
                                    roomId = tenantToSave.roomId,
                                    name = tenantToSave.name,
                                    mobile = tenantToSave.mobile,
                                    moveInDate = moveInDateValue,
                                    existingTenantId = null
                                )
                            }
                        }
                        onDismiss()
                    }
                }) {
                    Text(stringResource(R.string.save_button))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close_button))
                }
            }
        },
        dismissButton = {
            if (isEditing && currentTenant != null) {
                TextButton(onClick = {
                    isEditing = false
                    tenantToEdit = currentTenant // Reset changes
                }) {
                    Text(stringResource(R.string.cancel_button))
                }
            } else if (!isEditing && currentTenant == null) {
                 TextButton(onClick = { 
                    // Ensure new tenant is initialized with the correct roomId from the dialog's parameters
                    tenantToEdit = TenantEntity(roomId = roomId, name = "", mobile = "", moveInDate = System.currentTimeMillis())
                    isEditing = true
                 }) {
                    Text(stringResource(R.string.add_tenant_button))
                }
            } else { // Default dismiss for other cases (e.g., viewing tenant, or editing and then cancelling)
                 TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel_button))
                 }
            }
        }
    )
}
