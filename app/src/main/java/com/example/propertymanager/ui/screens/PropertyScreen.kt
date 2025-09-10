package com.example.propertymanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input // For Import
import androidx.compose.material.icons.automirrored.filled.ReceiptLong // Added for AutoMirrored version
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline // For Advance
import androidx.compose.material.icons.filled.ErrorOutline // For Due
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info // For Vacant/General Info
import androidx.compose.material.icons.filled.Share // For Export
import androidx.compose.material.icons.filled.Verified // For Paid
import androidx.compose.material.icons.outlined.MeetingRoom // For Room
import androidx.compose.material.icons.outlined.Person // For Tenant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.PropertyEntity
import com.example.propertymanager.ui.viewmodel.PropertyFinancialSummary
import com.example.propertymanager.ui.viewmodel.PropertyViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// Enum to represent the semantic status of a room bill/occupancy
enum class BillStatusType {
    DUE,
    ADVANCE,
    PAID,
    VACANT,
    NO_BILLS_YET,
    GENERAL_INFO // Fallback or for other info states
}

// Data class for room summary (used by ViewModel and this Screen)
data class RoomSummaryForPropertyCard(
    val roomName: String,
    val tenantName: String?,
    val statusText: String,
    val statusType: BillStatusType // Changed from statusColor
)

enum class ExportFormat { // Defined here for screen
    CSV,
    TEXT
}

