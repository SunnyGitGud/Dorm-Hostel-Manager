package com.example.propertymanager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.TenantRepository
import com.example.propertymanager.ui.screens.RoomSummaryForPropertyCard
import com.example.propertymanager.ui.screens.BillStatusType
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.ui.screens.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
            propertyRepository.insert(property)
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
            val cal = Calendar.getInstance() // Reuse calendar instance

            // 1. Set overall header
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

                val propertySpecificTextBuilder = StringBuilder() // Used only if format is TEXT

                for (roomWithTenant in roomsWithTenants) {
                    val room = roomWithTenant.room
                    val monthsToIterate = if (monthInput != null) listOf(monthInput) else (1..12).toList()

                    for (currentMonthInLoop in monthsToIterate) {
                        cal.clear()
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, currentMonthInLoop - 1) // Calendar month is 0-indexed
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        val startOfMonthTimestamp = cal.timeInMillis

                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                        val endOfMonthTimestamp = cal.timeInMillis

                        val tenant = tenantRepository.getTenantForBillPeriod(room.id, startOfMonthTimestamp, endOfMonthTimestamp)
                        val tenantName = tenant?.name ?: "N/A"

                        var billAmountStr = "0.0"
                        var amountPaidStr = "0.0"
                        var balanceStr = "0.0"
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
                                    balance > 0.001 -> {
                                        balanceStr = currencyFormatter.format(ceil(balance))
                                        statusStr = "Due"
                                    }
                                    balance < -0.001 -> {
                                        balanceStr = currencyFormatter.format(ceil(abs(balance)))
                                        statusStr = "Advance"
                                    }
                                    else -> {
                                        balanceStr = "0.0"
                                        statusStr = "Paid"
                                    }
                                }
                            } else {
                                billAmountStr = "N/A"
                                amountPaidStr = "N/A"
                                balanceStr = "N/A"
                                statusStr = "No Bill Generated"
                            }
                        } else {
                            billAmountStr = "N/A"
                            amountPaidStr = "N/A"
                            balanceStr = "N/A"
                            // statusStr is already "Vacant"
                        }

                        if (format == ExportFormat.TEXT) {
                            propertySpecificTextBuilder.append("  Room: ${room.name}\n")
                            propertySpecificTextBuilder.append("    Tenant: $tenantName\n")
                            propertySpecificTextBuilder.append("    Period: $currentMonthInLoop/$year\n")
                            propertySpecificTextBuilder.append("    Bill Amount: $billAmountStr\n")
                            propertySpecificTextBuilder.append("    Amount Paid: $amountPaidStr\n")
                            propertySpecificTextBuilder.append("    Balance: $balanceStr\n")
                            propertySpecificTextBuilder.append("    Status: $statusStr\n")
                            propertySpecificTextBuilder.append("    --------------------------\n")
                        } else { // CSV
                            mainBuilder.append("${property.name},${room.name},$tenantName,$year,$currentMonthInLoop,$billAmountStr,$amountPaidStr,$balanceStr,$statusStr\n")
                        }
                    } // End month loop
                } // End room loop

                if (format == ExportFormat.TEXT && propertySpecificTextBuilder.isNotEmpty()) {
                    mainBuilder.append("Property: ${property.name}\n")
                    mainBuilder.append(propertySpecificTextBuilder)
                    mainBuilder.append("\n") // Add a newline after each property's text data
                }
            } // End property loop

            if (format == ExportFormat.TEXT) {
                _exportTextData.value = mainBuilder.toString()
                Log.d("PropertyViewModel", "Text Data Generated: Length ${mainBuilder.length}")
            } else { // CSV
                _exportCsvData.value = mainBuilder.toString()
                Log.d("PropertyViewModel", "CSV Data Generated: Length ${mainBuilder.length}")
            }
        }
    }
}
