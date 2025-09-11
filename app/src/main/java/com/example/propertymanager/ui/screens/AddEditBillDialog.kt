package com.example.propertymanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentInstallment
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.utils.formatDate
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

val PositiveGreenColor = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillDialog(
    bill: MonthlyBillEntity,
    roomViewModel: RoomViewModel,
    roomName: String,
    isCurrentActiveBill: Boolean,
    currentRoomInitialMeterReading: Double?,
    currentRoomElectricityRate: Double,
    fullRoomRent: Double,
    onDismiss: () -> Unit,
    onSave: (MonthlyBillEntity) -> Unit
) {
    val currentBillState = remember(bill, bill.id) { 
        mutableStateOf(bill.copy().apply {
            installments = bill.installments 
        })
    }

    val initialRentForThisDialogInstance = remember(bill, bill.id, bill.rentAtBillingTime) {
        bill.rentAtBillingTime
    }

    var applyFullRentOverride by remember(bill.id, bill.isFullRentAppliedOverride) {
        mutableStateOf(bill.isFullRentAppliedOverride ?: false)
    }

    val wasBillForOccupiedRoomInitially = remember(bill.id) {
        bill.tenantNameAtBillingTime != "Not Occupied" && bill.rentAtBillingTime >= 0.0
    }

    val isMeterReadingActuallyEditable = remember(bill.id, isCurrentActiveBill, wasBillForOccupiedRoomInitially) {
        isCurrentActiveBill && wasBillForOccupiedRoomInitially && !bill.isInitialReadingRolledOver
    }

    val canRecordPayment = remember(bill.id) {
        bill.tenantIdAtBillingTime != null
    }

    val showFullRentOverrideCheckbox = remember(bill.id, isCurrentActiveBill, wasBillForOccupiedRoomInitially, initialRentForThisDialogInstance, fullRoomRent, bill.isFullRentAppliedOverride) {
        isCurrentActiveBill &&
        wasBillForOccupiedRoomInitially &&
        fullRoomRent > 0 &&
        bill.isFullRentAppliedOverride == null && 
        (initialRentForThisDialogInstance < fullRoomRent - 0.001) 
    }

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

    val installmentsToDisplay = currentBillState.value.installments

    LaunchedEffect(
        applyFullRentOverride,
        initialRentForThisDialogInstance, fullRoomRent,
        monthEndMeterReadingString, waterBillString, otherChargesString, otherChargesDescription,
        wasBillForOccupiedRoomInitially, isMeterReadingActuallyEditable,
        currentRoomInitialMeterReading, currentRoomElectricityRate,
        currentBillState.value.installments, // Keep this to react to new payments
        currentBillState.value.amountPaid,   // Keep this to react to new payments
        showFullRentOverrideCheckbox
    ) {
        // Capture the state at the very beginning of this effect's execution
        val originalBillSnapshot = currentBillState.value

        val definitiveIsFullRentAppliedOverride = if (showFullRentOverrideCheckbox) {
            applyFullRentOverride
        } else {
            bill.isFullRentAppliedOverride ?: false
        }

        val newRentToApply = if (definitiveIsFullRentAppliedOverride) {
            fullRoomRent
        } else {
            initialRentForThisDialogInstance
        }

        // Start with a copy for modifications
        var workingBill = originalBillSnapshot.copy(
            rentAtBillingTime = newRentToApply,
            isFullRentAppliedOverride = definitiveIsFullRentAppliedOverride
        )

        if (wasBillForOccupiedRoomInitially) {
            workingBill = workingBill.copy(
                waterBill = waterBillString.toDoubleOrNull() ?: 0.0,
                otherCharges = otherChargesString.toDoubleOrNull() ?: 0.0,
                otherChargesDescription = otherChargesDescription.ifBlank { null }
            )
        } else {
            workingBill = workingBill.copy(
                waterBill = 0.0, otherCharges = 0.0, otherChargesDescription = null,
                monthEndMeterReading = null, electricityUnits = 0.0
            )
        }

        if (isMeterReadingActuallyEditable) {
            val newMonthEndReadingDouble = monthEndMeterReadingString.toDoubleOrNull()
            workingBill = workingBill.copy(monthEndMeterReading = newMonthEndReadingDouble)
            if (currentRoomInitialMeterReading != null && newMonthEndReadingDouble != null && newMonthEndReadingDouble >= currentRoomInitialMeterReading) {
                workingBill = workingBill.copy(
                    electricityUnits = newMonthEndReadingDouble - currentRoomInitialMeterReading,
                    electricityRateAtBillingTime = currentRoomElectricityRate
                )
            } else {
                workingBill = workingBill.copy(electricityUnits = 0.0, electricityRateAtBillingTime = currentRoomElectricityRate)
            }
        } else {
            // If meter reading not editable, ensure original values from the initial bill prop are kept
            workingBill = workingBill.copy(
                monthEndMeterReading = bill.monthEndMeterReading,
                electricityUnits = bill.electricityUnits,
                electricityRateAtBillingTime = bill.electricityRateAtBillingTime
            )
        }

        // Restore installments and amountPaid from the snapshot *after* all other .copy() operations
        workingBill.installments = originalBillSnapshot.installments
        workingBill.amountPaid = originalBillSnapshot.amountPaid // Ensures amountPaid is what it was before this effect ran,
                                                                 // unless explicitly changed by adding a new payment (handled elsewhere)

        workingBill.calculateTotalDue() // Now calculate totals with correct installments and amountPaid
        currentBillState.value = workingBill
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bill for $roomName - $billPeriod") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Tenant: ${currentBillState.value.tenantNameAtBillingTime ?: "N/A"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                Text("Base Rent: ${currencyFormat.format(currentBillState.value.rentAtBillingTime)}", style = MaterialTheme.typography.bodyLarge)

                if (showFullRentOverrideCheckbox) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { applyFullRentOverride = !applyFullRentOverride }.padding(vertical = 4.dp)) {
                        Checkbox(checked = applyFullRentOverride, onCheckedChange = { applyFullRentOverride = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Charge Full Month's Rent (${currencyFormat.format(fullRoomRent)})")
                    }
                }

                val previousDuesOrCredit = currentBillState.value.previousMonthDues
                if (previousDuesOrCredit != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (previousDuesOrCredit > 0) {
                        Text("Previous Dues: ${currencyFormat.format(previousDuesOrCredit)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Previous Credit: ${currencyFormat.format(abs(previousDuesOrCredit))}", style = MaterialTheme.typography.bodyMedium, color = PositiveGreenColor)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = monthEndMeterReadingString, onValueChange = { if (isMeterReadingActuallyEditable) monthEndMeterReadingString = it }, label = { Text("Month End Meter Reading") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = isMeterReadingActuallyEditable)
                if (wasBillForOccupiedRoomInitially && currentBillState.value.electricityUnits != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Units Consumed: ${String.format(Locale.getDefault(), "%.2f", currentBillState.value.electricityUnits)}", style = MaterialTheme.typography.bodyMedium)
                }
                if (wasBillForOccupiedRoomInitially && ((currentBillState.value.electricityUnits != null && currentBillState.value.electricityUnits!! > 0.009) || currentBillState.value.electricityBill > 0.009)) {
                     Spacer(modifier = Modifier.height(4.dp))
                     Text("Electricity Bill: ${currencyFormat.format(currentBillState.value.electricityBill)}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = waterBillString, onValueChange = { if (wasBillForOccupiedRoomInitially) waterBillString = it }, label = { Text("Water Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = wasBillForOccupiedRoomInitially)
                OutlinedTextField(value = otherChargesString, onValueChange = { if (wasBillForOccupiedRoomInitially) otherChargesString = it }, label = { Text("Other Charges") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = wasBillForOccupiedRoomInitially)
                OutlinedTextField(value = otherChargesDescription, onValueChange = { if (wasBillForOccupiedRoomInitially) otherChargesDescription = it }, label = { Text("Description for Other Charges") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = {keyboardController?.hide()}), singleLine = false, modifier = Modifier.fillMaxWidth(), enabled = wasBillForOccupiedRoomInitially)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Total Due: ${currencyFormat.format(currentBillState.value.totalAmountDue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Payment Status:", style = MaterialTheme.typography.titleSmall)
                Text("Amount Paid: ${currencyFormat.format(currentBillState.value.amountPaid)}", style = MaterialTheme.typography.bodyMedium)

                val balance = currentBillState.value.amountPaid - currentBillState.value.totalAmountDue

                if (currentBillState.value.isFullyPaid) {
                    Text(text = "Status: Fully Paid on ${formatDate(currentBillState.value.paymentDate)}", color = PositiveGreenColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    if (balance > 0.001) {
                        Text(text = "Advance: ${currencyFormat.format(balance)}", color = PositiveGreenColor, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    val remainingDueForPayment = max(0.0, currentBillState.value.totalAmountDue - currentBillState.value.amountPaid)
                     if (currentBillState.value.amountPaid > 0 && remainingDueForPayment < 0.001 && currentBillState.value.totalAmountDue > 0) {
                        Text("Status: Fully Paid (Pending Save of Bill Changes)", style = MaterialTheme.typography.bodyMedium, color = PositiveGreenColor)
                    } else if (remainingDueForPayment > 0) {
                         Text("Status: ${currencyFormat.format(remainingDueForPayment)} remaining", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else {
                         Text("Status: ${currencyFormat.format(remainingDueForPayment)} remaining", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (installmentsToDisplay.isNotEmpty()) {
                        Text("Payment History (Installments):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 150.dp).padding(bottom = 8.dp)) {
                            items(installmentsToDisplay) { payment -> 
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(currencyFormat.format(payment.amount), style = MaterialTheme.typography.bodyMedium)
                                        Text(formatDate(payment.date), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = paymentAmountToRecord,
                        onValueChange = { paymentAmountToRecord = it; paymentError = null },
                        label = { Text("Record New Payment Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        isError = paymentError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canRecordPayment && currentBillState.value.totalAmountDue > 0 && !currentBillState.value.isFullyPaid
                    )
                    if (paymentError != null) { Text(paymentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = {
                            val paymentValue = paymentAmountToRecord.toDoubleOrNull()
                            if (paymentValue == null || paymentValue <= 0) {
                                paymentError = "Enter a valid positive amount"
                            } else {
                                val newAmountPaid = currentBillState.value.amountPaid + paymentValue
                                val newInstallment = PaymentInstallment(amount = paymentValue, date = System.currentTimeMillis())
                                val newInstallmentsList = currentBillState.value.installments + newInstallment
                                
                                val updatedBill = currentBillState.value.copy(
                                    amountPaid = newAmountPaid,
                                    paymentDate = System.currentTimeMillis() 
                                )
                                updatedBill.installments = newInstallmentsList 
                                updatedBill.calculateTotalDue() 
                                
                                currentBillState.value = updatedBill
                                
                                paymentAmountToRecord = ""
                                paymentError = null
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = canRecordPayment && currentBillState.value.totalAmountDue > 0 && !currentBillState.value.isFullyPaid
                    ) {
                        Icon(Icons.Filled.Payment, contentDescription = "Record Payment", modifier = Modifier.padding(end = 4.dp))
                        Text("Record New Payment")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                currentBillState.value.calculateTotalDue() // Final calculation before save
                onSave(currentBillState.value) 
            }) { Text("Save Bill Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
