package com.anish.expirydatereminder.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish.expirydatereminder.BuildConfig
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.DateFormat
import com.anish.expirydatereminder.domain.model.pattern
import com.anish.expirydatereminder.locale.AppLanguage
import com.anish.expirydatereminder.notifications.ExpiryReminderScheduler
import com.anish.expirydatereminder.ui.common.categoryDisplayName
import com.anish.expirydatereminder.ui.common.rememberFeedback
import com.anish.expirydatereminder.ui.common.sample
import com.anish.expirydatereminder.ui.theme.Space
import org.koin.androidx.compose.koinViewModel

/**
 * Settings.
 *
 * Every setting is the same shape: a row with a label, optional supporting line, and one
 * trailing control. Sections are cards. The previous version mixed chip rows, radio lists,
 * bare text buttons and a two-column table, which made a short screen feel busy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val feedback = rememberFeedback()

    var newCategory by remember { mutableStateOf("") }
    var confirmDeleteAllItems by remember { mutableStateOf(false) }
    var confirmDeleteCategories by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotificationsEnabled(granted) }

    val categoryExistsMessage = stringResource(R.string.settings_category_exists)
    LaunchedEffect(messages.categoryExists) {
        if (messages.categoryExists) {
            snackbarHost.showSnackbar(categoryExistsMessage)
            viewModel.clearMessages()
        }
    }
    val importedMessage = messages.importSummary?.let {
        stringResource(R.string.migration_done, it.itemCount, it.categoryCount)
    }
    LaunchedEffect(messages.importSummary) {
        if (importedMessage != null) {
            feedback.toast(importedMessage, long = true)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.md,
                end = Space.md,
                top = Space.sm,
                bottom = Space.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item {
                SettingsSection(stringResource(R.string.settings_notifications)) {
                    SettingRow(
                        title = stringResource(R.string.settings_notifications_enabled),
                        trailing = {
                            Switch(
                                checked = state.settings.notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setNotificationsEnabled(enabled)
                                    }
                                },
                            )
                        },
                    )
                    RowDivider()
                    // A dropdown rather than a chip row: four chips took a whole line to
                    // express one value.
                    DropdownSettingRow(
                        title = stringResource(R.string.settings_reminder_lead),
                        selectedLabel = pluralStringResource(
                            R.plurals.settings_reminder_lead_days,
                            state.settings.reminderLeadDays,
                            state.settings.reminderLeadDays,
                        ),
                        options = LEAD_DAY_OPTIONS,
                        labelFor = { days -> pluralStringResource(R.plurals.settings_reminder_lead_days, days, days) },
                        onSelect = viewModel::setReminderLeadDays,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_date_format)) {
                    // Six radio rows were most of a screen for a value shown once.
                    DropdownSettingRow(
                        title = stringResource(R.string.settings_date_format),
                        selectedLabel = state.settings.dateFormat.sample(),
                        options = DateFormat.entries,
                        // Shows the pattern next to the example, so it is clear what is being
                        // chosen rather than having to infer it from one date.
                        labelFor = { "${it.sample()}    ${it.pattern}" },
                        onSelect = viewModel::setDateFormat,
                    )
                    RowDivider()
                    DropdownSettingRow(
                        title = stringResource(R.string.settings_language),
                        selectedLabel = AppLanguage.current().endonym
                            .ifEmpty { stringResource(R.string.settings_language_system) },
                        options = AppLanguage.entries,
                        labelFor = { it.endonym.ifEmpty { stringResource(R.string.settings_language_system) } },
                        onSelect = { AppLanguage.apply(it) },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_categories)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = ROW_INSET, end = Space.sm, top = Space.md, bottom = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        TextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            placeholder = { Text(stringResource(R.string.settings_category_name)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                viewModel.addCategory(newCategory)
                                newCategory = ""
                            },
                            enabled = newCategory.isNotBlank(),
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.settings_add_category),
                            )
                        }
                    }

                    // Built-in and custom rows share one shape; only the trailing control
                    // differs, so the list reads as one thing.
                    state.categories.forEach { category ->
                        RowDivider()
                        CategoryRow(
                            name = categoryDisplayName(category.builtinKey, category.name),
                            deletable = !category.isBuiltin,
                            onDelete = { viewModel.askToDeleteCategory(category) },
                        )
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_data)) {
                    DestructiveRow(
                        title = stringResource(R.string.settings_delete_all_items),
                        onClick = { confirmDeleteAllItems = true },
                        leading = Icons.Outlined.DeleteSweep,
                    )
                    RowDivider()
                    DestructiveRow(
                        title = stringResource(R.string.settings_delete_user_categories),
                        onClick = { confirmDeleteCategories = true },
                        leading = Icons.Outlined.DeleteForever,
                    )
                    if (messages.canRetryImport) {
                        RowDivider()
                        Row(Modifier.padding(Space.md)) {
                            Button(onClick = viewModel::retryImport, enabled = !messages.importRunning) {
                                Text(stringResource(R.string.migration_retry))
                            }
                        }
                    }
                    if (messages.importFailed) {
                        Text(
                            stringResource(R.string.migration_failed_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(Space.md),
                        )
                    }
                }
            }

            item {
                val context = LocalContext.current
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .then(
                            // Debug builds only: fires the reminder check immediately so the
                            // notification can be inspected without waiting for 07:00.
                            if (BuildConfig.DEBUG) {
                                Modifier.clickable { ExpiryReminderScheduler.runNow(context) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = ROW_INSET, top = Space.sm, bottom = Space.xxl),
                )
            }
        }
    }

    pendingDelete?.let { pending ->
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_category_title),
            body = if (pending.itemCount == 0) {
                stringResource(R.string.settings_delete_category_empty, pending.category.name)
            } else {
                pluralStringResource(
                    R.plurals.settings_delete_category_with_items,
                    pending.itemCount,
                    pending.itemCount,
                    pending.category.name,
                )
            },
            onConfirm = viewModel::confirmCategoryDelete,
            onDismiss = viewModel::cancelCategoryDelete,
        )
    }

    if (confirmDeleteAllItems) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all_items),
            body = stringResource(R.string.settings_delete_all_items_body),
            onConfirm = {
                viewModel.deleteAllItems()
                confirmDeleteAllItems = false
            },
            onDismiss = { confirmDeleteAllItems = false },
        )
    }

    if (confirmDeleteCategories) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_user_categories),
            body = stringResource(R.string.settings_delete_user_categories_body),
            onConfirm = {
                viewModel.deleteAllUserCategories()
                confirmDeleteCategories = false
            },
            onDismiss = { confirmDeleteCategories = false },
        )
    }
}

/** A titled card. Matches the grouping used on the item list, so the app feels of a piece. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            // Same left edge as the row text inside the card below it.
            modifier = Modifier.padding(start = ROW_INSET, bottom = Space.sm),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        // Inset to the text, so the divider reads as separating rows rather than
        // cutting the card in half.
        modifier = Modifier.padding(start = ROW_INSET, end = Space.sm),
    )
}

/** The single row shape every setting uses. */
@Composable
private fun SettingRow(
    title: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .padding(start = ROW_INSET, end = Space.sm, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSettingRow(
    title: String,
    selectedLabel: String,
    options: List<T>,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        SettingRow(
            title = title,
            onClick = { expanded = true },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelFor(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, deletable: Boolean, onDelete: () -> Unit) {
    SettingRow(
        title = name,
        trailing = {
            if (deletable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                // Occupies the same slot as the delete button so every row lines up.
                Text(
                    stringResource(R.string.settings_category_builtin_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Space.sm),
                )
            }
        },
    )
}

/**
 * Destructive actions get an icon and a tinted container rather than being bare red text,
 * which was easy to miss and easy to hit by accident.
 */
@Composable
private fun DestructiveRow(
    title: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .padding(start = ROW_INSET, end = Space.md, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                leading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val ROW_HEIGHT = 56.dp

/** Distance from the card edge to row text. Section labels and dividers share it. */
private val ROW_INSET = 20.dp

private val LEAD_DAY_OPTIONS = listOf(1, 3, 7, 14, 30, 60)
