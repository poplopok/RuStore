package com.example.rustorecatalog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rustorecatalog.R
import com.example.rustorecatalog.model.AppCategory
import com.example.rustorecatalog.model.AppInfo
import com.example.rustorecatalog.ui.components.BrandedTopBarTitle
import com.example.rustorecatalog.ui.components.EmptyState
import com.example.rustorecatalog.ui.components.ErrorState
import com.example.rustorecatalog.ui.state.LoadState
import com.example.rustorecatalog.viewmodel.AppStoreViewModel

private const val INITIAL_APPS_COUNT = 20
private const val APPS_LOAD_STEP = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStoreScreen(
    selectedCategory: AppCategory?,
    onOpenCategories: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: AppStoreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedCategory) {
        viewModel.loadApps(selectedCategory)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { BrandedTopBarTitle(text = "Каталог приложений") },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
            }
        )

        CategoryHeader(
            selectedCategory = selectedCategory,
            onOpenCategories = onOpenCategories
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                scope.launch { listState.scrollToItem(0) }
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = uiState.appsState) {
                is LoadState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is LoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is LoadState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadApps(selectedCategory) }
                    )
                }

                is LoadState.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyState()
                    } else {
                        AppsList(apps = state.data, onOpenDetails = onOpenDetails, listState = listState)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    selectedCategory: AppCategory?,
    onOpenCategories: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenCategories),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = selectedCategory?.displayName ?: "Все приложения",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Выбрать категорию",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun AppsList(
    apps: List<AppInfo>,
    onOpenDetails: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    var visibleCount by remember(apps) {
        mutableStateOf(minOf(INITIAL_APPS_COUNT, apps.size))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps.take(visibleCount), key = { it.id }) { app ->
            AppListItem(app = app, onClick = { onOpenDetails(app.id) })
        }
        if (visibleCount < apps.size) {
            item(key = "load_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = {
                        visibleCount = (visibleCount + APPS_LOAD_STEP).coerceAtMost(apps.size)
                    }) {
                        Text(text = "Показать еще")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = null,
                placeholder = androidx.compose.ui.res.painterResource(R.drawable.ic_image_placeholder),
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_image_placeholder),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_image_placeholder),
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.shortDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.category.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
