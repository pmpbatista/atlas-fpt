package com.atlasfpt.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.atlasfpt.ui.navigation.Screen

private data class CurrencyOption(val symbol: String, val code: String, val label: String)

private val currencyOptions = listOf(
    CurrencyOption("€", "EUR", "Euro (€)"),
    CurrencyOption("$", "USD", "US Dollar ($)"),
    CurrencyOption("£", "GBP", "British Pound (£)"),
    CurrencyOption("R$", "BRL", "Brazilian Real (R$)"),
    CurrencyOption("Fr", "CHF", "Swiss Franc (Fr)")
)

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val fxRates by viewModel.fxRates.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDisplayCurrencyPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        HorizontalDivider()

        SettingsRow(
            icon = Icons.Default.Person,
            title = "Persons",
            subtitle = "Manage persons",
            onClick = { navController.navigate(Screen.Persons.route) }
        )

        HorizontalDivider()

        SettingsRow(
            icon = Icons.Default.AttachMoney,
            title = "Currency",
            subtitle = "${settings.currencyCode} (${settings.currencySymbol})",
            onClick = { showCurrencyPicker = !showCurrencyPicker }
        )

        if (showCurrencyPicker) {
            Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp)) {
                currencyOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateCurrency(option.symbol, option.code)
                                showCurrencyPicker = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.currencyCode == option.code,
                            onClick = {
                                viewModel.updateCurrency(option.symbol, option.code)
                                showCurrencyPicker = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        HorizontalDivider()

        val displayCurrencySubtitle = buildString {
            append(settings.displayCurrencyCode)
            val fetched = fxRates.values.minByOrNull { it.fetchedAt }?.fetchedAt
            if (fetched != null) {
                append(" · FX as of ")
                append(formatShortDate(fetched))
            } else if (settings.displayCurrencyCode != settings.currencyCode) {
                append(" · FX rates not yet downloaded")
            }
        }
        SettingsRow(
            icon = Icons.Default.CurrencyExchange,
            title = "Display currency",
            subtitle = displayCurrencySubtitle,
            onClick = { showDisplayCurrencyPicker = !showDisplayCurrencyPicker }
        )

        if (showDisplayCurrencyPicker) {
            val availableCodes = (listOf("EUR") + fxRates.keys).distinct().sorted()
            Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp)) {
                availableCodes.forEach { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setDisplayCurrencyCode(code)
                                showDisplayCurrencyPicker = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.displayCurrencyCode == code,
                            onClick = {
                                viewModel.setDisplayCurrencyCode(code)
                                showDisplayCurrencyPicker = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(code, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (availableCodes.size == 1) {
                    Text(
                        "Refresh prices on the Assets screen to download FX rates from the ECB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        HorizontalDivider()

        SettingsToggleRow(
            icon = Icons.Default.Sync,
            title = "Background price refresh",
            subtitle = "Refresh held-asset prices daily on Wi-Fi",
            checked = settings.backgroundRefreshEnabled,
            onCheckedChange = viewModel::setBackgroundRefreshEnabled,
        )

        HorizontalDivider()

        SettingsRow(
            icon = Icons.Default.CloudUpload,
            title = "Import from CSV",
            subtitle = "Import transactions from a CSV file",
            onClick = { navController.navigate(Screen.Import.route) }
        )

        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Atlas v1.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatShortDate(millis: Long): String {
    val instant = java.time.Instant.ofEpochMilli(millis)
    val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return date.toString()
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
