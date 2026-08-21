package com.vipercode.ide.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.ui.screens.AboutScreen
import com.vipercode.ide.ui.screens.EditorScreen
import com.vipercode.ide.ui.screens.HomeScreen
import com.vipercode.ide.ui.screens.PreviewScreen
import com.vipercode.ide.ui.screens.QuickOpenScreen
import com.vipercode.ide.ui.screens.SearchInFilesScreen
import com.vipercode.ide.ui.screens.SettingsScreen
import com.vipercode.ide.ui.screens.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val EDITOR = "editor/{tabId}"
    const val PREVIEW = "preview/{tabId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val SEARCH_IN_FILES = "search_in_files"
    const val QUICK_OPEN = "quick_open"
}

/**
 * Top-level navigation graph for ViperCode.
 *
 * v0.0.4 additions:
 *  - [Routes.SEARCH_IN_FILES] — workspace-wide text search.
 *  - [Routes.QUICK_OPEN] — VS Code "Ctrl+P" style file picker.
 *
 * Both new screens hand off to the editor route when the user taps a
 * result. The editor route's `tabId` argument is honoured so the
 * cursor restoration logic in [com.vipercode.ide.ui.components.CodeEditor]
 * picks up the matching line/column set by the search screen.
 */
@Composable
fun ViperNavHost(
    externalFileUri: Uri?,
    onExternalUriConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FileRepository.get(context) }

    // Open a file URI that arrived via ACTION_VIEW (or from the splash's
    // "continue" gesture). Each new URI re-keys the effect so subsequent
    // invocations from MainActivity.onNewIntent are honoured.
    LaunchedEffect(externalFileUri) {
        val uri = externalFileUri ?: return@LaunchedEffect
        repo.openExternalFile(uri)
        navController.navigate(Routes.HOME) {
            launchSingleTop = true
        }
        onExternalUriConsumed()
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onContinue = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenFile = { tabId ->
                    navController.navigate(Routes.EDITOR.replace("{tabId}", tabId))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenSearchInFiles = { navController.navigate(Routes.SEARCH_IN_FILES) },
                onOpenQuickOpen = { navController.navigate(Routes.QUICK_OPEN) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("tabId") { type = NavType.StringType }),
        ) { backStack ->
            val tabId = backStack.arguments?.getString("tabId") ?: return@composable
            EditorScreen(
                tabId = tabId,
                onBack = { navController.popBackStack() },
                onOpenPreview = { id ->
                    navController.navigate(Routes.PREVIEW.replace("{tabId}", id))
                },
                onOpenQuickOpen = { navController.navigate(Routes.QUICK_OPEN) },
                onOpenSearchInFiles = { navController.navigate(Routes.SEARCH_IN_FILES) },
            )
        }
        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("tabId") { type = NavType.StringType }),
        ) { backStack ->
            val tabId = backStack.arguments?.getString("tabId") ?: return@composable
            PreviewScreen(
                tabId = tabId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH_IN_FILES) {
            SearchInFilesScreen(
                onBack = { navController.popBackStack() },
                onOpenFile = { tabId ->
                    navController.navigate(Routes.EDITOR.replace("{tabId}", tabId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(Routes.QUICK_OPEN) {
            QuickOpenScreen(
                onBack = { navController.popBackStack() },
                onOpenFile = { tabId ->
                    navController.navigate(Routes.EDITOR.replace("{tabId}", tabId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
    }
}
