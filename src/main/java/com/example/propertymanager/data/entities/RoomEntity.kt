package com.example.propertymanager.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index // Added import for Index

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["propertyId"])] // Added index for propertyId
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val propertyId: Int,
    val name: String,
    val rent: Double
)
