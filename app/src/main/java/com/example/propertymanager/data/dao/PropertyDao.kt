package com.example.propertymanager.data.dao

import androidx.room.*
import com.example.propertymanager.data.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties")
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Insert
    suspend fun insert(property: PropertyEntity)

    @Delete
    suspend fun delete(property: PropertyEntity)
}
