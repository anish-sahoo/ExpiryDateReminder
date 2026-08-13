package com.anish.expirydatereminder.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.anish.expirydatereminder.domain.repository.ItemRepository
import com.anish.expirydatereminder.domain.repository.SettingsRepository
import com.anish.expirydatereminder.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps the home screen widget in step with the database.
 *
 * Without this the widget only redraws on its `updatePeriodMillis`, which the platform
 * clamps to 30 minutes — so adding or deleting an item left a stale count on the home
 * screen for up to an hour, and a fresh install sat on the loading placeholder until the
 * first tick.
 *
 * Watching the repository rather than calling [androidx.glance.appwidget.GlanceAppWidget.updateAll]
 * from each write site means no future code path can forget to. It is cheap: with no widget
 * placed, `updateAll` finds no ids and returns.
 */
class WidgetRefresher(
    private val context: Context,
    private val items: ItemRepository,
    private val settings: SettingsRepository,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                items.observeItems(categoryId = null),
                settings.observeSettings(),
            ) { list, config -> list to config }
                .distinctUntilChanged()
                // The first emission is the initial read, which the widget has already
                // done for itself; redrawing on it would be pure duplication.
                .drop(1)
                .collect {
                    runCatching { ExpiryWidget().updateAll(context) }
                        .onFailure { error -> Log.w(TAG, "Widget refresh failed", error) }
                }
        }
    }

    private companion object {
        const val TAG = "WidgetRefresher"
    }
}
