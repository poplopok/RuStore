package com.example.rustorecatalog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rustorecatalog.navigation.RuStoreNavHost
import com.example.rustorecatalog.ui.theme.RuStoreCatalogTheme
import com.example.rustorecatalog.viewmodel.OnboardingViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            RuStoreCatalogAppContent()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun RuStoreCatalogAppContent() {
    RuStoreCatalogTheme {
        Surface {
            val onboardingViewModel: OnboardingViewModel = viewModel()
            val hasSeenOnboarding by onboardingViewModel.hasSeenOnboarding.collectAsState()

            LaunchedEffect(Unit) {
                onboardingViewModel.loadOnboardingState()
            }

            if (hasSeenOnboarding == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                RuStoreNavHost(
                    showOnboarding = hasSeenOnboarding == false,
                    onOnboardingFinished = {
                        onboardingViewModel.setOnboardingSeen()
                    }
                )
            }
        }
    }
}
