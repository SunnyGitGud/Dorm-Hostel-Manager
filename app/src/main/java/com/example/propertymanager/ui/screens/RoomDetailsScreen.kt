package com.example.propertymanager.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.utils.formatDate // Import formatDate
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

    var billIdForDialog by remember { mutableStateOf<Int?>(null) }
    val billToEditInDialog: MonthlyBillEntity? by key(billIdForDialog) {
        if (billIdForDialog != null) {
            // You will need to add getBillByIdFlow to your RoomViewModel and Repository
            roomViewModel.getBillByIdFlow(billIdForDialog!!).collectAsState(initial = null)
        } else {
            remember { mutableStateOf(null) }
        }
    }

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

    LaunchedEffect(currentRoomWithTenant, showTenantInfoDialog, showBillHistoryDialog) {
        if (currentRoomWithTenant != null && (showTenantInfoDialog || showBillHistoryDialog) && allTenantsForRoom.isEmpty()) {
            allTenantsForRoom = roomViewModel.getAllTenantsForRoomFlow(roomId).firstOrNull() ?: emptyList()
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
                                    billYear = year, // Corrected parameter name
                                    billMonth = month, // Corrected parameter name
                                    currentRoomFullRent = currentRoomWithTenant.room.rent // Corrected parameter name
                                )
                                if (billForCurrentMonth.id != 0 && billForCurrentMonth.year == year && billForCurrentMonth.month == month) {
                                    currentMonthEndReadingForDialog = billForCurrentMonth.monthEndMeterReading
                                } else {
                                    currentMonthEndReadingForDialog = null
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
                            val bill = roomViewModel.getOrCreateBillForRoom(
                                roomId = currentRoomWithTenant.room.id,
                                billYear = year, // Corrected parameter name
                                billMonth = month, // Corrected parameter name
                                currentRoomFullRent = currentRoomWithTenant.room.rent // Corrected parameter name
                            )
                            billIdForDialog = bill.id // Set the ID to trigger dialog display
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = "Manage Current Bill", modifier = Modifier.padding(end = 4.dp))
                    Text("Manage Current Month's Bill")
                }

                Spacer(modifier = Modifier.height(8.dp))

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

        if (billToEditInDialog != null && currentRoomWithTenant != null) {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val openedBillIsCurrentActive = billToEditInDialog!!.year == currentYear && billToEditInDialog!!.month == currentMonth

            AddEditBillDialog(
                bill = billToEditInDialog!!,
                roomViewModel = roomViewModel, // Added missing roomViewModel parameter
                roomName = currentRoomWithTenant.room.name,
                isCurrentActiveBill = openedBillIsCurrentActive,
                currentRoomInitialMeterReading = currentRoomWithTenant.room.initialMeterReading,
                currentRoomElectricityRate = currentRoomWithTenant.room.electricityRatePerUnit,
                fullRoomRent = currentRoomWithTenant.room.rent,
                onDismiss = {
                    billIdForDialog = null 
                },
                // onBillUpdated was removed from AddEditBillDialog
                onSave = {
                    coroutineScope.launch {
                        roomViewModel.saveBill(it)
                        billIdForDialog = null 
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
                    // Handle bill selection from history, e.g., open for editing or viewing details
                    billIdForDialog = bill.id // Example: Open AddEditBillDialog for the selected historical bill
                    showBillHistoryDialog = false // Dismiss history dialog
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
