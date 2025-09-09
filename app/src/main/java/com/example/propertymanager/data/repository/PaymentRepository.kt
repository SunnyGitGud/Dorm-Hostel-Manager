package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.PaymentDao
import com.example.propertymanager.data.entities.PaymentEntity

class PaymentRepository(private val dao: PaymentDao) {
    fun getPaymentsForRoom(roomId: Int) = dao.getPaymentsForRoom(roomId)

    suspend fun insert(payment: PaymentEntity) = dao.insert(payment)
    suspend fun delete(payment: PaymentEntity) = dao.delete(payment)
}
