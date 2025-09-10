package com.example.propertymanager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PropertyEntity
// RoomEntity, TenantEntity, MonthlyBillEntity, PaymentInstallmentEntity are used by the service
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.TenantRepository
import com.example.propertymanager.services.DataImportExportService
import com.example.propertymanager.services.ImportResult
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
    private val tenantRepository: TenantRepository,
    private val dataImportExportService: DataImportExportService // Service injected
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

    // Financial summary and room summary logic remains the same as it reads current DB state
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
                    bill.amountPaid = installmentEntities.sumOf { it.amount } // Ensure amountPaid is current before calc
                    bill.calculateTotalDue()
                    val balance = bill.totalAmountDue - bill.amountPaid // Use bill.amountPaid
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
                    bill.amountPaid = installmentEntities.sumOf { it.amount } // Ensure amountPaid is current
                    bill.calculateTotalDue()
                    val balance = bill.totalAmountDue - bill.amountPaid // Use bill.amountPaid
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
            // Refresh summaries, existing logic for this is fine.
            val currentProps = _properties.value
            updatePropertyFinancialSummaries(currentProps.filterNot { it.isHidden })
            updatePropertyRoomSummaries(currentProps.filterNot { it.isHidden })
        }
    }

    fun triggerExportData(year: Int, monthInput: Int?, format: ExportFormat) {
        viewModelScope.launch {
            _exportCsvData.value = null // Clear previous data
            _exportTextData.value = null

            val currentPropertiesToExport = _properties.value.filter { !it.isHidden }
            if (currentPropertiesToExport.isEmpty()) {
                Log.i("PropertyViewModel", "No properties to export.")
                if (format == ExportFormat.CSV) _exportCsvData.value = ""
                else _exportTextData.value = "No properties to export."
                return@launch
            }

            val generatedData = dataImportExportService.generateExportData(
                properties = currentPropertiesToExport,
                year = year,
                monthInput = monthInput,
                format = format
            )

            if (format == ExportFormat.TEXT) {
                _exportTextData.value = generatedData
            } else {
                _exportCsvData.value = generatedData
            }
            Log.d("PropertyViewModel", "${format.name} Data Generated by service: Length ${generatedData.length}")
        }
    }

    fun importCsvData(csvString: String) {
        viewModelScope.launch {
            _importStatus.value = "Importing..."
            val result: ImportResult = dataImportExportService.processImportData(csvString)
            _importStatus.value = result.message

            // Trigger data refresh if import had successful records or even if it didn't (to clear any stale views)
            // The collectLatest in init should take care of this automatically when properties table changes,
            // but an explicit refresh ensures UI consistency immediately after import.
            val currentProps = propertyRepository.getAllProperties().first()
            _properties.value = currentProps // This will trigger financial and room summaries update via collectLatest
            // Forcing update of summaries if properties haven't changed but their financial details have
            updatePropertyFinancialSummaries(currentProps)
            updatePropertyRoomSummaries(currentProps)
        }
    }
}
