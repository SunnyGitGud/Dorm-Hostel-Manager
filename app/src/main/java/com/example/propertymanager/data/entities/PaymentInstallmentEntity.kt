package com.example.propertymanager.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_installments",
    foreignKeys = [
        ForeignKey(
            entity = MonthlyBillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE // If a bill is deleted, its installments are also deleted
        )
    ],
    indices = [Index(value = ["billId"])]
)
data class PaymentInstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val billId: Int, // Foreign key to MonthlyBillEntity
    val amount: Double,
    val date: Long // Timestamp of the payment
)