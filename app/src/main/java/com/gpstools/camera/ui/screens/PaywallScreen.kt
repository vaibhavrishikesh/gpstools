package com.gpstools.camera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gpstools.camera.R
import com.gpstools.camera.billing.Subscription

/**
 * The Pro subscription paywall (US-018): a dialog describing the subscriber
 * benefits (unlimited PDF reports, batch export, no watermark) with monthly /
 * yearly India pricing and subscribe + restore actions.
 *
 * Rendered as a centered [Dialog] Card (not a fullscreen edge-to-edge surface) so
 * the window-inset clipping that bites fullscreen dialogs in this app doesn't apply.
 *
 * Prices are the live Play prices when loaded ([monthlyPrice]/[yearlyPrice]); the
 * caller falls back to static India pricing strings when billing isn't connected.
 */
@Composable
fun PaywallDialog(
    monthlyPrice: String,
    yearlyPrice: String,
    onSubscribe: (productId: String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    showDebug: Boolean = false,
    onDebugSimulate: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.pro_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.pro_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                BenefitRow(stringResource(R.string.pro_benefit_unlimited))
                BenefitRow(stringResource(R.string.pro_benefit_batch))
                BenefitRow(stringResource(R.string.pro_benefit_watermark))

                Button(
                    onClick = { onSubscribe(Subscription.YEARLY_PRODUCT_ID) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pro_subscribe_yearly, yearlyPrice))
                }
                Text(
                    text = stringResource(R.string.pro_yearly_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedButton(
                    onClick = { onSubscribe(Subscription.MONTHLY_PRODUCT_ID) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pro_subscribe_monthly, monthlyPrice))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onRestore) {
                        Text(stringResource(R.string.premium_restore))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.pro_not_now))
                    }
                }

                if (showDebug) {
                    OutlinedButton(
                        onClick = onDebugSimulate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pro_debug_simulate))
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}
