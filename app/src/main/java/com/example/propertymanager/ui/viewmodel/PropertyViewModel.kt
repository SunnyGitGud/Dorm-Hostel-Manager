package com.example.propertymanager.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity
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
import com.example.propertymanager.utils.LanguageManager // Import LanguageManager
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
    application: Application, // Added Application context
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository,
    private val monthlyBillRepository: MonthlyBillRepository,
    private val paymentInstallmentRepository: PaymentInstallmentRepository,
    private val tenantRepository: TenantRepository,
    private val dataImportExportService: DataImportExportService
) : AndroidViewModel(application) { // Inherit from AndroidViewModel

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

    private val _currentLanguageCode = MutableStateFlow("en") // Default, will be updated
    val currentLanguageCode: StateFlow<String> = _currentLanguageCode.asStateFlow()

    private val _languageChangeRequiresRestart = MutableStateFlow(false)
    val languageChangeRequiresRestart: StateFlow<Boolean> = _languageChangeRequiresRestart.asStateFlow()

    private val _showProrateRentDialog = MutableStateFlow(false)
    val showProrateRentDialog: StateFlow<Boolean> = _showProrateRentDialog.asStateFlow()

    private val _prorationStatusMessage = MutableStateFlow<String?>(null)
    val prorationStatusMessage: StateFlow<String?> = _prorationStatusMessage.asStateFlow()

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

    init {
        _currentLanguageCode.value = LanguageManager.getCurrentLanguage(getApplication())

        viewModelScope.launch {
            propertyRepository.getAllProperties().collectLatest { propertyList ->
                _properties.value = propertyList
                updatePropertyFinancialSummaries(propertyList)
                updatePropertyRoomSummaries(propertyList)
            }
        }
        // Listen for bill changes to update summaries reactively
        viewModelScope.launch {
            monthlyBillRepository.getAllBillsStream().collectLatest { allBills ->
                Log.d("PropertyViewModel", "Bill stream emitted (count: ${allBills.size}), re-calculating all property summaries.")
                updatePropertyFinancialSummaries(_properties.value) // Use current properties list
                updatePropertyRoomSummaries(_properties.value)   // Use current properties list
            }
        }
    }

    fun setLanguage(languageCode: String) {
        val currentContext = getApplication<Application>().applicationContext
        val currentSelectedLanguage = LanguageManager.getCurrentLanguage(currentContext)
        if (currentSelectedLanguage != languageCode) {
            LanguageManager.setLanguage(currentContext, languageCode)
            _currentLanguageCode.value = languageCode
            _languageChangeRequiresRestart.value = true
        }
    }

    fun consumedLanguageChangeRestartSignal() {
        _languageChangeRequiresRestart.value = false
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
    
    fun clearProrationStatusMessage() {
        _prorationStatusMessage.value = null
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
                    bill.amountPaid = installmentEntities.sumOf { it.amount } 
                    bill.calculateTotalDue()
                    val balance = bill.totalAmountDue - bill.amountPaid 
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
                    bill.amountPaid = installmentEntities.sumOf { it.amount } 
                    bill.calculateTotalDue()
                    val balance = bill.totalAmountDue - bill.amountPaid 
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
            // The reactive collection of properties will trigger summary updates.
        }
    }

    fun triggerExportData(year: Int, monthInput: Int?, format: ExportFormat) {
        viewModelScope.launch {
            _exportCsvData.value = null 
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

            if (result.successfulRecords > 0) {
                _showProrateRentDialog.value = true
            }
            // Refresh properties & summaries happens reactively via collectors
        }
    }

    fun userAcknowledgedProrateDialog() {
        _showProrateRentDialog.value = false
    }

    fun onConfirmProrateRent() {
        _showProrateRentDialog.value = false
        viewModelScope.launch {
            executeProrationLogic()
        }
    }

    private suspend fun executeProrationLogic() {
        Log.d("PropertyViewModel", "Proration logic execution started.")
        var proratedTenantsCount = 0
        val currentCalendar = Calendar.getInstance()
        val currentSystemYear = currentCalendar.get(Calendar.YEAR)
        val currentSystemMonth = currentCalendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-indexed

        val activeProperties = _properties.value.filter { !it.isHidden }

        for (property in activeProperties) {
            val roomsInProperty = roomRepository.getRoomsForProperty(property.id).first()
            for (roomWithTenant in roomsInProperty) {
                val room = roomWithTenant.room ?: continue
                // CORRECTED: Changed getTenantsForRoom to getAllTenantsForRoom
                val tenantsInRoom: List<TenantEntity> = tenantRepository.getAllTenantsForRoom(room.id).first()
                
                for (tenant in tenantsInRoom) {
                    if (tenant.moveOutDate != null && tenant.moveOutDate!! < currentCalendar.timeInMillis) {
                        continue // Skip tenants who have already moved out
                    }

                    val moveInCalendar = Calendar.getInstance().apply { timeInMillis = tenant.moveInDate }
                    val moveInYear = moveInCalendar.get(Calendar.YEAR)
                    val moveInMonth = moveInCalendar.get(Calendar.MONTH) + 1
                    val moveInDay = moveInCalendar.get(Calendar.DAY_OF_MONTH)

                    // Check if tenant moved in the current system month and not on the 1st day
                    if (moveInYear == currentSystemYear && moveInMonth == currentSystemMonth && moveInDay > 1) {
                        var bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(room.id, currentSystemYear, currentSystemMonth)
                        
                        val fullRent = room.rent
                        val totalDaysInMonth = moveInCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val daysOccupied = totalDaysInMonth - moveInDay + 1
                        val proratedRent = if (totalDaysInMonth > 0) (fullRent / totalDaysInMonth) * daysOccupied else fullRent

                        if (bill != null) {
                            // Prorate if not already explicitly prorated (isFullRentAppliedOverride is true or null)
                            // and rentAtBillingTime is different from newly calculated proratedRent (allowing for small float diffs)
                            val needsProration = (bill.isFullRentAppliedOverride != false) && (abs(bill.rentAtBillingTime - proratedRent) > 0.001)
                            if (needsProration) {
                                bill.rentAtBillingTime = proratedRent
                                bill.isFullRentAppliedOverride = false
                                // bill.calculateTotalDue() // This is called inside upsertBill
                                monthlyBillRepository.upsertBill(bill)
                                proratedTenantsCount++
                                Log.d("PropertyViewModel", "Prorated rent for existing bill: Tenant ${tenant.id}, Room ${room.id}, Bill ${bill.id}")
                            }
                        } else {
                            // Bill does not exist, create a new one with prorated rent
                            val newBill = MonthlyBillEntity(
                                roomId = room.id,
                                year = currentSystemYear,
                                month = currentSystemMonth,
                                tenantIdAtBillingTime = tenant.id,
                                tenantNameAtBillingTime = tenant.name,
                                rentAtBillingTime = proratedRent,
                                isFullRentAppliedOverride = false,
                                electricityRateAtBillingTime = room.electricityRatePerUnit,
                                previousMonthDues = 0.0, // Assuming 0 for a new mover's first bill post-import
                                dueDate = Calendar.getInstance().apply { set(currentSystemYear, currentSystemMonth - 1, 5) }.timeInMillis,
                                isInitialReadingRolledOver = false
                            )
                            // newBill.calculateTotalDue() // This is called inside upsertBill
                            monthlyBillRepository.upsertBill(newBill)
                            proratedTenantsCount++
                            Log.d("PropertyViewModel", "Created new prorated bill: Tenant ${tenant.id}, Room ${room.id}")
                        }
                    }
                }
            }
        }

        if (proratedTenantsCount > 0) {
            _prorationStatusMessage.value = "Proration applied for $proratedTenantsCount tenant(s)."
        } else {
            _prorationStatusMessage.value = "No new tenants required proration for the current month."
        }
        Log.d("PropertyViewModel", "Proration logic finished. Processed $proratedTenantsCount tenants.")
    }
}
