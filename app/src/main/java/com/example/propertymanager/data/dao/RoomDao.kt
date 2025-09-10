package com.example.propertymanager.data.dao

import androidx.room.*
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.model.RoomWithTenant
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomEntity)

    @Update
    suspend fun update(room: RoomEntity)

    @Query("UPDATE rooms SET initialMeterReading = :initialMeterReading, initialMeterReadingDate = :initialMeterReadingDate WHERE id = :roomId")
    suspend fun updateMeterReading(roomId: Int, initialMeterReading: Double?, initialMeterReadingDate: Long?)

    @Transaction
    // Updated to only fetch non-hidden rooms by default
    @Query("SELECT * FROM rooms WHERE propertyId = :propertyId AND isHidden = 0") 
    fun getRoomsForProperty(propertyId: Int): Flow<List<RoomWithTenant>>

    @Delete
    suspend fun delete(room: RoomEntity)

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: Int): RoomEntity?

    // Added method to update the isHidden status of a room
    @Query("UPDATE rooms SET isHidden = :isHidden WHERE id = :roomId")
    suspend fun updateRoomHiddenStatus(roomId: Int, isHidden: Boolean)
}
