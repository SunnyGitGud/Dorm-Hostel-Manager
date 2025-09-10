package com.example.propertymanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.ui.viewmodel.PropertyViewModel
import com.example.propertymanager.ui.viewmodel.PropertyFinancialSummary // Added import
import java.text.NumberFormat // Added import
import java.util.Locale // Added import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyScreen(
    viewModel: PropertyViewModel,
    onPropertyClick: (Int) -> Unit
) {
    val properties by viewModel.properties.collectAsState()
    val propertyFinancialSummaries by viewModel.propertyFinancialSummaries.collectAsState() // Added
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Properties") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Property")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (properties.isEmpty()) {
                item {
                    Text("No properties found. Click the '+' button to add one.")
                }
            } else {
                items(properties, key = { it.id }) { property ->
                    PropertyItem(
                        property = property,
                        financialSummary = propertyFinancialSummaries[property.id], // Pass summary
                        onClick = { onPropertyClick(property.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (showDialog) {
            AddPropertyDialog(
                onDismiss = { showDialog = false },
                onAddProperty = { name, address ->
                    viewModel.addProperty(name, address)
                    showDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyItem(
    property: PropertyEntity,
    financialSummary: PropertyFinancialSummary?,
    onClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = property.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = property.address, style = MaterialTheme.typography.bodySmall)
            
            financialSummary?.let {
                if (it.totalDue > 0.001) { // Epsilon for double comparison
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total Due: ${currencyFormat.format(it.totalDue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB00020) // Red color for due amounts
                    )
                }
                // Optionally, display totalAdvance if needed in the future
                // if (it.totalAdvance > 0.001) {
                //     Spacer(modifier = Modifier.height(4.dp))
                //     Text(
                //         text = "Total Advance: ${currencyFormat.format(it.totalAdvance)}",
                //         style = MaterialTheme.typography.bodyMedium,
                //         fontWeight = FontWeight.SemiBold,
                //         color = Color(0xFF008000) // Green color for advance amounts
                //     )
                // }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPropertyDialog(
    onDismiss: () -> Unit,
    onAddProperty: (name: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Property") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Property Name") },
                    isError = nameError != null,
                    singleLine = true
                )
                val currentNameError = nameError // Fix for smart cast
                if (currentNameError != null) {
                    Text(currentNameError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; addressError = null },
                    label = { Text("Property Address") },
                    isError = addressError != null,
                    singleLine = true
                )
                val currentAddressError = addressError // Fix for smart cast
                 if (currentAddressError != null) {
                    Text(currentAddressError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var valid = true
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        valid = false
                    }
                    if (address.isBlank()) {
                        addressError = "Address cannot be empty"
                        valid = false
                    }
                    if (valid) {
                        onAddProperty(name, address)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
