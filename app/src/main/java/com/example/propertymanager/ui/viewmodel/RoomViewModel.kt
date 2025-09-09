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
import kotlin.math.abs
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
            roomRepository.update(room)
        }
    }

    fun addOrUpdateTenantDetails(roomId: Int, name: String, mobile: String, moveInDate: Long, existingTenantId: Int? = null) {
        viewModelScope.launch {
            val tenantToSave = TenantEntity(
                id = existingTenantId ?: 0,
                roomId = roomId,
                name = name,
                mobile = mobile,
                moveInDate = moveInDate,
                moveOutDate = null
            )
            val savedTenantId = tenantRepository.insertOrUpdateTenant(tenantToSave)

            if (savedTenantId > 0) {
                val moveInCalendar = Calendar.getInstance().apply { timeInMillis = moveInDate }
                val moveInYear = moveInCalendar.get(Calendar.YEAR)
                val moveInMonth = moveInCalendar.get(Calendar.MONTH) + 1

                val existingBillForMoveInMonth = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, moveInYear, moveInMonth)

                if (existingBillForMoveInMonth == null) {
                    val roomEntity = roomRepository.getRoomById(roomId)
                    if (roomEntity != null) {
                        var rentForMoveInMonth = roomEntity.rent
                        val moveInDay = moveInCalendar.get(Calendar.DAY_OF_MONTH)
                        if (moveInDay > 1) {
                            val totalDaysInMonth = moveInCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                            if (totalDaysInMonth > 0) {
                                val daysOccupied = totalDaysInMonth - moveInDay + 1
                                rentForMoveInMonth = (roomEntity.rent / totalDaysInMonth.toDouble()) * daysOccupied.toDouble()
                            }
                        }
                        val defaultDueDateCalendar = Calendar.getInstance().apply { set(moveInYear, moveInMonth - 1, 5) }
                        val initialMoveInBill = MonthlyBillEntity(
                            roomId = roomId, year = moveInYear, month = moveInMonth,
                            tenantIdAtBillingTime = savedTenantId.toInt(),
                            tenantNameAtBillingTime = name,
                            rentAtBillingTime = rentForMoveInMonth,
                            electricityRateAtBillingTime = roomEntity.electricityRatePerUnit,
                            previousMonthDues = 0.0,
                            dueDate = defaultDueDateCalendar.timeInMillis,
                            isInitialReadingRolledOver = false
                        )
                        saveBill(initialMoveInBill)
                    }
                }
            }
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
        billYear: Int, // The year of the bill being requested
        billMonth: Int, // The month of the bill being requested
        currentRoomFullRent: Double // UI's idea of full rent for this period, use roomEntity.rent for definitive
    ): MonthlyBillEntity {
        val roomEntity = roomRepository.getRoomById(roomId)
            ?: return MonthlyBillEntity( // Fallback for no room data
                roomId = roomId, year = billYear, month = billMonth,
                tenantNameAtBillingTime = "Error: Room not found", rentAtBillingTime = 0.0, isFullyPaid = true
            )

        val startOfRequestedMonth = Calendar.getInstance().apply { set(billYear, billMonth - 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val endOfRequestedMonth = Calendar.getInstance().apply { set(billYear, billMonth - 1, 1, 23, 59, 59); set(Calendar.MILLISECOND, 999); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }.timeInMillis
        val tenantForRequestedPeriod = tenantRepository.getTenantForBillPeriod(roomId, startOfRequestedMonth, endOfRequestedMonth)

        var calculatedPreviousMonthDues = 0.0
        if (tenantForRequestedPeriod != null) {
            val prevBillCal = Calendar.getInstance().apply { timeInMillis = startOfRequestedMonth; add(Calendar.MONTH, -1) }
            val prevBillQueryYear = prevBillCal.get(Calendar.YEAR)
            val prevBillQueryMonth = prevBillCal.get(Calendar.MONTH) + 1

            val tenantMoveInCal = Calendar.getInstance().apply { timeInMillis = tenantForRequestedPeriod.moveInDate }
            val tenantMoveInYear = tenantMoveInCal.get(Calendar.YEAR)
            val tenantMoveInMonth = tenantMoveInCal.get(Calendar.MONTH) + 1

            if (prevBillQueryYear > tenantMoveInYear || (prevBillQueryYear == tenantMoveInYear && prevBillQueryMonth >= tenantMoveInMonth)) {
                val previousMonthBillObject = getOrCreateBillForRoom(roomId, prevBillQueryYear, prevBillQueryMonth, roomEntity.rent) // Recursive call
                
                val startOfPrevBillMonthForTenantCheck = Calendar.getInstance().apply { set(prevBillQueryYear, prevBillQueryMonth - 1, 1, 0,0,0); set(Calendar.MILLISECOND,0)}.timeInMillis
                val endOfPrevBillMonthForTenantCheck = Calendar.getInstance().apply { set(prevBillQueryYear, prevBillQueryMonth - 1, 1,23,59,59); set(Calendar.MILLISECOND,999); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))}.timeInMillis
                val tenantForPreviousBillObjectPeriod = tenantRepository.getTenantForBillPeriod(roomId, startOfPrevBillMonthForTenantCheck, endOfPrevBillMonthForTenantCheck)

                if (tenantForPreviousBillObjectPeriod != null && tenantForRequestedPeriod.id == tenantForPreviousBillObjectPeriod.id) {
                    calculatedPreviousMonthDues = previousMonthBillObject.totalAmountDue - previousMonthBillObject.amountPaid
                }
            }
        }

        var billToProcess = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, billYear, billMonth) // Declared as var
        val defaultDueDateCalendar = Calendar.getInstance().apply { set(billYear, billMonth - 1, 5) }
        var needsSave = false

        if (billToProcess == null) {
            needsSave = true
            val rentForNewBill: Double = if (tenantForRequestedPeriod == null) {
                0.0
            } else {
                var tempRent = roomEntity.rent
                val moveInCal = Calendar.getInstance().apply { timeInMillis = tenantForRequestedPeriod.moveInDate }
                if (moveInCal.get(Calendar.YEAR) == billYear && (moveInCal.get(Calendar.MONTH) + 1) == billMonth && moveInCal.get(Calendar.DAY_OF_MONTH) > 1) {
                    val totalDaysInMonth = moveInCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    if (totalDaysInMonth > 0) {
                        val daysOccupied = totalDaysInMonth - moveInCal.get(Calendar.DAY_OF_MONTH) + 1
                        tempRent = (roomEntity.rent / totalDaysInMonth.toDouble()) * daysOccupied.toDouble()
                    }
                }
                tempRent // Assign the calculated rent to rentForNewBill
            }
            
            billToProcess = if (tenantForRequestedPeriod == null) {
                MonthlyBillEntity( 
                    roomId = roomId, year = billYear, month = billMonth,
                    tenantIdAtBillingTime = null, tenantNameAtBillingTime = "Not Occupied",
                    rentAtBillingTime = rentForNewBill, electricityRateAtBillingTime = roomEntity.electricityRatePerUnit,
                    previousMonthDues = 0.0, 
                    dueDate = defaultDueDateCalendar.timeInMillis, isInitialReadingRolledOver = false
                )
            } else {
                MonthlyBillEntity( 
                    roomId = roomId, year = billYear, month = billMonth,
                    tenantIdAtBillingTime = tenantForRequestedPeriod.id, tenantNameAtBillingTime = tenantForRequestedPeriod.name,
                    rentAtBillingTime = rentForNewBill, electricityRateAtBillingTime = roomEntity.electricityRatePerUnit,
                    previousMonthDues = calculatedPreviousMonthDues,
                    dueDate = defaultDueDateCalendar.timeInMillis, isInitialReadingRolledOver = false
                )
            }
        } else {
            val expectedTenantId = tenantForRequestedPeriod?.id
            val expectedTenantName = tenantForRequestedPeriod?.name ?: "Not Occupied"
            var expectedRent = billToProcess.rentAtBillingTime
            var tenantOrRentChanged = false

            if (billToProcess.tenantIdAtBillingTime != expectedTenantId || billToProcess.tenantNameAtBillingTime != expectedTenantName) {
                tenantOrRentChanged = true
                if (tenantForRequestedPeriod != null) {
                    expectedRent = roomEntity.rent 
                    val moveInCal = Calendar.getInstance().apply { timeInMillis = tenantForRequestedPeriod.moveInDate }
                    if (moveInCal.get(Calendar.YEAR) == billYear && (moveInCal.get(Calendar.MONTH) + 1) == billMonth && moveInCal.get(Calendar.DAY_OF_MONTH) > 1) {
                        val totalDaysInMonth = moveInCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        if (totalDaysInMonth > 0) {
                            val daysOccupied = totalDaysInMonth - moveInCal.get(Calendar.DAY_OF_MONTH) + 1
                            expectedRent = (roomEntity.rent / totalDaysInMonth.toDouble()) * daysOccupied.toDouble()
                        }
                    }
                } else {
                    expectedRent = 0.0
                }
            } else if (tenantForRequestedPeriod != null) { 
                val moveInCal = Calendar.getInstance().apply { timeInMillis = tenantForRequestedPeriod.moveInDate }
                if (moveInCal.get(Calendar.YEAR) == billYear && (moveInCal.get(Calendar.MONTH) + 1) == billMonth && moveInCal.get(Calendar.DAY_OF_MONTH) > 1) {
                    val proRataRent = (roomEntity.rent / moveInCal.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()) * (moveInCal.getActualMaximum(Calendar.DAY_OF_MONTH) - moveInCal.get(Calendar.DAY_OF_MONTH) + 1).toDouble()
                    if (abs(billToProcess.rentAtBillingTime - proRataRent) > 0.001) {
                        expectedRent = proRataRent
                        tenantOrRentChanged = true
                    }
                } else { 
                    if (abs(billToProcess.rentAtBillingTime - roomEntity.rent) > 0.001) {
                        expectedRent = roomEntity.rent
                        tenantOrRentChanged = true
                    }
                }
            }

            if (tenantOrRentChanged || abs((billToProcess.previousMonthDues ?: 0.0) - calculatedPreviousMonthDues) > 0.001) {
                needsSave = true
                billToProcess = billToProcess.copy( 
                    tenantIdAtBillingTime = expectedTenantId,
                    tenantNameAtBillingTime = expectedTenantName,
                    rentAtBillingTime = expectedRent,
                    previousMonthDues = calculatedPreviousMonthDues,
                    electricityRateAtBillingTime = billToProcess.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
                )
            }
        }

        if (needsSave) {
            val savedBillId = saveBill(billToProcess)
        }
        
        billToProcess.calculateTotalDue() 
        return billToProcess
    }

    suspend fun saveBill(bill: MonthlyBillEntity): Long {
        val roomEntity = roomRepository.getRoomById(bill.roomId)
        var performRoomMeterRolloverThisSave = false

        if (roomEntity != null && bill.tenantNameAtBillingTime != "Not Occupied") {
            if (bill.electricityUnits == null || !bill.isInitialReadingRolledOver) {
                bill.electricityRateAtBillingTime = roomEntity.electricityRatePerUnit
                if (roomEntity.initialMeterReading != null && bill.monthEndMeterReading != null &&
                    bill.monthEndMeterReading!! >= roomEntity.initialMeterReading!!) {
                    bill.electricityUnits = bill.monthEndMeterReading!! - roomEntity.initialMeterReading!!
                } else {
                    bill.electricityUnits = 0.0
                }
            }
            if (!bill.isInitialReadingRolledOver && bill.monthEndMeterReading != null && 
                (bill.electricityUnits ?: 0.0) >= 0.0) { 
                performRoomMeterRolloverThisSave = true
            }
        } else { 
            bill.electricityUnits = 0.0
            bill.monthEndMeterReading = null
            bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: roomEntity?.electricityRatePerUnit ?: 0.0
        }
        
        val billToSave = if (performRoomMeterRolloverThisSave) bill.copy(isInitialReadingRolledOver = true) else bill.copy()
        billToSave.calculateTotalDue() 
        
        val savedBillId = monthlyBillRepository.insertOrUpdateBill(billToSave)

        if (savedBillId > 0 && performRoomMeterRolloverThisSave && billToSave.monthEndMeterReading != null) {
            roomRepository.updateMeterReading(billToSave.roomId, billToSave.monthEndMeterReading, System.currentTimeMillis())
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
