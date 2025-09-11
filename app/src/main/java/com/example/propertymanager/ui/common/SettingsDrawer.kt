package com.example.propertymanager.ui.common

// Keep existing relevant imports
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode // ADDED
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode // ADDED
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette // ADDED
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // ADDED
import androidx.compose.runtime.remember // ADDED
import androidx.compose.runtime.setValue // ADDED
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
// AppTheme is used indirectly via themeViewModel, explicit import might not be needed here
// import com.example.propertymanager.ui.theme.AppTheme
import com.example.propertymanager.ui.viewmodel.ThemeViewModel

// UPDATED Enum: TOGGLE_DARK_MODE is removed
enum class SettingsAction {
    EXPORT_DATA,
    IMPORT_DATA,
    SYNC_GOOGLE_DRIVE,
    CHANGE_LANGUAGE
}

@Composable
fun SettingsDrawerContent(
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel,
    onItemSelected: (SettingsAction) -> Unit
) {
    val currentTheme by themeViewModel.selectedTheme.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) } // State for dialog visibility

    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SettingsDrawerItem(
                label = "Export Data",
                icon = Icons.Filled.CloudUpload,
                onClick = { onItemSelected(SettingsAction.EXPORT_DATA) }
            )
            SettingsDrawerItem(
                label = "Import Data",
                icon = Icons.Filled.CloudDownload,
                onClick = { onItemSelected(SettingsAction.IMPORT_DATA) }
            )
            SettingsDrawerItem(
                label = "Sync with Google Drive",
                icon = Icons.Filled.DriveFolderUpload,
                onClick = { onItemSelected(SettingsAction.SYNC_GOOGLE_DRIVE) },
                comingSoon = true
            )
            SettingsDrawerItem(
                label = "Change Language",
                icon = Icons.Filled.Language,
                onClick = { onItemSelected(SettingsAction.CHANGE_LANGUAGE) },
                comingSoon = true // Assuming this is still WIP
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Theme Selection Item
            SettingsDrawerItem(
                label = "Theme: ${currentTheme.getBaseForDialog().displayName}",
                icon = Icons.Filled.Palette,
                onClick = { showThemeDialog = true }
            )

            // Modified "Toggle Dark Mode" Item
            val isThemeToggleable = currentTheme.isCustomToggleable()
            val darkModeLabel = when {
                isThemeToggleable && currentTheme.isDarkVariant -> "Switch to Light Mode"
                isThemeToggleable && !currentTheme.isDarkVariant -> "Switch to Dark Mode"
                else -> "Dark Mode (System Controlled)" // For SYSTEM_DEFAULT
            }
            val darkModeIcon = when {
                isThemeToggleable && currentTheme.isDarkVariant -> Icons.Filled.LightMode
                isThemeToggleable && !currentTheme.isDarkVariant -> Icons.Filled.DarkMode
                else -> Icons.Filled.NightsStay // Default icon for system
            }

            SettingsDrawerItem(
                label = darkModeLabel,
                icon = darkModeIcon,
                onClick = {
                    if (isThemeToggleable) {
                        themeViewModel.toggleDarkMode()
                    }
                    // For SYSTEM_DEFAULT, clicking does nothing as it's system controlled.
                },
                comingSoon = false // This feature is now implemented
            )

            // Removed direct theme iteration and ThemeSelectionRow
        }
    }

    // Dialog is shown outside the ModalDrawerSheet's content lambda,
    // but within the same Composable scope as SettingsDrawerContent
    if (showThemeDialog) {
        ThemeSelectionDialog( // Assuming this composable is defined in another file
            themeViewModel = themeViewModel,
            onDismissRequest = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SettingsDrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    comingSoon: Boolean = false
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (comingSoon) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(Coming Soon)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

// Local ThemeSelectionRow composable is now removed.
// It's expected that ThemeSelectionDialog.kt provides the dialog.
