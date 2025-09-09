package com.example.propertymanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width // Added import
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.utils.formatDate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillDialog(
    bill: MonthlyBillEntity, // This is the initial bill state from ViewModel
    roomName: String,
    isCurrentActiveBill: Boolean,
    currentRoomInitialMeterReading: Double?,
    currentRoomElectricityRate: Double,
    fullRoomRent: Double,
    onDismiss: () -> Unit,
    onBillUpdated: (MonthlyBillEntity) -> Unit, // Callback for intermediate updates
    onSave: (MonthlyBillEntity) -> Unit
) {
    // currentBillState holds the mutable version of the bill being edited in the dialog
    val currentBillState = remember(bill.id) { mutableStateOf(bill.copy()) }
    // Stash the original rent from the bill passed to the dialog (likely pro-rata)
    val initialRentForThisDialogInstance = remember(bill.id) { bill.rentAtBillingTime }

    var applyFullRentOverride by remember(bill.id, isCurrentActiveBill) { mutableStateOf(false) }

    // Determined once based on initial bill state passed to dialog
    val wasBillForOccupiedRoomInitially = remember(bill.id) {
        bill.tenantNameAtBillingTime != "Not Occupied" && bill.rentAtBillingTime >= 0.0
    }

    val isMeterReadingActuallyEditable = remember(bill.id, isCurrentActiveBill, wasBillForOccupiedRoomInitially) {
        isCurrentActiveBill && wasBillForOccupiedRoomInitially && !bill.isInitialReadingRolledOver
    }

    val canRecordPayment = remember(bill.id) { // Based on initial bill for consistency unless tenant changes
        bill.tenantIdAtBillingTime != null
    }

    // Visibility of checkbox is determined once based on initial conditions for this bill instance
    val showFullRentOverrideCheckbox = remember(bill.id, isCurrentActiveBill, wasBillForOccupiedRoomInitially, initialRentForThisDialogInstance, fullRoomRent) {
        isCurrentActiveBill && wasBillForOccupiedRoomInitially && (initialRentForThisDialogInstance < fullRoomRent - 0.001) && fullRoomRent > 0
    }

    // UI state for text fields, keyed to bill.id to reset if a different bill is shown
    var monthEndMeterReadingString by remember(bill.id, bill.monthEndMeterReading, wasBillForOccupiedRoomInitially) {
        mutableStateOf(if (wasBillForOccupiedRoomInitially) bill.monthEndMeterReading?.toString() ?: "" else "")
    }
    var waterBillString by remember(bill.id, bill.waterBill, wasBillForOccupiedRoomInitially) {
        mutableStateOf(if (wasBillForOccupiedRoomInitially) bill.waterBill.takeIf { it > 0 }?.toString() ?: "" else "0.0")
    }
    var otherChargesString by remember(bill.id, bill.otherCharges, wasBillForOccupiedRoomInitially) {
        mutableStateOf(if (wasBillForOccupiedRoomInitially) bill.otherCharges.takeIf { it > 0 }?.toString() ?: "" else "0.0")
    }
    var otherChargesDescription by remember(bill.id, bill.otherChargesDescription, wasBillForOccupiedRoomInitially) {
        mutableStateOf(if (wasBillForOccupiedRoomInitially) bill.otherChargesDescription ?: "" else "")
    }

    var paymentAmountToRecord by remember { mutableStateOf("") }
    var paymentError by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val billPeriod = remember(currentBillState.value.year, currentBillState.value.month) {
        monthYearFormat.format(Calendar.getInstance().apply { set(currentBillState.value.year, currentBillState.value.month - 1, 1) }.time)
    }

    LaunchedEffect(
        applyFullRentOverride, initialRentForThisDialogInstance, fullRoomRent, // Rent influencing factors
        monthEndMeterReadingString, waterBillString, otherChargesString, otherChargesDescription, // Editable fields
        wasBillForOccupiedRoomInitially, isMeterReadingActuallyEditable, // Initial state derived flags
        currentRoomInitialMeterReading, currentRoomElectricityRate,
        currentBillState.value.isInitialReadingRolledOver, // This can change with save
        currentBillState.value.amountPaid, // This changes with record payment
        bill.id // Ensure recalculation if the underlying bill object changes entirely
    ) {
        // Start with a fresh copy of the original bill for this LaunchedEffect cycle
        // then layer current UI state and calculations on top.
        var tempBill = bill.copy(
            // Preserve payment state from currentBillState as it's modified by Record Payment button
            amountPaid = currentBillState.value.amountPaid,
            isFullyPaid = currentBillState.value.isFullyPaid, // This will be recalculated by calculateTotalDue
            paymentDate = currentBillState.value.paymentDate,
            isInitialReadingRolledOver = currentBillState.value.isInitialReadingRolledOver // Preserve rollover status
        )

        val newRentToApply = if (showFullRentOverrideCheckbox && applyFullRentOverride) {
            fullRoomRent
        } else {
            initialRentForThisDialogInstance // Revert to initial pro-rata/original rent if override not active
        }
        tempBill = tempBill.copy(rentAtBillingTime = newRentToApply)

        if (wasBillForOccupiedRoomInitially) {
            tempBill = tempBill.copy(
                waterBill = waterBillString.toDoubleOrNull() ?: 0.0,
                otherCharges = otherChargesString.toDoubleOrNull() ?: 0.0,
                otherChargesDescription = otherChargesDescription.ifBlank { null }
            )
        } else {
            // If not occupied initially, these should be zero/null, overriding any text field input
            tempBill = tempBill.copy(waterBill = 0.0, otherCharges = 0.0, otherChargesDescription = null, monthEndMeterReading = null, electricityUnits = 0.0)
        }

        if (isMeterReadingActuallyEditable) {
            val newMonthEndReadingDouble = monthEndMeterReadingString.toDoubleOrNull()
            tempBill = tempBill.copy(monthEndMeterReading = newMonthEndReadingDouble)
            if (currentRoomInitialMeterReading != null && newMonthEndReadingDouble != null && newMonthEndReadingDouble >= currentRoomInitialMeterReading) {
                tempBill = tempBill.copy(
                    electricityUnits = newMonthEndReadingDouble - currentRoomInitialMeterReading,
                    electricityRateAtBillingTime = currentRoomElectricityRate
                )
            } else {
                tempBill = tempBill.copy(electricityUnits = 0.0, electricityRateAtBillingTime = currentRoomElectricityRate)
            }
        } else {
             if (!wasBillForOccupiedRoomInitially) {
                // Already handled above if not occupied initially
            } else {
                // If occupied but meter reading not editable (e.g., historical bill or already rolled over),
                // retain the original bill's electricity details (already in tempBill from bill.copy())
                tempBill = tempBill.copy(
                    monthEndMeterReading = bill.monthEndMeterReading,
                    electricityUnits = bill.electricityUnits,
                    electricityRateAtBillingTime = bill.electricityRateAtBillingTime
                )
            }
        }

        tempBill.calculateTotalDue() // This recalculates total and updates isFullyPaid status
        currentBillState.value = tempBill // Update the state that UI observes
        onBillUpdated(tempBill)     // Inform the caller for its own state management
    }

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
                // Display the rent based on currentBillState, which reflects the override
                Text("Base Rent: ${currencyFormat.format(currentBillState.value.rentAtBillingTime)}", style = MaterialTheme.typography.bodyLarge)

                if (showFullRentOverrideCheckbox) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) {
                                applyFullRentOverride = !applyFullRentOverride
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = applyFullRentOverride,
                            onCheckedChange = { applyFullRentOverride = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Charge Full Month's Rent (${currencyFormat.format(fullRoomRent)})")
                    }
                }

                val previousDuesOrCredit = currentBillState.value.previousMonthDues
                if (previousDuesOrCredit != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (previousDuesOrCredit > 0) {
                        Text(
                            "Previous Dues: ${currencyFormat.format(previousDuesOrCredit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Previous Credit: ${currencyFormat.format(abs(previousDuesOrCredit))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PositiveGreenColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = monthEndMeterReadingString,
                    onValueChange = { if (isMeterReadingActuallyEditable) monthEndMeterReadingString = it },
                    label = { Text("Month End Meter Reading") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isMeterReadingActuallyEditable
                )

                if (wasBillForOccupiedRoomInitially && currentBillState.value.electricityUnits != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Units Consumed: ${String.format(Locale.getDefault(), "%.2f", currentBillState.value.electricityUnits)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (wasBillForOccupiedRoomInitially && ((currentBillState.value.electricityUnits != null && currentBillState.value.electricityUnits!! > 0.009) || currentBillState.value.electricityBill > 0.009)) {
                     Spacer(modifier = Modifier.height(4.dp))
                     Text(
                        "Electricity Bill: ${currencyFormat.format(currentBillState.value.electricityBill)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = waterBillString,
                    onValueChange = { if (wasBillForOccupiedRoomInitially) waterBillString = it },
                    label = { Text("Water Bill") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = wasBillForOccupiedRoomInitially
                )
                OutlinedTextField(
                    value = otherChargesString,
                    onValueChange = { if (wasBillForOccupiedRoomInitially) otherChargesString = it },
                    label = { Text("Other Charges") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = wasBillForOccupiedRoomInitially
                )
                OutlinedTextField(
                    value = otherChargesDescription,
                    onValueChange = { if (wasBillForOccupiedRoomInitially) otherChargesDescription = it },
                    label = { Text("Description for Other Charges") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {keyboardController?.hide()}),
                    singleLine = false, modifier = Modifier.fillMaxWidth(),
                    enabled = wasBillForOccupiedRoomInitially
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Total Due: ${currencyFormat.format(currentBillState.value.totalAmountDue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Payment Status:", style = MaterialTheme.typography.titleSmall)
                Text("Amount Paid: ${currencyFormat.format(currentBillState.value.amountPaid)}", style = MaterialTheme.typography.bodyMedium)

                val balance = currentBillState.value.amountPaid - currentBillState.value.totalAmountDue

                if (currentBillState.value.isFullyPaid) {
                    Text(
                        text = "Status: Fully Paid on ${formatDate(currentBillState.value.paymentDate)}",
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
                } else {
                    val remainingDueForPayment = max(0.0, currentBillState.value.totalAmountDue - currentBillState.value.amountPaid)
                    if (balance < -0.001) {
                         Text("Status: ${currencyFormat.format(remainingDueForPayment)} remaining", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else if (abs(balance) < 0.001 && currentBillState.value.amountPaid > 0 && currentBillState.value.totalAmountDue > 0) { // Check totalAmountDue > 0 for pending confirmation
                        Text("Status: Cleared (Pending Confirmation)", style = MaterialTheme.typography.bodyMedium)
                    } else {
                         Text("Status: ${currencyFormat.format(remainingDueForPayment)} remaining", style = MaterialTheme.typography.bodyMedium)
                    }

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
                        enabled = canRecordPayment && currentBillState.value.totalAmountDue > 0 // Can only pay if amount is due
                    )
                    if (paymentError != null) { Text(paymentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = {
                            val paymentValue = paymentAmountToRecord.toDoubleOrNull()
                            if (paymentValue == null || paymentValue <= 0) {
                                paymentError = "Enter a valid positive amount"
                            } else {
                                val newAmountPaid = currentBillState.value.amountPaid + paymentValue
                                var billWithPayment = currentBillState.value.copy(
                                    amountPaid = newAmountPaid,
                                    // isFullyPaid will be set by calculateTotalDue
                                    paymentDate = if (newAmountPaid >= currentBillState.value.totalAmountDue - 0.001 || currentBillState.value.isFullyPaid) System.currentTimeMillis() else currentBillState.value.paymentDate
                                )
                                billWithPayment.calculateTotalDue() // This updates isFullyPaid
                                currentBillState.value = billWithPayment 
                                onBillUpdated(billWithPayment) 

                                paymentAmountToRecord = ""
                                paymentError = null
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = canRecordPayment && currentBillState.value.totalAmountDue > 0 // Can only pay if amount is due
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

// PositiveGreenColor should be defined in a shared file like BillHistoryComponents.kt
// import androidx.compose.ui.graphics.Color
// val PositiveGreenColor = Color(0xFF006400) // Dark Green
