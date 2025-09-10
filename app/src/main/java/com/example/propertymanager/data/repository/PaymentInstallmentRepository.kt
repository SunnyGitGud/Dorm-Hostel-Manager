package com.example.propertymanager.data.repository

import com.example.propertymanager.data.dao.PaymentInstallmentDao
import com.example.propertymanager.data.entities.PaymentInstallmentEntity
import kotlinx.coroutines.flow.Flow

class PaymentInstallmentRepository(private val paymentInstallmentDao: PaymentInstallmentDao) {

    suspend fun addInstallment(installment: PaymentInstallmentEntity): Long {
        return paymentInstallmentDao.insertInstallment(installment)
    }

    suspend fun addAllInstallments(installments: List<PaymentInstallmentEntity>) {
        paymentInstallmentDao.insertAllInstallments(installments)
    }

    fun getInstallmentsForBill(billId: Int): Flow<List<PaymentInstallmentEntity>> {
        return paymentInstallmentDao.getInstallmentsForBill(billId)
    }

    suspend fun getInstallmentsForBillSuspend(billId: Int): List<PaymentInstallmentEntity> {
        return paymentInstallmentDao.getInstallmentsForBillSuspend(billId)
    }

    suspend fun deleteInstallmentsForBill(billId: Int) {
        paymentInstallmentDao.deleteInstallmentsForBill(billId)
    }
}
