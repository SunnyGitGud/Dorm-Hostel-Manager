package com.example.propertymanager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size // Added for icon sizing
import androidx.compose.foundation.layout.width // Added for spacing between icon and text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call // ADDED for Call button
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CurrencyRupee // Added for Rent icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack // ADDED for back button
import androidx.compose.material.icons.outlined.Bolt // Added for Electricity Rate icon
import androidx.compose.material.icons.outlined.CalendarToday // Added for Date icon
import androidx.compose.material.icons.outlined.Person // Added for Tenant Name icon
import androidx.compose.material.icons.outlined.PersonOff // Added for No Tenant icon
import androidx.compose.material.icons.outlined.Phone // Added for Tenant Mobile icon
import androidx.compose.material.icons.outlined.Speed // Added for Meter Reading icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults // ADDED for Call button icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton // ADDED for back button
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext // ADDED for Intent
import androidx.compose.ui.res.stringResource
import com.example.propertymanager.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.ui.viewmodel.BillStatusSummary
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

    var selectedRoomForOptions by remember { mutableStateOf<RoomWithTenant?>(null) }
    var showRoomOptionsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rooms_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRoomDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_add_room_content_description))
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
                        stringResource(R.string.rooms_screen_no_rooms_found),
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
                        },
                        onLongClick = {
                            selectedRoomForOptions = roomWithTenant
                            showRoomOptionsDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                tenantName = tenantPendingRemoval!!.tenant?.name ?: stringResource(R.string.unknown_tenant),
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

        if (showRoomOptionsDialog && selectedRoomForOptions != null) {
            RoomOptionsDialog(
                roomWithTenant = selectedRoomForOptions!!,
                roomViewModel = roomViewModel,
                onDismiss = {
                    showRoomOptionsDialog = false
                    selectedRoomForOptions = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomItem(
    roomWithTenant: RoomWithTenant,
    billStatusSummary: BillStatusSummary?,
    onAddEditTenant: (RoomWithTenant) -> Unit,
    onRemoveTenant: (RoomWithTenant) -> Unit,
    onViewDetails: (propertyId: Int, roomId: Int) -> Unit,
    onLongClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current // Context for launching intent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { isExpanded = !isExpanded }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.room_item_title, roomWithTenant.room.name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (billStatusSummary != null && (billStatusSummary.isDue || billStatusSummary.isAdvance)) {
                    val statusText = currencyFormat.format(billStatusSummary.displayAmount)
                    val backgroundColor = if (billStatusSummary.isDue) MaterialTheme.colorScheme.errorContainer
                                          else MaterialTheme.colorScheme.secondaryContainer
                    val textColor = if (billStatusSummary.isDue) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(backgroundColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (billStatusSummary.isDue) stringResource(R.string.room_item_status_due, statusText) else stringResource(R.string.room_item_status_advance, statusText),
                            color = textColor,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Spacer(Modifier.height(MaterialTheme.typography.labelLarge.lineHeight.value.dp)) // Placeholder to match badge height
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CurrencyRupee, contentDescription = stringResource(R.string.content_description_rent), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.room_item_rent_details, currencyFormat.format(roomWithTenant.room.rent)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bolt, contentDescription = stringResource(R.string.content_description_electricity_rate), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.room_item_electricity_rate_details, currencyFormat.format(roomWithTenant.room.electricityRatePerUnit)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    roomWithTenant.room.initialMeterReading?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Speed, contentDescription = stringResource(R.string.content_description_initial_meter_reading), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.room_item_initial_reading_details, it.toString(), formatDate(roomWithTenant.room.initialMeterReadingDate)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.room_item_tenant_info_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = stringResource(R.string.content_description_tenant_name), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.room_item_tenant_name_details, roomWithTenant.tenant.name), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Phone, contentDescription = stringResource(R.string.content_description_tenant_mobile), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.room_item_tenant_mobile_details, roomWithTenant.tenant.mobile), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = stringResource(R.string.content_description_move_in_date), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.room_item_move_in_date_details, formatDate(roomWithTenant.tenant.moveInDate)), style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // HORIZONTAL BUTTONS ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRemoveTenant(roomWithTenant) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.content_description_move_out), modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.button_move_out))
                            }

                            val tenantMobile = roomWithTenant.tenant.mobile
                            if (tenantMobile.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tenantMobile"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.Call,
                                        contentDescription = stringResource(R.string.content_description_call_tenant),
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.button_call))
                                }
                            }
                        }

                    } else if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = stringResource(R.string.content_description_tenant_name), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.room_item_tenant_name_moved_out_details, roomWithTenant.tenant.name), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = stringResource(R.string.content_description_move_out_date), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.room_item_move_out_date_details, formatDate(roomWithTenant.tenant.moveOutDate)), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onAddEditTenant(roomWithTenant) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.content_description_add_new_tenant), modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.button_add_new_tenant))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PersonOff, contentDescription = stringResource(R.string.content_description_no_tenant), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.room_item_no_tenant_assigned), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onAddEditTenant(roomWithTenant) }, modifier = Modifier.fillMaxWidth()) {
                             Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.content_description_add_tenant), modifier = Modifier.size(ButtonDefaults.IconSize))
                             Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.button_add_tenant))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { onViewDetails(roomWithTenant.room.propertyId, roomWithTenant.room.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = stringResource(R.string.content_description_view_room_details), modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.button_view_details_manage_bills))
                    }
                }
            }
        }
    }
}

