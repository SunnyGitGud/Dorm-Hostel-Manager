package com.example.propertymanager.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tenants",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE // If a room is deleted, its tenants are also deleted
        )
    ],
    indices = [Index(value = ["roomId"])]
)
data class TenantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val name: String,
    val moveInDate: Long, // Timestamp representing the start of occupancy
    var moveOutDate: Long? = null // Timestamp, null if currently active/occupying
)
