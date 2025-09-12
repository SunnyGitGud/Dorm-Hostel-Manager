package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.MonthlyBillDao
import com.example.propertymanager.data.entities.MonthlyBillEntity
import kotlinx.coroutines.flow.Flow

class MonthlyBillRepository(private val monthlyBillDao: MonthlyBillDao) {

    fun getBillsForRoom(roomId: Int): Flow<List<MonthlyBillEntity>> {
        return monthlyBillDao.getBillsForRoom(roomId)
    }

    // Added for one-time fetch of all bills for a room
    suspend fun getBillsForRoomSuspend(roomId: Int): List<MonthlyBillEntity> {
        return monthlyBillDao.getBillsForRoomSuspend(roomId)
    }

    fun getBillById(billId: Int): Flow<MonthlyBillEntity?> {
        return monthlyBillDao.getBillById(billId)
    }

    // Added suspend function for one-time fetch
    suspend fun getBillByIdSuspend(billId: Int): MonthlyBillEntity? {
        return monthlyBillDao.getBillByIdSuspend(billId)
    }

    fun getBillForRoomMonthYear(roomId: Int, year: Int, month: Int): Flow<MonthlyBillEntity?> {
        return monthlyBillDao.getBillForRoomMonthYear(roomId, year, month)
    }

    suspend fun getBillForRoomMonthYearSuspend(roomId: Int, year: Int, month: Int): MonthlyBillEntity? {
        return monthlyBillDao.getBillForRoomMonthYearSuspend(roomId, year, month)
    }

    suspend fun upsertBill(bill: MonthlyBillEntity): Long { // Renamed from insertOrUpdateBill
        // Ensure total is calculated before saving
        bill.calculateTotalDue()
        return monthlyBillDao.insertOrUpdate(bill)
    }

    // Added delete function
    suspend fun deleteBillById(billId: Int): Int {
        return monthlyBillDao.deleteBillById(billId)
    }

    // ADDED FOR REACTIVE UPDATES IN PropertyViewModel
    fun getAllBillsStream(): Flow<List<MonthlyBillEntity>> {
        return monthlyBillDao.getAllBillsStream()
    }
}
