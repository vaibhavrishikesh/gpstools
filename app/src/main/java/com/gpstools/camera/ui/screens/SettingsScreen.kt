package com.gpstools.camera.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gpstools.camera.R
import com.gpstools.camera.locale.AppLanguage
import com.gpstools.camera.locale.LocaleStore
import com.gpstools.camera.locale.findActivity
import com.gpstools.camera.settings.AppSettingsStore
import com.gpstools.camera.settings.CoordinateFormat
import com.gpstools.camera.settings.TimeFormat
import com.gpstools.camera.ui.navigation.Destination

/** Placeholder privacy-policy URL (US-014); replaced when a real policy is hosted. */
private const val PRIVACY_POLICY_URL = "https://gpstools.example.com/privacy"

/**
 * Settings screen.
 *  - Language toggle (US-013): persists + recreates the activity so the locale applies live.
 *  - Coordinate + time format (US-014): persisted and snapshot into the stamp at capture.
 *  - Location accuracy / NavIC info + About/version + privacy link (US-014).
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var language by remember { mutableStateOf(LocaleStore.load(context)) }
    var coordinateFormat by remember { mutableStateOf(AppSettingsStore.loadCoordinateFormat(context)) }
    var timeFormat by remember { mutableStateOf(AppSettingsStore.loadTimeFormat(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Destination.Settings.labelRes),
            style = MaterialTheme.typography.headlineSmall,
        )

        // --- Language (US-013) ---
        SectionHeader(
            title = stringResource(R.string.settings_language),
            summary = stringResource(R.string.settings_language_summary),
        )
        OptionCard {
            AppLanguage.entries.forEach { option ->
                OptionRow(
                    label = stringResource(option.labelRes),
                    selected = option == language,
                    onSelect = {
                        if (option != language) {
                            language = option
                            LocaleStore.save(context, option)
                            // Recreate so attachBaseContext re-wraps with the new
                            // locale and the whole UI re-resolves strings.
                            context.findActivity()?.recreate()
                        }
                    },
                )
            }
        }

        // --- Coordinate format (US-014) ---
        SectionHeader(
            title = stringResource(R.string.settings_coord_format),
            summary = stringResource(R.string.settings_coord_format_summary),
        )
        OptionCard {
            CoordinateFormat.entries.forEach { option ->
                OptionRow(
                    label = stringResource(option.labelRes),
                    selected = option == coordinateFormat,
                    onSelect = {
                        coordinateFormat = option
                        AppSettingsStore.saveCoordinateFormat(context, option)
                    },
                )
            }
        }

        // --- Time format (US-014) ---
        SectionHeader(
            title = stringResource(R.string.settings_time_format),
            summary = stringResource(R.string.settings_time_format_summary),
        )
        OptionCard {
            TimeFormat.entries.forEach { option ->
                OptionRow(
                    label = stringResource(option.labelRes),
                    selected = option == timeFormat,
                    onSelect = {
                        timeFormat = option
                        AppSettingsStore.saveTimeFormat(context, option)
                    },
                )
            }
        }

        // --- Location accuracy / NavIC (US-014) ---
        SectionHeader(title = stringResource(R.string.settings_navic_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_navic_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        // --- About (US-014) ---
        SectionHeader(title = stringResource(R.string.settings_about_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, appVersionName(context)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()),
                            )
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(stringResource(R.string.settings_privacy_policy))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, summary: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (summary != null) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.selectableGroup()) { content() }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** App version name from the package manager (no BuildConfig dependency). */
private fun appVersionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "—"
