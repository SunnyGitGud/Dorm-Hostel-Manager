package com.example.propertymanager.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

// Define PaymentInstallment as a separate data class (can be in the same file)
data class PaymentInstallment(
    val amount: Double,
    val date: Long // Timestamp of the payment
)

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
    var isFullRentAppliedOverride: Boolean? = null,

    var monthEndMeterReading: Double? = null,
    var electricityUnits: Double? = null,
    var electricityRateAtBillingTime: Double? = null,

    var electricityBill: Double = 0.0,
    var waterBill: Double = 0.0,
    var otherCharges: Double = 0.0,
    var otherChargesDescription: String? = null,
    var previousMonthDues: Double = 0.0,

    var totalAmountDue: Double = 0.0,
    var amountPaid: Double = 0.0,
    var dueDate: Long = 0L,
    var paymentDate: Long? = null,
    var isFullyPaid: Boolean = false,
    var isInitialReadingRolledOver: Boolean = false
    // installments is removed from primary constructor
) {
    @Ignore // Room will ignore this field. Persistence requires a TypeConverter.
    var installments: List<PaymentInstallment> = emptyList()

    fun calculateTotalDue() {
        electricityBill = (electricityUnits ?: 0.0) * (electricityRateAtBillingTime ?: 0.0)
        
        totalAmountDue = rentAtBillingTime +
                         electricityBill +
                         waterBill +
                         otherCharges +
                         previousMonthDues

        isFullyPaid = amountPaid >= totalAmountDue - 0.001 
        if (!isFullyPaid) {
            // paymentDate on the bill might represent the date the bill became fully paid.
        } else {
            if (paymentDate == null && amountPaid > 0) {
                paymentDate = installments.maxOfOrNull { it.date } ?: System.currentTimeMillis()
            } else if (amountPaid == 0.0 && totalAmountDue == 0.0) {
                paymentDate = paymentDate ?: System.currentTimeMillis()
            }
        }
    }
}
