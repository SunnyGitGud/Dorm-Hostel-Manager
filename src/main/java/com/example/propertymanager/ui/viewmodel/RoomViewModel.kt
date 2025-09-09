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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max

class RoomViewModel(
    private val roomRepository: RoomRepository,
    private val tenantRepository: TenantRepository, // Assumed to have the necessary methods
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

    // Assumes TenantRepository has a method like insertOrUpdateTenant
    // existingTenantId can be used if updating an existing tenant.
    fun addOrUpdateTenantDetails(roomId: Int, name: String, moveInDate: Long, existingTenantId: Int? = null) {
        viewModelScope.launch {
            val tenant = TenantEntity(
                id = existingTenantId ?: 0, // If 0, Room will treat as new insert if id is autoGenerate
                roomId = roomId,
                name = name,
                moveInDate = moveInDate,
                moveOutDate = null // New/updated active tenant
            )
            tenantRepository.insertOrUpdateTenant(tenant)
        }
    }

    // Assumes TenantRepository has a method like setTenantMoveOutDate
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

        // Determine tenant for the current bill's month (e.g., check mid-month for occupancy)
        val currentBillDateCalendar = Calendar.getInstance().apply { set(year, month - 1, 15) }
        val tenantCurrentMonth = tenantRepository.getTenantForRoomAtDate(roomId, currentBillDateCalendar.timeInMillis)

        // Determine tenant for the previous bill's month
        val previousBillDateCalendar = Calendar.getInstance().apply { timeInMillis = currentBillDateCalendar.timeInMillis }
        previousBillDateCalendar.add(Calendar.MONTH, -1)
        val tenantPreviousMonth = tenantRepository.getTenantForRoomAtDate(roomId, previousBillDateCalendar.timeInMillis)

        val prevBillEffectiveYear = previousBillDateCalendar.get(Calendar.YEAR)
        val prevBillEffectiveMonth = previousBillDateCalendar.get(Calendar.MONTH) + 1
        val previousMonthBillEntity = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, prevBillEffectiveYear, prevBillEffectiveMonth)

        // Dues Transfer Logic:
        if (tenantCurrentMonth != null && tenantPreviousMonth != null && tenantCurrentMonth.id == tenantPreviousMonth.id) {
            // Same tenant, room occupied in both periods (implicitly by tenantCurrentMonth != null)
            if (previousMonthBillEntity != null && !previousMonthBillEntity.isFullyPaid) {
                calculatedPreviousMonthDues = max(0.0, previousMonthBillEntity.totalAmountDue - previousMonthBillEntity.amountPaid)
            }
        }
        // Else: Tenant changed, or room became vacant/occupied, so previousMonthDues remain 0.0

        val existingBill = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, year, month)
        val billToReturn: MonthlyBillEntity
        val defaultDueDateCalendar = Calendar.getInstance().apply { set(year, month - 1, 5) }

        if (tenantCurrentMonth == null) {
            // Room is Unoccupied for the current bill's month
            val tenantNameDisplay = "Not Occupied"
            billToReturn = existingBill?.copy(
                tenantNameAtBillingTime = tenantNameDisplay,
                rentAtBillingTime = 0.0, electricityBill = 0.0, waterBill = 0.0, otherCharges = 0.0,
                otherChargesDescription = null,
                previousMonthDues = 0.0, // No dues carry-over if room is vacant
                amountPaid = 0.0, isFullyPaid = true, paymentDate = null
            ) ?: MonthlyBillEntity(
                roomId = roomId, year = year, month = month,
                tenantNameAtBillingTime = tenantNameDisplay,
                rentAtBillingTime = 0.0, electricityBill = 0.0, waterBill = 0.0, otherCharges = 0.0,
                previousMonthDues = 0.0,
                dueDate = defaultDueDateCalendar.timeInMillis,
                amountPaid = 0.0, isFullyPaid = true, paymentDate = null
            )
        } else {
            // Room is Occupied for the current bill's month
            billToReturn = if (existingBill != null) {
                existingBill.copy(
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    previousMonthDues = calculatedPreviousMonthDues,
                    // If rent was 0 because it was previously "Not Occupied", reset to currentRoomRent. Else, keep existing.
                    rentAtBillingTime = if (existingBill.rentAtBillingTime == 0.0 && existingBill.tenantNameAtBillingTime == "Not Occupied") currentRoomRent else existingBill.rentAtBillingTime,
                    // Preserve other charges from existing bill if they were manually set
                    electricityBill = existingBill.electricityBill,
                    waterBill = existingBill.waterBill,
                    otherCharges = existingBill.otherCharges,
                    otherChargesDescription = existingBill.otherChargesDescription
                )
            } else {
                // New bill for an occupied room
                MonthlyBillEntity(
                    roomId = roomId, year = year, month = month,
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
}
