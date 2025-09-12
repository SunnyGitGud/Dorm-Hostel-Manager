package com.example.propertymanager.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History // Using History icon for installments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.example.propertymanager.R // Assuming your R file is here
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.utils.formatDate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// PositiveGreenColor is defined in AddEditBillDialog.kt

internal fun formatBillHistoryForSharing(
    context: Context, // Added context
    bills: List<MonthlyBillEntity>,
    roomName: String,
    filterYear: String,
    selectedTenantName: String,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat
): String {
    val builder = StringBuilder()
    val allTenantsText = context.getString(R.string.all_tenants) // R.string.all_tenants

    builder.append(context.getString(R.string.bill_history_for_room_share_title, roomName)) // R.string.bill_history_for_room_share_title
    builder.append("\n")
    if (filterYear.isNotBlank()) {
        builder.append(context.getString(R.string.filtered_by_year_share, filterYear)) // R.string.filtered_by_year_share
        builder.append("\n")
    }
    if (selectedTenantName != allTenantsText) {
        builder.append(context.getString(R.string.filtered_by_tenant_share, selectedTenantName)) // R.string.filtered_by_tenant_share
        builder.append("\n")
    }
    builder.append("------------------------------------\n\n")

    if (bills.isEmpty()) {
        builder.append(context.getString(R.string.no_bills_matching_filters_share)) // R.string.no_bills_matching_filters_share
        builder.append("\n")
    } else {
        val textNa = context.getString(R.string.text_na) // R.string.text_na
        bills.forEach { bill ->
            val billPeriod = monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
            builder.append(context.getString(R.string.period_share, billPeriod)) // R.string.period_share
            builder.append("\n")
            builder.append(context.getString(R.string.billed_to_share, bill.tenantNameAtBillingTime ?: textNa)) // R.string.billed_to_share
            builder.append("\n")
            builder.append(context.getString(R.string.rent_share, currencyFormat.format(bill.rentAtBillingTime))) // R.string.rent_share
            builder.append("\n")

            if (bill.electricityUnits != null && bill.electricityRateAtBillingTime != null && (bill.electricityUnits!! > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder))) { // R.string.not_occupied_placeholder
                val units = bill.electricityUnits ?: 0.0
                val rate = bill.electricityRateAtBillingTime ?: 0.0
                val totalElecCost = units * rate
                builder.append(context.getString(R.string.electricity_details_share, String.format("%.1f", units), currencyFormat.format(rate), currencyFormat.format(totalElecCost))) // R.string.electricity_details_share
                builder.append("\n")
            } else if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder)) { // R.string.not_occupied_placeholder
                 builder.append(context.getString(R.string.electricity_total_share, currencyFormat.format(bill.electricityBill))) // R.string.electricity_total_share
                 builder.append("\n")
            }

            if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder)) { // R.string.not_occupied_placeholder
                builder.append(context.getString(R.string.water_share, currencyFormat.format(bill.waterBill))) // R.string.water_share
                builder.append("\n")
            }
            if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder)) { // R.string.not_occupied_placeholder
                val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                builder.append(context.getString(R.string.other_charges_share, currencyFormat.format(bill.otherCharges), description)) // R.string.other_charges_share
                builder.append("\n")
            }
            if (bill.previousMonthDues != 0.0) {
                if (bill.previousMonthDues > 0) {
                    builder.append(context.getString(R.string.previous_dues_share, currencyFormat.format(bill.previousMonthDues))) // R.string.previous_dues_share
                    builder.append("\n")
                } else {
                    builder.append(context.getString(R.string.previous_credit_share, currencyFormat.format(abs(bill.previousMonthDues)))) // R.string.previous_credit_share
                    builder.append("\n")
                }
            }
            builder.append(context.getString(R.string.total_amount_share, currencyFormat.format(bill.totalAmountDue))) // R.string.total_amount_share
            builder.append("\n")
            builder.append(context.getString(R.string.amount_paid_share, currencyFormat.format(bill.amountPaid))) // R.string.amount_paid_share
            builder.append("\n")

            val balance = bill.amountPaid - bill.totalAmountDue
            var statusText: String
            if (bill.isFullyPaid) {
                statusText = context.getString(R.string.status_fully_paid_on_date_share, formatDate(bill.paymentDate))
                if (balance > 0.001) {
                    statusText += context.getString(R.string.status_advance_amount_share_suffix, currencyFormat.format(balance))
                }
            } else {
                if (balance > 0.001) {
                    statusText = context.getString(R.string.status_advance_amount_share, currencyFormat.format(balance))
                } else if (abs(balance) < 0.001 && bill.amountPaid > 0) {
                    statusText = context.getString(R.string.status_paid_share)
                } else if (bill.totalAmountDue > 0 && bill.amountPaid == 0.0) {
                    statusText = context.getString(R.string.status_due_amount_share, currencyFormat.format(bill.totalAmountDue))
                } else { 
                    statusText = context.getString(R.string.status_due_amount_share, currencyFormat.format(abs(balance)))
                }
            }
            builder.append(context.getString(R.string.status_share, statusText)) // R.string.status_share
            builder.append("\n")
            // Append Installment History
            if (bill.installments.isNotEmpty()) {
                builder.append(context.getString(R.string.payment_installments_share_title)) // R.string.payment_installments_share_title
                builder.append("\n")
                bill.installments.forEach {
                    builder.append(context.getString(R.string.payment_installment_item_share, currencyFormat.format(it.amount), formatDate(it.date))) // R.string.payment_installment_item_share
                    builder.append("\n")
                }
            }
            builder.append("------------------------------------\n")
        }
    }
    return builder.toString()
}

