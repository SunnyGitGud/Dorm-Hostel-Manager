package com.example.propertymanager.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures // Added import
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
import androidx.compose.ui.input.pointer.pointerInput // Added import
import androidx.compose.ui.text.font.FontWeight
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

    // State for long-press options dialog
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
                        financialSummary = propertyFinancialSummaries[property.id],
                        onClick = { onPropertyClick(property.id) },
                        onLongClick = { // Added onLongClick
                            selectedPropertyForOptions = property
                            showPropertyOptionsDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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

        // Show property options dialog
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
    onLongClick: () -> Unit // Added onLongClick parameter
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { // Added pointerInput for long press
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() } // Ensure regular click still works
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        // onClick = onClick // onClick is now handled by detectTapGestures
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = property.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = property.address, style = MaterialTheme.typography.bodySmall)
            
            financialSummary?.let {
                if (it.totalDue > 0.001) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total Due: ${currencyFormat.format(it.totalDue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB00020) 
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

// New composable for property options dialog
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
        dismissButton = null // No separate dismiss button, Cancel in text acts as one
    )
}
