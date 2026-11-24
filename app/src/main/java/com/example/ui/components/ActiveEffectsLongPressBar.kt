package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHighContrast

data class ActiveEffectChipData(
    val id: String,
    val label: String,
    val detail: String = ""
)

/**
 * Displays currently active/applied effects in the stack.
 * Long-pressing any effect chip opens a confirmation dialog to remove it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActiveEffectsLongPressBar(
    effects: List<ActiveEffectChipData>,
    onRemoveEffect: (ActiveEffectChipData) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Active Effects (Long-press to remove)"
) {
    if (effects.isEmpty()) return
    val isHighContrast = LocalHighContrast.current
    var pendingRemoval by remember { mutableStateOf<ActiveEffectChipData?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_active_effects_stack"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                effects.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .testTag("chip_active_effect_${item.id}")
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { pendingRemoval = item },
                                onLongClickLabel = "Remove ${item.label}"
                            )
                            .semantics {
                                contentDescription = if (item.detail.isNotBlank()) {
                                    "${item.label}, ${item.detail}. Long-press to remove."
                                } else {
                                    "${item.label}. Long-press to remove."
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction("Remove ${item.label}") {
                                        pendingRemoval = item
                                        true
                                    }
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (item.detail.isNotBlank()) "${item.label} (${item.detail})" else item.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    val toRemove = pendingRemoval
    if (toRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            modifier = Modifier.testTag("dialog_confirm_remove_effect"),
            title = { Text("Remove Effect?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove \"${toRemove.label}\" from your active effect stack?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveEffect(toRemove)
                        pendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_remove_effect")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingRemoval = null },
                    modifier = Modifier.testTag("btn_cancel_remove_effect")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
