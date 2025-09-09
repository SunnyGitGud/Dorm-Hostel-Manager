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

    var tenantIdAtBillingTime: Int? = null, // Added for robust tenant filtering
    var tenantNameAtBillingTime: String? = null,

    var rentAtBillingTime: Double = 0.0,
    var electricityBill: Double = 0.0,
    var waterBill: Double = 0.0,
    var otherCharges: Double = 0.0,
    var otherChargesDescription: String? = null,
    var previousMonthDues: Double = 0.0, 

    var totalAmountDue: Double = 0.0, // Should be calculated: rent + electricity + water + other + previousMonthDues
    var amountPaid: Double = 0.0,
    var dueDate: Long = 0L, // Timestamp
    var paymentDate: Long? = null, // Timestamp, null if not fully paid
    var isFullyPaid: Boolean = false
) {
    // Helper function to recalculate total due (useful before saving)
    fun calculateTotalDue() {
        totalAmountDue = rentAtBillingTime + electricityBill + waterBill + otherCharges + previousMonthDues
    }
}
