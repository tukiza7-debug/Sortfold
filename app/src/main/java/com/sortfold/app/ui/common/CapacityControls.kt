package com.sortfold.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sortfold.app.R
import com.sortfold.app.core.model.CapacityOrder
import com.sortfold.app.core.rules.CapacityPacker
import com.sortfold.app.ui.theme.LocalReducedMotion
import com.sortfold.app.ui.theme.Motion
import com.sortfold.app.ui.theme.Spacing
import com.sortfold.app.ui.theme.pressable
import com.sortfold.app.ui.theme.rememberHaptics
import com.sortfold.app.ui.theme.rememberPressInteraction

/**
 * "Split by capacity" controls shared by the wizard's Modes step and the Auto
 * Rules editor (1.2.0 Part A). All motion comes from Motion tokens and
 * respects LocalReducedMotion (Part C).
 */

/** Live "About N folders · largest Part 3.94 GB" estimate for the capacity panel. */
data class CapacityEstimate(val folderCount: Int, val largestPartBytes: Long, val oversized: Int)

object CapacityLimits {
    const val MIN_BYTES: Long = 50L * 1000 * 1000          // 50 MB (decimal)
    const val MAX_BYTES: Long = 1000L * 1000 * 1000 * 1000 // 1 TB (decimal)
    const val MIN_BYTES_BINARY: Long = 50L * 1024 * 1024
    const val MAX_BYTES_BINARY: Long = 1024L * 1024 * 1024 * 1024

    fun minBytes(decimal: Boolean) = if (decimal) MIN_BYTES else MIN_BYTES_BINARY
    fun maxBytes(decimal: Boolean) = if (decimal) MAX_BYTES else MAX_BYTES_BINARY
}

/** Byte value of one unit (MB / GB) under the active decimal/binary definition. */
fun unitBytes(unit: String, decimal: Boolean): Long = when (unit) {
    "MB" -> if (decimal) 1_000_000L else 1_048_576L
    else -> if (decimal) 1_000_000_000L else 1_073_741_824L
}

@Composable
fun capacityUnitHelperText(decimal: Boolean): String = stringResource(
    if (decimal) R.string.capacity_unit_helper_decimal else R.string.capacity_unit_helper_binary,
)

/** Expanding panel with all capacity controls; wraps the wizard/auto-rules use. */
@Composable
fun CapacityPanel(
    visible: Boolean,
    presetIndex: Int,
    customText: String,
    order: CapacityOrder,
    prefix: String,
    unit: String,
    unitDecimal: Boolean,
    error: String?,
    estimate: CapacityEstimate?,
    onPreset: (Long, Int) -> Unit,
    onCustomText: (String) -> Unit,
    onOrder: (CapacityOrder) -> Unit,
    onPrefix: (String) -> Unit,
    onUnit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            fadeIn(Motion.reduced())
        } else {
            expandVertically(Motion.enter()) + fadeIn(Motion.enter())
        },
        exit = if (reduced) {
            fadeOut(Motion.reduced())
        } else {
            shrinkVertically(Motion.exit()) + fadeOut(Motion.exit())
        },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CapacityPresetChips(
                selected = presetIndex,
                unitDecimal = unitDecimal,
                onSelect = onPreset,
            )
            if (presetIndex == 4) {
                CapacityCustomField(
                    text = customText,
                    unit = unit,
                    unitDecimal = unitDecimal,
                    error = error,
                    onText = onCustomText,
                    onUnit = onUnit,
                )
            }
            CapacityOrderSelector(order = order, onSelect = onOrder)
            CapacityPrefixField(prefix = prefix, onPrefix = onPrefix)
            CapacityEstimateLine(estimate = estimate, unitDecimal = unitDecimal)
        }
    }
}

