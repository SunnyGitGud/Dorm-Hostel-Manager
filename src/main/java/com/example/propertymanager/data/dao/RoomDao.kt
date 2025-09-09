package com.example.propertymanager.data.dao

import androidx.room.*
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.model.RoomWithTenant
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Kept for addRoom, assuming specific intent
    suspend fun insert(room: RoomEntity)

    @Update
    suspend fun update(room: RoomEntity) // New general update method

    @Query("UPDATE rooms SET initialMeterReading = :initialMeterReading, initialMeterReadingDate = :initialMeterReadingDate WHERE id = :roomId")
    suspend fun updateMeterReading(roomId: Int, initialMeterReading: Double?, initialMeterReadingDate: Long?) // Specific update for meter readings

    @Transaction
    @Query("SELECT * FROM rooms WHERE propertyId = :propertyId")
    fun getRoomsForProperty(propertyId: Int): Flow<List<RoomWithTenant>>

    @Delete
    suspend fun delete(room: RoomEntity)

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: Int): RoomEntity?
}
