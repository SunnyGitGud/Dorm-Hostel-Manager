package com.example.propertymanager.ui.viewmodel

// import androidx.compose.ui.graphics.Color // No longer needed for status colors here
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.ui.screens.RoomSummaryForPropertyCard 
import com.example.propertymanager.ui.screens.BillStatusType // Import the enum
import com.example.propertymanager.data.model.RoomWithTenant
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

// Data class for property financial summary (already exists)
data class PropertyFinancialSummary(
    val totalDue: Double = 0.0,
    val totalAdvance: Double = 0.0
)

class PropertyViewModel(
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository,
    private val monthlyBillRepository: MonthlyBillRepository,
    private val paymentInstallmentRepository: PaymentInstallmentRepository
) : ViewModel() {

    private val _properties = MutableStateFlow<List<PropertyEntity>>(emptyList())
    val properties: StateFlow<List<PropertyEntity>> = _properties.asStateFlow()

    private val _propertyFinancialSummaries = MutableStateFlow<Map<Int, PropertyFinancialSummary>>(emptyMap())
    val propertyFinancialSummaries: StateFlow<Map<Int, PropertyFinancialSummary>> = _propertyFinancialSummaries.asStateFlow()

    private val _propertyRoomSummaries = MutableStateFlow<Map<Int, List<RoomSummaryForPropertyCard>>>(emptyMap())
    val propertyRoomSummaries: StateFlow<Map<Int, List<RoomSummaryForPropertyCard>>> = _propertyRoomSummaries.asStateFlow()

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

    // Removed Predefined colors for status, as Composable will handle this based on BillStatusType

    init {
        viewModelScope.launch {
            propertyRepository.getAllProperties().collectLatest { propertyList ->
                _properties.value = propertyList
                updatePropertyFinancialSummaries(propertyList)
                updatePropertyRoomSummaries(propertyList)
            }
        }
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
                    bill.calculateTotalDue() // Ensure totalAmountDue is calculated
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
}
