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
    private val propertyId: Int // This is the propertyId for which this ViewModel instance is created
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

    fun addRoom(name: String, rent: Double, electricityRatePerUnit: Double, initialMeterReading: Double?) {
        viewModelScope.launch {
            val room = RoomEntity(
                propertyId = propertyId, 
                name = name,
                rent = rent,
                electricityRatePerUnit = electricityRatePerUnit,
                initialMeterReading = initialMeterReading,
                initialMeterReadingDate = if (initialMeterReading != null) System.currentTimeMillis() else null
            )
            roomRepository.insert(room)
        }
    }

    fun updateRoomDetails(room: RoomEntity) {
        viewModelScope.launch {
            roomRepository.insert(room)
        }
    }

    fun addOrUpdateTenantDetails(roomId: Int, name: String, mobile: String, moveInDate: Long, existingTenantId: Int? = null) {
        viewModelScope.launch {
            val tenant = TenantEntity(
                id = existingTenantId ?: 0,
                roomId = roomId,
                name = name,
                mobile = mobile,
                moveInDate = moveInDate,
                moveOutDate = null
            )
            tenantRepository.insertOrUpdateTenant(tenant)
        }
    }

    fun updateTenant(tenant: TenantEntity) {
        viewModelScope.launch {
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
        month: Int,
        currentRoomRent: Double
    ): MonthlyBillEntity {
        val roomEntity = roomRepository.getRoomById(roomId) // Fetch the room entity directly
        val currentRoomElectricityRate = roomEntity?.electricityRatePerUnit ?: 10.0

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
                tenantIdAtBillingTime = null,
                tenantNameAtBillingTime = tenantNameDisplay,
                rentAtBillingTime = 0.0,
                monthEndMeterReading = null,
                electricityUnits = 0.0,
                electricityRateAtBillingTime = currentRoomElectricityRate,
                waterBill = 0.0,
                otherCharges = 0.0,
                otherChargesDescription = null,
                previousMonthDues = 0.0,
                amountPaid = 0.0,
                isFullyPaid = true,
                paymentDate = null
            ) ?: MonthlyBillEntity(
                roomId = roomId, year = year, month = month,
                tenantIdAtBillingTime = null,
                tenantNameAtBillingTime = tenantNameDisplay,
                rentAtBillingTime = 0.0,
                monthEndMeterReading = null,
                electricityUnits = 0.0,
                electricityRateAtBillingTime = currentRoomElectricityRate,
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
                    tenantIdAtBillingTime = tenantCurrentMonth.id,
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    previousMonthDues = calculatedPreviousMonthDues,
                    rentAtBillingTime = if (existingBill.rentAtBillingTime == 0.0 && existingBill.tenantNameAtBillingTime == "Not Occupied") currentRoomRent else existingBill.rentAtBillingTime,
                    electricityRateAtBillingTime = existingBill.electricityRateAtBillingTime ?: currentRoomElectricityRate
                    // monthEndMeterReading and electricityUnits will be populated from dialog or re-calculated in saveBill
                )
            } else {
                MonthlyBillEntity(
                    roomId = roomId, year = year, month = month,
                    tenantIdAtBillingTime = tenantCurrentMonth.id,
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    rentAtBillingTime = currentRoomRent,
                    electricityRateAtBillingTime = currentRoomElectricityRate,
                    previousMonthDues = calculatedPreviousMonthDues,
                    dueDate = defaultDueDateCalendar.timeInMillis
                    // monthEndMeterReading and electricityUnits will be populated from dialog or re-calculated in saveBill
                )
            }
        }
        billToReturn.calculateTotalDue()
        return billToReturn
    }

    suspend fun saveBill(bill: MonthlyBillEntity): Long {
        val roomEntity = roomRepository.getRoomById(bill.roomId)

        if (roomEntity != null && bill.tenantNameAtBillingTime != "Not Occupied") {
            bill.electricityRateAtBillingTime = roomEntity.electricityRatePerUnit // Ensure rate is current from room

            if (roomEntity.initialMeterReading != null && bill.monthEndMeterReading != null && bill.monthEndMeterReading!! >= roomEntity.initialMeterReading!!) {
                bill.electricityUnits = bill.monthEndMeterReading!! - roomEntity.initialMeterReading!!
            } else {
                bill.electricityUnits = 0.0 // Or null if your calculateTotalDue handles it; 0.0 is simpler for now
            }
        } else {
            // For unoccupied rooms or if room data is missing, ensure electricity components are zeroed out
            bill.electricityUnits = 0.0
            bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: 0.0 // Keep existing or default to 0
        }

        bill.calculateTotalDue() // Crucial: Recalculate with updated units/rate before saving
        return monthlyBillRepository.insertOrUpdateBill(bill)
    }

    suspend fun getAllBillsForRoom(roomId: Int): List<MonthlyBillEntity> {
        return monthlyBillRepository.getBillsForRoomSuspend(roomId)
    }

    fun getAllTenantsForRoomFlow(roomId: Int): Flow<List<TenantEntity>> {
        return tenantRepository.getAllTenantsForRoom(roomId)
    }
}
