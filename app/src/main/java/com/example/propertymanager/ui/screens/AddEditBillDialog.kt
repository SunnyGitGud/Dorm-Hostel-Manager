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
import androidx.compose.ui.graphics.Color
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

// Removed local PositiveGreenColor definition, will use the one from BillHistoryComponents.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillDialog(
    bill: MonthlyBillEntity,
    roomName: String,
    currentRoomInitialMeterReading: Double?,
    currentRoomElectricityRate: Double,
    onDismiss: () -> Unit,
    onBillUpdated: (MonthlyBillEntity) -> Unit,
    onSave: (MonthlyBillEntity) -> Unit
) {
    val currentBillState = remember(bill) { mutableStateOf(bill.copy()) }

    val isRoomOccupied = currentBillState.value.tenantNameAtBillingTime != "Not Occupied" && currentBillState.value.rentAtBillingTime >= 0.0
    val isMeterReadingEditable = isRoomOccupied && !currentBillState.value.isInitialReadingRolledOver

    var monthEndMeterReadingString by remember(currentBillState.value.id, currentBillState.value.monthEndMeterReading, isRoomOccupied) {
        mutableStateOf(if (isRoomOccupied) currentBillState.value.monthEndMeterReading?.toString() ?: "" else "")
    }
    var waterBillString by remember(currentBillState.value.id, currentBillState.value.waterBill, isRoomOccupied) {
        mutableStateOf(if (isRoomOccupied) currentBillState.value.waterBill.takeIf { it > 0 }?.toString() ?: "" else "0.0")
    }
    var otherChargesString by remember(currentBillState.value.id, currentBillState.value.otherCharges, isRoomOccupied) {
        mutableStateOf(if (isRoomOccupied) currentBillState.value.otherCharges.takeIf { it > 0 }?.toString() ?: "" else "0.0")
    }
    var otherChargesDescription by remember(currentBillState.value.id, currentBillState.value.otherChargesDescription, isRoomOccupied) {
        mutableStateOf(if (isRoomOccupied) currentBillState.value.otherChargesDescription ?: "" else "")
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
        isRoomOccupied,
        currentBillState.value.isInitialReadingRolledOver,
        currentRoomInitialMeterReading,
        currentRoomElectricityRate
    ) {
        val newMonthEndReadingDouble = if (isRoomOccupied) monthEndMeterReadingString.toDoubleOrNull() else null
        val newWater = if (isRoomOccupied) waterBillString.toDoubleOrNull() ?: 0.0 else 0.0
        val newOther = if (isRoomOccupied) otherChargesString.toDoubleOrNull() ?: 0.0 else 0.0
        val newDesc = if (isRoomOccupied) otherChargesDescription.ifBlank { null } else null

        val billForDisplay = currentBillState.value.copy(
            monthEndMeterReading = newMonthEndReadingDouble,
            waterBill = newWater,
            otherCharges = newOther,
            otherChargesDescription = newDesc
        )

        if (isMeterReadingEditable && isRoomOccupied) {
            if (currentRoomInitialMeterReading != null && newMonthEndReadingDouble != null && newMonthEndReadingDouble >= currentRoomInitialMeterReading) {
                billForDisplay.electricityUnits = newMonthEndReadingDouble - currentRoomInitialMeterReading
            } else {
                billForDisplay.electricityUnits = 0.0
            }
            billForDisplay.electricityRateAtBillingTime = currentRoomElectricityRate
        } else if (isRoomOccupied) {
            // Preserve existing units and rate for display
        } else { // Not occupied
             billForDisplay.electricityUnits = 0.0
             billForDisplay.electricityRateAtBillingTime = currentRoomElectricityRate
        }

        billForDisplay.calculateTotalDue()
        currentBillState.value = billForDisplay

        val finalBillForViewModel = currentBillState.value.copy(
            electricityUnits = if (isMeterReadingEditable) {
                                 null
                             } else {
                                 currentBillState.value.electricityUnits
                             },
            electricityRateAtBillingTime = if (isMeterReadingEditable) {
                                              currentRoomElectricityRate
                                          } else {
                                              currentBillState.value.electricityRateAtBillingTime
                                          }
        )
        onBillUpdated(finalBillForViewModel)
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

                // Display Previous Dues or Credit
                val previousDuesOrCredit = currentBillState.value.previousMonthDues
                if (previousDuesOrCredit != 0.0) { // Only display if there's a non-zero due or credit
                    Spacer(modifier = Modifier.height(4.dp))
                    if (previousDuesOrCredit > 0) {
                        Text(
                            "Previous Dues: ${currencyFormat.format(previousDuesOrCredit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else { // previousDuesOrCredit < 0, it's a credit
                        Text(
                            "Previous Credit: ${currencyFormat.format(abs(previousDuesOrCredit))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PositiveGreenColor // Use the green color for credit
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = monthEndMeterReadingString,
                    onValueChange = { if(isRoomOccupied) monthEndMeterReadingString = it },
                    label = { Text("Month End Meter Reading") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isMeterReadingEditable
                )

                if (isRoomOccupied && currentBillState.value.electricityUnits != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Units Consumed: ${String.format(Locale.getDefault(), "%.2f", currentBillState.value.electricityUnits)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isRoomOccupied && (currentBillState.value.electricityUnits != null && currentBillState.value.electricityUnits!! > 0.009 || currentBillState.value.electricityBill > 0.009)) {
                     Spacer(modifier = Modifier.height(4.dp))
                     Text(
                        "Electricity Bill: ${currencyFormat.format(currentBillState.value.electricityBill)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = waterBillString, onValueChange = { if(isRoomOccupied) waterBillString = it }, label = { Text("Water Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = otherChargesString, onValueChange = { if(isRoomOccupied) otherChargesString = it }, label = { Text("Other Charges") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = otherChargesDescription, onValueChange = { if(isRoomOccupied) otherChargesDescription = it }, label = { Text("Description for Other Charges") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = {keyboardController?.hide()}), singleLine = false, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Total Due: ${currencyFormat.format(currentBillState.value.totalAmountDue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Payment Status:", style = MaterialTheme.typography.titleSmall)
                Text("Amount Paid: ${currencyFormat.format(currentBillState.value.amountPaid)}", style = MaterialTheme.typography.bodyMedium)

                val balance = currentBillState.value.amountPaid - currentBillState.value.totalAmountDue

                if (currentBillState.value.isFullyPaid) {
                    Text(
                        text = "Status: Fully Paid on ${formatDate(currentBillState.value.paymentDate)}",
                        color = PositiveGreenColor, // Using the green color for consistency
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (balance > 0.001) { // Show advance if there's a significant positive balance
                        Text(
                            text = "Advance: ${currencyFormat.format(balance)}",
                            color = PositiveGreenColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    // If not fully paid, show options to make a payment
                    val remainingDueForPayment = max(0.0, currentBillState.value.totalAmountDue - currentBillState.value.amountPaid)
                    if (balance < -0.001) { // If there are dues
                         Text("Status: ${currencyFormat.format(remainingDueForPayment)} remaining", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else if (balance >= 0 && balance < 0.001 && currentBillState.value.amountPaid > 0) { // Paid exactly or very close, but not marked fully paid
                        Text("Status: Cleared (Pending Confirmation)", style = MaterialTheme.typography.bodyMedium)
                    } else { // Covers balance > 0 (advance) or amountPaid = 0 for a non-zero bill
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
                        enabled = isRoomOccupied // Allow payment input even if remainingDue is 0, to record advance
                    )
                    if (paymentError != null) { Text(paymentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = {
                            val paymentValue = paymentAmountToRecord.toDoubleOrNull()
                            if (paymentValue == null || paymentValue <= 0) {
                                paymentError = "Enter a valid positive amount"
                            } else {
                                val newAmountPaid = currentBillState.value.amountPaid + paymentValue
                                // Bill is fully paid if new amount paid is >= total due (with tolerance)
                                val isNowFullyPaid = newAmountPaid >= currentBillState.value.totalAmountDue - 0.001

                                val billWithPayment = currentBillState.value.copy(
                                    amountPaid = newAmountPaid,
                                    isFullyPaid = isNowFullyPaid,
                                    // Update paymentDate if it's newly fully paid or if it's already fully paid and another payment is made
                                    paymentDate = if (isNowFullyPaid || currentBillState.value.isFullyPaid) System.currentTimeMillis() else currentBillState.value.paymentDate
                                )
                                billWithPayment.calculateTotalDue()
                                currentBillState.value = billWithPayment

                                val finalBillForViewModel = currentBillState.value.copy(
                                    electricityUnits = if (isMeterReadingEditable) null else currentBillState.value.electricityUnits,
                                    electricityRateAtBillingTime = if (isMeterReadingEditable) currentRoomElectricityRate else currentBillState.value.electricityRateAtBillingTime
                                )
                                onBillUpdated(finalBillForViewModel)

                                paymentAmountToRecord = ""
                                paymentError = null
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = isRoomOccupied // Allow payment button if room is occupied
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
