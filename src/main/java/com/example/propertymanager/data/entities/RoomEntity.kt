package com.example.propertymanager.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

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
    indices = [Index(value = ["propertyId"])]
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val propertyId: Int,
    val name: String,
    val rent: Double,
    val electricityRatePerUnit: Double = 10.0, // Existing field with default value
    val initialMeterReading: Double? = null,    // New field for initial meter reading
    val initialMeterReadingDate: Long? = null   // New field for the date of initial reading
)
