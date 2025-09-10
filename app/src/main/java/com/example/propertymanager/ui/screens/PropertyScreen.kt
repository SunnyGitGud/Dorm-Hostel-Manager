package com.example.propertymanager.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
// Removed FontWeight import as it's no longer explicitly used in PropertyItem
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.ui.viewmodel.PropertyViewModel
import com.example.propertymanager.ui.viewmodel.PropertyFinancialSummary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyScreen(
    viewModel: PropertyViewModel,
    onPropertyClick: (Int) -> Unit
) {
    val properties by viewModel.properties.collectAsState()
    val propertyFinancialSummaries by viewModel.propertyFinancialSummaries.collectAsState()
    var showAddPropertyDialog by remember { mutableStateOf(false) }

    var selectedPropertyForOptions by remember { mutableStateOf<PropertyEntity?>(null) }
    var showPropertyOptionsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Properties") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddPropertyDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Property")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp) // Overall padding for the list
        ) {
            if (properties.isEmpty()) {
                item {
                    Text("No properties found. Click the '+' button to add one.")
                }
            } else {
                items(properties, key = { it.id }) { property ->
                    PropertyItem(
                        property = property,
                        financialSummary = propertyFinancialSummaries[property.id],
                        onClick = { onPropertyClick(property.id) },
                        onLongClick = { 
                            selectedPropertyForOptions = property
                            showPropertyOptionsDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp)) // Increased spacing between cards
                }
            }
        }

        if (showAddPropertyDialog) {
            AddPropertyDialog(
                onDismiss = { showAddPropertyDialog = false },
                onAddProperty = { name, address ->
                    viewModel.addProperty(name, address)
                    showAddPropertyDialog = false
                }
            )
        }

        if (showPropertyOptionsDialog && selectedPropertyForOptions != null) {
            PropertyOptionsDialog(
                property = selectedPropertyForOptions!!,
                viewModel = viewModel,
                onDismiss = {
                    showPropertyOptionsDialog = false
                    selectedPropertyForOptions = null
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { 
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() } 
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Slightly increased elevation
        shape = MaterialTheme.shapes.medium // Using a predefined shape
    ) {
        Column(modifier = Modifier.padding(16.dp)) { // Standard padding inside card
            Text(
                text = property.name, 
                style = MaterialTheme.typography.titleLarge // Increased font size for name
            )
            Spacer(modifier = Modifier.height(6.dp)) // Increased spacer
            Text(
                text = property.address, 
                style = MaterialTheme.typography.bodyMedium // Increased font size for address
            )
            
            financialSummary?.let {
                // Check against 0 as totalDue is now ceil-ed Double (e.g. 106.0)
                if (it.totalDue > 0) { 
                    Spacer(modifier = Modifier.height(8.dp)) // Increased spacer
                    Text(
                        text = "Total Due: ${currencyFormat.format(it.totalDue)}",
                        style = MaterialTheme.typography.titleSmall, // Larger and bolder style for due amount
                        color = MaterialTheme.colorScheme.error // Retain error color for due
                    )
                }
                 // Optionally display totalAdvance as well if needed, with similar styling
                if (it.totalAdvance > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total Advance: ${currencyFormat.format(it.totalAdvance)}",
                        style = MaterialTheme.typography.titleSmall, // Consistent styling for financial summaries
                        color = Color(0xFF2E7D32) // PositiveGreenColor or similar for advance
                    )
                }
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
                val currentNameError = nameError
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
                val currentAddressError = addressError
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

@Composable
fun PropertyOptionsDialog(
    property: PropertyEntity,
    viewModel: PropertyViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Options for ${property.name}") },
        text = {
            Column {
                TextButton(onClick = {
                    viewModel.setPropertyHiddenStatus(property.id, true)
                    onDismiss()
                }) {
                    Text("Hide Property")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    viewModel.deleteProperty(property)
                    onDismiss()
                }) {
                    Text("Delete Property", color = Color.Red)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        dismissButton = null
    )
}
