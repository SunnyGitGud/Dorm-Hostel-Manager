package com.example.propertymanager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentInstallmentEntity
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.TenantRepository
import com.example.propertymanager.ui.screens.RoomSummaryForPropertyCard
import com.example.propertymanager.ui.screens.BillStatusType
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.ui.screens.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

data class PropertyFinancialSummary(
    val totalDue: Double = 0.0,
    val totalAdvance: Double = 0.0
)

class PropertyViewModel(
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository,
    private val monthlyBillRepository: MonthlyBillRepository,
    private val paymentInstallmentRepository: PaymentInstallmentRepository,
    private val tenantRepository: TenantRepository
) : ViewModel() {

    private val _properties = MutableStateFlow<List<PropertyEntity>>(emptyList())
    val properties: StateFlow<List<PropertyEntity>> = _properties.asStateFlow()

    private val _propertyFinancialSummaries = MutableStateFlow<Map<Int, PropertyFinancialSummary>>(emptyMap())
    val propertyFinancialSummaries: StateFlow<Map<Int, PropertyFinancialSummary>> = _propertyFinancialSummaries.asStateFlow()

    private val _propertyRoomSummaries = MutableStateFlow<Map<Int, List<RoomSummaryForPropertyCard>>>(emptyMap())
    val propertyRoomSummaries: StateFlow<Map<Int, List<RoomSummaryForPropertyCard>>> = _propertyRoomSummaries.asStateFlow()

    private val _exportCsvData = MutableStateFlow<String?>(null)
    val exportCsvData: StateFlow<String?> = _exportCsvData.asStateFlow()

    private val _exportTextData = MutableStateFlow<String?>(null)
    val exportTextData: StateFlow<String?> = _exportTextData.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

    init {
        viewModelScope.launch {
            propertyRepository.getAllProperties().collectLatest { propertyList ->
                _properties.value = propertyList
                updatePropertyFinancialSummaries(propertyList)
                updatePropertyRoomSummaries(propertyList)
            }
        }
    }

    fun clearExportData() {
        _exportCsvData.value = null
    }

    fun clearExportTextData() {
        _exportTextData.value = null
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }

    private suspend fun calculatePropertyFinancialSummary(property: PropertyEntity): PropertyFinancialSummary {
        var currentPropertyTotalDue = 0.0
        var currentPropertyTotalAdvance = 0.0
        val rooms: List<RoomWithTenant> = roomRepository.getRoomsForProperty(property.id).first()
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        for (roomWithTenant in rooms) {
            if (roomWithTenant.tenant != null && roomWithTenant.tenant.moveOutDate == null) {
                val bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomWithTenant.room.id, currentYear, currentMonth)
                if (bill != null && bill.id != 0) {
                    val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                    val amountPaidForBill = installmentEntities.sumOf { it.amount }
                    bill.calculateTotalDue()
                    val balance = bill.totalAmountDue - amountPaidForBill
                    if (balance > 0.001) {
                        currentPropertyTotalDue += balance
                    } else if (balance < -0.001) {
                        currentPropertyTotalAdvance += abs(balance)
                    }
                }
            }
        }
        return PropertyFinancialSummary(
            totalDue = ceil(currentPropertyTotalDue),
            totalAdvance = ceil(currentPropertyTotalAdvance)
        )
    }

    private fun updatePropertyFinancialSummaries(propertyList: List<PropertyEntity>) {
        viewModelScope.launch {
            val summaries = mutableMapOf<Int, PropertyFinancialSummary>()
            for (property in propertyList) {
                if (!property.isHidden) {
                    summaries[property.id] = calculatePropertyFinancialSummary(property)
                }
            }
            _propertyFinancialSummaries.value = summaries
        }
    }

    private suspend fun generateRoomSummariesForProperty(property: PropertyEntity): List<RoomSummaryForPropertyCard> {
        val roomSummaries = mutableListOf<RoomSummaryForPropertyCard>()
        val rooms = roomRepository.getRoomsForProperty(property.id).first()
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        for (roomWithTenant in rooms) {
            val roomName = roomWithTenant.room.name
            val tenantName = roomWithTenant.tenant?.name?.takeIf { roomWithTenant.tenant.moveOutDate == null }
            var statusText: String
            var statusType: BillStatusType

            if (tenantName == null) {
                statusText = "Vacant"
                statusType = BillStatusType.VACANT
            } else {
                val bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomWithTenant.room.id, currentYear, currentMonth)
                if (bill == null || bill.id == 0) {
                    statusText = "No Bills Yet"
                    statusType = BillStatusType.NO_BILLS_YET
                } else {
                    val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                    val amountPaidForBill = installmentEntities.sumOf { it.amount }
                    bill.calculateTotalDue()
                    val balance = bill.totalAmountDue - amountPaidForBill
                    when {
                        balance > 0.001 -> {
                            statusText = "Due: ${currencyFormatter.format(ceil(balance))}"
                            statusType = BillStatusType.DUE
                        }
                        balance < -0.001 -> {
                            statusText = "Advance: ${currencyFormatter.format(ceil(abs(balance)))}"
                            statusType = BillStatusType.ADVANCE
                        }
                        else -> {
                            statusText = "Paid"
                            statusType = BillStatusType.PAID
                        }
                    }
                }
            }
            roomSummaries.add(RoomSummaryForPropertyCard(roomName, tenantName, statusText, statusType))
        }
        return roomSummaries
    }

    private fun updatePropertyRoomSummaries(propertyList: List<PropertyEntity>) {
        viewModelScope.launch {
            val summariesMap = mutableMapOf<Int, List<RoomSummaryForPropertyCard>>()
            for (property in propertyList) {
                if (!property.isHidden) {
                    summariesMap[property.id] = generateRoomSummariesForProperty(property)
                }
            }
            _propertyRoomSummaries.value = summariesMap
        }
    }

    fun addProperty(name: String, address: String) {
        viewModelScope.launch {
            val property = PropertyEntity(name = name, address = address, isHidden = false)
            propertyRepository.insert(property) // This now returns Long, but it's not used here, which is fine.
        }
    }

    fun deleteProperty(property: PropertyEntity) {
        viewModelScope.launch {
            propertyRepository.delete(property)
        }
    }

    fun setPropertyHiddenStatus(propertyId: Int, isHidden: Boolean) {
        viewModelScope.launch {
            propertyRepository.updatePropertyHiddenStatus(propertyId, isHidden)
            val currentSummaries = _propertyFinancialSummaries.value.toMutableMap()
            val currentRoomSummariesMap = _propertyRoomSummaries.value.toMutableMap()

            if (isHidden) {
                currentSummaries.remove(propertyId)
                currentRoomSummariesMap.remove(propertyId)
            } else {
                val property = _properties.value.find { it.id == propertyId }
                if (property != null) {
                    currentSummaries[propertyId] = calculatePropertyFinancialSummary(property)
                    currentRoomSummariesMap[propertyId] = generateRoomSummariesForProperty(property)
                }
            }
            _propertyFinancialSummaries.value = currentSummaries
            _propertyRoomSummaries.value = currentRoomSummariesMap
        }
    }

    fun triggerExportData(year: Int, monthInput: Int?, format: ExportFormat) {
        viewModelScope.launch {
            _exportCsvData.value = null
            _exportTextData.value = null
            val mainBuilder = StringBuilder()
            val currentProperties = _properties.value.filter { !it.isHidden }
            val cal = Calendar.getInstance()
            if (format == ExportFormat.TEXT) {
                if (monthInput != null) {
                    mainBuilder.append("Property Data Export - $monthInput/$year\n")
                } else {
                    mainBuilder.append("Property Data Export - Full Year $year\n")
                }
                mainBuilder.append("=====================================\n\n")
            } else { // CSV
                mainBuilder.append("Property Name,Room Name,Tenant Name,Year,Month,Bill Amount,Amount Paid,Balance,Status\n")
            }
            for (property in currentProperties) {
                val roomsWithTenants = roomRepository.getRoomsForProperty(property.id).first()
                if (roomsWithTenants.isEmpty()) continue
                val propertySpecificTextBuilder = StringBuilder()
                for (roomWithTenant in roomsWithTenants) {
                    val room = roomWithTenant.room
                    val monthsToIterate = if (monthInput != null) listOf(monthInput) else (1..12).toList()
                    for (currentMonthInLoop in monthsToIterate) {
                        cal.clear(); cal.set(Calendar.YEAR, year); cal.set(Calendar.MONTH, currentMonthInLoop - 1); cal.set(Calendar.DAY_OF_MONTH, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        val startOfMonthTimestamp = cal.timeInMillis
                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                        val endOfMonthTimestamp = cal.timeInMillis
                        val tenant = tenantRepository.getTenantForBillPeriod(room.id, startOfMonthTimestamp, endOfMonthTimestamp)
                        val tenantName = tenant?.name ?: "N/A"
                        var billAmountStr = "0.0"; var amountPaidStr = "0.0"; var balanceStr = "0.0"
                        var statusStr = if (tenantName == "N/A") "Vacant" else "No Bill"
                        if (tenantName != "N/A") {
                            val bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(room.id, year, currentMonthInLoop)
                            if (bill != null && bill.id != 0) {
                                bill.calculateTotalDue()
                                val installments = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                                val totalPaid = installments.sumOf { it.amount }
                                val balance = bill.totalAmountDue - totalPaid
                                billAmountStr = currencyFormatter.format(ceil(bill.totalAmountDue))
                                amountPaidStr = currencyFormatter.format(ceil(totalPaid))
                                when {
                                    balance > 0.001 -> { balanceStr = currencyFormatter.format(ceil(balance)); statusStr = "Due" }
                                    balance < -0.001 -> { balanceStr = currencyFormatter.format(ceil(abs(balance))); statusStr = "Advance" }
                                    else -> { balanceStr = "0.0"; statusStr = "Paid" }
                                }
                            } else {
                                billAmountStr = "N/A"; amountPaidStr = "N/A"; balanceStr = "N/A"; statusStr = "No Bill Generated"
                            }
                        } else {
                            billAmountStr = "N/A"; amountPaidStr = "N/A"; balanceStr = "N/A"
                        }
                        if (format == ExportFormat.TEXT) {
                            propertySpecificTextBuilder.append("  Room: ${room.name}\n    Tenant: $tenantName\n    Period: $currentMonthInLoop/$year\n    Bill Amount: $billAmountStr\n    Amount Paid: $amountPaidStr\n    Balance: $balanceStr\n    Status: $statusStr\n    --------------------------\n")
                        } else {
                            mainBuilder.append("${property.name},${room.name},$tenantName,$year,$currentMonthInLoop,$billAmountStr,$amountPaidStr,$balanceStr,$statusStr\n")
                        }
                    }
                }
                if (format == ExportFormat.TEXT && propertySpecificTextBuilder.isNotEmpty()) {
                    mainBuilder.append("Property: ${property.name}\n").append(propertySpecificTextBuilder).append("\n")
                }
            }
            if (format == ExportFormat.TEXT) { _exportTextData.value = mainBuilder.toString() } else { _exportCsvData.value = mainBuilder.toString() }
            Log.d("PropertyViewModel", "${format.name} Data Generated: Length ${mainBuilder.length}")
        }
    }

    private fun parseCurrencyStringToDouble(currencyString: String): Double? {
        if (currencyString.equals("N/A", ignoreCase = true)) {
            return 0.0 // Treat N/A as 0.0 for calculations, or null if explicit absence is needed
        }
        try {
            val parsedNumber = currencyFormatter.parse(currencyString.trim())
            if (parsedNumber != null) {
                return parsedNumber.toDouble()
            }
        } catch (e: java.text.ParseException) {
            Log.w("ImportCSV", "NumberFormat failed to parse '$currencyString', trying fallback.")
        }
        val sanitizedString = currencyString
            .replace("₹", "").replace("$", "").replace("€", "").replace("£", "")
            .replace(",", "").trim()
        return sanitizedString.toDoubleOrNull()
    }

    fun importCsvData(csvString: String) {
        viewModelScope.launch {
            _importStatus.value = "Importing..."
            var processedRecords = 0
            var successfulRecords = 0
            var skippedRecords = 0
            var rowErrors = 0

            try {
                withContext(Dispatchers.IO) {
                    val lines = csvString.lines()
                    if (lines.size <= 1) {
                        _importStatus.value = "Import failed: CSV is empty or has only a header."
                        return@withContext
                    }

                    val importTimestamp = Calendar.getInstance().time.toString() // For default address/notes

                    // Skip header line (index 0)
                    for (i in 1 until lines.size) {
                        val line = lines[i].trim()
                        if (line.isEmpty()) {
                            skippedRecords++
                            continue
                        }
                        processedRecords++
                        val tokens = line.split(',').map { it.trim() }

                        if (tokens.size < 9) { // Property Name,Room Name,Tenant Name,Year,Month,Bill Amount,Amount Paid,Balance,Status
                            Log.w("ImportCSV", "Skipping malformed line (not enough columns: ${tokens.size}): $line")
                            rowErrors++; continue
                        }

                        val propertyNameStr = tokens[0]
                        val roomNameStr = tokens[1]
                        val tenantNameStr = tokens[2]
                        val yearStr = tokens[3]
                        val monthStr = tokens[4]
                        val billAmountStr = tokens[5]
                        val amountPaidStr = tokens[6]
                        // tokens[7] is Balance, tokens[8] is Status - useful for logic but not directly stored if calculable

                        val year: Int; val month: Int
                        val billAmountDouble: Double
                        val amountPaidDouble: Double

                        try {
                            year = yearStr.toInt()
                            month = monthStr.toInt()
                            if (month !in 1..12) {
                                Log.w("ImportCSV", "Skipping line (invalid month $month): $line"); rowErrors++; continue
                            }
                            billAmountDouble = parseCurrencyStringToDouble(billAmountStr) ?: 0.0
                            amountPaidDouble = parseCurrencyStringToDouble(amountPaidStr) ?: 0.0
                        } catch (e: NumberFormatException) {
                            Log.w("ImportCSV", "Skipping line (number format error for year/month/amounts): $line", e); rowErrors++; continue
                        }

                        // 1. Property
                        var property = propertyRepository.getByName(propertyNameStr)
                        if (property == null) {
                            val newPropertyId = propertyRepository.insert(PropertyEntity(name = propertyNameStr, address = "Imported on $importTimestamp", isHidden = false))
                            if (newPropertyId <= 0) {
                                Log.e("ImportCSV", "Failed to insert new property: $propertyNameStr. Skipping line: $line"); rowErrors++; continue
                            }
                            property = propertyRepository.getByName(propertyNameStr) // Re-fetch to get full entity with ID
                            if (property == null) {
                                Log.e("ImportCSV", "Critical: Failed to retrieve newly inserted property $propertyNameStr. Skipping line."); rowErrors++; continue
                            }
                        }

                        // 2. Room
                        var room = roomRepository.getByNameAndPropertyId(roomNameStr, property.id)
                        if (room == null) {
                            val newRoomId = roomRepository.insert(RoomEntity(propertyId = property.id, name = roomNameStr, rent = 0.0, isHidden = false)) // Added rent = 0.0
                            if (newRoomId <= 0) {
                                Log.e("ImportCSV", "Failed to insert new room '$roomNameStr' for property '${property.name}'. Skipping line: $line"); rowErrors++; continue
                            }
                            room = roomRepository.getByNameAndPropertyId(roomNameStr, property.id) // Re-fetch
                             if (room == null) {
                                Log.e("ImportCSV", "Critical: Failed to retrieve newly inserted room $roomNameStr. Skipping line."); rowErrors++; continue
                            }
                        }

                        // 3. Tenant (if applicable)
                        var tenantIdForBill: Int? = null
                        if (tenantNameStr.isNotBlank() && !tenantNameStr.equals("N/A", ignoreCase = true)) {
                            // Try to find existing tenant by name and ensure they are linked to the current room or are new
                            var tenant = tenantRepository.getTenantByNameAndRoomId(tenantNameStr, room.id)
                            if (tenant == null) { // Not found for this room, try by name only (might be in another room or new)
                                tenant = tenantRepository.getByName(tenantNameStr) // Check if tenant exists at all
                                if (tenant != null && tenant.roomId != room.id) {
                                    Log.w("ImportCSV", "Tenant '$tenantNameStr' exists but is linked to another room. Creating a new tenancy for room '${room.name}'.")
                                    tenant = null // Force creation if existing tenant is in a different room
                                }
                            }

                            if (tenant == null) {
                                val calMoveIn = Calendar.getInstance().apply { set(year, month - 1, 1) }
                                val newTenant = TenantEntity(
                                    roomId = room.id,
                                    name = tenantNameStr,
                                    mobile = "", // Default mobile
                                    moveInDate = calMoveIn.timeInMillis,
                                    imageUri = null,
                                    assetUris = emptyList()
                                )
                                val newTenantId = tenantRepository.insertOrUpdateTenant(newTenant) // Assumes insertOrUpdate based on name/room or just inserts if ID is 0
                                if (newTenantId > 0) tenantIdForBill = newTenantId.toInt()
                                else Log.w("ImportCSV", "Failed to insert/update new tenant '$tenantNameStr'. Bill may lack tenant link. Line: $line")
                            } else {
                                tenantIdForBill = tenant.id
                            }
                        }

                        // 4. Monthly Bill
                        val billDueDateCal = Calendar.getInstance().apply { set(year, month - 1, 5) } // Default due date to 5th
                        var existingBill = monthlyBillRepository.getBillForRoomMonthYearSuspend(room.id, year, month)
                        
                        val billToUpsert:
                        MonthlyBillEntity
                        if (existingBill != null) {
                            billToUpsert = existingBill.copy(
                                totalAmountDue = billAmountDouble,
                                dueDate = billDueDateCal.timeInMillis, // Or keep existing if preferred
                                tenantIdAtBillingTime = tenantIdForBill ?: existingBill.tenantIdAtBillingTime // Keep old if new is null
                            )
                        } else {
                            billToUpsert = MonthlyBillEntity(
                                roomId = room.id,
                                tenantIdAtBillingTime = tenantIdForBill,
                                year = year,
                                month = month,
                                totalAmountDue = billAmountDouble,
                                dueDate = billDueDateCal.timeInMillis
                            )
                        }
                        billToUpsert.calculateTotalDue() // Ensure derived fields are correct
                        val billId = monthlyBillRepository.upsertBill(billToUpsert)

                        if (billId <= 0) {
                            Log.e("ImportCSV", "Failed to upsert bill for room '${room.name}', $month/$year. Skipping payments. Line: $line"); rowErrors++; continue
                        }

                        // 5. Payment Installment (overwrite existing for simplicity for this bill)
                        paymentInstallmentRepository.deleteInstallmentsForBill(billId.toInt())
                        if (amountPaidDouble > 0.001) { // Only add if there's a payment
                            val paymentDateCal = Calendar.getInstance().apply { set(year, month - 1, 1) } // Default payment to 1st of bill month
                            val newInstallment = PaymentInstallmentEntity(
                                billId = billId.toInt(),
                                amount = amountPaidDouble,
                                date = paymentDateCal.timeInMillis
                            )
                            val installmentId = paymentInstallmentRepository.addInstallment(newInstallment)
                            if (installmentId <= 0) {
                                Log.w("ImportCSV", "Failed to add payment installment for bill ID $billId (Amount: $amountPaidDouble). Line: $line")
                            }
                        }
                        successfulRecords++
                    } // End of for loop for lines
                } // End withContext(Dispatchers.IO)

                val summaryMessage = mutableListOf<String>()
                summaryMessage.add("Import complete.")
                summaryMessage.add("$successfulRecords record(s) processed successfully.")
                if (rowErrors > 0) summaryMessage.add("$rowErrors record(s) had errors and were skipped/partially skipped.")
                if (skippedRecords > 0) summaryMessage.add("$skippedRecords empty line(s) skipped.")
                _importStatus.value = summaryMessage.joinToString("\n")

                // Trigger data refresh
                val currentProps = propertyRepository.getAllProperties().first()
                _properties.value = currentProps // This will trigger financial summaries update via collectLatest
                // Explicitly update summaries as well, though collectLatest should handle it
                updatePropertyFinancialSummaries(currentProps)
                updatePropertyRoomSummaries(currentProps)

            } catch (e: Exception) {
                Log.e("ImportCSV", "Critical error during CSV import process", e)
                _importStatus.value = "Import failed: ${e.message}"
            }
        }
    }
}
