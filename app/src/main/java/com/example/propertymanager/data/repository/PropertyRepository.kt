package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.PropertyDao
import com.example.propertymanager.data.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow

class PropertyRepository(private val dao: PropertyDao) {
    fun getAllProperties(): Flow<List<PropertyEntity>> = dao.getAllProperties()
    suspend fun getByName(name: String): PropertyEntity? = dao.getByName(name) // Added this method
    suspend fun insert(property: PropertyEntity): Long = dao.insert(property) // Changed return type to Long
    suspend fun delete(property: PropertyEntity) = dao.delete(property)

    // Added method to update the hidden status of a property
    suspend fun updatePropertyHiddenStatus(propertyId: Int, isHidden: Boolean) {
        dao.updateHiddenStatus(propertyId, isHidden)
    }
}
