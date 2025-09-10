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
import kotlin.math.ceil // Ensure ceil is imported

// Data class for bill summary on Room Card
data class BillStatusSummary(
    val amount: Double = 0.0, // Positive for due, negative for advance (precise value)
    val isDue: Boolean = false,
    val isAdvance: Boolean = false,
    val isGenerated: Boolean = false,
    val displayAmount: Double = 0.0 // abs(amount) rounded up for display
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

                // bill.totalAmountDue is already ceil-ed. bill.amountPaid is precise.
                val balance = bill.totalAmountDue - bill.amountPaid 
                val isGenerated = bill.id != 0
                // Epsilon comparison for floating point precision issues with balance
                val isDue = isGenerated && balance > 0.001 
                val isAdvance = isGenerated && balance < -0.001

                summaries[room.id] = BillStatusSummary(
                    amount = balance, // Store the precise balance
                    isDue = isDue,
                    isAdvance = isAdvance,
                    isGenerated = isGenerated,
                    // Round up the absolute value of the balance for display
                    displayAmount = ceil(abs(balance)) 
                )
            }
            _roomBillSummaries.value = summaries
        }
    }

    fun loadCurrentCalendarMonthBill(roomId: Int) {
        viewModelScope.launch {
            val roomEntity = roomRepository.getRoomById(roomId) 
            if (roomEntity != null && !roomEntity.isHidden) {
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
                initialMeterReadingDate = if (initialMeterReading != null) System.currentTimeMillis() else null,
                isHidden = false
            )
            roomRepository.insert(room)
        }
    }

    fun updateRoomDetails(room: RoomEntity) {
        viewModelScope.launch {
            roomRepository.update(room) 
            updateRoomBillSummaries(_roomsWithTenants.value) 
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
                    if (roomEntity != null && !roomEntity.isHidden) { 
                        var rentForMoveInMonth = roomEntity.rent // This is Double
                        val moveInDay = moveInCalendar.get(Calendar.DAY_OF_MONTH)
                        if (moveInDay > 1) {
                            val totalDaysInMonth = moveInCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                            if (totalDaysInMonth > 0) {
                                val daysOccupied = totalDaysInMonth - moveInDay + 1
                                rentForMoveInMonth = (roomEntity.rent / totalDaysInMonth.toDouble()) * daysOccupied.toDouble()
                                // Note: rentForMoveInMonth for the first bill is not explicitly ceil-ed here before storing in rentAtBillingTime.
                                // It will be part of the sum that *then* gets ceil-ed for totalAmountDue.
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
                        saveBill(initialMoveInBill) 
                    }
                } else {
                    updateRoomBillSummaries(_roomsWithTenants.value) 
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
        
        if (roomEntity == null || roomEntity.isHidden) { 
             return MonthlyBillEntity( 
                roomId = roomId, year = billYear, month = billMonth,
                tenantNameAtBillingTime = if (roomEntity == null) "Error: Room not found" else "Room Hidden", 
                rentAtBillingTime = 0.0, 
                isFullyPaid = true,
                totalAmountDue = 0.0, // Explicitly set to 0.0 as it's a ceil-ed field
                isFullRentAppliedOverride = false 
            ).apply { installments = emptyList(); calculateTotalDue() } // Ensure calculateTotalDue is called for consistency
        }

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
                    // previousMonthBillObject.totalAmountDue is ceil-ed, amountPaid is precise.
                    val prevBalance = previousMonthBillObject.totalAmountDue - previousMonthBillObject.amountPaid
                    if (prevBalance > 0.001) { // Only carry forward if it's a due
                         calculatedPreviousMonthDues = prevBalance
                    } else {
                        calculatedPreviousMonthDues = 0.0 // Don't carry forward credits this way, they are on the bill itself.
                    }
                }
            }
        }

        var billToProcess = monthlyBillRepository.getBillForRoomMonthYearSuspend(roomId, billYear, billMonth)
        var needsSave = false 

        if (billToProcess == null) {
            needsSave = true
            var rentForNewBill = 0.0
            if (tenantForRequestedPeriod != null) {
                rentForNewBill = roomEntity.rent // Full rent as Double
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
            if (billToProcess.id != 0 && billToProcess.installments.isEmpty()) { // Check if persisted but installments not loaded
                val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
            } 

            var updatedRentAtBillingTime = billToProcess.rentAtBillingTime
            var updatedIsFullRentAppliedOverride = billToProcess.isFullRentAppliedOverride
            val tenantActuallyChanged = (billToProcess.tenantIdAtBillingTime != tenantForRequestedPeriod?.id) || 
                                      (billToProcess.tenantNameAtBillingTime != (tenantForRequestedPeriod?.name ?: "Not Occupied"))
            
            val previousDuesChanged = abs(billToProcess.previousMonthDues - calculatedPreviousMonthDues) > 0.001

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
            if (previousDuesChanged) {
                needsSave = true 
            }
            if (billToProcess.electricityRateAtBillingTime == null && roomEntity.electricityRatePerUnit > 0) {
                 billToProcess = billToProcess.copy(electricityRateAtBillingTime = roomEntity.electricityRatePerUnit)
                needsSave = true 
            }
            if (needsSave) {
                val originalInstallments = billToProcess.installments
                val originalAmountPaid = billToProcess.amountPaid // Preserve manually set amount paid if any, during this copy

                billToProcess = billToProcess.copy(
                    tenantIdAtBillingTime = tenantForRequestedPeriod?.id,
                    tenantNameAtBillingTime = tenantForRequestedPeriod?.name ?: "Not Occupied",
                    rentAtBillingTime = updatedRentAtBillingTime, 
                    isFullRentAppliedOverride = updatedIsFullRentAppliedOverride, 
                    previousMonthDues = calculatedPreviousMonthDues,
                    electricityRateAtBillingTime = billToProcess.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
                )
                billToProcess.installments = originalInstallments 
                billToProcess.amountPaid = originalAmountPaid
            }
        }

        if (needsSave) {
            val savedId = saveBill(billToProcess) // saveBill calls calculateTotalDue before DB op
            // Re-fetch or update billToProcess with ID and potentially re-loaded installments if it was a new bill
            if (billToProcess.id == 0 && savedId > 0L) {
                 val fetchedBill = monthlyBillRepository.getBillByIdSuspend(savedId.toInt())
                 if (fetchedBill != null) {
                     billToProcess = fetchedBill
                     if (billToProcess.id != 0 && billToProcess.installments.isEmpty()) {
                        val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                        billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
                     }
                 }
            } else if (billToProcess.id != 0) { // If it was an existing bill that was saved
                 // Potentially re-fetch to get the most accurate state after save, though saveBill should handle calculateTotalDue
                 val fetchedBill = monthlyBillRepository.getBillByIdSuspend(billToProcess.id)
                  if (fetchedBill != null) {
                     billToProcess = fetchedBill
                      if (billToProcess.installments.isEmpty()) { // Ensure installments are loaded
                         val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                         billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
                      }
                 }
            }
        } else {
            // If not saved, but previous dues might have changed from a recursive call, ensure total is recalculated.
            // Or if tenant changed without triggering a save yet.
            billToProcess.calculateTotalDue() 
        }
        
        // Final calculation if not done by saveBill or if state changed post-last-calc
        // if (!needsSave) billToProcess.calculateTotalDue()
        return billToProcess
    }

    suspend fun saveBill(bill: MonthlyBillEntity): Long {
        val roomEntity = roomRepository.getRoomById(bill.roomId)
        var performRoomMeterRolloverThisSave = false

        if (roomEntity == null || roomEntity.isHidden) { 
            updateRoomBillSummaries(_roomsWithTenants.value) 
            return 0L
        }

        if (bill.tenantNameAtBillingTime != "Not Occupied") {
            if (bill.electricityUnits == null || !bill.isInitialReadingRolledOver) {
                bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
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
            bill.waterBill = 0.0 // No water bill for not occupied room
            bill.otherCharges = 0.0 // No other charges for not occupied room
            bill.otherChargesDescription = null
            bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
        }
        
        // Make a copy to ensure calculateTotalDue uses the latest state before saving
        val billToSave = if (performRoomMeterRolloverThisSave) bill.copy(isInitialReadingRolledOver = true) else bill.copy()
        billToSave.installments = bill.installments // Ensure installments are carried over for amountPaid calculation if any
        
        // Crucially, calculateTotalDue is called here, which applies ceil() to totalAmountDue
        billToSave.calculateTotalDue()
        
        val savedBillId = monthlyBillRepository.upsertBill(billToSave) // Changed from insertOrUpdateBill

        if (savedBillId > 0) {
            val billIdInt = if (savedBillId > Int.MAX_VALUE) billToSave.id else savedBillId.toInt() // Use existing ID if it's an update
            paymentInstallmentRepository.deleteInstallmentsForBill(billIdInt)
            val installmentEntities = bill.installments.map {
                PaymentInstallmentEntity(billId = billIdInt, amount = it.amount, date = it.date)
            }
            if (installmentEntities.isNotEmpty()) {
                paymentInstallmentRepository.addAllInstallments(installmentEntities)
            }
            
            loadCurrentCalendarMonthBill(bill.roomId) 
            updateRoomBillSummaries(_roomsWithTenants.value) 
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
        val roomEntity = roomRepository.getRoomById(roomId)
        if (roomEntity == null || roomEntity.isHidden) return emptyList()

        val bills = monthlyBillRepository.getBillsForRoomSuspend(roomId)
        return bills.map { bill ->
            if (bill.id != 0 && bill.installments.isEmpty()) {
                val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(bill.id)
                bill.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
            } 
            // Ensure calculateTotalDue is called if it hasn't been from DB load (though Room usually calls default constructor)
            // However, our `ceil` logic is in calculateTotalDue, so it's good to ensure it's run.
            bill.calculateTotalDue()
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

    fun getBillByIdFlow(billId: Int): Flow<MonthlyBillEntity?> { // Changed return type here
        return monthlyBillRepository.getBillById(billId).map { bill ->
            bill?.let {
                val roomEntity = roomRepository.getRoomById(it.roomId) 
                if (roomEntity == null || roomEntity.isHidden) {
                    return@map null
                }
                if (it.id != 0 && it.installments.isEmpty()) {
                    val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(it.id)
                    it.installments = installmentEntities.map { inst -> PaymentInstallment(amount = inst.amount, date = inst.date) }
                }
                it.calculateTotalDue() // Ensure totalAmountDue is correctly ceil-ed
            }
            bill
        }
    }

    fun setRoomHiddenStatus(roomId: Int, isHidden: Boolean) {
        viewModelScope.launch {
            roomRepository.updateRoomHiddenStatus(roomId, isHidden)
        }
    }

    fun deleteRoom(roomWithTenant: RoomWithTenant) {
        viewModelScope.launch {
            roomRepository.delete(roomWithTenant.room)
        }
    }
}
