package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.PropertyDao
import com.example.propertymanager.data.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow

class PropertyRepository(private val dao: PropertyDao) {
    fun getAllProperties(): Flow<List<PropertyEntity>> = dao.getAllProperties()
    suspend fun insert(property: PropertyEntity) = dao.insert(property)
    suspend fun delete(property: PropertyEntity) = dao.delete(property)
}
