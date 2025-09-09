package com.example.propertymanager.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlin.math.max

val PositiveGreenColor = Color(0xFF388E3C) // A Material Design like green

// Helper function to format a list of bills for sharing
internal fun formatBillHistoryForSharing(
    bills: List<MonthlyBillEntity>,
    roomName: String,
    filterYear: String,
    selectedTenantName: String,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat
): String {
    val builder = StringBuilder()
    builder.append("Bill History for Room: $roomName\n")
    if (filterYear.isNotBlank()) {
        builder.append("Filtered by Year: $filterYear\n")
    }
    if (selectedTenantName != "All Tenants") {
        builder.append("Filtered by Tenant: $selectedTenantName\n")
    }
    builder.append("------------------------------------\n\n")

    if (bills.isEmpty()) {
        builder.append("No bills found matching current filters.\n")
    } else {
        bills.forEach { bill ->
            val billPeriod = monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
            builder.append("Period: $billPeriod\n")
            builder.append("Billed to: ${bill.tenantNameAtBillingTime ?: "N/A"}\n")
            builder.append("Rent: ${currencyFormat.format(bill.rentAtBillingTime)}\n")

            if (bill.electricityUnits != null && bill.electricityRateAtBillingTime != null && (bill.electricityUnits!! > 0 || bill.tenantNameAtBillingTime == "Not Occupied")) {
                val units = bill.electricityUnits ?: 0.0
                val rate = bill.electricityRateAtBillingTime ?: 0.0
                val totalElecCost = units * rate
                builder.append("Electricity: ${String.format("%.1f", units)} units x ${currencyFormat.format(rate)}/unit = ${currencyFormat.format(totalElecCost)}\n")
            } else if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                 builder.append("Electricity: ${currencyFormat.format(bill.electricityBill)}\n")
            }

            if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                builder.append("Water: ${currencyFormat.format(bill.waterBill)}\n")
            }
            if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                builder.append("Other Charges: ${currencyFormat.format(bill.otherCharges)}$description\n")
            }
            if (bill.previousMonthDues != 0.0) {
                if (bill.previousMonthDues > 0) {
                    builder.append("Previous Dues: ${currencyFormat.format(bill.previousMonthDues)}\n")
                } else {
                    builder.append("Previous Credit: ${currencyFormat.format(abs(bill.previousMonthDues))}\n")
                }
            }
            builder.append("Total Amount: ${currencyFormat.format(bill.totalAmountDue)}\n")
            builder.append("Amount Paid: ${currencyFormat.format(bill.amountPaid)}\n")

            val balance = bill.amountPaid - bill.totalAmountDue
            var statusText: String
            if (bill.isFullyPaid) {
                statusText = "Fully Paid on ${formatDate(bill.paymentDate)}"
                if (balance > 0.001) {
                    statusText += " (Advance: ${currencyFormat.format(balance)})"
                }
            } else {
                if (balance > 0.001) {
                    statusText = "Advance: ${currencyFormat.format(balance)}"
                } else if (abs(balance) < 0.001) {
                    statusText = "Paid" // Changed from "Cleared (Pending Confirmation)"
                } else { // balance < 0
                    statusText = "Due: ${currencyFormat.format(abs(balance))}"
                }
            }
            builder.append("Status: $statusText\n")
            builder.append("------------------------------------\n")
        }
    }
    return builder.toString()
}

