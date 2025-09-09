package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PaymentEntity
import com.example.propertymanager.data.repository.PaymentRepository
import kotlinx.coroutines.launch

class PaymentViewModel(private val repository: PaymentRepository) : ViewModel() {
    fun getPayments(roomId: Int) = repository.getPaymentsForRoom(roomId)

    fun addPayment(payment: PaymentEntity) = viewModelScope.launch {
        repository.insert(payment)
    }

    fun deletePayment(payment: PaymentEntity) = viewModelScope.launch {
        repository.delete(payment)
    }
}
