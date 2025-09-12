package com.example.propertymanager.ui.viewmodel

import android.util.Log // Added for logging
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
import kotlinx.coroutines.flow.firstOrNull 
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil

// Data class for bill summary on Room Card (remains unchanged)
data class BillStatusSummary(
    val amount: Double = 0.0,
    val isDue: Boolean = false,
    val isAdvance: Boolean = false,
    val isGenerated: Boolean = false,
    val displayAmount: Double = 0.0
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

    private val _displayedBillDetails = MutableStateFlow<MonthlyBillEntity?>(null)
    val displayedBillDetails: StateFlow<MonthlyBillEntity?> = _displayedBillDetails.asStateFlow()
    private var displayedYear: Int? = null
    private var displayedMonth: Int? = null

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
                    displayAmount = ceil(abs(balance))
                )
            }
            _roomBillSummaries.value = summaries
        }
    }

    fun loadBillDetailsForMonth(roomId: Int, year: Int, month: Int) {
        viewModelScope.launch {
            Log.d("RoomViewModel", "loadBillDetailsForMonth: roomId=$roomId, year=$year, month=$month")
            val roomEntity = roomRepository.getRoomById(roomId)
            if (roomEntity != null && !roomEntity.isHidden) {
                val bill = getOrCreateBillForRoom(
                    roomId = roomId,
                    billYear = year,
                    billMonth = month,
                    currentRoomFullRent = roomEntity.rent
                )
                _displayedBillDetails.value = bill
                displayedYear = year
                displayedMonth = month
                Log.d("RoomViewModel", "loadBillDetailsForMonth: Loaded bill for $year-$month, ID: ${bill.id}, Due: ${bill.totalAmountDue}")
            } else {
                _displayedBillDetails.value = null
                displayedYear = null
                displayedMonth = null
                Log.d("RoomViewModel", "loadBillDetailsForMonth: Room not found or hidden for roomId=$roomId")
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
            if (room.id == _displayedBillDetails.value?.roomId && displayedYear != null && displayedMonth != null) {
                loadBillDetailsForMonth(room.id, displayedYear!!, displayedMonth!!)
            }
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
                            previousMonthDues = 0.0, // New tenant, new bill = 0 previous dues for this bill
                            dueDate = defaultDueDateCalendar.timeInMillis,
                            isInitialReadingRolledOver = false
                        )
                        initialMoveInBill.installments = emptyList()
                        saveBill(initialMoveInBill) 
                    }
                } else {
                    // Bill exists, ensure it's updated for the new tenant if needed
                    // This will trigger getOrCreateBillForRoom which should handle tenant changes
                    if (roomId == _displayedBillDetails.value?.roomId && moveInYear == displayedYear && moveInMonth == displayedMonth) {
                        loadBillDetailsForMonth(roomId, moveInYear, moveInMonth)
                    }
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
            if (tenant.roomId == _displayedBillDetails.value?.roomId && displayedYear != null && displayedMonth != null) {
                 loadBillDetailsForMonth(tenant.roomId, displayedYear!!, displayedMonth!!)
            }
            updateRoomBillSummaries(_roomsWithTenants.value)
        }
    }

    fun recordTenantMoveOutDate(tenantId: Int, moveOutTimestamp: Long) {
        viewModelScope.launch {
            val tenant = tenantRepository.getTenantById(tenantId)
            val moveOutSuccessful = tenantRepository.setTenantMoveOutDate(tenantId, moveOutTimestamp)

            if (moveOutSuccessful && tenant != null) {
                if (tenant.roomId == _displayedBillDetails.value?.roomId &&
                    displayedYear != null &&
                    displayedMonth != null
                ) {
                    loadBillDetailsForMonth(tenant.roomId, displayedYear!!, displayedMonth!!)
                }
            }
            updateRoomBillSummaries(_roomsWithTenants.value)
        }
    }

    suspend fun getOrCreateBillForRoom(
        roomId: Int,
        billYear: Int,
        billMonth: Int,
        currentRoomFullRent: Double // This might not be needed if roomEntity is always fetched
    ): MonthlyBillEntity {
        val roomEntity = roomRepository.getRoomById(roomId)
        
        if (roomEntity == null || roomEntity.isHidden) { 
             return MonthlyBillEntity( 
                roomId = roomId, year = billYear, month = billMonth,
                tenantNameAtBillingTime = if (roomEntity == null) "Error: Room not found" else "Room Hidden", 
                rentAtBillingTime = 0.0, 
                isFullyPaid = true,
                totalAmountDue = 0.0, 
                isFullRentAppliedOverride = false 
            ).apply { installments = emptyList(); calculateTotalDue() }
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
                    val prevBalance = previousMonthBillObject.totalAmountDue - previousMonthBillObject.amountPaid
                    // Carry forward the balance directly.
                    // If prevBalance is positive, it's dues.
                    // If prevBalance is negative, it's an advance.
                    calculatedPreviousMonthDues = prevBalance
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
            if (billToProcess.id != 0 && billToProcess.installments.isEmpty()) { 
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
                    updatedRentAtBillingTime = 0.0 // No tenant for period, rent is 0
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

            // Core fix for tenant change data carry-over
            if (needsSave) {
                val newTenantId = tenantForRequestedPeriod?.id
                val newTenantName = tenantForRequestedPeriod?.name ?: "Not Occupied"
                
                if (tenantActuallyChanged) {
                    // Tenant has changed. Reset tenant-specific financial data for this bill record.
                    billToProcess = billToProcess.copy(
                        tenantIdAtBillingTime = newTenantId,
                        tenantNameAtBillingTime = newTenantName,
                        rentAtBillingTime = updatedRentAtBillingTime, // Prorated rent for new tenant
                        isFullRentAppliedOverride = null, // Reset rent override flag
                        previousMonthDues = calculatedPreviousMonthDues, // Dues specific to THIS new tenant
                        electricityRateAtBillingTime = roomEntity.electricityRatePerUnit, // Reset to current room rate
                        
                        // Reset fields that belonged to the previous tenant on this bill record
                        electricityUnits = null, 
                        monthEndMeterReading = null,
                        waterBill = 0.0,
                        otherCharges = 0.0,
                        otherChargesDescription = null,
                        isInitialReadingRolledOver = false, // Critical for new tenant's unit calculation
                        
                        amountPaid = 0.0 // Explicitly reset amount paid to zero for new tenant on this bill
                    )
                    billToProcess.installments = emptyList() // Explicitly clear previous tenant's installments
                } else {
                    // Tenant is the same, or no tenant, or only previous dues/rate changed for the same tenant.
                    // Preserve existing financial data (installments, amountPaid, meter readings etc.)
                    val originalInstallments = billToProcess.installments
                    val originalAmountPaid = billToProcess.amountPaid

                    billToProcess = billToProcess.copy(
                        // tenantId and tenantNameAtBillingTime might be updated if tenant just moved out (becomes "Not Occupied")
                        // but it's not a *new* tenant taking over, so financial data is preserved for finalization.
                        tenantIdAtBillingTime = newTenantId, 
                        tenantNameAtBillingTime = newTenantName,
                        rentAtBillingTime = updatedRentAtBillingTime, 
                        isFullRentAppliedOverride = updatedIsFullRentAppliedOverride, 
                        previousMonthDues = calculatedPreviousMonthDues,
                        electricityRateAtBillingTime = billToProcess.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
                        // Meter readings, units, etc., from original billToProcess are preserved if not a tenant change
                        // amountPaid and installments are NOT part of copy, will be set below for this 'else' branch
                    )
                    billToProcess.installments = originalInstallments
                    billToProcess.amountPaid = originalAmountPaid
                }
            }
        }

        if (needsSave) {
            val savedId = saveBillInternal(billToProcess)
            if (billToProcess.id == 0 && savedId > 0L) {
                 val fetchedBill = monthlyBillRepository.getBillByIdSuspend(savedId.toInt())
                 if (fetchedBill != null) {
                     billToProcess = fetchedBill
                     if (billToProcess.id != 0 && billToProcess.installments.isEmpty()) {
                        val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                        billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
                     }
                 }
            } else if (billToProcess.id != 0) { 
                 val fetchedBill = monthlyBillRepository.getBillByIdSuspend(billToProcess.id)
                  if (fetchedBill != null) {
                     billToProcess = fetchedBill
                      if (billToProcess.installments.isEmpty()) {
                         val installmentEntities = paymentInstallmentRepository.getInstallmentsForBillSuspend(billToProcess.id)
                         billToProcess.installments = installmentEntities.map { PaymentInstallment(amount = it.amount, date = it.date) }
                      }
                 }
            }
        } else {
            // Even if not saved, ensure totalAmountDue is up-to-date with any potential runtime changes
            // (though in this path, it should largely be based on DB loaded values)
            billToProcess.calculateTotalDue() 
        }
        return billToProcess
    }

    // Corrected saveBillInternal method
    private suspend fun saveBillInternal(bill: MonthlyBillEntity): Long {
        val roomEntity = roomRepository.getRoomById(bill.roomId)
        var performRoomMeterRolloverThisSave = false

        if (roomEntity == null || roomEntity.isHidden) {
            updateRoomBillSummaries(_roomsWithTenants.value)
            return 0L
        }

        if (bill.tenantNameAtBillingTime != "Not Occupied") {
            bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit

            if (bill.monthEndMeterReading != null && roomEntity.initialMeterReading != null) {
                // Calculate units if they are not set or if the bill needs recalculation (e.g., new tenant)
                if (bill.electricityUnits == null || !bill.isInitialReadingRolledOver) {
                    val startReading = roomEntity.initialMeterReading // Correctly use room's initial reading
                    if (bill.monthEndMeterReading!! >= startReading) {
                        bill.electricityUnits = bill.monthEndMeterReading!! - startReading
                    } else {
                        bill.electricityUnits = 0.0
                        Log.w("RoomViewModel", "monthEndMeterReading ${bill.monthEndMeterReading} is less than startReading $startReading for bill ID ${bill.id}. Units set to 0.")
                    }
                }
            } else if (bill.electricityUnits == null) {
                bill.electricityUnits = 0.0 // Default to 0 if cannot calculate
            }

            // Determine if this bill's monthEndMeterReading should update the room's initialMeterReading for the NEXT period
            if (!bill.isInitialReadingRolledOver && bill.monthEndMeterReading != null && (bill.electricityUnits ?: -1.0) >= 0.0) {
                performRoomMeterRolloverThisSave = true
            }
        } else {
            // No active tenant for this bill, clear/reset usage-based charges
            bill.electricityUnits = 0.0
            bill.monthEndMeterReading = null
            bill.waterBill = 0.0
            bill.otherCharges = 0.0
            bill.otherChargesDescription = null
            bill.electricityRateAtBillingTime = bill.electricityRateAtBillingTime ?: roomEntity.electricityRatePerUnit
        }
        
        // Create a copy for saving, updating isInitialReadingRolledOver if a rollover is performed this save.
        val billToSave = if (performRoomMeterRolloverThisSave) bill.copy(isInitialReadingRolledOver = true) else bill.copy()
        billToSave.installments = bill.installments // Ensure installments list is part of the object being saved
        billToSave.calculateTotalDue() // Calculate total due with potentially updated units/charges before saving
        
        val savedBillId = monthlyBillRepository.upsertBill(billToSave)

        if (savedBillId > 0) {
            val billIdInt = if (savedBillId > Int.MAX_VALUE) billToSave.id else savedBillId.toInt()
            // Save/update installments for the bill
            paymentInstallmentRepository.deleteInstallmentsForBill(billIdInt) // Clear existing
            val installmentEntities = billToSave.installments.map { // Use billToSave's installments
                PaymentInstallmentEntity(billId = billIdInt, amount = it.amount, date = it.date)
            }
            if (installmentEntities.isNotEmpty()) {
                paymentInstallmentRepository.addAllInstallments(installmentEntities)
            }
        }

        // If meter reading was used and needs to update the room's current initial reading for the NEXT period
        if (savedBillId > 0 && performRoomMeterRolloverThisSave && billToSave.monthEndMeterReading != null) {
            roomRepository.updateMeterReading(billToSave.roomId, billToSave.monthEndMeterReading, System.currentTimeMillis())
        }
        return savedBillId
    }

    suspend fun saveBill(bill: MonthlyBillEntity): Long {
        val savedId = saveBillInternal(bill)
        if (savedId > 0) {
            if (bill.roomId == _displayedBillDetails.value?.roomId &&
                bill.year == displayedYear &&
                bill.month == displayedMonth
            ) {
                loadBillDetailsForMonth(bill.roomId, bill.year, bill.month)
            }
            updateRoomBillSummaries(_roomsWithTenants.value)
        }
        return savedId
    }

    fun clearDisplayedBillDetailsState(){
        _displayedBillDetails.value = null
        displayedYear = null
        displayedMonth = null
        Log.d("RoomViewModel", "clearDisplayedBillDetailsState: Cleared displayed bill details.")
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

    fun getBillByIdFlow(billId: Int): Flow<MonthlyBillEntity?> {
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
                it.calculateTotalDue()
            }
            bill
        }
    }

    fun setRoomHiddenStatus(roomId: Int, isHidden: Boolean) {
        viewModelScope.launch {
            roomRepository.updateRoomHiddenStatus(roomId, isHidden)
             if (isHidden && roomId == _displayedBillDetails.value?.roomId) {
                clearDisplayedBillDetailsState()
            }
        }
    }

    fun deleteRoom(roomWithTenant: RoomWithTenant) {
        viewModelScope.launch {
            roomRepository.delete(roomWithTenant.room)
            if (roomWithTenant.room.id == _displayedBillDetails.value?.roomId) {
                clearDisplayedBillDetailsState()
            }
        }
    }
}
