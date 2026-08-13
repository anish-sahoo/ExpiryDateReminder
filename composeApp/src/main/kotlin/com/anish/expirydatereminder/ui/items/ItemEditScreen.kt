package com.anish.expirydatereminder.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.camera.ScanAvailability
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.DatePart
import com.anish.expirydatereminder.domain.model.ExpiryDate
import com.anish.expirydatereminder.domain.model.parts
import com.anish.expirydatereminder.domain.model.separator
import com.anish.expirydatereminder.domain.usecase.DraftError
import com.anish.expirydatereminder.domain.usecase.DraftField
import com.anish.expirydatereminder.domain.usecase.DuplicateCheck
import com.anish.expirydatereminder.logging.Log
import com.anish.expirydatereminder.ui.common.categoryDisplayName
import com.anish.expirydatereminder.ui.common.format
import com.anish.expirydatereminder.ui.common.rememberFeedback
import com.anish.expirydatereminder.ui.scan.rememberCameraCapture
import com.anish.expirydatereminder.ui.theme.Space
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditSheet(itemId: Long?, onDismiss: () -> Unit, viewModel: ItemEditViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Half-expanded is left out of the anchor set rather than vetoed by a callback: the form
    // is tall enough that a partially expanded sheet shows little more than the name field.
    // The replacement for the old `skipPartiallyExpanded` flag is simply not offering the
    // anchor in the first place.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var showDatePicker by remember { mutableStateOf(false) }

    val feedback = rememberFeedback()
    val camera = rememberCameraCapture { bitmap -> viewModel.scan(bitmap) }
    // Read once per sheet: neither a camera nor Play Services appears while the form is open.
    val scanAvailability: ScanAvailability = koinInject()
    val scanSupported = remember { scanAvailability.scanSupported() }
    val savedMessage = stringResource(if (itemId == null) R.string.toast_item_added else R.string.toast_item_updated)

    LaunchedEffect(itemId) { viewModel.load(itemId) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            // A Toast rather than a Snackbar: the sheet is closing, so there is no host
            // left to show one in, and the confirmation should outlive the sheet.
            feedback.toast(savedMessage)
            viewModel.consumeSaved()
            onDismiss()
        }
    }
    LaunchedEffect(state.scanFoundNothing) {
        if (state.scanFoundNothing) Log.d("ItemEdit", "Scan produced no usable date")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (state.isEditing) R.string.edit_title_edit else R.string.edit_title_add),
                style = MaterialTheme.typography.headlineLarge,
            )

            TextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.edit_name)) },
                singleLine = true,
                isError = state.errors.containsKey(DraftField.NAME),
                supportingText = state.errors[DraftField.NAME]?.let { { Text(it.message()) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.titleMedium,
                shape = MaterialTheme.shapes.medium,
                // Filled and borderless so it sits in the same visual family as the date
                // panel below; outlined fields read as thin next to tonal surfaces.
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // One large tappable date surface instead of three thin outlined boxes. The
            // date is the point of this screen, so it carries the largest type on it.
            DateSurface(
                dateFormat = state.dateFormat,
                day = state.day,
                month = state.month,
                year = state.year,
                errorText = listOfNotNull(
                    state.errors[DraftField.DAY],
                    state.errors[DraftField.MONTH],
                    state.errors[DraftField.YEAR],
                ).firstOrNull()?.message(),
                onPickDate = { showDatePicker = true },
                onScan = if (scanSupported) ({ camera.capture() }) else null,
                onDayChange = viewModel::setDay,
                onMonthChange = viewModel::setMonth,
                onYearChange = viewModel::setYear,
            )

            if (state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // A failed scan is a quiet hint, never an error dialog.
            if (state.scanFoundNothing) {
                Text(
                    stringResource(R.string.scan_nothing_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.scanAlternatives.isNotEmpty()) {
                Text(
                    stringResource(R.string.scan_pick_candidate).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.scanAlternatives.forEach { candidate ->
                        AssistChip(
                            onClick = { viewModel.setExpiry(candidate) },
                            label = { Text(candidate.format(DateFormat.DAY_FIRST)) },
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.edit_category).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                state.categories.forEach { category ->
                    val selected = state.categoryId == category.id
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { viewModel.setCategory(category.id) },
                    ) {
                        Text(
                            categoryDisplayName(category.builtinKey, category.name),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = 10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xs))
            TextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.edit_notes)) },
                placeholder = { Text(stringResource(R.string.edit_notes_hint)) },
                minLines = 2,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            when (val duplicate = state.duplicate) {
                is DuplicateCheck.Exact -> DuplicateNotice(
                    stringResource(R.string.duplicate_exact),
                    onDismiss = viewModel::dismissDuplicate,
                    onConfirm = { viewModel.save(force = true) },
                )

                is DuplicateCheck.SameNameDifferentDate -> DuplicateNotice(
                    stringResource(R.string.duplicate_same_name_different_date, duplicate.existing.name),
                    onDismiss = viewModel::dismissDuplicate,
                    onConfirm = { viewModel.save(force = true) },
                )

                is DuplicateCheck.SameNameDifferentCategory -> DuplicateNotice(
                    stringResource(R.string.duplicate_same_name_different_category, duplicate.existing.name),
                    onDismiss = viewModel::dismissDuplicate,
                    onConfirm = { viewModel.save(force = true) },
                )

                DuplicateCheck.None -> Unit
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { viewModel.save() }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.fromEpochDays((millis / MILLIS_PER_DAY).toInt())
                        viewModel.setExpiry(ExpiryDate.from(date))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DuplicateNotice(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun DraftError.message(): String = stringResource(
    when (this) {
        DraftError.NAME_BLANK -> R.string.error_name_blank
        DraftError.MONTH_REQUIRED -> R.string.error_month_required
        DraftError.MONTH_OUT_OF_RANGE -> R.string.error_month_range
        DraftError.DAY_OUT_OF_RANGE_FOR_MONTH -> R.string.error_day_range
        DraftError.YEAR_OUT_OF_RANGE -> R.string.error_year_range
    },
)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The date input.
 *
 * One raised surface holding three large numeric fields with the scan and picker actions
 * attached to it, so it reads as a single control rather than three unrelated boxes.
 */
@Composable
private fun DateSurface(
    dateFormat: DateFormat,
    day: String,
    month: String,
    year: String,
    errorText: String?,
    onPickDate: () -> Unit,
    onScan: (() -> Unit)?,
    onDayChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md)) {
            Text(
                stringResource(R.string.edit_expiry).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))

            // Field order follows the user's chosen format, so someone on YYYY-MM-DD
            // types year first rather than mentally transposing.
            Row(verticalAlignment = Alignment.CenterVertically) {
                dateFormat.parts.forEachIndexed { index, part ->
                    if (index > 0) DateSeparator(dateFormat.separator)
                    when (part) {
                        DatePart.DAY -> BigDateField(
                            day,
                            onDayChange,
                            stringResource(R.string.edit_day),
                            2,
                            Modifier.width(62.dp),
                        )

                        DatePart.MONTH -> BigDateField(
                            month,
                            onMonthChange,
                            stringResource(R.string.edit_month),
                            2,
                            Modifier.width(62.dp),
                        )

                        DatePart.YEAR -> BigDateField(
                            year,
                            onYearChange,
                            stringResource(R.string.edit_year),
                            4,
                            Modifier.width(100.dp),
                        )
                    }
                }
            }

            if (errorText != null) {
                Spacer(Modifier.height(Space.xs))
                Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Space.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                // Offered only where it can actually work. Scanning needs a camera and Play
                // Services for ML Kit; on a device without either, the button would open the
                // camera and then quietly fail to find anything, which reads as the feature
                // being broken rather than absent.
                if (onScan != null) {
                    FilledTonalButton(onClick = onScan) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text(stringResource(R.string.edit_scan))
                    }
                }
                FilledTonalButton(onClick = onPickDate) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text(stringResource(R.string.edit_pick_date))
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(symbol: String) {
    Text(
        symbol,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = Space.xs),
    )
}

@Composable
private fun BigDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxLength)) },
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        singleLine = true,
        // The label lives in the decoration box, which makes it a sibling rather than this
        // node's own semantics: without this the field is an unlabeled text box to a screen
        // reader, announced only as "edit box".
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { inner ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(
                            "-".repeat(maxLength),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    inner()
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
