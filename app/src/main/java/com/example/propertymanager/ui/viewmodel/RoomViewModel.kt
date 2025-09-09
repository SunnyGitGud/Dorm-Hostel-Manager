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
            roomRepository.update(room) // Changed to use safer update method
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
        val roomEntity = roomRepository.getRoomById(roomId)
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
                paymentDate = null,
                isInitialReadingRolledOver = existingBill.isInitialReadingRolledOver // Preserve flag
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
                paymentDate = null,
                isInitialReadingRolledOver = false // Default for new bill
            )
        } else {
            billToReturn = if (existingBill != null) {
                existingBill.copy(
                    tenantIdAtBillingTime = tenantCurrentMonth.id,
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    previousMonthDues = calculatedPreviousMonthDues,
                    rentAtBillingTime = if (existingBill.rentAtBillingTime == 0.0 && existingBill.tenantNameAtBillingTime == "Not Occupied") currentRoomRent else existingBill.rentAtBillingTime,
                    electricityUnits = existingBill.electricityUnits, // Preserve, saveBill will recalculate if needed
                    electricityRateAtBillingTime = existingBill.electricityRateAtBillingTime ?: currentRoomElectricityRate,
                    isInitialReadingRolledOver = existingBill.isInitialReadingRolledOver // Preserve flag
                )
            } else {
                MonthlyBillEntity(
                    roomId = roomId, year = year, month = month,
                    tenantIdAtBillingTime = tenantCurrentMonth.id,
                    tenantNameAtBillingTime = tenantCurrentMonth.name,
                    rentAtBillingTime = currentRoomRent,
                    electricityRateAtBillingTime = currentRoomElectricityRate,
                    previousMonthDues = calculatedPreviousMonthDues,
                    dueDate = defaultDueDateCalendar.timeInMillis,
                    isInitialReadingRolledOver = false // Default for new bill
                )
            }
        }
        
        if (billToReturn.electricityUnits != null) { 
            billToReturn.calculateTotalDue()
        }
        return billToReturn
    }

    suspend fun saveBill(bill: MonthlyBillEntity): Long {
        val roomEntity = roomRepository.getRoomById(bill.roomId)
        var performRoomMeterRolloverThisSave = false

        if (roomEntity != null && bill.tenantNameAtBillingTime != "Not Occupied") {
            // Recalculate units if they are null (signaling monthEndMeterReading changed in UI) 
            // OR if the bill's initial reading hasn't been rolled over yet.
            if (bill.electricityUnits == null || !bill.isInitialReadingRolledOver) {
                bill.electricityRateAtBillingTime = roomEntity.electricityRatePerUnit
                if (roomEntity.initialMeterReading != null && bill.monthEndMeterReading != null &&
                    bill.monthEndMeterReading!! >= roomEntity.initialMeterReading!!) {
                    bill.electricityUnits = bill.monthEndMeterReading!! - roomEntity.initialMeterReading!!
                } else {
                    bill.electricityUnits = 0.0 // Default to 0 units if readings are invalid/missing
                }
            }

            // Decide if room meter rollover should happen with THIS save operation.
            // This occurs only if the bill hasn't been rolled over yet, and has valid readings.
            if (!bill.isInitialReadingRolledOver && bill.monthEndMeterReading != null && 
                bill.electricityUnits != null && bill.electricityUnits!! >= 0) {
                performRoomMeterRolloverThisSave = true
                bill.isInitialReadingRolledOver = true // Mark this bill instance as having processed its rollover duty.
            }
        } else {
            // For unoccupied rooms or if room data is missing
            bill.electricityUnits = 0.0
            bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: roomEntity?.electricityRatePerUnit ?: 0.0
        }

        bill.calculateTotalDue() // Calculate total with potentially updated units
        val savedBillId = monthlyBillRepository.insertOrUpdateBill(bill) // Save bill with updated flags/units

        // Update room's initial meter reading for the NEXT period, only if decided AND successful save.
        if (savedBillId > 0 && performRoomMeterRolloverThisSave && bill.monthEndMeterReading != null) {
            roomRepository.updateMeterReading(bill.roomId, bill.monthEndMeterReading, System.currentTimeMillis())
        }
        
        return savedBillId
    }

    suspend fun getAllBillsForRoom(roomId: Int): List<MonthlyBillEntity> {
        return monthlyBillRepository.getBillsForRoomSuspend(roomId)
    }

    fun getAllTenantsForRoomFlow(roomId: Int): Flow<List<TenantEntity>> {
        return tenantRepository.getAllTenantsForRoom(roomId)
    }
}
