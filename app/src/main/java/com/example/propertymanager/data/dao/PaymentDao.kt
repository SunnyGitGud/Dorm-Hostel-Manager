package com.example.propertymanager.data.dao

import androidx.room.*
import com.example.propertymanager.data.entities.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE roomId = :roomId")
    fun getPaymentsForRoom(roomId: Int): Flow<List<PaymentEntity>>

    @Delete
    suspend fun delete(payment: PaymentEntity)
}
