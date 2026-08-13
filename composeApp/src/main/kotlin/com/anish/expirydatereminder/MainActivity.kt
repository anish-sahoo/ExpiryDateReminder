package com.anish.expirydatereminder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anish.expirydatereminder.ui.help.HelpSheet
import com.anish.expirydatereminder.ui.items.ItemDetailScreen
import com.anish.expirydatereminder.ui.items.ItemEditSheet
import com.anish.expirydatereminder.ui.items.ItemListScreen
import com.anish.expirydatereminder.ui.settings.SettingsScreen
import com.anish.expirydatereminder.ui.theme.EdrTheme

/**
 * The whole app runs in one Activity with Compose Navigation.
 *
 * AppCompatActivity rather than ComponentActivity purely so
 * `AppCompatDelegate.setApplicationLocales` drives the per-app language picker.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The real system splash, replacing the pre-2.0 fake 2000ms Handler delay.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val openAddDirectly = intent?.data?.toString() == ADD_ITEM_DEEP_LINK

        setContent {
            EdrTheme {
                EdrApp(startWithAddSheet = openAddDirectly)
            }
        }
    }

    private companion object {
        /** Matches the launcher shortcut declared in xml/shortcuts.xml. */
        const val ADD_ITEM_DEEP_LINK = "edr://item/new"
    }
}

private object Routes {
    const val ITEMS = "items"
    const val SETTINGS = "settings"
    const val DETAIL = "item/{itemId}"

    fun detail(itemId: Long) = "item/$itemId"
}

@Composable
fun EdrApp(startWithAddSheet: Boolean = false) {
    val navController = rememberNavController()
    var editingItemId by remember { mutableStateOf<Long?>(null) }
    var showEditSheet by remember { mutableStateOf(startWithAddSheet) }
    var showHelp by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = Routes.ITEMS) {
        composable(Routes.ITEMS) {
            ItemListScreen(
                onAddItem = {
                    editingItemId = null
                    showEditSheet = true
                },
                onOpenItem = { id -> navController.navigate(Routes.detail(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHelp = { showHelp = true },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val itemId = entry.arguments?.getLong("itemId") ?: return@composable
            ItemDetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    editingItemId = id
                    showEditSheet = true
                },
            )
        }
    }

    if (showEditSheet) {
        ItemEditSheet(
            itemId = editingItemId,
            onDismiss = {
                showEditSheet = false
                editingItemId = null
            },
        )
    }

    if (showHelp) {
        HelpSheet(onDismiss = { showHelp = false })
    }
}
