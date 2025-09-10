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
import com.example.propertymanager.data.dao.PaymentInstallmentDao // Added import
import com.example.propertymanager.data.entities.MonthlyBillEntity
import com.example.propertymanager.data.entities.PaymentEntity
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.entities.RoomEntity
import com.example.propertymanager.data.entities.TenantEntity
import com.example.propertymanager.data.entities.PaymentInstallmentEntity // Added import

@Database(
    entities = [
        PropertyEntity::class,
        RoomEntity::class,
        TenantEntity::class,
        MonthlyBillEntity::class,
        PaymentEntity::class,
        PaymentInstallmentEntity::class // Added new entity
    ],
    version = 7, // Incremented version from 6 to 7
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PropertyManagerDatabase : RoomDatabase() {

    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun tenantDao(): TenantDao
    abstract fun monthlyBillDao(): MonthlyBillDao
    abstract fun paymentDao(): PaymentDao
    abstract fun paymentInstallmentDao(): PaymentInstallmentDao // Added DAO for new entity

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE monthly_bills ADD COLUMN isFullRentAppliedOverride INTEGER")
            }
        }

        // Migration from version 6 to 7
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new payment_installments table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payment_installments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        billId INTEGER NOT NULL, 
                        amount REAL NOT NULL, 
                        date INTEGER NOT NULL, 
                        FOREIGN KEY(billId) REFERENCES monthly_bills(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                // Create an index on billId for faster lookups
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_installments_billId ON payment_installments (billId)")
            }
        }

        fun getDatabase(context: Context): PropertyManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PropertyManagerDatabase::class.java,
                    "property_manager_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7) // Add new MIGRATION_6_7
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
