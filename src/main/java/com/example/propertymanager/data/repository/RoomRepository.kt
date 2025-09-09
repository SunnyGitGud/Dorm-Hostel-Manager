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

    suspend fun delete(room: RoomEntity) {
        dao.delete(room)
    }
}
