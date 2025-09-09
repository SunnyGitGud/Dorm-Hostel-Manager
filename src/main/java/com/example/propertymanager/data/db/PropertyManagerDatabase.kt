package com.example.propertymanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.propertymanager.data.dao.PropertyDao
import com.example.propertymanager.data.dao.RoomDao
import com.example.propertymanager.data.dao.PaymentDao
import com.example.propertymanager.data.dao.TenantDao
import com.example.propertymanager.data.dao.MonthlyBillDao
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.PaymentEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.data.entities.MonthlyBillEntity

@Database(
    entities = [
        PropertyEntity::class,
        RoomEntity::class,
        PaymentEntity::class,
        TenantEntity::class,
        MonthlyBillEntity::class
    ],
    version = 6, // Incremented version to 6
    exportSchema = false
)
abstract class PropertyManagerDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun paymentDao(): PaymentDao
    abstract fun tenantDao(): TenantDao
    abstract fun monthlyBillDao(): MonthlyBillDao

    companion object {
        @Volatile
        private var INSTANCE: PropertyManagerDatabase? = null

        fun getDatabase(context: Context): PropertyManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PropertyManagerDatabase::class.java,
                    "property_manager_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
