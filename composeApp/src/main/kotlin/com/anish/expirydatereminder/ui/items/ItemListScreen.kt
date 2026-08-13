package com.anish.expirydatereminder.ui.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.Category
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.domain.model.Item
import com.anish.expirydatereminder.domain.usecase.ItemSort
import com.anish.expirydatereminder.ui.common.EmptyState
import com.anish.expirydatereminder.ui.common.categoryDisplayName
import com.anish.expirydatereminder.ui.common.compactRelative
import com.anish.expirydatereminder.ui.common.format
import com.anish.expirydatereminder.ui.common.relativeExpiry
import com.anish.expirydatereminder.ui.theme.Space
import org.koin.androidx.compose.koinViewModel

/**
 * The item list.
 *
 * Layered rather than flat: a tinted hero panel at the top, then each urgency tier as its
 * own raised card. Depth carries the grouping so type does not have to do all the work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onAddItem: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: ItemListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var searchOpen by remember { mutableStateOf(false) }

    val deletedMessage = stringResource(R.string.item_deleted, (event as? ItemListEvent.Deleted)?.item?.name.orEmpty())
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(event) {
        val deleted = (event as? ItemListEvent.Deleted)?.item ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete(deleted)
        } else {
            viewModel.confirmDelete(deleted)
        }
    }

    val grouped = remember(state.items, state.today, state.settings.reminderLeadDays) {
        state.items.groupBy { it.statusOn(state.today, state.settings.reminderLeadDays) }
    }

    // Expired and expiring-soon start open because they are the reason to open the app;
    // "Later" starts collapsed so a long tail of fine items does not bury them.
    val expanded = remember {
        mutableStateMapOf(
            ExpiryStatus.EXPIRED to true,
            ExpiryStatus.EXPIRING_SOON to true,
            ExpiryStatus.OK to false,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItem,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.items_add), style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp, 10.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "hero") {
                HeroPanel(
                    expired = grouped[ExpiryStatus.EXPIRED]?.size ?: 0,
                    soon = grouped[ExpiryStatus.EXPIRING_SOON]?.size ?: 0,
                    total = state.items.size,
                    sort = state.sort,
                    searchOpen = searchOpen,
                    query = state.query,
                    onQueryChange = viewModel::search,
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.search("")
                    },
                    onToggleSort = viewModel::toggleSort,
                    onOpenSettings = onOpenSettings,
                    onOpenHelp = onOpenHelp,
                )
            }

            item(key = "filters") {
                CategoryFilters(state.categories, state.selectedCategoryId, viewModel::selectCategory)
            }

            when {
                state.isEmptyOverall -> item {
                    EmptyState(
                        stringResource(R.string.items_empty_title),
                        stringResource(R.string.items_empty_body),
                        Modifier.height(300.dp),
                    )
                }

                state.hasNoMatches -> item {
                    EmptyState(
                        stringResource(R.string.items_no_results_title),
                        stringResource(R.string.items_no_results_body),
                        Modifier.height(300.dp),
                    )
                }

                else -> listOf(ExpiryStatus.EXPIRED, ExpiryStatus.EXPIRING_SOON, ExpiryStatus.OK).forEach { status ->
                    val group = grouped[status].orEmpty()
                    if (group.isEmpty()) return@forEach

                    item(key = "group-$status") {
                        UrgencyGroupCard(
                            status = status,
                            items = group,
                            state = state,
                            expanded = expanded[status] != false,
                            onToggle = { expanded[status] = expanded[status] == false },
                            onOpenItem = onOpenItem,
                            onDelete = viewModel::delete,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tinted hero panel.
 *
 * The text block has a fixed minimum height and the supporting line always occupies its
 * slot, so switching category filters no longer makes the whole page jump.
 */
