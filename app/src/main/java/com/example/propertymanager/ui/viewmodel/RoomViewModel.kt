package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentInstallment // For UI model
import com.example.propertymanager.data.entities.PaymentInstallmentEntity // For DB entity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.data.model.RoomWithTenant
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

// Data class for bill summary on Room Card
data class BillStatusSummary(
    val amount: Double = 0.0, // Positive for due, negative for advance
    val isDue: Boolean = false,
    val isAdvance: Boolean = false,
    val isGenerated: Boolean = false,
    val displayAmount: Double = 0.0 // abs(amount)
)

class RoomViewModel(
    private val roomRepository: RoomRepository,
    private val tenantRepository: TenantRepository,
    private val monthlyBillRepository: MonthlyBillRepository,
    private val paymentInstallmentRepository: PaymentInstallmentRepository,
    private val propertyId: Int
) : ViewModel() {

    private val _roomsWithTenants = MutableStateFlow<List<RoomWithTenant>>(emptyList())
    val roomsWithTenants: StateFlow<List<RoomWithTenant>> = _roomsWithTenants.asStateFlow()

    private val _currentCalendarMonthBill = MutableStateFlow<MonthlyBillEntity?>(null)
    val currentCalendarMonthBill: StateFlow<MonthlyBillEntity?> = _currentCalendarMonthBill.asStateFlow()

    private val _roomBillSummaries = MutableStateFlow<Map<Int, BillStatusSummary>>(emptyMap())
    val roomBillSummaries: StateFlow<Map<Int, BillStatusSummary>> = _roomBillSummaries.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepository.getRoomsForProperty(propertyId).collect { rooms ->
                _roomsWithTenants.value = rooms
                updateRoomBillSummaries(rooms)
            }
        }
    }

    private fun updateRoomBillSummaries(rooms: List<RoomWithTenant>) {
        viewModelScope.launch {
            val summaries = mutableMapOf<Int, BillStatusSummary>()
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1

            for (roomWithTenant in rooms) {
                val room = roomWithTenant.room
                val bill = getOrCreateBillForRoom(room.id, currentYear, currentMonth, room.rent)

                val balance = bill.totalAmountDue - bill.amountPaid
                val isGenerated = bill.id != 0
                val isDue = isGenerated && balance > 0.001 
                val isAdvance = isGenerated && balance < -0.001

                summaries[room.id] = BillStatusSummary(
                    amount = balance,
                    isDue = isDue,
                    isAdvance = isAdvance,
                    isGenerated = isGenerated,
                    displayAmount = kotlin.math.abs(balance)
                )
            }
            _roomBillSummaries.value = summaries
        }
    }

    fun loadCurrentCalendarMonthBill(roomId: Int) {
        viewModelScope.launch {
            val roomEntity = roomRepository.getRoomById(roomId) 
            if (roomEntity != null) {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val bill = getOrCreateBillForRoom(
                    roomId = roomId,
                    billYear = year,
                    billMonth = month,
                    currentRoomFullRent = roomEntity.rent
                )
                _currentCalendarMonthBill.value = bill
            } else {
                _currentCalendarMonthBill.value = null
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
            // roomsWithTenants will auto-update and trigger updateRoomBillSummaries via collect
        }
    }

    fun updateRoomDetails(room: RoomEntity) {
        viewModelScope.launch {
            roomRepository.update(room)
            updateRoomBillSummaries(_roomsWithTenants.value) // Manually trigger for existing rooms
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
                            isFullRentAppliedOverride = null, 
                            electricityRateAtBillingTime = roomEntity.electricityRatePerUnit,
                            previousMonthDues = 0.0,
                            dueDate = defaultDueDateCalendar.timeInMillis,
                            isInitialReadingRolledOver = false
                        )
                        initialMoveInBill.installments = emptyList()
                        saveBill(initialMoveInBill) // saveBill will trigger updateRoomBillSummaries
                    }
                } else {
                    updateRoomBillSummaries(_roomsWithTenants.value) // Tenant details might affect existing bill display name
                }
            } else {
                 updateRoomBillSummaries(_roomsWithTenants.value)
            }
        }
    }

    fun updateTenant(tenant: TenantEntity) {
        viewModelScope.launch {
            tenantRepository.insertOrUpdateTenant(tenant)
            updateRoomBillSummaries(_roomsWithTenants.value)
        }
    }

    fun recordTenantMoveOutDate(tenantId: Int, moveOutTimestamp: Long) {
        viewModelScope.launch {
            tenantRepository.setTenantMoveOutDate(tenantId, moveOutTimestamp)
            updateRoomBillSummaries(_roomsWithTenants.value)
        }
    }

    suspend fun getOrCreateBillForRoom(
        roomId: Int,
        billYear: Int,
        billMonth: Int,
        currentRoomFullRent: Double 
    ): MonthlyBillEntity {
        val roomEntity = roomRepository.getRoomById(roomId)
            ?: return MonthlyBillEntity( 
                roomId = roomId, year = billYear, month = billMonth,
                tenantNameAtBillingTime = "Error: Room not found", rentAtBillingTime = 0.0, isFullyPaid = true,
                isFullRentAppliedOverride = false 
            ).apply { installments = emptyList() } 

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
                val previousMonthBillObject = getOrCreateBillForRoom(roomId, prevBillQueryYear, prevBillQueryMonth, roomEntity.rent)
                val startOfPrevBillMonthForTenantCheck = Calendar.getInstance().apply { set(prevBillQueryYear, prevBillQueryMonth - 1, 1, 0,0,0); set(Calendar.MILLISECOND,0)}.timeInMillis
                val endOfPrevBillMonthForTenantCheck = Calendar.getInstance().apply { set(prevBillQueryYear, prevBillQueryMonth - 1, 1,23,59,59); set(Calendar.MILLISECOND,999); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))}.timeInMillis
                val tenantForPreviousBillObjectPeriod = tenantRepository.getTenantForBillPeriod(roomId, startOfPrevBillMonthForTenantCheck, endOfPrevBillMonthForTenantCheck)
                if (tenantForPreviousBillObjectPeriod != null && tenantForRequestedPeriod.id == tenantForPreviousBillObjectPeriod.id) {
                    calculatedPreviousMonthDues = previousMonthBillObject.totalAmountDue - previousMonthBillObject.amountPaid
                }
            }
        }

        var billToProcess = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, billYear, billMonth)
        var needsSave = false 

        if (billToProcess == null) {
            needsSave = true
            var rentForNewBill = 0.0
            if (tenantForRequestedPeriod != null) {
                rentForNewBill = roomEntity.rent
                val moveInCal = Calendar.getInstance().apply { timeInMillis = tenantForRequestedPeriod.moveInDate }
                if (moveInCal.get(Calendar.YEAR) == billYear && (moveInCal.get(Calendar.MONTH) + 1) == billMonth && moveInCal.get(Calendar.DAY_OF_MONTH) > 1) {
                    val totalDaysInMonth = moveInCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    if (totalDaysInMonth > 0) {
                        val daysOccupied = totalDaysInMonth - moveInCal.get(Calendar.DAY_OF_MONTH) + 1
                        rentForNewBill = (roomEntity.rent / totalDaysInMonth.toDouble()) * daysOccupied.toDouble()
                    }
                }
            }
            val defaultDueDateCalendar = Calendar.getInstance().apply { set(billYear, billMonth - 1, 5) }
            billToProcess = MonthlyBillEntity(
                roomId = roomId, year = billYear, month = billMonth,
                tenantIdAtBillingTime = tenantForRequestedPeriod?.id,
                tenantNameAtBillingTime = tenantForRequestedPeriod?.name ?: "Not Occupied",
                rentAtBillingTime = rentForNewBill,
                isFullRentAppliedOverride = null, 
                electricityRateAtBillingTime = roomEntity.electricityRatePerUnit,
                previousMonthDues = calculatedPreviousMonthDues, 
                dueDate = defaultDueDateCalendar.timeInMillis,
                isInitialReadingRolledOver = false
            )
            billToProcess.installments = emptyList()
        } else {
            if (billToProcess.id != 0) { 
                val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
            } 

            var updatedRentAtBillingTime = billToProcess.rentAtBillingTime
            var updatedIsFullRentAppliedOverride = billToProcess.isFullRentAppliedOverride
            val tenantActuallyChanged = (billToProcess.tenantIdAtBillingTime != tenantForRequestedPeriod?.id) || 
                                      (billToProcess.tenantNameAtBillingTime != (tenantForRequestedPeriod?.name ?: "Not Occupied"))
            if (tenantActuallyChanged) {
                needsSave = true 
                if (tenantForRequestedPeriod != null) {
                    updatedRentAtBillingTime = roomEntity.rent 
                    val moveInCal = Calendar.getInstance().apply { timeInMillis = tenantForRequestedPeriod.moveInDate }
                    if (moveInCal.get(Calendar.YEAR) == billYear && (moveInCal.get(Calendar.MONTH) + 1) == billMonth && moveInCal.get(Calendar.DAY_OF_MONTH) > 1) {
                        val totalDaysInMonth = moveInCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        if (totalDaysInMonth > 0) {
                            val daysOccupied = totalDaysInMonth - moveInCal.get(Calendar.DAY_OF_MONTH) + 1
                            updatedRentAtBillingTime = (roomEntity.rent / totalDaysInMonth.toDouble()) * daysOccupied.toDouble()
                        }
                    }
                } else {
                    updatedRentAtBillingTime = 0.0
                }
                updatedIsFullRentAppliedOverride = null 
            }
            if (abs((billToProcess.previousMonthDues ?: 0.0) - calculatedPreviousMonthDues) > 0.001) {
                needsSave = true 
            }
            if (billToProcess.electricityRateAtBillingTime == null && roomEntity.electricityRatePerUnit > 0) {
                needsSave = true 
            }
            if (needsSave) {
                val originalInstallments = billToProcess.installments
                billToProcess = billToProcess.copy(
                    tenantIdAtBillingTime = tenantForRequestedPeriod?.id,
                    tenantNameAtBillingTime = tenantForRequestedPeriod?.name ?: "Not Occupied",
                    rentAtBillingTime = updatedRentAtBillingTime, 
                    isFullRentAppliedOverride = updatedIsFullRentAppliedOverride, 
                    previousMonthDues = calculatedPreviousMonthDues,
                    electricityRateAtBillingTime = billToProcess.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
                )
                billToProcess.installments = originalInstallments 
            }
        }

        if (needsSave) {
            val savedId = saveBill(billToProcess)
            if (billToProcess.id == 0 && savedId > 0L) {
                 billToProcess = billToProcess.copy(id = savedId.toInt())
                 val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                 billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
            }
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
        billToSave.installments = bill.installments 
        billToSave.calculateTotalDue()
        
        val savedBillId = monthlyBillRepository.insertOrUpdateBill(billToSave)

        if (savedBillId > 0) {
            paymentInstallmentRepository.deleteInstallmentsForBill(savedBillId.toInt())
            val installmentEntities = bill.installments.map {
                PaymentInstallmentEntity(billId = savedBillId.toInt(), amount = it.amount, date = it.date)
            }
            if (installmentEntities.isNotEmpty()) {
                paymentInstallmentRepository.addAllInstallments(installmentEntities)
            }
            
            loadCurrentCalendarMonthBill(bill.roomId) 
            updateRoomBillSummaries(_roomsWithTenants.value) // Update summaries after save
        }

        if (savedBillId > 0 && performRoomMeterRolloverThisSave && billToSave.monthEndMeterReading != null) {
            roomRepository.updateMeterReading(billToSave.roomId, billToSave.monthEndMeterReading, System.currentTimeMillis())
        }
        return savedBillId
    }

    fun clearCurrentCalendarMonthBillState(){
        _currentCalendarMonthBill.value = null
    }

    suspend fun getAllBillsForRoom(roomId: Int): List<MonthlyBillEntity> {
        val bills = monthlyBillRepository.getBillsForRoomSuspend(roomId)
        return bills.map { bill ->
            if (bill.id != 0) {
                val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                bill.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
            } 
            bill
        }
    }
    
    fun getInstallmentsForBillFlow(billId: Int): Flow<List<PaymentInstallment>> {
        return paymentInstallmentRepository.getInstallmentsForBill(billId).map { entities ->
            entities.map { PaymentInstallment(amount = it.amount, date = it.date) }
        }
    }

    fun getAllTenantsForRoomFlow(roomId: Int): Flow<List<TenantEntity>> {
        return tenantRepository.getAllTenantsForRoom(roomId)
    }

    fun getBillByIdFlow(billId: Int): Flow<MonthlyBillEntity?> {
        return monthlyBillRepository.getBillById(billId).map { bill ->
            bill?.let {
                if (it.id != 0 && it.installments.isEmpty()) {
                    val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(it.id)
                    it.installments = installmentEntities.map { inst -> PaymentInstallment(amount = inst.amount, date = inst.date) }
                }
            }
            bill
        }
    }
}