//This is the internal version for BillHistoryDialog
internal fun formatSingleBillForSharing(
    context: Context, // Added context
    bill: MonthlyBillEntity,
    roomName: String,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat
): String {
    val builder = StringBuilder()
    val billPeriod = monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
    val textNa = context.getString(R.string.text_na) // R.string.text_na
    
    builder.append(context.getString(R.string.bill_details_for_room_share_title, roomName)) // R.string.bill_details_for_room_share_title
    builder.append("\n")
    builder.append(context.getString(R.string.period_share, billPeriod)) // R.string.period_share
    builder.append("\n")
    builder.append(context.getString(R.string.billed_to_share, bill.tenantNameAtBillingTime ?: textNa)) // R.string.billed_to_share
    builder.append("\n")
    builder.append(context.getString(R.string.rent_share, currencyFormat.format(bill.rentAtBillingTime))) // R.string.rent_share
    builder.append("\n")

    if (bill.electricityUnits != null && bill.electricityRateAtBillingTime != null && (bill.electricityUnits!! > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder))) { // R.string.not_occupied_placeholder
        val units = bill.electricityUnits ?: 0.0
        val rate = bill.electricityRateAtBillingTime ?: 0.0
        val totalElecCost = units * rate
        builder.append(context.getString(R.string.electricity_details_share, String.format("%.1f", units), currencyFormat.format(rate), currencyFormat.format(totalElecCost))) // R.string.electricity_details_share
        builder.append("\n")
    } else if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder)) { // R.string.not_occupied_placeholder
        builder.append(context.getString(R.string.electricity_total_share, currencyFormat.format(bill.electricityBill))) // R.string.electricity_total_share
        builder.append("\n")
    }

    if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder)) { // R.string.not_occupied_placeholder
        builder.append(context.getString(R.string.water_share, currencyFormat.format(bill.waterBill))) // R.string.water_share
        builder.append("\n")
    }
    if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == context.getString(R.string.not_occupied_placeholder)) { // R.string.not_occupied_placeholder
        val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        builder.append(context.getString(R.string.other_charges_share, currencyFormat.format(bill.otherCharges), description)) // R.string.other_charges_share
        builder.append("\n")
    }
    if (bill.previousMonthDues != 0.0) {
        if (bill.previousMonthDues > 0) {
            builder.append(context.getString(R.string.previous_dues_share, currencyFormat.format(bill.previousMonthDues))) // R.string.previous_dues_share
            builder.append("\n")
        } else {
            builder.append(context.getString(R.string.previous_credit_share, currencyFormat.format(abs(bill.previousMonthDues)))) // R.string.previous_credit_share
            builder.append("\n")
        }
    }
    builder.append(context.getString(R.string.total_amount_share, currencyFormat.format(bill.totalAmountDue))) // R.string.total_amount_share
    builder.append("\n")
    builder.append(context.getString(R.string.amount_paid_share, currencyFormat.format(bill.amountPaid))) // R.string.amount_paid_share
    builder.append("\n")

    val balance = bill.amountPaid - bill.totalAmountDue
    var statusText: String
    if (bill.isFullyPaid) {
        statusText = context.getString(R.string.status_fully_paid_on_date_share, formatDate(bill.paymentDate))
        if (balance > 0.001) {
             statusText += context.getString(R.string.status_advance_amount_share_suffix, currencyFormat.format(balance))
        }
    } else {
        if (balance > 0.001) {
            statusText = context.getString(R.string.status_advance_amount_share, currencyFormat.format(balance))
        } else if (abs(balance) < 0.001 && bill.amountPaid > 0) {
            statusText = context.getString(R.string.status_paid_share)
        } else if (bill.totalAmountDue > 0 && bill.amountPaid == 0.0){
            statusText = context.getString(R.string.status_due_amount_share, currencyFormat.format(bill.totalAmountDue))
        } else { 
            statusText = context.getString(R.string.status_due_amount_share, currencyFormat.format(abs(balance)))
        }
    }
    builder.append(context.getString(R.string.status_share, statusText)) // R.string.status_share
    builder.append("\n")

    if (bill.installments.isNotEmpty()) {
        builder.append(context.getString(R.string.payment_installments_share_title)) // R.string.payment_installments_share_title
        builder.append("\n")
        bill.installments.forEach {
            builder.append(context.getString(R.string.payment_installment_item_share, currencyFormat.format(it.amount), formatDate(it.date))) // R.string.payment_installment_item_share
            builder.append("\n")
        }
    }
    return builder.toString()
}

