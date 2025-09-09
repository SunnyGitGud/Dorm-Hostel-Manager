package com.example.propertymanager.data.model // Create a new 'model' package

import androidx.room.Embedded
import androidx.room.Relation
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity

data class RoomWithTenant(
    @Embedded val room: RoomEntity,
    @Relation(
        parentColumn = "id", // Primary key of RoomEntity
        entityColumn = "roomId"  // Foreign key in TenantEntity
    )
    val tenant: TenantEntity? // A room might not have a tenant
)
