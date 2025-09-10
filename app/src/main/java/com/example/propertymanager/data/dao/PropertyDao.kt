package com.example.propertymanager.data.dao

import androidx.room.*
import com.example.propertymanager.data.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {
    // Updated to only fetch non-hidden properties by default
    @Query("SELECT * FROM properties WHERE isHidden = 0") 
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Insert
    suspend fun insert(property: PropertyEntity)

    @Delete
    suspend fun delete(property: PropertyEntity)

    // Added method to update the isHidden status of a property
    @Query("UPDATE properties SET isHidden = :isHidden WHERE id = :propertyId")
    suspend fun updateHiddenStatus(propertyId: Int, isHidden: Boolean)
}