@Composable
fun RoomOptionsDialog(
    roomWithTenant: RoomWithTenant,
    roomViewModel: RoomViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_options_dialog_title, roomWithTenant.room.name)) },
        text = {
            Column {
                TextButton(onClick = {
                    roomViewModel.setRoomHiddenStatus(roomWithTenant.room.id, true)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.button_hide_room))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    roomViewModel.deleteRoom(roomWithTenant)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.button_delete_room), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button)) // Reusing existing cancel_button string
            }
        },
        dismissButton = null
    )
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
        title = { Text(stringResource(R.string.confirm_move_out_dialog_title)) },
        text = { Text(stringResource(R.string.confirm_move_out_dialog_message, tenantName, roomName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.button_confirm_move_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button)) // Reusing existing cancel_button string
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
    val context = LocalContext.current

    val dialogTitle = if (roomWithTenant.tenant == null || roomWithTenant.tenant.moveOutDate != null) {
        stringResource(R.string.add_tenant_dialog_title)
    } else {
        stringResource(R.string.edit_tenant_dialog_title)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.label_tenant_name)) },
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
                    label = { Text(stringResource(R.string.label_tenant_mobile)) },
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
                        nameError = context.getString(R.string.name_cannot_be_empty) // Reusing existing string
                        valid = false
                    }
                    if (mobile.isBlank()) {
                        mobileError = context.getString(R.string.mobile_cannot_be_empty)
                        valid = false
                    }

                    if (valid) {
                        val existingTenantIdToPass = if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate == null) {
                            roomWithTenant.tenant.id
                        } else {
                            null
                        }

                        val moveInDateToUse = if (existingTenantIdToPass == null) {
                            System.currentTimeMillis() // Default to now if new tenant
                        } else {
                            roomWithTenant.tenant!!.moveInDate // Keep original if editing existing tenant
                        }

                        onConfirm(roomWithTenant.room.id, name, mobile, moveInDateToUse, existingTenantIdToPass)
                    }
                }
            ) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button)) // Reusing existing string
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
    var electricityRateString by remember { mutableStateOf("10.0") } // Default value
    var initialMeterReadingString by remember { mutableStateOf("") }
    val context = LocalContext.current

    var nameError by remember { mutableStateOf<String?>(null) }
    var rentError by remember { mutableStateOf<String?>(null) }
    var electricityRateError by remember { mutableStateOf<String?>(null) }
    var initialMeterReadingError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_room_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.label_room_name_number)) },
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
                    label = { Text(stringResource(R.string.label_monthly_rent)) },
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
                    label = { Text(stringResource(R.string.label_electricity_rate_per_unit)) },
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
                    label = { Text(stringResource(R.string.label_initial_meter_reading_optional)) },
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
                        nameError = context.getString(R.string.name_cannot_be_empty) // Reusing existing string
                        valid = false
                    }

                    val rentDouble = rent.toDoubleOrNull()
                    if (rent.isBlank()) {
                        rentError = context.getString(R.string.rent_cannot_be_empty)
                        valid = false
                    } else if (rentDouble == null || rentDouble <= 0) {
                        rentError = context.getString(R.string.error_invalid_positive_rent)
                        valid = false
                    }

                    val electricityRateDouble = electricityRateString.toDoubleOrNull()
                    if (electricityRateString.isBlank()) {
                        electricityRateError = context.getString(R.string.rate_cannot_be_empty)
                        valid = false
                    } else if (electricityRateDouble == null || electricityRateDouble < 0) {
                        electricityRateError = context.getString(R.string.error_invalid_rate)
                        valid = false
                    }

                    val initialMeterReadingDouble = initialMeterReadingString.toDoubleOrNull()
                    if (initialMeterReadingString.isNotBlank() && (initialMeterReadingDouble == null || initialMeterReadingDouble < 0)) {
                        initialMeterReadingError = context.getString(R.string.error_invalid_meter_reading_optional)
                        valid = false
                    }

                    if (valid) {
                        onAddRoom(name, rent, electricityRateString, initialMeterReadingString)
                    }
                }
            ) {
                Text(stringResource(R.string.add_button)) // Reusing existing add_button string
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button)) // Reusing existing cancel_button string
            }
        }
    )
}
