package com.example.rustorecatalog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rustorecatalog.model.AppCategory
import com.example.rustorecatalog.ui.components.BrandedTopBarTitle
import com.example.rustorecatalog.ui.components.EmptyState
import com.example.rustorecatalog.ui.components.ErrorState
import com.example.rustorecatalog.ui.state.LoadState
import com.example.rustorecatalog.viewmodel.CategoriesViewModel
import com.example.rustorecatalog.viewmodel.CategoryInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onCategorySelected: (AppCategory?) -> Unit,
    viewModel: CategoriesViewModel = viewModel()
) {
    val state by viewModel.categoriesState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCategories()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { BrandedTopBarTitle(text = "Категории") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = state is LoadState.Loading,
            onRefresh = { viewModel.loadCategories() },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val s = state) {
                is LoadState.Idle,
                is LoadState.Loading -> {
                }

                is LoadState.Error -> {
                    ErrorState(
                        message = s.message,
                        onRetry = { viewModel.loadCategories() }
                    )
                }

                is LoadState.Success -> {
                    if (s.data.isEmpty()) {
                        EmptyState(message = "Нет категорий")
                    } else {
                        CategoriesList(
                            categories = s.data,
                            onCategorySelected = onCategorySelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesList(
    categories: List<CategoryInfo>,
    onCategorySelected: (AppCategory?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            CategoryRowCard(
                title = "Все приложения",
                count = null,
                onClick = { onCategorySelected(null) }
            )
        }

        items(categories, key = { it.category.name }) { info ->
            CategoryRowCard(
                title = info.category.displayName,
                count = info.count,
                onClick = { onCategorySelected(info.category) }
            )
        }
    }
}

@Composable
private fun CategoryRowCard(
    title: String,
    count: Int?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
