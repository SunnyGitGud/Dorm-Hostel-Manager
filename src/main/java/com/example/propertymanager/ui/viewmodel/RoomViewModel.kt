package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max

class RoomViewModel(
    private val roomRepository: RoomRepository,
    private val tenantRepository: TenantRepository,
    private val monthlyBillRepository: MonthlyBillRepository,
    private val propertyId: Int
) : ViewModel() {

    private val _roomsWithTenants = MutableStateFlow<List<RoomWithTenant>>(emptyList())
    val roomsWithTenants: StateFlow<List<RoomWithTenant>> = _roomsWithTenants.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepository.getRoomsForProperty(propertyId).collect {
                _roomsWithTenants.value = it
            }
        }
    }

    fun addRoom(name: String, rent: Double) {
        viewModelScope.launch {
            val room = RoomEntity(propertyId = propertyId, name = name, rent = rent)
            roomRepository.insert(room)
        }
    }

    fun addOrUpdateTenantDetails(roomId: Int, name: String, moveInDate: Long, existingTenantId: Int? = null) {
        viewModelScope.launch {
            val tenant = TenantEntity(
                id = existingTenantId ?: 0,
                roomId = roomId,
                name = name,
                moveInDate = moveInDate,
                moveOutDate = null
            )
            tenantRepository.insertOrUpdateTenant(tenant)
        }
    }

    fun recordTenantMoveOutDate(tenantId: Int, moveOutTimestamp: Long) {
        viewModelScope.launch {
            tenantRepository.setTenantMoveOutDate(tenantId, moveOutTimestamp)
        }
    }

    suspend fun getOrCreateBillForRoom(
        roomId: Int,
        year: Int,
        month: Int, // 1-12
        currentRoomRent: Double
    ): MonthlyBillEntity {
        var calculatedPreviousMonthDues = 0.0

        val currentBillDateCalendar = Calendar.getInstance().apply { set(year, month - 1, 15) }
        val tenantCurrentMonth = tenantRepository.getTenantForRoomAtDate(roomId, currentBillDateCalendar.timeInMillis)

        val previousBillDateCalendar = Calendar.getInstance().apply { timeInMillis = currentBillDateCalendar.timeInMillis }
        previousBillDateCalendar.add(Calendar.MONTH, -1)
        val tenantPreviousMonth = tenantRepository.getTenantForRoomAtDate(roomId, previousBillDateCalendar.timeInMillis)

        val prevBillEffectiveYear = previousBillDateCalendar.get(Calendar.YEAR)
        val prevBillEffectiveMonth = previousBillDateCalendar.get(Calendar.MONTH) + 1
        val previousMonthBillEntity = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, prevBillEffectiveYear, prevBillEffectiveMonth)

        if (tenantCurrentMonth != null && tenantPreviousMonth != null && tenantCurrentMonth.id == tenantPreviousMonth.id) {
            if (previousMonthBillEntity != null && !previousMonthBillEntity.isFullyPaid) {
                calculatedPreviousMonthDues = max(0.0, previousMonthBillEntity.totalAmountDue - previousMonthBillEntity.amountPaid)
            }
        }

        val existingBill = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, year, month)
        val billToReturn: MonthlyBillEntity
        val defaultDueDateCalendar = Calendar.getInstance().apply { set(year, month - 1, 5) }

        if (tenantCurrentMonth == null) {
            val tenantNameDisplay = "Not Occupied"
            billToReturn = existingBill?.copy(
                tenantIdAtBillingTime = null, // Explicitly null for unoccupied
                tenantNameAtBillingTime = tenantNameDisplay,
                rentAtBillingTime = 0.0,
                electricityBill = 0.0,
                waterBill = 0.0,
                otherCharges = 0.0,
                otherChargesDescription = null,
                previousMonthDues = 0.0,
                amountPaid = 0.0,
                isFullyPaid = true,
                paymentDate = null
            ) ?: MonthlyBillEntity(
                roomId = roomId, year = year, month = month,
                tenantIdAtBillingTime = null, // Explicitly null for unoccupied
                tenantNameAtBillingTime = tenantNameDisplay,
                rentAtBillingTime = 0.0,
                electricityBill = 0.0,
                waterBill = 0.0,
                otherCharges = 0.0,
                previousMonthDues = 0.0,
                dueDate = defaultDueDateCalendar.timeInMillis,
                amountPaid = 0.0,
                isFullyPaid = true,
                paymentDate = null
            )
        } else {
            billToReturn = if (existingBill != null) {
                existingBill.copy(
                    tenantIdAtBillingTime = tenantCurrentMonth.id, // Set tenant ID
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    previousMonthDues = calculatedPreviousMonthDues,
                    rentAtBillingTime = if (existingBill.rentAtBillingTime == 0.0 && existingBill.tenantNameAtBillingTime == "Not Occupied") currentRoomRent else existingBill.rentAtBillingTime,
                    electricityBill = existingBill.electricityBill,
                    waterBill = existingBill.waterBill,
                    otherCharges = existingBill.otherCharges,
                    otherChargesDescription = existingBill.otherChargesDescription
                )
            } else {
                MonthlyBillEntity(
                    roomId = roomId, year = year, month = month,
                    tenantIdAtBillingTime = tenantCurrentMonth.id, // Set tenant ID
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    rentAtBillingTime = currentRoomRent,
                    previousMonthDues = calculatedPreviousMonthDues,
                    dueDate = defaultDueDateCalendar.timeInMillis
                )
            }
        }

        billToReturn.calculateTotalDue()
        return billToReturn
    }

    suspend fun saveBill(bill: MonthlyBillEntity): Long {
        bill.calculateTotalDue()
        return monthlyBillRepository.insertOrUpdateBill(bill)
    }

    suspend fun getAllBillsForRoom(roomId: Int): List<MonthlyBillEntity> {
        return monthlyBillRepository.getBillsForRoomSuspend(roomId)
    }

    // Function to get all tenants for a room - needed for the tenant filter
    fun getAllTenantsForRoomFlow(roomId: Int): Flow<List<TenantEntity>> {
        return tenantRepository.getAllTenantsForRoom(roomId)
    }
}
