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
    version = 6, // Incremented version from 5 to 6
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN isInitialReadingRolledOver INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration from version 5 to 6
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new column to 'monthly_bills' table for storing full rent override checkbox state
                // SQLite uses INTEGER for booleans (0 for false, 1 for true). Field is nullable.
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN isFullRentAppliedOverride INTEGER")
            }
        }

        fun getDatabase(context: Context): PropertyManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PropertyManagerDatabase::class.java,
                    "property_manager_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6) // Add the new MIGRATION_5_6
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
