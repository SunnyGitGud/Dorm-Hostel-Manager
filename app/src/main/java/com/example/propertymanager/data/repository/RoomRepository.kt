package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.RoomDao
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.model.RoomWithTenant // Added import
import kotlinx.coroutines.flow.Flow // Ensure Flow is imported

class RoomRepository(private val dao: RoomDao) {
    // Updated to return Flow<List<RoomWithTenant>>
    fun getRoomsForProperty(propertyId: Int): Flow<List<RoomWithTenant>> {
        return dao.getRoomsForProperty(propertyId)
    }

    suspend fun insert(room: RoomEntity) {
        dao.insert(room)
    }

    suspend fun update(room: RoomEntity) { // New general update method
        dao.update(room)
    }

    suspend fun updateMeterReading(roomId: Int, initialMeterReading: Double?, initialMeterReadingDate: Long?) { // Specific update for meter readings
        dao.updateMeterReading(roomId, initialMeterReading, initialMeterReadingDate)
    }

    suspend fun delete(room: RoomEntity) {
        dao.delete(room)
    }

    // Added function to get a single room by ID
    suspend fun getRoomById(roomId: Int): RoomEntity? {
        return dao.getRoomById(roomId)
    }
}