/** Preset chips 1/2/3/4 GB + Custom, with one sliding selection indicator (C-03). */
@Composable
private fun CapacityPresetChips(
    selected: Int,
    unitDecimal: Boolean,
    onSelect: (Long, Int) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val labels = listOf(
        stringResource(R.string.capacity_preset_1gb),
        stringResource(R.string.capacity_preset_2gb),
        stringResource(R.string.capacity_preset_3gb),
        stringResource(R.string.capacity_preset_4gb),
        stringResource(R.string.capacity_preset_custom),
    )
    val gbBytes = unitBytes("GB", unitDecimal)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chipWidth = maxWidth / 5
        val indicatorOffset by animateDpAsState(
            targetValue = chipWidth * selected.coerceIn(0, 4),
            animationSpec = if (reduced) Motion.reduced() else Motion.small(),
            label = "capacity-chip-indicator",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(chipWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val interaction = rememberPressInteraction()
                Box(
                    Modifier
                        .width(chipWidth)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .pressable(interaction)
                        .clickable(interactionSource = interaction, indication = null) {
                            haptics()
                            when (i) {
                                0 -> onSelect(gbBytes, 0)
                                1 -> onSelect(2 * gbBytes, 1)
                                2 -> onSelect(3 * gbBytes, 2)
                                3 -> onSelect(4 * gbBytes, 3)
                                else -> onSelect(-1L, 4) // custom: bytes set by the field
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected == i) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Custom value: decimals allowed ("2.5"), unit dropdown MB / GB, inline
 * validation 50 MB .. 1 TB, no crash on empty/invalid input. The raw text
 * survives rotation via rememberSaveable.
 */
@Composable
private fun CapacityCustomField(
    text: String,
    unit: String,
    unitDecimal: Boolean,
    error: String?,
    onText: (String) -> Unit,
    onUnit: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text(stringResource(R.string.capacity_custom_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        error?.let { contentDescription = it }
                    },
            )
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                listOf("MB", "GB").forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = unit == u,
                        onClick = { onUnit(u) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(u) }
                }
            }
        }
        Text(
            capacityUnitHelperText(unitDecimal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** "Keep order" / "Fewest folders" segmented control. */
@Composable
private fun CapacityOrderSelector(order: CapacityOrder, onSelect: (CapacityOrder) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(R.string.capacity_order_label),
            style = MaterialTheme.typography.titleSmall,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = order == CapacityOrder.SEQUENTIAL,
                onClick = { onSelect(CapacityOrder.SEQUENTIAL) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.capacity_order_sequential)) }
            SegmentedButton(
                selected = order == CapacityOrder.BEST_FIT,
                onClick = { onSelect(CapacityOrder.BEST_FIT) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.capacity_order_bestfit)) }
        }
    }
}

/** Optional part-folder name prefix (default "Part"). */
@Composable
private fun CapacityPrefixField(prefix: String, onPrefix: (String) -> Unit) {
    val sanitized = remember(prefix) { CapacityPacker.sanitizePrefix(prefix) }
    OutlinedTextField(
        value = prefix,
        onValueChange = onPrefix,
        label = { Text(stringResource(R.string.capacity_prefix_label)) },
        supportingText = { Text(stringResource(R.string.capacity_prefix_helper, sanitized)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "About N folders · largest Part 3.94 GB" with an animated count (C-03). */
@Composable
fun CapacityEstimateLine(
    estimate: CapacityEstimate?,
    unitDecimal: Boolean,
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
) {
    val reduced = LocalReducedMotion.current
    if (estimate == null || estimate.folderCount == 0) return
    val animatedCount by animateIntAsState(
        targetValue = estimate.folderCount,
        animationSpec = if (reduced) Motion.reduced() else Motion.small(),
        label = "capacity-estimate-count",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(R.string.capacity_estimate_folders, animatedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (estimate.largestPartBytes > 0) {
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = Formatters.bytes(context, estimate.largestPartBytes),
                transitionSpec = {
                    if (reduced) {
                        (fadeIn(Motion.reduced())) togetherWith (fadeOut(Motion.reduced()))
                    } else {
                        (fadeIn(Motion.small())) togetherWith (fadeOut(Motion.small()))
                    }
                },
                label = "capacity-estimate-largest",
            ) { largest ->
                Text(
                    stringResource(R.string.capacity_estimate_largest, largest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Parses and validates the custom capacity text; null = not (yet) valid. */
fun parseCustomCapacity(text: String, unit: String, decimal: Boolean): Long? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return null
    val bytes = (value * unitBytes(unit, decimal)).toLong()
    if (bytes < CapacityLimits.minBytes(decimal)) return null
    if (bytes > CapacityLimits.maxBytes(decimal)) return null
    return bytes
}

/**
 * Validation for the custom field, as a string resource id so it can be
 * resolved from non-composable callbacks (context.getString). Null = valid.
 */
fun capacityErrorRes(text: String, unit: String, decimal: Boolean): Int? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return R.string.capacity_error_invalid
    if (value <= 0.0) return R.string.capacity_error_invalid
    val bytes = (value * unitBytes(unit, decimal)).toLong()
    return when {
        bytes < CapacityLimits.minBytes(decimal) -> R.string.capacity_error_min
        bytes > CapacityLimits.maxBytes(decimal) -> R.string.capacity_error_max
        else -> null
    }
}