@Composable
private fun HeroPanel(
    expired: Int,
    soon: Int,
    total: Int,
    sort: ItemSort,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSort: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val needsAttention = expired + soon

    // A floating card rather than a full-bleed panel. Rounding only the bottom corners
    // left the top edge butting against the status bar, which read as unfinished.
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Column(Modifier.padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.items_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                // Sort is a first-class action rather than something buried under a gear
                // icon next to unrelated destinations.
                IconButton(onClick = onToggleSort) {
                    Icon(
                        if (sort == ItemSort.BY_EXPIRY) Icons.Outlined.Schedule else Icons.Outlined.SortByAlpha,
                        contentDescription = stringResource(
                            if (sort == ItemSort.BY_EXPIRY) {
                                R.string.items_sort_by_name
                            } else {
                                R.string.items_sort_by_expiry
                            },
                        ),
                    )
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.items_search_hint),
                    )
                }
                IconButton(onClick = onOpenHelp) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.help_title),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }

            Spacer(Modifier.height(Space.sm))

            Column(Modifier.defaultMinSize(minHeight = if (searchOpen) 0.dp else HERO_TEXT_HEIGHT)) {
                Text(
                    text = when {
                        total == 0 -> stringResource(R.string.header_nothing_tracked)
                        needsAttention == 0 -> stringResource(R.string.header_all_good)
                        else -> stringResource(R.string.header_needs_attention, needsAttention)
                    },
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = Space.md),
                )
                Spacer(Modifier.height(Space.xs))
                // Hidden while searching: the field takes that space, and a "start by
                // adding an item" prompt is noise when the user is already looking for one.
                if (!searchOpen) {
                    Text(
                        text = when {
                            total == 0 -> stringResource(R.string.header_hint_start)
                            needsAttention == 0 -> stringResource(R.string.header_hint_all_good)
                            else -> buildString {
                                if (expired > 0) append(stringResource(R.string.header_detail_expired, expired))
                                if (expired > 0 && soon > 0) append("   ·   ")
                                if (soon > 0) append(stringResource(R.string.header_detail_soon, soon))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }

            AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                InlineSearch(query, onQueryChange, Modifier.padding(top = Space.md, end = Space.md))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineSearch(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Tapping the search icon should put the cursor in the field and raise the keyboard;
    // making the user tap twice to start typing is a wasted step.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.items_search_hint)) },
        singleLine = true,
        // Same shape as the category field in Settings; they are the same control.
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
        // Sits on the tinted hero, so it needs its own surface to separate from it; the
        // default container color is close enough to primaryContainer to disappear.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun CategoryFilters(categories: List<Category>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            FilterPill(stringResource(R.string.items_filter_all), selectedId == null) { onSelect(null) }
        }
        items(categories, key = { it.id }) { category ->
            FilterPill(
                text = categoryDisplayName(category.builtinKey, category.name),
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
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
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Space.md, vertical = 10.dp),
        )
    }
}

/**
 * One raised card per urgency tier.
 *
 * Grouping by surface rather than by a rule and a label gives the page a layer to sit on,
 * and keeps the tinted urgency color contained instead of striping the whole screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrgencyGroupCard(
    status: ExpiryStatus,
    items: List<Item>,
    state: ItemListUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onDelete: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Expressive motion: the chevron rotates on the same spring the content expands on,
    // so the two read as one gesture rather than two animations.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    val style = urgencyStyle(status)
    val title = stringResource(
        when (status) {
            ExpiryStatus.EXPIRED -> R.string.items_section_expired
            ExpiryStatus.EXPIRING_SOON -> R.string.items_section_expiring
            ExpiryStatus.OK -> R.string.items_section_ok
        },
    )

    Surface(
        color = style.container,
        contentColor = style.onContainer,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp,
        modifier = modifier.padding(horizontal = Space.md),
    ) {
        Column(Modifier.padding(vertical = Space.md)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = Space.md, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrgencyGlyph(status, size = 16.dp)
                Spacer(Modifier.width(Space.sm))
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = style.accent)
                Spacer(Modifier.weight(1f))
                Surface(color = style.accent.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                    Text(
                        items.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = style.accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier.padding(start = Space.xs).rotate(chevronRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(Space.xs))

                    items.forEach { item ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        // Reacting to the settled value rather than vetoing the change: the
                        // confirmValueChange callback is deprecated, and a swipe here is never
                        // refused anyway — it always deletes, with an undo offered afterwards.
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                onDelete(item)
                                dismissState.reset()
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                                        .padding(horizontal = Space.lg),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            },
                        ) {
                            ItemRow(item, state, status) { onOpenItem(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, state: ItemListUiState, status: ExpiryStatus, onClick: () -> Unit) {
    val style = urgencyStyle(status)
    val daysUntil = item.daysUntilExpiry(state.today)
    val relative = relativeExpiry(daysUntil)
    val absolute = item.expiry.format(state.settings.dateFormat)
    val category = categoryDisplayName(item.categoryBuiltinKey, item.categoryName)

    Row(
        Modifier
            .fillMaxWidth()
            .background(style.container)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = "${item.name}, $relative, $absolute, $category"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapedPhotoFrame(
            tint = style.accent.copy(alpha = 0.18f),
            modifier = Modifier.size(52.dp),
        ) {
            ItemPhoto(item.imagePath, Modifier.fillMaxSize(), cornerRadius = 0.dp)
        }
        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleLarge,
                color = style.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Anything urgent reads better in relative terms; for things months away the
            // absolute date is the more useful fact.
            Text(
                if (status == ExpiryStatus.OK) "$absolute · $category" else "$relative · $category",
                style = MaterialTheme.typography.bodySmall,
                color = style.onContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(Space.sm))

        // Compact countdown, so the row carries urgency without a sentence of text.
        Surface(color = style.accent.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
            Text(
                compactRelative(daysUntil),
                style = MaterialTheme.typography.labelMedium,
                color = style.accent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

private val HERO_TEXT_HEIGHT = 104.dp
