package com.example.propertymanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.propertymanager.data.entities.TenantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tenant: TenantEntity): Long // Changed to return Long

    @Update
    suspend fun updateTenant(tenant: TenantEntity): Int // Returns number of rows updated

    // Gets the current tenant (no moveOutDate)
    @Query("SELECT * FROM tenants WHERE roomId = :roomId AND moveOutDate IS NULL LIMIT 1")
    fun getCurrentTenantForRoom(roomId: Int): Flow<TenantEntity?>

    // Gets all tenants for a room (past and present), ordered by most recent move-in
    @Query("SELECT * FROM tenants WHERE roomId = :roomId ORDER BY moveInDate DESC")
    fun getAllTenantsForRoom(roomId: Int): Flow<List<TenantEntity>>

    // Gets the tenant who was occupying the room on a specific date
    @Query("SELECT * FROM tenants WHERE roomId = :roomId AND moveInDate <= :dateInMillis AND (moveOutDate IS NULL OR moveOutDate >= :dateInMillis) ORDER BY moveInDate DESC LIMIT 1")
    suspend fun getTenantForRoomAtDate(roomId: Int, dateInMillis: Long): TenantEntity?

    // Gets the tenant who was occupying the room at any point within the given bill period (month)
    // Prioritizes the tenant who moved in latest if multiple were active in that month.
    @Query("""
        SELECT * FROM tenants 
        WHERE roomId = :roomId 
        AND moveInDate <= :endOfMonthTimestamp 
        AND (moveOutDate IS NULL OR moveOutDate > :startOfMonthTimestamp) 
        ORDER BY moveInDate DESC 
        LIMIT 1
    """)
    suspend fun getTenantForBillPeriod(roomId: Int, startOfMonthTimestamp: Long, endOfMonthTimestamp: Long): TenantEntity?

    @Query("SELECT * FROM tenants WHERE id = :tenantId LIMIT 1") // Added this method
    suspend fun getTenantById(tenantId: Int): TenantEntity?

    // Added for import functionality
    @Query("SELECT * FROM tenants WHERE name = :name AND roomId = :roomId AND moveOutDate IS NULL LIMIT 1")
    suspend fun getTenantByNameAndRoomId(name: String, roomId: Int): TenantEntity?

    @Query("SELECT * FROM tenants WHERE name = :name ORDER BY moveInDate DESC LIMIT 1")
    suspend fun getByName(name: String): TenantEntity?

    // Optional: If you need to delete a specific tenant instance
    // @Delete
    // suspend fun deleteTenant(tenant: TenantEntity): Int
}