// Helper function to format a single bill for sharing
internal fun formatSingleBillForSharing(
    bill: MonthlyBillEntity,
    roomName: String,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat
): String {
    val builder = StringBuilder()
    val billPeriod = monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
    builder.append("Bill Details for Room: $roomName\n")
    builder.append("Period: $billPeriod\n")
    builder.append("Billed to: ${bill.tenantNameAtBillingTime ?: "N/A"}\n")
    builder.append("Rent: ${currencyFormat.format(bill.rentAtBillingTime)}\n")

    if (bill.electricityUnits != null && bill.electricityRateAtBillingTime != null && (bill.electricityUnits!! > 0 || bill.tenantNameAtBillingTime == "Not Occupied")) {
        val units = bill.electricityUnits ?: 0.0
        val rate = bill.electricityRateAtBillingTime ?: 0.0
        val totalElecCost = units * rate
        builder.append("Electricity: ${String.format("%.1f", units)} units x ${currencyFormat.format(rate)}/unit = ${currencyFormat.format(totalElecCost)}\n")
    } else if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
        builder.append("Electricity: ${currencyFormat.format(bill.electricityBill)}\n")
    }

    if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
        builder.append("Water: ${currencyFormat.format(bill.waterBill)}\n")
    }
    if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
        val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        builder.append("Other Charges: ${currencyFormat.format(bill.otherCharges)}$description\n")
    }
    if (bill.previousMonthDues != 0.0) {
        if (bill.previousMonthDues > 0) {
            builder.append("Previous Dues: ${currencyFormat.format(bill.previousMonthDues)}\n")
        } else {
            builder.append("Previous Credit: ${currencyFormat.format(abs(bill.previousMonthDues))}\n")
        }
    }
    builder.append("Total Amount: ${currencyFormat.format(bill.totalAmountDue)}\n")
    builder.append("Amount Paid: ${currencyFormat.format(bill.amountPaid)}\n")

    val balance = bill.amountPaid - bill.totalAmountDue
    var statusText: String
    if (bill.isFullyPaid) {
        statusText = "Fully Paid on ${formatDate(bill.paymentDate)}"
        if (balance > 0.001) {
            statusText += " (Advance: ${currencyFormat.format(balance)})"
        }
    } else {
        if (balance > 0.001) {
            statusText = "Advance: ${currencyFormat.format(balance)}"
        } else if (abs(balance) < 0.001) {
            statusText = "Paid" // Changed from "Cleared (Pending Confirmation)"
        } else { // balance < 0
            statusText = "Due: ${currencyFormat.format(abs(balance))}"
        }
    }
    builder.append("Status: $statusText\n")
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
    onBillSelected: (MonthlyBillEntity) -> Unit // New parameter
) {
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredBills = remember(bills, filterYear, selectedTenantId) {
        bills.filter { bill ->
            val yearMatches = filterYear.isBlank() || bill.year.toString() == filterYear.trim()
            val tenantMatches = selectedTenantId == null || bill.tenantIdAtBillingTime == selectedTenantId
            yearMatches && tenantMatches
        }.sortedWith(compareByDescending<MonthlyBillEntity> { it.year }.thenByDescending { it.month })
    }

    val selectedTenantName = remember(selectedTenantId, allTenants) {
        selectedTenantId?.let { id -> allTenants.find { it.id == id }?.name } ?: "All Tenants"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bill History for $roomName") },
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
                        label = { Text("Year (YYYY)") },
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
                            Text(selectedTenantName)
                            Icon(Icons.Filled.ArrowDropDown, "Select Tenant")
                        }
                        DropdownMenu(
                            expanded = showTenantFilterDropdown,
                            onDismissRequest = onToggleTenantFilterDropdown
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Tenants") },
                                onClick = {
                                    onTenantSelected(null)
                                    onToggleTenantFilterDropdown()
                                }
                            )
                            allTenants.forEach { tenant ->
                                DropdownMenuItem(
                                    text = { Text(tenant.name) },
                                    onClick = {
                                        onTenantSelected(tenant.id)
                                        onToggleTenantFilterDropdown()
                                    }
                                )
                            }
                        }
                    }
                     IconButton(onClick = onClearFilters) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear Filters")
                    }
                }

                if (filteredBills.isEmpty()) {
                    Text("No bill history found matching your filters.")
                } else {
                    LazyColumn(modifier = Modifier.padding(top = 8.dp).heightIn(max = 300.dp)) {
                        items(filteredBills) { bill ->
                            BillHistoryItem(
                                bill = bill,
                                monthYearFormat = monthYearFormat,
                                currencyFormat = currencyFormat,
                                roomName = roomName,
                                onShareThisBill = { billToShare ->
                                    val singleBillText = formatSingleBillForSharing(
                                        bill = billToShare,
                                        roomName = roomName,
                                        monthYearFormat = monthYearFormat,
                                        currencyFormat = currencyFormat
                                    )
                                    onShareClicked(singleBillText)
                                },
                                onBillClick = { onBillSelected(bill) } // Call the new callback
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
                Icon(Icons.Filled.Share, contentDescription = "Share Full History", modifier = Modifier.padding(end = 4.dp))
                Text("Share All")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class) // Added OptIn for Card onClick
@Composable
fun BillHistoryItem(
    bill: MonthlyBillEntity,
    monthYearFormat: SimpleDateFormat,
    currencyFormat: NumberFormat,
    roomName: String,
    onShareThisBill: (MonthlyBillEntity) -> Unit,
    onBillClick: (MonthlyBillEntity) -> Unit // New parameter
) {
    val billPeriod = remember(bill.year, bill.month) {
        monthYearFormat.format(Calendar.getInstance().apply { set(bill.year, bill.month - 1, 1) }.time)
    }
    val balance = remember(bill.totalAmountDue, bill.amountPaid) {
        bill.amountPaid - bill.totalAmountDue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { onBillClick(bill) } // Added onClick handler
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(billPeriod, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onShareThisBill(bill) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share this bill")
                }
            }
            Text(
                "Billed to: ${bill.tenantNameAtBillingTime ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Base Rent: ${currencyFormat.format(bill.rentAtBillingTime)}")

            if (bill.electricityUnits != null && bill.electricityRateAtBillingTime != null && (bill.electricityUnits!! > 0 || bill.tenantNameAtBillingTime == "Not Occupied")) {
                val units = bill.electricityUnits ?: 0.0
                val rate = bill.electricityRateAtBillingTime ?: 0.0
                val totalElecCost = units * rate
                Text("Electricity: ${String.format("%.1f", units)} units x ${currencyFormat.format(rate)}/unit = ${currencyFormat.format(totalElecCost)}")
            } else if (bill.electricityBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                 Text("Electricity: ${currencyFormat.format(bill.electricityBill)}")
            }

            if (bill.waterBill > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                Text("Water: ${currencyFormat.format(bill.waterBill)}")
            }
            if (bill.otherCharges > 0 || bill.tenantNameAtBillingTime == "Not Occupied") {
                val description = bill.otherChargesDescription?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                Text("Other Charges: ${currencyFormat.format(bill.otherCharges)}$description")
            }

            if (bill.previousMonthDues != 0.0) {
                if (bill.previousMonthDues > 0) {
                    Text(
                        "Previous Dues Carried Over: ${currencyFormat.format(bill.previousMonthDues)}",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "Previous Credit: ${currencyFormat.format(abs(bill.previousMonthDues))}",
                        color = PositiveGreenColor
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Total Bill Amount: ${currencyFormat.format(bill.totalAmountDue)}", fontWeight = FontWeight.Bold)
            Text("Amount Paid: ${currencyFormat.format(bill.amountPaid)}")

            Spacer(modifier = Modifier.height(4.dp))

            if (bill.isFullyPaid) {
                Text(
                    text = "Status: Fully Paid",
                    color = PositiveGreenColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (balance > 0.001) {
                    Text(
                        text = "Advance: ${currencyFormat.format(balance)}",
                        color = PositiveGreenColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("Paid on: ${formatDate(bill.paymentDate)}", style = MaterialTheme.typography.bodySmall)
            } else {
                when {
                    balance > 0.001 -> {
                        Text(
                            text = "Status: ${currencyFormat.format(balance)} Advance",
                            color = PositiveGreenColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    abs(balance) < 0.001 -> {
                        Text(
                            text = "Status: Paid", // Changed from "Status: Cleared (Pending Confirmation)"
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> { // balance < 0 (Dues)
                        Text(
                            text = "Status: ${currencyFormat.format(abs(balance))} Due",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if(bill.paymentDate != null && bill.paymentDate != 0L && bill.amountPaid > 0) {
                    Text("Last Payment: ${formatDate(bill.paymentDate)}", style = MaterialTheme.typography.bodySmall)
                } else if (bill.amountPaid > 0) {
                     Text("Last Payment: Date N/A", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}