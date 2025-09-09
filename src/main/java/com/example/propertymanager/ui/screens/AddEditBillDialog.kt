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
import com.example.propertymanager.utils.formatDate // Corrected import
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillDialog(
    bill: MonthlyBillEntity,
    roomName: String,
    onDismiss: () -> Unit,
    onBillUpdated: (MonthlyBillEntity) -> Unit,
    onSave: (MonthlyBillEntity) -> Unit
) {
    val currentBillState = remember(bill) { mutableStateOf(bill.copy()) } // Work on a copy

    val isRoomOccupied = currentBillState.value.tenantNameAtBillingTime != "Not Occupied" && currentBillState.value.rentAtBillingTime >= 0.0

    // Renamed and repurposed for month end meter reading
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

    LaunchedEffect(monthEndMeterReadingString, waterBillString, otherChargesString, otherChargesDescription, isRoomOccupied) {
        val monthEndReading = if (isRoomOccupied) monthEndMeterReadingString.toDoubleOrNull() else null
        val water = if (isRoomOccupied) waterBillString.toDoubleOrNull() ?: 0.0 else 0.0
        val other = if (isRoomOccupied) otherChargesString.toDoubleOrNull() ?: 0.0 else 0.0
        val desc = if (isRoomOccupied) otherChargesDescription.ifBlank { null } else null

        var updatedBillCopy = currentBillState.value.copy(
            monthEndMeterReading = monthEndReading, // Store month end reading
            electricityUnits = null, // To be calculated by ViewModel based on initial and month end
            waterBill = water,
            otherCharges = other,
            otherChargesDescription = desc
        )
        // The actual electricityBill and totalAmountDue will be fully calculated in ViewModel before save
        // For now, calculateTotalDue might use a zero electricity bill or stale units here.
        // We call it to update other parts of totalAmountDue like rent, water, other charges.
        updatedBillCopy.calculateTotalDue() 
        
        currentBillState.value = updatedBillCopy
        onBillUpdated(updatedBillCopy)
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

                if (currentBillState.value.previousMonthDues > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Previous Dues: ${currencyFormat.format(currentBillState.value.previousMonthDues)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = monthEndMeterReadingString,
                    onValueChange = { if(isRoomOccupied) monthEndMeterReadingString = it },
                    label = { Text("Month End Meter Reading") }, // Changed label
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isRoomOccupied
                )
                // Removed electricity cost breakdown preview
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(value = waterBillString, onValueChange = { if(isRoomOccupied) waterBillString = it }, label = { Text("Water Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = otherChargesString, onValueChange = { if(isRoomOccupied) otherChargesString = it }, label = { Text("Other Charges") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)
                OutlinedTextField(value = otherChargesDescription, onValueChange = { if(isRoomOccupied) otherChargesDescription = it }, label = { Text("Description for Other Charges") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = {keyboardController?.hide()}), singleLine = false, modifier = Modifier.fillMaxWidth(), enabled = isRoomOccupied)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Total Due: ${currencyFormat.format(currentBillState.value.totalAmountDue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Payment Status:", style = MaterialTheme.typography.titleSmall)
                Text("Amount Paid: ${currencyFormat.format(currentBillState.value.amountPaid)}", style = MaterialTheme.typography.bodyMedium)
                
                val remainingDue = max(0.0, currentBillState.value.totalAmountDue - currentBillState.value.amountPaid)

                if (currentBillState.value.isFullyPaid) {
                    Text("Status: Fully Paid on ${formatDate(currentBillState.value.paymentDate)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Status: ${currencyFormat.format(remainingDue)} remaining", style = MaterialTheme.typography.bodyMedium)
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
                        enabled = remainingDue > 0 && isRoomOccupied
                    )
                    if (paymentError != null) { Text(paymentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = {
                            val paymentValue = paymentAmountToRecord.toDoubleOrNull()
                            if (paymentValue == null || paymentValue <= 0) {
                                paymentError = "Enter a valid amount"
                            } else if (paymentValue > remainingDue + 0.001) { // Add small tolerance for double comparison
                                paymentError = "Cannot pay more than remaining due (${currencyFormat.format(remainingDue)})"
                            } else {
                                val newAmountPaid = currentBillState.value.amountPaid + paymentValue
                                val isNowFullyPaid = newAmountPaid >= currentBillState.value.totalAmountDue - 0.001 // Tolerance
                                
                                val billWithPayment = currentBillState.value.copy(
                                    amountPaid = newAmountPaid,
                                    isFullyPaid = isNowFullyPaid,
                                    paymentDate = if (isNowFullyPaid) System.currentTimeMillis() else currentBillState.value.paymentDate
                                )
                                billWithPayment.calculateTotalDue() // Recalculate just in case
                                currentBillState.value = billWithPayment
                                onBillUpdated(billWithPayment) // Update parent state
                                paymentAmountToRecord = ""
                                paymentError = null
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = remainingDue > 0 && isRoomOccupied
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
