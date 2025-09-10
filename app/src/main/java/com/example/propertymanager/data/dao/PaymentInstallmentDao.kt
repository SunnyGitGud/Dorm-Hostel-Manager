package com.example.propertymanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.propertymanager.data.entities.PaymentInstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentInstallmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: PaymentInstallmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInstallments(installments: List<PaymentInstallmentEntity>)

    @Query("SELECT * FROM payment_installments WHERE billId = :billId ORDER BY date ASC")
    fun getInstallmentsForBill(billId: Int): Flow<List<PaymentInstallmentEntity>>

    @Query("SELECT * FROM payment_installments WHERE billId = :billId ORDER BY date ASC")
    suspend fun getInstallmentsForBillSuspend(billId: Int): List<PaymentInstallmentEntity>

    @Query("DELETE FROM payment_installments WHERE billId = :billId")
    suspend fun deleteInstallmentsForBill(billId: Int)
}
