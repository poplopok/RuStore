package com.example.rustorecatalog.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rustorecatalog.model.AppCategory
import com.example.rustorecatalog.ui.screens.AppDetailsScreen
import com.example.rustorecatalog.ui.screens.AppStoreScreen
import com.example.rustorecatalog.ui.screens.CategoriesScreen
import com.example.rustorecatalog.ui.screens.OnboardingScreen
import com.example.rustorecatalog.ui.screens.SearchScreen
import com.example.rustorecatalog.ui.screens.ScreenshotsViewerScreen

@Composable
fun RuStoreNavHost(
    showOnboarding: Boolean,
    onOnboardingFinished: () -> Unit
) {
    val navController = rememberNavController()
    val startDestination = remember(showOnboarding) {
        if (showOnboarding) NavRoutes.ONBOARDING else NavRoutes.STORE
    }

    fun safeBackToStore() {
        if (!navController.popBackStack()) {
            navController.navigate(NavRoutes.STORE) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(
                onContinue = {
                    onOnboardingFinished()
                    navController.navigate(NavRoutes.STORE) {
                        popUpTo(NavRoutes.ONBOARDING) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(
            route = "${NavRoutes.STORE}?${NavRoutes.ARG_CATEGORY}={${NavRoutes.ARG_CATEGORY}}",
            arguments = listOf(
                navArgument(NavRoutes.ARG_CATEGORY) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val categoryName = backStackEntry.arguments?.getString(NavRoutes.ARG_CATEGORY)
            val category = categoryName?.let {
                runCatching { AppCategory.valueOf(it) }.getOrNull()
            }
            AppStoreScreen(
                selectedCategory = category,
                onOpenCategories = {
                    navController.navigate(NavRoutes.CATEGORIES)
                },
                onOpenSearch = {
                    navController.navigate(NavRoutes.SEARCH)
                },
                onOpenDetails = { appId ->
                    navController.navigate("${NavRoutes.APP_DETAILS}/${appId}")
                }
            )
        }

        composable(NavRoutes.CATEGORIES) {
            CategoriesScreen(
                onBack = { safeBackToStore() },
                onCategorySelected = { category ->
                    if (category == null) {
                        navController.navigate(NavRoutes.STORE) {
                            popUpTo(NavRoutes.STORE) {
                                inclusive = true
                            }
                        }
                    } else {
                        navController.navigate("${NavRoutes.STORE}?${NavRoutes.ARG_CATEGORY}=${category.name}") {
                            popUpTo(NavRoutes.STORE) {
                                inclusive = true
                            }
                        }
                    }
                }
            )
        }

        composable(NavRoutes.SEARCH) {
            SearchScreen(
                onBack = { safeBackToStore() },
                onOpenDetails = { appId ->
                    navController.navigate("${NavRoutes.APP_DETAILS}/${appId}")
                }
            )
        }

        composable(
            route = "${NavRoutes.APP_DETAILS}/{${NavRoutes.ARG_APP_ID}}",
            arguments = listOf(
                navArgument(NavRoutes.ARG_APP_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(NavRoutes.ARG_APP_ID)
            if (appId == null) {
                LaunchedEffect(Unit) { safeBackToStore() }
                return@composable
            }
            AppDetailsScreen(
                appId = appId,
                onBack = { safeBackToStore() },
                onOpenScreenshots = { startIndex ->
                    navController.navigate("${NavRoutes.SCREENSHOTS}/${appId}/${startIndex}")
                }
            )
        }

        composable(
            route = "${NavRoutes.SCREENSHOTS}/{${NavRoutes.ARG_APP_ID}}/{${NavRoutes.ARG_START_INDEX}}",
            arguments = listOf(
                navArgument(NavRoutes.ARG_APP_ID) { type = NavType.StringType },
                navArgument(NavRoutes.ARG_START_INDEX) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(NavRoutes.ARG_APP_ID)
            if (appId == null) {
                LaunchedEffect(Unit) { safeBackToStore() }
                return@composable
            }
            val startIndex = backStackEntry.arguments?.getInt(NavRoutes.ARG_START_INDEX) ?: 0
            ScreenshotsViewerScreen(
                appId = appId,
                startIndex = startIndex,
                onBack = { safeBackToStore() }
            )
        }
    }
}

