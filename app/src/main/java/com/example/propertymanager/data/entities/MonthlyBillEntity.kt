package com.example.propertymanager.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_bills",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE // If a room is deleted, its bills are also deleted
        )
    ],
    indices = [
        Index(value = ["roomId"]),
        Index(value = ["roomId", "year", "month"], unique = true) // Ensures one bill per room per month
    ]
)
data class MonthlyBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val year: Int,
    val month: Int, // 1 for Jan, 12 for Dec

    var tenantIdAtBillingTime: Int? = null,
    var tenantNameAtBillingTime: String? = null,

    var rentAtBillingTime: Double = 0.0,

    // Fields for electricity calculation
    var monthEndMeterReading: Double? = null,      // Month end meter reading input by user
    var electricityUnits: Double? = null,          // Calculated as (monthEndMeterReading - initialMeterReading from RoomEntity)
    var electricityRateAtBillingTime: Double? = null, // Rate at the time of bill generation, from RoomEntity

    var electricityBill: Double = 0.0, // This will store the calculated (units * rate)
    var waterBill: Double = 0.0,
    var otherCharges: Double = 0.0,
    var otherChargesDescription: String? = null,
    var previousMonthDues: Double = 0.0,

    var totalAmountDue: Double = 0.0,
    var amountPaid: Double = 0.0,
    var dueDate: Long = 0L, // Timestamp
    var paymentDate: Long? = null, // Timestamp, null if not fully paid
    var isFullyPaid: Boolean = false,
    var isInitialReadingRolledOver: Boolean = false // Ensures this field is present
) {
    fun calculateTotalDue() {
        // electricityUnits and electricityRateAtBillingTime should be set by the ViewModel before this is called.
        electricityBill = (electricityUnits ?: 0.0) * (electricityRateAtBillingTime ?: 0.0)
        
        // Calculate total amount due
        totalAmountDue = rentAtBillingTime +
                         electricityBill +
                         waterBill +
                         otherCharges +
                         previousMonthDues
    }
}