enum class ExportScope { // Added enum for export scope
    SpecificMonth,
    FullYear
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyScreen(
    viewModel: PropertyViewModel,
    onPropertyClick: (Int) -> Unit // This will be for "View Details" button
) {
    val properties by viewModel.properties.collectAsState()
    val propertyFinancialSummaries by viewModel.propertyFinancialSummaries.collectAsState()
    val propertyRoomSummariesValue by viewModel.propertyRoomSummaries.collectAsState()
    val exportCsvData by viewModel.exportCsvData.collectAsState()
    val exportTextData by viewModel.exportTextData.collectAsState() // Observe new text data
    val importStatus by viewModel.importStatus.collectAsState()

    var showAddPropertyDialog by remember { mutableStateOf(false) }
    var selectedPropertyForOptions by remember { mutableStateOf<PropertyEntity?>(null) }
    var showPropertyOptionsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    var exportYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var exportMonth by remember { mutableStateOf<Int?>(Calendar.getInstance().get(Calendar.MONTH) + 1) } // Changed to Int?

    val context = LocalContext.current

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(exportCsvData ?: "")
                    }
                }
                Toast.makeText(context, "CSV exported successfully", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error exporting CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        viewModel.clearExportData()
    }

    val createTextFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(exportTextData ?: "")
                    }
                }
                Toast.makeText(context, "Text file exported successfully", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error exporting text file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        viewModel.clearExportTextData() // Clear text data
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val content = reader.readText()
                        viewModel.importCsvData(content)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(exportCsvData) {
        exportCsvData?.let {
            if (it.isNotBlank()) {
                val monthPart = exportMonth?.toString()?.padStart(2, '0') ?: "full_year"
                createCsvLauncher.launch("property_export_${exportYear}_$monthPart.csv")
            }
        }
    }

    LaunchedEffect(exportTextData) {
        exportTextData?.let {
            if (it.isNotBlank()) {
                val monthPart = exportMonth?.toString()?.padStart(2, '0') ?: "full_year"
                createTextFileLauncher.launch("property_export_${exportYear}_$monthPart.txt")
            }
        }
    }

    LaunchedEffect(importStatus) {
        importStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearImportStatus() // Reset status after showing toast
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Properties") },
                actions = {
                    IconButton(onClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/plain")) }) { // Changed to text/csv, text/plain
                        Icon(Icons.AutoMirrored.Filled.Input, contentDescription = "Import CSV")
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export Data")
                    }
                }
            )
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (properties.isEmpty()) {
                item {
                    Text(
                        text = "No properties found. Click the '+' button to add one.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(properties, key = { it.id }) { property ->
                    val currentRoomSummaries = propertyRoomSummariesValue[property.id] ?: emptyList()
                    PropertyItem(
                        property = property,
                        financialSummary = propertyFinancialSummaries[property.id],
                        roomSummaries = currentRoomSummaries,
                        onViewDetailsClick = { onPropertyClick(property.id) },
                        onLongClick = {
                            selectedPropertyForOptions = property
                            showPropertyOptionsDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
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

        if (showExportDialog) {
            ExportDataDialog(
                onDismiss = { showExportDialog = false },
                onConfirmExport = { yearValue, monthValue, formatValue -> // Updated lambda params
                    exportYear = yearValue
                    exportMonth = monthValue // Assign Int?
                    viewModel.triggerExportData(yearValue, monthValue, formatValue) // Pass format
                    showExportDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataDialog(
    onDismiss: () -> Unit,
    onConfirmExport: (year: Int, month: Int?, format: ExportFormat) -> Unit // month is now Int?
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

    var yearText by remember { mutableStateOf(currentYear.toString()) }
    var monthText by remember { mutableStateOf(currentMonth.toString()) }
    var yearError by remember { mutableStateOf<String?>(null) } 
    var monthError by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var selectedScope by remember { mutableStateOf(ExportScope.SpecificMonth) } // New state for scope

    val formats = ExportFormat.values()
    val scopes = ExportScope.values()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Room Data") },
        text = {
            Column {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = {
                        yearText = it
                        yearError = null
                    },
                    label = { Text("Year (YYYY)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = yearError != null,
                    singleLine = true
                )
                if (yearError != null) {
                    Text(yearError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Export Scope:", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    scopes.forEach { scope ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (scope == selectedScope),
                                    onClick = { selectedScope = scope },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (scope == selectedScope),
                                onClick = null
                            )
                            Text(
                                text = when (scope) {
                                    ExportScope.SpecificMonth -> "Specific Month"
                                    ExportScope.FullYear -> "Full Year"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (selectedScope == ExportScope.SpecificMonth) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = {
                            monthText = it
                            monthError = null
                        },
                        label = { Text("Month (1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = monthError != null,
                        singleLine = true
                    )
                    if (monthError != null) {
                        Text(monthError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Export Format:", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    formats.forEach { format ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (format == selectedFormat),
                                    onClick = { selectedFormat = format },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (format == selectedFormat),
                                onClick = null // null recommended for accessibility with selectable parent
                            )
                            Text(
                                text = format.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val year = yearText.toIntOrNull()
                    var finalMonth: Int? = null
                    var isValid = true

                    if (year == null || year < 1900 || year > 2200) {
                        yearError = "Invalid year"
                        isValid = false
                    }

                    if (selectedScope == ExportScope.SpecificMonth) {
                        finalMonth = monthText.toIntOrNull()
                        if (finalMonth == null || finalMonth !in 1..12) {
                            monthError = "Invalid month"
                            isValid = false
                        }
                    } else {
                        monthError = null // Clear month error if full year
                    }

                    if (isValid && year != null) {
                        onConfirmExport(year, finalMonth, selectedFormat)
                    }
                }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyItem(
    property: PropertyEntity,
    financialSummary: PropertyFinancialSummary?,
    roomSummaries: List<RoomSummaryForPropertyCard>,
    onViewDetailsClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { isExpanded = !isExpanded }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = property.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = property.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            financialSummary?.let {
                if (it.totalDue > 0 || it.totalAdvance > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (it.totalDue > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total Due: ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = currencyFormat.format(it.totalDue),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (it.totalAdvance > 0) Spacer(modifier = Modifier.height(4.dp))
                }
                if (it.totalAdvance > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total Advance: ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = currencyFormat.format(it.totalAdvance),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (roomSummaries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                if (!isExpanded) {
                    Text(
                        text = "${roomSummaries.size} Room${if (roomSummaries.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) // Updated Divider
                    Text(
                        text = "Rooms:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    roomSummaries.forEach { roomSummary ->
                        val statusColor = when (roomSummary.statusType) {
                            BillStatusType.DUE -> MaterialTheme.colorScheme.error
                            BillStatusType.ADVANCE -> MaterialTheme.colorScheme.secondary
                            BillStatusType.PAID -> MaterialTheme.colorScheme.primary // Or a specific success color
                            BillStatusType.VACANT, BillStatusType.NO_BILLS_YET, BillStatusType.GENERAL_INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val statusIcon: ImageVector = when (roomSummary.statusType) {
                            BillStatusType.DUE -> Icons.Filled.ErrorOutline
                            BillStatusType.ADVANCE -> Icons.Filled.CheckCircleOutline
                            BillStatusType.PAID -> Icons.Filled.Verified
                            BillStatusType.VACANT -> Icons.Filled.Info
                            BillStatusType.NO_BILLS_YET -> Icons.AutoMirrored.Filled.ReceiptLong // Corrected Icon usage
                            BillStatusType.GENERAL_INFO -> Icons.Filled.Info
                        }

                        Column(
                            modifier = Modifier
                                .padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                                .fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.MeetingRoom,
                                    contentDescription = "Room",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = roomSummary.roomName,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Tenant",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (roomSummary.tenantName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = roomSummary.tenantName ?: "N/A", // Removed "Tenant: " prefix
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = "Status: ${roomSummary.statusText}", // More descriptive
                                    modifier = Modifier.size(20.dp),
                                    tint = statusColor // Use theme-derived color
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = roomSummary.statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusColor, // Use theme-derived color
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            val spacerHeight = if (isExpanded && roomSummaries.isNotEmpty()) 4.dp else 12.dp
            Spacer(modifier = Modifier.height(spacerHeight))

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) // Updated Divider
            OutlinedButton(
                onClick = onViewDetailsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Details")
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
                    Text("Delete Property", color = MaterialTheme.colorScheme.error)
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