@Composable
fun ShareBillHistoryAction(historyText: String) {
    val context = LocalContext.current
    LaunchedEffect(historyText, context) {
        if (historyText.isNotBlank()){
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, historyText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillHistoryDialog(
    roomName: String,
    bills: List<MonthlyBillEntity>,
    allTenants: List<TenantEntity>,
    filterYear: String,
    onFilterYearChange: (String) -> Unit,
    selectedTenantId: Int?,
    onTenantSelected: (Int?) -> Unit,
    showTenantFilterDropdown: Boolean,
    onToggleTenantFilterDropdown: () -> Unit,
    onDismiss: () -> Unit,
    onClearFilters: () -> Unit,
    onShareClicked: (String) -> Unit,
    onBillSelected: (MonthlyBillEntity) -> Unit
) {
    val context = LocalContext.current // Get context for string resources
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredBills = remember(bills, filterYear, selectedTenantId) {
        bills.filter { bill ->
            val yearMatches = filterYear.isBlank() || bill.year.toString() == filterYear.trim()
            val tenantMatches = selectedTenantId == null || bill.tenantIdAtBillingTime == selectedTenantId
            yearMatches && tenantMatches
        }.sortedWith(compareByDescending<MonthlyBillEntity> { it.year }.thenByDescending { it.month })
    }

    val allTenantsText = stringResource(R.string.all_tenants) // R.string.all_tenants
    val selectedTenantName = remember(selectedTenantId, allTenants, allTenantsText) {
        selectedTenantId?.let { id -> allTenants.find { it.id == id }?.name } ?: allTenantsText
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_history_for_room, roomName)) }, // R.string.bill_history_for_room
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = filterYear,
                        onValueChange = onFilterYearChange,
                        label = { Text(stringResource(R.string.year_yyyy_label)) }, // R.string.year_yyyy_label
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        TextButton(onClick = onToggleTenantFilterDropdown) {
                            Text(selectedTenantName) // Already localized or "All Tenants"
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.select_tenant_dropdown_description)) // R.string.select_tenant_dropdown_description
                        }
                        DropdownMenu(
                            expanded = showTenantFilterDropdown,
                            onDismissRequest = onToggleTenantFilterDropdown
                        ) {
                            DropdownMenuItem(
                                text = { Text(allTenantsText) }, // R.string.all_tenants (used variable)
                                onClick = {
                                    onTenantSelected(null)
                                    onToggleTenantFilterDropdown()
                                }
                            )
                            allTenants.forEach { tenant ->
                                DropdownMenuItem(
                                    text = { Text(tenant.name) }, // Tenant name from data, not localized here
                                    onClick = {
                                        onTenantSelected(tenant.id)
                                        onToggleTenantFilterDropdown()
                                    }
                                )
                            }
                        }
                    }
                     IconButton(onClick = onClearFilters) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_filters_description)) // R.string.clear_filters_description
                    }
                }

                if (filteredBills.isEmpty()) {
                    Text(stringResource(R.string.no_bill_history_matching_filters)) // R.string.no_bill_history_matching_filters
                } else {
                    LazyColumn(modifier = Modifier.padding(top = 8.dp).heightIn(max = 300.dp)) {
                        items(filteredBills) { bill ->
                            BillHistoryItem(
                                bill = bill,
                                monthYearFormat = monthYearFormat,
                                currencyFormat = currencyFormat,
                                roomName = roomName,
                                onShareThisBill = {
                                    val singleBillText = formatSingleBillForSharing(
                                        context = context, // Pass context
                                        bill = bill,
                                        roomName = roomName,
                                        monthYearFormat = monthYearFormat,
                                        currencyFormat = currencyFormat
                                    )
                                    onShareClicked(singleBillText)
                                },
                                onBillClick = { onBillSelected(bill) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val historyText = formatBillHistoryForSharing(
                        context = context, // Pass context
                        bills = filteredBills,
                        roomName = roomName,
                        filterYear = filterYear,
                        selectedTenantName = selectedTenantName,
                        monthYearFormat = monthYearFormat,
                        currencyFormat = currencyFormat
                    )
                    onShareClicked(historyText)
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_full_history_description), modifier = Modifier.padding(end = 4.dp)) // R.string.share_full_history_description
                Text(stringResource(R.string.share_all_button)) // R.string.share_all_button
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_button)) // R.string.close_button
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillHistoryItem(
    bill: MonthlyBillEntity,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    roomName: String, 
    onShareThisBill: (MonthlyBillEntity) -> Unit,
    onBillClick: (MonthlyBillEntity) -> Unit
) {
    val context = LocalContext.current // For stringResource
    var showInstallments by remember { mutableStateOf(false) }
    val billPeriod = remember(bill.year, bill.month) {
        monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
    }
    val balance = remember(bill.totalAmountDue, bill.amountPaid) {
        bill.amountPaid - bill.totalAmountDue
    }
    val textNa = stringResource(R.string.text_na) // R.string.text_na
    val notOccupiedText = stringResource(R.string.not_occupied_placeholder) // R.string.not_occupied_placeholder

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { onBillClick(bill) } 
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(billPeriod, style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { showInstallments = !showInstallments }) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.view_payment_installments_description)) // R.string.view_payment_installments_description
                    }
                    IconButton(onClick = { onShareThisBill(bill) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_this_bill_description)) // R.string.share_this_bill_description
                    }
                }
            }
            Text(
                stringResource(R.string.billed_to, bill.tenantNameAtBillingTime ?: textNa), // R.string.billed_to
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.base_rent_colon, currencyFormat.format(bill.rentAtBillingTime))) // R.string.base_rent_colon

            if (bill.electricityUnits != null && bill.electricityRateAtBillingTime != null && (bill.electricityUnits!! > 0 || bill.tenantNameAtBillingTime == notOccupiedText)) {
                val units = bill.electricityUnits ?: 0.0
                val rate = bill.electricityRateAtBillingTime ?: 0.0
                val totalElecCost = units * rate
                Text(stringResource(R.string.electricity_details_units_rate_total, String.format("%.1f", units), currencyFormat.format(rate), currencyFormat.format(totalElecCost))) // R.string.electricity_details_units_rate_total
            } else if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == notOccupiedText) {
                 Text(stringResource(R.string.electricity_charges_colon, currencyFormat.format(bill.electricityBill))) // R.string.electricity_charges_colon
            }

            if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == notOccupiedText) {
                Text(stringResource(R.string.water_charges_colon, currencyFormat.format(bill.waterBill))) // R.string.water_charges_colon
            }
            if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == notOccupiedText) {
                val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                Text(stringResource(R.string.other_charges_colon_description, currencyFormat.format(bill.otherCharges), description)) // R.string.other_charges_colon_description
            }

            if (bill.previousMonthDues != 0.0) {
                if (bill.previousMonthDues > 0) {
                    Text(
                        stringResource(R.string.previous_dues_carried_over_colon, currencyFormat.format(bill.previousMonthDues)), // R.string.previous_dues_carried_over_colon
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(R.string.previous_credit_colon, currencyFormat.format(abs(bill.previousMonthDues))), // R.string.previous_credit_colon
                        color = PositiveGreenColor
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.total_bill_amount_colon, currencyFormat.format(bill.totalAmountDue)), fontWeight = FontWeight.Bold) // R.string.total_bill_amount_colon
            Text(stringResource(R.string.amount_paid_colon, currencyFormat.format(bill.amountPaid))) // R.string.amount_paid_colon

            Spacer(modifier = Modifier.height(4.dp))

            if (bill.isFullyPaid) {
                Text(
                    text = stringResource(R.string.status_fully_paid), // R.string.status_fully_paid
                    color = PositiveGreenColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (balance > 0.001) {
                    Text(
                        text = stringResource(R.string.advance_colon, currencyFormat.format(balance)), // R.string.advance_colon
                        color = PositiveGreenColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(stringResource(R.string.paid_on_date, formatDate(bill.paymentDate)), style = MaterialTheme.typography.bodySmall) // R.string.paid_on_date
            } else {
                when {
                    balance > 0.001 -> {
                        Text(
                            text = stringResource(R.string.status_advance_amount, currencyFormat.format(balance)), // R.string.status_advance_amount
                            color = PositiveGreenColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    abs(balance) < 0.001 && bill.amountPaid > 0 -> {
                        Text(
                            text = stringResource(R.string.status_paid), // R.string.status_paid
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                     bill.totalAmountDue > 0 && bill.amountPaid == 0.0 -> { 
                        Text(
                            text = stringResource(R.string.status_due_amount, currencyFormat.format(bill.totalAmountDue)), // R.string.status_due_amount
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> { 
                        Text(
                            text = stringResource(R.string.status_due_amount, currencyFormat.format(abs(balance))), // R.string.status_due_amount
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if(bill.paymentDate != null && bill.paymentDate != 0L && bill.amountPaid > 0) {
                    Text(stringResource(R.string.last_payment_date, formatDate(bill.paymentDate)), style = MaterialTheme.typography.bodySmall) // R.string.last_payment_date
                } else if (bill.amountPaid > 0) { 
                     Text(stringResource(R.string.last_payment_date_na), style = MaterialTheme.typography.bodySmall) // R.string.last_payment_date_na
                }
            }
            
            AnimatedVisibility(visible = showInstallments) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                    Text(stringResource(R.string.payment_history_colon), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) // R.string.payment_history_colon
                    if (bill.installments.isNotEmpty()) {
                        bill.installments.forEach { installment ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.payment_installment_item, currencyFormat.format(installment.amount)), style = MaterialTheme.typography.bodySmall) // R.string.payment_installment_item
                                Text(formatDate(installment.date), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(stringResource(R.string.no_payment_installments_recorded), style = MaterialTheme.typography.bodySmall) // R.string.no_payment_installments_recorded
                    }
                }
            }
        }
    }
}
