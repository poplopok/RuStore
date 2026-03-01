package com.example.rustorecatalog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import com.example.rustorecatalog.R
import com.example.rustorecatalog.install.ApkDownloadService
import com.example.rustorecatalog.install.InstallManager
import com.example.rustorecatalog.install.InstallState
import com.example.rustorecatalog.model.AppInfo
import com.example.rustorecatalog.ui.components.BrandedTopBarTitle
import com.example.rustorecatalog.ui.components.EmptyState
import com.example.rustorecatalog.ui.components.ErrorState
import com.example.rustorecatalog.ui.state.LoadState
import com.example.rustorecatalog.viewmodel.AppDetailsViewModel
import com.example.rustorecatalog.viewmodel.InstallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    appId: String,
    onBack: () -> Unit,
    onOpenScreenshots: (Int) -> Unit,
    viewModel: AppDetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(appId) {
        viewModel.loadApp(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandedTopBarTitle(text = stringResource(R.string.title_app_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState.appState) {
                is LoadState.Idle,
                is LoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is LoadState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadApp(appId) }
                    )
                }

                is LoadState.Success -> {
                    AppDetailsContent(
                        app = state.data,
                        snackbarHostState = snackbarHostState,
                        onOpenScreenshots = onOpenScreenshots
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun InstallSection(
    appId: String,
    appName: String,
    apkUrl: String?,
    packageName: String?,
    snackbarHostState: SnackbarHostState,
    viewModel: InstallViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(appId) {
        viewModel.observe(appId)
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedPackage by produceState<String?>(
        initialValue = null,
        appName,
        packageName,
        resumeTick
    ) {
        value = withContext(Dispatchers.IO) {
            resolveInstalledPackageName(context.packageManager, appName, packageName)
        }
    }
    val installedByPackage = installedPackage != null
    LaunchedEffect(appId, installedPackage, state) {
        if (installedPackage != null &&
            state !is InstallState.Downloading &&
            state !is InstallState.Installing &&
            state !is InstallState.Installed
        ) {
            InstallManager.updateState(appId, InstallState.Installed)
        } else if (installedPackage == null &&
            (state is InstallState.Installed || state is InstallState.Uninstalling)
        ) {
            InstallManager.updateState(appId, InstallState.NotInstalled)
        }
    }

    val showProgress = state is InstallState.Downloading

    val baseEnabled = when (state) {
        is InstallState.NotInstalled,
        is InstallState.Error,
        is InstallState.Installed -> true
        else -> false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        val isInstalled = installedPackage != null
        val canInstall = apkUrl != null

        Button(
            onClick = {
                if (isInstalled) {
                    val pkg = installedPackage ?: return@Button
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    } else {
                        val detailsIntent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$pkg")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(detailsIntent)
                    }
                } else {
                    if (!canInstall) return@Button
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        && !context.packageManager.canRequestPackageInstalls()
                    ) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        ApkDownloadService.start(context, appId, apkUrl!!, packageName)
                    }
                }
            },
            enabled = if (isInstalled) true else (baseEnabled && canInstall),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (showProgress) {
                val progress = (state as InstallState.Downloading).progress
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
                Text(text = "$progress%")
            } else {
                Text(
                    text = if (isInstalled) "Открыть" else stringResource(R.string.details_download)
                )
            }
        }

        val canUninstall = installedPackage != null &&
            (installedByPackage || state is InstallState.Installed || state is InstallState.Uninstalling)
        OutlinedButton(
            onClick = {
                val pkg = installedPackage ?: return@OutlinedButton
                ApkDownloadService.uninstall(context, appId, pkg)
            },
            enabled = canUninstall && state !is InstallState.Downloading && state !is InstallState.Installing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text(text = stringResource(R.string.details_uninstall))
        }

        if (state is InstallState.Error) {
            val message = (state as InstallState.Error).message
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }
}


@Composable
private fun AppDetailsContent(
    app: AppInfo,
    snackbarHostState: SnackbarHostState,
    onOpenScreenshots: (Int) -> Unit
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = null,
                placeholder = painterResource(R.drawable.ic_image_placeholder),
                error = painterResource(R.drawable.ic_image_placeholder),
                fallback = painterResource(R.drawable.ic_image_placeholder),
                modifier = Modifier
                    .size(72.dp)
                    .padding(end = 16.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.details_developer, app.developer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.details_category, app.category.displayName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = stringResource(R.string.details_age_rating, app.ageRating.label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        InstallSection(
            appId = app.id,
            appName = app.name,
            apkUrl = app.apkUrl,
            packageName = app.packageName,
            snackbarHostState = snackbarHostState
        )

        Text(
            text = stringResource(R.string.details_description),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        if (app.fullDescription.isBlank()) {
            EmptyState(message = "Описание недоступно")
        } else {
            Text(
                text = app.fullDescription,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (app.screenshots.isNotEmpty()) {
            Text(
                text = stringResource(R.string.title_screenshots),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(app.screenshots) { index, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.ic_image_placeholder),
                        error = painterResource(R.drawable.ic_image_placeholder),
                        fallback = painterResource(R.drawable.ic_image_placeholder),
                        modifier = Modifier
                            .height(160.dp)
                            .fillMaxWidth()
                            .clickable { onOpenScreenshots(index) }
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.title_screenshots),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            AsyncImage(
                model = R.drawable.ic_image_placeholder,
                contentDescription = null,
                modifier = Modifier
                    .height(160.dp)
                    .fillMaxWidth()
            )
        }
    }
}

private fun resolveInstalledPackageName(
    packageManager: PackageManager,
    appName: String,
    expectedPackageName: String?
): String? {
    if (!expectedPackageName.isNullOrBlank()) {
        val byExpected = runCatching {
            packageManager.getPackageInfo(expectedPackageName, 0)
            expectedPackageName
        }.getOrNull()
        if (byExpected != null) return byExpected
    }

    val normalizedAppName = appName.normalizeForCompare()
    if (normalizedAppName.isBlank()) return null

    val installed = runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
    for (app in installed) {
        val label = runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault("")
        val normalizedLabel = label.normalizeForCompare()
        if (normalizedLabel == normalizedAppName) {
            return app.packageName
        }
    }
    return null
}

private fun String.normalizeForCompare(): String =
    lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
