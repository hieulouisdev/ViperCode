package com.vipercode.ide.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.ui.screens.AboutScreen
import com.vipercode.ide.ui.screens.EditorScreen
import com.vipercode.ide.ui.screens.HomeScreen
import com.vipercode.ide.ui.screens.SettingsScreen
import com.vipercode.ide.ui.screens.SplashScreen
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val EDITOR = "editor/{tabId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun ViperNavHost(
    externalFileUri: Uri?,
    onExternalUriConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FileRepository.get(context) }

    // Auto-advance past splash after 1 frame.
    LaunchedEffect(Unit) {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    // Open a file URI that arrived via ACTION_VIEW.
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
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
