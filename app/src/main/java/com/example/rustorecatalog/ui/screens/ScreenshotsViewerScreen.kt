package com.example.rustorecatalog.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rustorecatalog.R
import com.example.rustorecatalog.ui.components.BrandedTopBarTitle
import com.example.rustorecatalog.ui.components.ErrorState
import com.example.rustorecatalog.ui.state.LoadState
import com.example.rustorecatalog.viewmodel.AppDetailsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsViewerScreen(
    appId: String,
    startIndex: Int,
    onBack: () -> Unit,
    viewModel: AppDetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(appId) {
        viewModel.loadApp(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandedTopBarTitle(text = "Скриншоты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    val screenshots = state.data.screenshots
                    if (screenshots.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Скриншоты недоступны",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        val initialPage = startIndex.coerceIn(0, screenshots.lastIndex)
                        val pagerState = rememberPagerState(
                            initialPage = initialPage,
                            pageCount = { screenshots.size }
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            AsyncImage(
                                model = screenshots[page],
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.ic_image_placeholder),
                                error = painterResource(R.drawable.ic_image_placeholder),
                                fallback = painterResource(R.drawable.ic_image_placeholder),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
