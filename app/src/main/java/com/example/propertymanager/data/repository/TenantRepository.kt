package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.TenantDao
import com.example.propertymanager.data.entities.TenantEntity
import kotlinx.coroutines.flow.Flow

class TenantRepository(private val tenantDao: TenantDao) {

    // Calls DAO to get the current tenant for a room (moveOutDate IS NULL)
    fun getCurrentTenantForRoom(roomId: Int): Flow<TenantEntity?> {
        return tenantDao.getCurrentTenantForRoom(roomId)
    }

    // Calls DAO to get all tenants (past and present) for a room
    fun getAllTenantsForRoom(roomId: Int): Flow<List<TenantEntity>> {
        return tenantDao.getAllTenantsForRoom(roomId)
    }

    // Calls DAO to get the tenant who was occupying the room on a specific date
    suspend fun getTenantForRoomAtDate(roomId: Int, dateInMillis: Long): TenantEntity? {
        return tenantDao.getTenantForRoomAtDate(roomId, dateInMillis)
    }

    // Calls DAO to insert or update a tenant, returns the ID
    suspend fun insertOrUpdateTenant(tenant: TenantEntity): Long {
        return tenantDao.insertOrUpdate(tenant)
    }

    // Sets the move-out date for a specific tenant
    suspend fun setTenantMoveOutDate(tenantId: Int, moveOutDateTimestamp: Long): Boolean {
        val tenant = tenantDao.getTenantById(tenantId)
        if (tenant != null) {
            tenant.moveOutDate = moveOutDateTimestamp
            val updatedRows = tenantDao.updateTenant(tenant)
            return updatedRows > 0
        }
        return false // Tenant not found or update failed
    }

    // The old `getTenantForRoom` is effectively replaced by `getCurrentTenantForRoom`.
    // The old `removeTenantFromRoom` which called a broad delete is intentionally removed.
    // Tenant "removal" is now handled by setting a moveOutDate.
    // If actual deletion is needed, a deleteTenant(tenant: TenantEntity) method could be added.
}
