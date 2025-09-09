package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.data.repository.PropertyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PropertyViewModel(private val repository: PropertyRepository) : ViewModel() {

    // Holds the current list of properties
    private val _properties = MutableStateFlow<List<PropertyEntity>>(emptyList())
    val properties: StateFlow<List<PropertyEntity>> = _properties.asStateFlow()

    init {
        // Load properties from database
        viewModelScope.launch {
            repository.getAllProperties().collect {
                _properties.value = it
            }
        }
    }

    // Insert a new property
    fun addProperty(name: String, address: String) {
        viewModelScope.launch {
            val property = PropertyEntity(name = name, address = address)
            repository.insert(property)
        }
    }

    // Delete a property
    fun deleteProperty(property: PropertyEntity) {
        viewModelScope.launch {
            repository.delete(property)
        }
    }
}
