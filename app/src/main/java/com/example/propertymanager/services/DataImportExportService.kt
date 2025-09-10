package com.example.propertymanager.services

import android.util.Log
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentInstallmentEntity
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.data.model.RoomWithTenant 
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.TenantRepository
import com.example.propertymanager.ui.screens.ExportFormat 
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

data class ImportResult(
    val message: String,
    val successfulRecords: Int,
    val skippedRecords: Int,
    val rowErrors: Int
)

class DataImportExportService(
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository,
    private val monthlyBillRepository: MonthlyBillRepository,
    private val paymentInstallmentRepository: PaymentInstallmentRepository,
    private val tenantRepository: TenantRepository
) {

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun escapeCsv(value: String?): String {
        return "\"${(value ?: "").replace("\"", "\"\"")}\""
    }

    private fun parseCurrencyStringToDouble(currencyString: String?): Double? {
        if (currencyString.isNullOrBlank() || currencyString.equals("N/A", ignoreCase = true)) {
            return null
        }
        try {
            val parsedNumber = currencyFormatter.parse(currencyString.trim())
            if (parsedNumber != null) {
                return parsedNumber.toDouble()
            }
        } catch (e: ParseException) {
            Log.w("DataService", "NumberFormat failed to parse '$currencyString', trying fallback.")
        }
        val sanitizedString = currencyString
            .replace("₹", "").replace("$", "").replace("€", "").replace("£", "")
            .replace(",", "").trim()
        return sanitizedString.toDoubleOrNull()
    }

    private fun parseBoolean(value: String?): Boolean? {
        return when (value?.uppercase(Locale.US)) {
            "TRUE" -> true
            "FALSE" -> false
            else -> null
        }
    }
    
    private fun formatDate(timestamp: Long?): String {
        return if (timestamp != null && timestamp > 0) dateFormat.format(Date(timestamp)) else ""
    }

    private fun parseDate(dateString: String?): Long? {
        if (dateString.isNullOrBlank()) return null
        return try {
            dateFormat.parse(dateString)?.time
        } catch (e: ParseException) {
            Log.w("DataService", "Could not parse date: $dateString", e)
            null
        }
    }

    suspend fun generateExportData(
        properties: List<PropertyEntity>,
        year: Int,
        monthInput: Int?,
        format: ExportFormat
    ): String {
        val mainBuilder = StringBuilder()
        val systemCalendar = Calendar.getInstance() // For current year/month check
        val actualCurrentYear = systemCalendar.get(Calendar.YEAR)
        val actualCurrentMonth = systemCalendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-indexed

        val cal = Calendar.getInstance() // For date manipulations specific to export

        if (format == ExportFormat.TEXT) {
            mainBuilder.append("Property Data Export - ")
            if (monthInput != null) mainBuilder.append("$monthInput/")
            mainBuilder.append("$year\n=====================================\n\n")
        } else { 
            mainBuilder.append(
                "Property Name,Room Name,Year,Month,Tenant Name At Billing Time," +
                "Rent At Billing Time,Electricity Units,Electricity Rate At Billing Time," +
                "Water Bill,Other Charges,Previous Month Dues," +
                "Is Full Rent Applied Override,Month End Meter Reading,Other Charges Description," +
                "Is Initial Reading Rolled Over,Due Date,Payment Date," +
                "Total Amount Due (Calculated by App),Amount Paid (CSV),Is Fully Paid (Calculated by App)\n"
            )
        }

        for (property in properties) {
            val roomsWithTenants: List<RoomWithTenant> = roomRepository.getRoomsForProperty(property.id).first() 
            if (roomsWithTenants.isEmpty()) continue

            val propertySpecificTextBuilder = if (format == ExportFormat.TEXT) StringBuilder() else null

            for (roomHolder in roomsWithTenants) { 
                val room = roomHolder.room 
                val monthsToIterate = if (monthInput != null) listOf(monthInput) else (1..12).toList()

                for (currentMonthInLoop in monthsToIterate) {
                    // Skip future months in the current year for a full year export
                    if (monthInput == null && year == actualCurrentYear && currentMonthInLoop > actualCurrentMonth) {
                        continue
                    }

                    val bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(room.id, year, currentMonthInLoop)
                    
                    val tenantNameForContext = bill?.tenantNameAtBillingTime ?: run {
                        cal.clear(); cal.set(Calendar.YEAR, year); cal.set(Calendar.MONTH, currentMonthInLoop - 1); cal.set(Calendar.DAY_OF_MONTH, 1)
                        val startOfMonth = cal.timeInMillis
                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                        val endOfMonth = cal.timeInMillis
                        tenantRepository.getTenantForBillPeriod(room.id, startOfMonth, endOfMonth)?.name ?: "N/A"
                    }

                    if (bill != null && bill.id != 0) {
                        val installments = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                        bill.amountPaid = installments.sumOf { it.amount } 
                        bill.calculateTotalDue() 

                        if (format == ExportFormat.CSV) {
                            val row = mutableListOf<String>()
                            row.add(escapeCsv(property.name))
                            row.add(escapeCsv(room.name))
                            row.add(year.toString())
                            row.add(currentMonthInLoop.toString())
                            row.add(escapeCsv(bill.tenantNameAtBillingTime ?: tenantNameForContext))
                            row.add(bill.rentAtBillingTime.toString())
                            row.add(bill.electricityUnits?.toString() ?: "")
                            row.add(bill.electricityRateAtBillingTime?.toString() ?: "")
                            row.add(bill.waterBill.toString())
                            row.add(bill.otherCharges.toString())
                            row.add(bill.previousMonthDues.toString())
                            row.add(bill.isFullRentAppliedOverride?.toString()?.uppercase(Locale.US) ?: "")
                            row.add(bill.monthEndMeterReading?.toString() ?: "")
                            row.add(escapeCsv(bill.otherChargesDescription))
                            row.add(bill.isInitialReadingRolledOver.toString().uppercase(Locale.US))
                            row.add(formatDate(bill.dueDate))
                            row.add(formatDate(bill.paymentDate))
                            row.add(bill.totalAmountDue.toString())
                            row.add(bill.amountPaid.toString())
                            row.add(bill.isFullyPaid.toString().uppercase(Locale.US))
                            mainBuilder.append(row.joinToString(",")).append("\n")
                        } else { 
                            propertySpecificTextBuilder?.apply {
                                append("  Room: ${room.name}\n")
                                append("    Tenant: ${bill.tenantNameAtBillingTime ?: tenantNameForContext}\n")
                                append("    Period: $currentMonthInLoop/$year\n")
                                append("    Rent Applied: ${currencyFormatter.format(bill.rentAtBillingTime)}\n")
                                bill.electricityUnits?.let { eu -> append("    Electricity Units: $eu at ${bill.electricityRateAtBillingTime?.let { currencyFormatter.format(it) } ?: "N/A"}\n") }
                                append("    Water Bill: ${currencyFormatter.format(bill.waterBill)}\n")
                                append("    Other Charges: ${currencyFormatter.format(bill.otherCharges)} (${bill.otherChargesDescription ?: ""})\n")
                                append("    Previous Dues: ${currencyFormatter.format(bill.previousMonthDues)}\n")
                                append("    Calculated Bill: ${currencyFormatter.format(bill.totalAmountDue)}\n")
                                append("    Amount Paid: ${currencyFormatter.format(bill.amountPaid)}\n")
                                append("    Status: ${if (bill.isFullyPaid) "Paid" else "Due: ${currencyFormatter.format(bill.totalAmountDue - bill.amountPaid)}"}\n")
                                append("    Due Date: ${formatDate(bill.dueDate)}\n")
                                bill.paymentDate?.let { pd -> append("    Payment Date: ${formatDate(pd)}\n") }
                                append("    --------------------------\n")
                            }
                        }
                    } else { 
                        if (format == ExportFormat.CSV) {
                            val row = mutableListOf<String>()
                            row.add(escapeCsv(property.name))
                            row.add(escapeCsv(room.name))
                            row.add(year.toString())
                            row.add(currentMonthInLoop.toString())
                            row.add(escapeCsv(tenantNameForContext))
                            repeat(15) { row.add("") }
                            mainBuilder.append(row.joinToString(",")).append("\n")
                        } else { 
                             propertySpecificTextBuilder?.apply {
                                append("  Room: ${room.name}\n")
                                append("    Tenant: $tenantNameForContext\n")
                                append("    Period: $currentMonthInLoop/$year\n")
                                append("    Status: No Bill Generated\n")
                                append("    --------------------------\n")
                            }
                        }
                    }
                }
            }
            if (format == ExportFormat.TEXT && propertySpecificTextBuilder != null && propertySpecificTextBuilder.isNotEmpty()) {
                mainBuilder.append("Property: ${property.name}\n").append(propertySpecificTextBuilder).append("\n")
            }
        }
        Log.d("DataImportExportService", "${format.name} Data Generated: Length ${mainBuilder.length}")
        return mainBuilder.toString()
    }

    suspend fun processImportData(csvString: String): ImportResult {
        var processedRecords = 0
        var successfulRecords = 0
        var skippedRecords = 0
        var rowErrors = 0
        val errorMessages = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            val lines = csvString.lines()
            if (lines.size <= 1) {
                errorMessages.add("Import failed: CSV is empty or has only a header.")
            } else {
                val importTimestamp = Calendar.getInstance().timeInMillis

                for (i in 1 until lines.size) { 
                    val line = lines[i].trim()
                    if (line.isEmpty()) {
                        skippedRecords++
                        continue
                    }
                    processedRecords++
                    val tokens = line.split(',').map { it.trim().removeSurrounding("\"") } 

                    if (tokens.size < 20) {
                        Log.w("ImportCSV", "Skipping malformed line ${i + 1} (not enough columns: ${tokens.size}): $line")
                        errorMessages.add("Line ${i + 1}: Malformed (expected 20 columns, got ${tokens.size}).")
                        rowErrors++; continue
                    }
                    
                    try {
                        val propertyNameStr = tokens[0]
                        val roomNameStr = tokens[1]
                        val yearVal = tokens[2].toIntOrNull()
                        val monthVal = tokens[3].toIntOrNull()
                        val tenantNameAtBillingTimeStr = tokens[4]
                        
                        val rentAtBillingTime = parseCurrencyStringToDouble(tokens[5]) ?: 0.0
                        val electricityUnits = parseCurrencyStringToDouble(tokens[6])
                        val electricityRateAtBillingTime = parseCurrencyStringToDouble(tokens[7])
                        val waterBill = parseCurrencyStringToDouble(tokens[8]) ?: 0.0
                        val otherCharges = parseCurrencyStringToDouble(tokens[9]) ?: 0.0
                        val previousMonthDues = parseCurrencyStringToDouble(tokens[10]) ?: 0.0
                        
                        val isFullRentAppliedOverride = parseBoolean(tokens[11])
                        val monthEndMeterReading = parseCurrencyStringToDouble(tokens[12])
                        val otherChargesDescription = tokens[13].takeIf { it.isNotBlank() }
                        val isInitialReadingRolledOver = parseBoolean(tokens[14]) ?: false
                        
                        val dueDateVal = parseDate(tokens[15]) ?: Calendar.getInstance().apply { set(yearVal ?: 0, (monthVal ?: 1)-1, 5) }.timeInMillis
                        val paymentDateFromCsv = parseDate(tokens[16])
                        
                        val totalAmountDueFromCsv = parseCurrencyStringToDouble(tokens[17])
                        val amountPaidFromCsv = parseCurrencyStringToDouble(tokens[18]) ?: 0.0
                        val isFullyPaidFromCsv = parseBoolean(tokens[19])

                        if (yearVal == null || monthVal == null || monthVal !in 1..12) {
                             Log.w("ImportCSV", "Skipping line ${i+1} (invalid year/month): $line"); errorMessages.add("Line ${i+1}: Invalid year/month."); rowErrors++; continue
                        }

                        var currentProperty = propertyRepository.getByName(propertyNameStr)
                        if (currentProperty == null) {
                            val newPropId = propertyRepository.insert(PropertyEntity(name = propertyNameStr, address = "Imported on ${dateTimeFormat.format(Date(importTimestamp))}", isHidden = false))
                            currentProperty = propertyRepository.getByName(propertyNameStr) 
                            if (currentProperty == null) {
                                Log.e("ImportCSV", "Failed to create/retrieve property $propertyNameStr. Line ${i+1}"); errorMessages.add("Line ${i+1}: Property creation failed."); rowErrors++; continue
                            }
                        }

                        var currentRoom = roomRepository.getByNameAndPropertyId(roomNameStr, currentProperty.id)
                        if (currentRoom == null) {
                            val newRoomId = roomRepository.insert(RoomEntity(propertyId = currentProperty.id, name = roomNameStr, rent = rentAtBillingTime, isHidden = false))
                            currentRoom = roomRepository.getByNameAndPropertyId(roomNameStr, currentProperty.id) 
                            if (currentRoom == null) {
                                 Log.e("ImportCSV", "Failed to create/retrieve room $roomNameStr for property ${currentProperty.name}. Line ${i+1}"); errorMessages.add("Line ${i+1}: Room creation failed."); rowErrors++; continue
                            }
                        } else { 
                            if (currentRoom.rent != rentAtBillingTime) {
                                // Consider if room's default rent should be updated, e.g.:
                                // roomRepository.updateRoomRent(currentRoom.id, rentAtBillingTime) // Requires Dao method
                            }
                        }
                        
                        var tenantIdForBill: Int? = null
                        if (tenantNameAtBillingTimeStr.isNotBlank() && !tenantNameAtBillingTimeStr.equals("N/A", ignoreCase = true)) {
                            var tenantForBill = tenantRepository.getTenantByNameAndRoomId(tenantNameAtBillingTimeStr, currentRoom.id)
                            if (tenantForBill == null) {
                                 tenantForBill = tenantRepository.getByName(tenantNameAtBillingTimeStr)
                                 if (tenantForBill != null && tenantForBill.roomId != currentRoom.id) tenantForBill = null
                            }
                            if (tenantForBill == null) {
                                val calMoveIn = Calendar.getInstance().apply { set(yearVal, monthVal - 1, 1) }
                                val newTenant = TenantEntity(
                                    roomId = currentRoom.id,
                                    name = tenantNameAtBillingTimeStr, 
                                    mobile = "", 
                                    moveInDate = calMoveIn.timeInMillis,
                                    imageUri = null, 
                                    assetUris = emptyList()
                                )
                                val newTenantId = tenantRepository.insertOrUpdateTenant(newTenant)
                                tenantIdForBill = newTenantId.toInt().takeIf { it > 0 }
                            } else {
                                tenantIdForBill = tenantForBill.id
                            }
                        }

                        var bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(currentRoom.id, yearVal, monthVal)
                        
                        if (bill == null) {
                            bill = MonthlyBillEntity(
                                roomId = currentRoom.id,
                                year = yearVal,
                                month = monthVal
                            )
                        }

                        bill.tenantIdAtBillingTime = tenantIdForBill
                        bill.tenantNameAtBillingTime = if (tenantIdForBill != null) tenantNameAtBillingTimeStr else null
                        bill.rentAtBillingTime = rentAtBillingTime
                        bill.isFullRentAppliedOverride = isFullRentAppliedOverride
                        bill.monthEndMeterReading = monthEndMeterReading
                        bill.electricityUnits = electricityUnits
                        bill.electricityRateAtBillingTime = electricityRateAtBillingTime
                        bill.waterBill = waterBill
                        bill.otherCharges = otherCharges
                        bill.otherChargesDescription = otherChargesDescription
                        bill.previousMonthDues = previousMonthDues
                        bill.dueDate = dueDateVal
                        bill.isInitialReadingRolledOver = isInitialReadingRolledOver
                        
                        bill.amountPaid = amountPaidFromCsv 
                        bill.paymentDate = paymentDateFromCsv 

                        bill.calculateTotalDue()

                        if (totalAmountDueFromCsv != null && abs(totalAmountDueFromCsv - bill.totalAmountDue) > 0.01) {
                            Log.w("ImportCSV", "Line ${i+1}: TotalAmountDue from CSV ($totalAmountDueFromCsv) differs from app calculation (${bill.totalAmountDue}). Using app calculation.")
                            errorMessages.add("Line ${i+1}: Bill total mismatch (CSV: $totalAmountDueFromCsv, App: ${bill.totalAmountDue}).")
                        }
                         if (isFullyPaidFromCsv != null && isFullyPaidFromCsv != bill.isFullyPaid) {
                            Log.w("ImportCSV", "Line ${i+1}: IsFullyPaid from CSV ($isFullyPaidFromCsv) differs from app calculation (${bill.isFullyPaid}). Using app calculation.")
                             errorMessages.add("Line ${i+1}: Payment status mismatch (CSV: $isFullyPaidFromCsv, App: ${bill.isFullyPaid}).")
                        }

                        val billId = monthlyBillRepository.upsertBill(bill)

                        if (billId <= 0) {
                            Log.e("ImportCSV", "Failed to upsert bill for room ${currentRoom.name}, $monthVal/$yearVal. Line ${i+1}"); errorMessages.add("Line ${i+1}: Bill upsert failed."); rowErrors++; continue
                        }

                        paymentInstallmentRepository.deleteInstallmentsForBill(billId.toInt())
                        if (amountPaidFromCsv > 0.001) {
                            val paymentTimestamp = paymentDateFromCsv ?: Calendar.getInstance().apply { set(yearVal, monthVal - 1, 1) }.timeInMillis
                            val newInstallment = PaymentInstallmentEntity(
                                billId = billId.toInt(),
                                amount = amountPaidFromCsv,
                                date = paymentTimestamp
                            )
                            paymentInstallmentRepository.addInstallment(newInstallment)
                        }
                        successfulRecords++

                    } catch (e: Exception) {
                        Log.e("ImportCSV", "Error processing line ${i + 1}: $line", e)
                        errorMessages.add("Line ${i + 1}: Error - ${e.message?.take(50) ?: "Unknown"}.")
                        rowErrors++
                    }
                }
            }
        }

        val finalMessage = StringBuilder("Import process finished.\n")
        finalMessage.append("$successfulRecords record(s) processed successfully.\n")
        if (rowErrors > 0) finalMessage.append("$rowErrors record(s) had errors.\n")
        if (skippedRecords > 0) finalMessage.append("$skippedRecords empty line(s) skipped.\n")
        if (errorMessages.isNotEmpty() && rowErrors > 0) {
             finalMessage.append("First few errors:\n${errorMessages.take(5).joinToString("\n")}")
        }

        return ImportResult(finalMessage.toString(), successfulRecords, skippedRecords, rowErrors)
    }
}
