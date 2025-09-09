package com.example.propertymanager.data.dao

import androidx.room.* // Import Transaction
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.model.RoomWithTenant // Added import
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomEntity)

    // Updated to return RoomWithTenant
    @Transaction
    @Query("SELECT * FROM rooms WHERE propertyId = :propertyId")
    fun getRoomsForProperty(propertyId: Int): Flow<List<RoomWithTenant>>

    @Delete
    suspend fun delete(room: RoomEntity)

    // Optional: Add a function to get a single RoomEntity if needed elsewhere
    // @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    // fun getRoomById(roomId: Int): Flow<RoomEntity?>
}
