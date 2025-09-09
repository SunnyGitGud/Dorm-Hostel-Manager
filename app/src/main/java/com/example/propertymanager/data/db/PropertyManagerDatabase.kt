package com.example.propertymanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.propertymanager.data.dao.MonthlyBillDao
import com.example.propertymanager.data.dao.PaymentDao
import com.example.propertymanager.data.dao.PropertyDao
import com.example.propertymanager.data.dao.RoomDao
import com.example.propertymanager.data.dao.TenantDao
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentEntity
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity

@Database(
    entities = [
        PropertyEntity::class,
        RoomEntity::class,
        TenantEntity::class,
        MonthlyBillEntity::class,
        PaymentEntity::class
    ],
    version = 5, // Incremented version from 4 to 5
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PropertyManagerDatabase : RoomDatabase() {

    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun tenantDao(): TenantDao
    abstract fun monthlyBillDao(): MonthlyBillDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: PropertyManagerDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN electricityRatePerUnit REAL NOT NULL DEFAULT 10.0")
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN electricityUnits REAL")
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN electricityRateAtBillingTime REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rooms ADD COLUMN initialMeterReading REAL")
                db.execSQL("ALTER TABLE rooms ADD COLUMN initialMeterReadingDate INTEGER")
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN monthEndMeterReading REAL")
            }
        }

        // Migration from version 4 to 5
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new column to 'monthly_bills' table for tracking rollover status
                // SQLite uses INTEGER for booleans (0 for false, 1 for true)
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN isInitialReadingRolledOver INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): PropertyManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PropertyManagerDatabase::class.java,
                    "property_manager_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5) // Add the new MIGRATION_4_5
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
