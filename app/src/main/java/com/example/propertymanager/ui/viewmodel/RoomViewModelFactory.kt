package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.propertymanager.data.repository.MonthlyBillRepository // Added import
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.TenantRepository

class RoomViewModelFactory(
    private val roomRepository: RoomRepository,
    private val tenantRepository: TenantRepository,
    private val monthlyBillRepository: MonthlyBillRepository, // Added MonthlyBillRepository
    private val propertyId: Int
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoomViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RoomViewModel(
                roomRepository,
                tenantRepository,
                monthlyBillRepository, // Pass MonthlyBillRepository
                propertyId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for RoomViewModelFactory")
    }
}
