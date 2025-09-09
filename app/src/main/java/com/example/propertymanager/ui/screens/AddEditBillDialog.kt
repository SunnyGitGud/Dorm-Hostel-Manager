package com.example.propertymanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    bill: MonthlyBillEntity,
    roomName: String,
    isCurrentActiveBill: Boolean, // New parameter
    currentRoomInitialMeterReading: Double?, // For current active bill calculation
    currentRoomElectricityRate: Double,   // For current active bill calculation
    onDismiss: () -> Unit,
    onBillUpdated: (MonthlyBillEntity) -> Unit,
    onSave: (MonthlyBillEntity) -> Unit
) {
    val currentBillState = remember(bill) { mutableStateOf(bill.copy()) }

    val wasBillForOccupiedRoom = remember(currentBillState.value.tenantNameAtBillingTime, currentBillState.value.rentAtBillingTime) {
        currentBillState.value.tenantNameAtBillingTime != "Not Occupied" && currentBillState.value.rentAtBillingTime >= 0.0
    }

    val isMeterReadingActuallyEditable = remember(isCurrentActiveBill, wasBillForOccupiedRoom, currentBillState.value.isInitialReadingRolledOver) {
        isCurrentActiveBill && wasBillForOccupiedRoom && !currentBillState.value.isInitialReadingRolledOver
    }

    val canRecordPayment = remember(currentBillState.value.tenantIdAtBillingTime) {
        currentBillState.value.tenantIdAtBillingTime != null
    }

    var monthEndMeterReadingString by remember(
        currentBillState.value.id, // Re-init when bill id changes
        currentBillState.value.monthEndMeterReading,
        wasBillForOccupiedRoom // Re-init if occupied status of original bill was different
    ) {
        mutableStateOf(
            if (wasBillForOccupiedRoom) currentBillState.value.monthEndMeterReading?.toString() ?: "" else ""
        )
    }
    var waterBillString by remember(currentBillState.value.id, currentBillState.value.waterBill, wasBillForOccupiedRoom) {
        mutableStateOf(if (wasBillForOccupiedRoom) currentBillState.value.waterBill.takeIf { it > 0 }?.toString() ?: "" else "0.0")
    }
    var otherChargesString by remember(currentBillState.value.id, currentBillState.value.otherCharges, wasBillForOccupiedRoom) {
        mutableStateOf(if (wasBillForOccupiedRoom) currentBillState.value.otherCharges.takeIf { it > 0 }?.toString() ?: "" else "0.0")
    }
    var otherChargesDescription by remember(currentBillState.value.id, currentBillState.value.otherChargesDescription, wasBillForOccupiedRoom) {
        mutableStateOf(if (wasBillForOccupiedRoom) currentBillState.value.otherChargesDescription ?: "" else "")
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
        monthEndMeterReadingString,
        waterBillString,
        otherChargesString,
        otherChargesDescription,
        wasBillForOccupiedRoom,
        isMeterReadingActuallyEditable,
        currentRoomInitialMeterReading,
        currentRoomElectricityRate,
        currentBillState.value.isInitialReadingRolledOver // Added to ensure re-trigger if this changes
    ) {
        var tempBill = currentBillState.value.copy() // Start with the current state (which is a copy of original bill)

        if (wasBillForOccupiedRoom) {
            tempBill = tempBill.copy(
                waterBill = waterBillString.toDoubleOrNull() ?: 0.0,
                otherCharges = otherChargesString.toDoubleOrNull() ?: 0.0,
                otherChargesDescription = otherChargesDescription.ifBlank { null }
            )
        } else {
            tempBill = tempBill.copy(
                waterBill = 0.0,
                otherCharges = 0.0,
                otherChargesDescription = null
            )
        }

        if (isMeterReadingActuallyEditable) {
            val newMonthEndReadingDouble = monthEndMeterReadingString.toDoubleOrNull()
            tempBill = tempBill.copy(
                monthEndMeterReading = newMonthEndReadingDouble
            )
            if (currentRoomInitialMeterReading != null && newMonthEndReadingDouble != null && newMonthEndReadingDouble >= currentRoomInitialMeterReading) {
                tempBill = tempBill.copy(
                    electricityUnits = newMonthEndReadingDouble - currentRoomInitialMeterReading,
                    electricityRateAtBillingTime = currentRoomElectricityRate
                )
            } else {
                tempBill = tempBill.copy(
                    electricityUnits = 0.0,
                    electricityRateAtBillingTime = currentRoomElectricityRate
                )
            }
        } else {
            // For non-editable readings, ensure these are from the original bill's state.
            // If the original bill was for "Not Occupied", these should be null/0.
            if (!wasBillForOccupiedRoom) {
                tempBill = tempBill.copy(
                    monthEndMeterReading = null,
                    electricityUnits = 0.0
                    // electricityRateAtBillingTime is preserved from original bill
                )
            } else {
                 // Preserve original bill's meter reading details if not editable
                tempBill = tempBill.copy(
                    monthEndMeterReading = currentBillState.value.monthEndMeterReading,
                    electricityUnits = currentBillState.value.electricityUnits,
                    electricityRateAtBillingTime = currentBillState.value.electricityRateAtBillingTime
                )
            }
        }

        tempBill.calculateTotalDue()
        currentBillState.value = tempBill // Update the display state
        onBillUpdated(tempBill)     // Inform the caller about the intermediate state
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
                Text("Base Rent: ${currencyFormat.format(currentBillState.value.rentAtBillingTime)}", style = MaterialTheme.typography.bodyLarge)

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

                if (wasBillForOccupiedRoom && currentBillState.value.electricityUnits != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Units Consumed: ${String.format(Locale.getDefault(), "%.2f", currentBillState.value.electricityUnits)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (wasBillForOccupiedRoom && ((currentBillState.value.electricityUnits != null && currentBillState.value.electricityUnits!! > 0.009) || currentBillState.value.electricityBill > 0.009)) {
                     Spacer(modifier = Modifier.height(4.dp))
                     Text(
                        "Electricity Bill: ${currencyFormat.format(currentBillState.value.electricityBill)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = waterBillString,
                    onValueChange = { if (wasBillForOccupiedRoom) waterBillString = it },
                    label = { Text("Water Bill") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = wasBillForOccupiedRoom
                )
                OutlinedTextField(
                    value = otherChargesString,
                    onValueChange = { if (wasBillForOccupiedRoom) otherChargesString = it },
                    label = { Text("Other Charges") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = wasBillForOccupiedRoom
                )
                OutlinedTextField(
                    value = otherChargesDescription,
                    onValueChange = { if (wasBillForOccupiedRoom) otherChargesDescription = it },
                    label = { Text("Description for Other Charges") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {keyboardController?.hide()}),
                    singleLine = false, modifier = Modifier.fillMaxWidth(),
                    enabled = wasBillForOccupiedRoom
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
                    } else if (abs(balance) < 0.001 && currentBillState.value.amountPaid > 0) {
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
                        enabled = canRecordPayment
                    )
                    if (paymentError != null) { Text(paymentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = {
                            val paymentValue = paymentAmountToRecord.toDoubleOrNull()
                            if (paymentValue == null || paymentValue <= 0) {
                                paymentError = "Enter a valid positive amount"
                            } else {
                                val newAmountPaid = currentBillState.value.amountPaid + paymentValue
                                val isNowFullyPaid = newAmountPaid >= currentBillState.value.totalAmountDue - 0.001

                                var billWithPayment = currentBillState.value.copy(
                                    amountPaid = newAmountPaid,
                                    isFullyPaid = isNowFullyPaid,
                                    paymentDate = if (isNowFullyPaid || currentBillState.value.isFullyPaid) System.currentTimeMillis() else currentBillState.value.paymentDate
                                )
                                // Recalculate total due as payment might affect previous dues if this logic is ever added to calculateTotalDue
                                // For now, it mainly updates the balance displayed based on new payment.
                                billWithPayment.calculateTotalDue() 
                                currentBillState.value = billWithPayment
                                onBillUpdated(billWithPayment) 

                                paymentAmountToRecord = ""
                                paymentError = null
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = canRecordPayment
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
