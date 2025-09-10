package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentInstallment
import com.example.propertymanager.data.model.RoomWithTenant // Import RoomWithTenant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

// Data class for property financial summary
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

    init {
        viewModelScope.launch {
            propertyRepository.getAllProperties().collect { propertyList ->
                _properties.value = propertyList
                updatePropertyFinancialSummaries(propertyList)
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
            val bill = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomWithTenant.room.id, currentYear, currentMonth)
            if (bill != null && bill.id != 0) {
                val installments = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                bill.installments = installments.map { PaymentInstallment(amount = it.amount, date = it.date) }
                bill.calculateTotalDue()
                
                val balance = bill.totalAmountDue - bill.amountPaid
                if (balance > 0.001) {
                    currentPropertyTotalDue += balance
                } else if (balance < -0.001) {
                    currentPropertyTotalAdvance += abs(balance)
                }
            }
        }
        return PropertyFinancialSummary(totalDue = currentPropertyTotalDue, totalAdvance = currentPropertyTotalAdvance)
    }

    private fun updatePropertyFinancialSummaries(propertyList: List<PropertyEntity>) {
        viewModelScope.launch {
            val summaries = mutableMapOf<Int, PropertyFinancialSummary>()
            for (property in propertyList) {
                // Only calculate for non-hidden properties as they are the ones displayed
                if (!property.isHidden) { 
                    summaries[property.id] = calculatePropertyFinancialSummary(property)
                }
            }
            _propertyFinancialSummaries.value = summaries
        }
    }

    fun addProperty(name: String, address: String) {
        viewModelScope.launch {
            // By default, a new property is not hidden
            val property = PropertyEntity(name = name, address = address, isHidden = false)
            propertyRepository.insert(property)
        }
    }

    fun deleteProperty(property: PropertyEntity) {
        viewModelScope.launch {
            propertyRepository.delete(property)
        }
    }

    // Added function to set the hidden status of a property
    fun setPropertyHiddenStatus(propertyId: Int, isHidden: Boolean) {
        viewModelScope.launch {
            propertyRepository.updatePropertyHiddenStatus(propertyId, isHidden)
            // The Flow in init {} will automatically update the properties list
            // and trigger updatePropertyFinancialSummaries.
        }
    }
}
