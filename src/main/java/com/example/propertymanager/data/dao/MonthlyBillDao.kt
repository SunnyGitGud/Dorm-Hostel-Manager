package com.example.propertymanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.propertymanager.data.entities.MonthlyBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(bill: MonthlyBillEntity): Long // Return Long for the new rowId or updated rowId

    @Query("SELECT * FROM monthly_bills WHERE roomId = :roomId ORDER BY year DESC, month DESC")
    fun getBillsForRoom(roomId: Int): Flow<List<MonthlyBillEntity>>

    // Added for one-time fetch of all bills for a room
    @Query("SELECT * FROM monthly_bills WHERE roomId = :roomId ORDER BY year DESC, month DESC")
    suspend fun getBillsForRoomSuspend(roomId: Int): List<MonthlyBillEntity>

    @Query("SELECT * FROM monthly_bills WHERE id = :billId")
    fun getBillById(billId: Int): Flow<MonthlyBillEntity?>

    @Query("SELECT * FROM monthly_bills WHERE roomId = :roomId AND year = :year AND month = :month LIMIT 1")
    fun getBillForRoomMonthYear(roomId: Int, year: Int, month: Int): Flow<MonthlyBillEntity?>

    // You might also want a simple suspend function for non-Flow one-time fetch
    @Query("SELECT * FROM monthly_bills WHERE roomId = :roomId AND year = :year AND month = :month LIMIT 1")
    suspend fun getBillForRoomMonthYearSuspend(roomId: Int, year: Int, month: Int): MonthlyBillEntity?
}
