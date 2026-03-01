package com.example.rustorecatalog.data

import com.example.rustorecatalog.model.AppCategory
import com.example.rustorecatalog.model.AppInfo
import com.example.rustorecatalog.network.toDomain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AppRepository {
    suspend fun getAllApps(): Result<List<AppInfo>>
    suspend fun getAppsByCategory(category: AppCategory?): Result<List<AppInfo>>
    suspend fun searchApps(query: String): Result<List<AppInfo>>
    suspend fun getPopularApps(): Result<List<AppInfo>>
    suspend fun getAppById(id: String): Result<AppInfo>
}

class NetworkAppRepository(
    private val api: com.example.rustorecatalog.network.BackendApi
) : AppRepository {
    private val cacheMutex = Mutex()
    private var cachedApps: List<AppInfo>? = null

    private suspend fun fetchAllAppsCached(forceRefresh: Boolean = false): List<AppInfo> {
        return cacheMutex.withLock {
            if (!forceRefresh) {
                cachedApps?.let { return it }
            }
            val loaded = api.getApps().map { it.toDomain() }
            cachedApps = loaded
            loaded
        }
    }

    override suspend fun getAllApps(): Result<List<AppInfo>> = runCatching {
        fetchAllAppsCached()
    }

    override suspend fun getAppsByCategory(category: AppCategory?): Result<List<AppInfo>> =
        runCatching {
            val all = fetchAllAppsCached()
            if (category == null) all else all.filter { it.category == category }
        }

    override suspend fun searchApps(query: String): Result<List<AppInfo>> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) emptyList()
        else fetchAllAppsCached()
            .filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    override suspend fun getPopularApps(): Result<List<AppInfo>> = runCatching {
        val all = fetchAllAppsCached()
        all.filter { it.isPopular }.ifEmpty { all.take(5) }
    }

    override suspend fun getAppById(id: String): Result<AppInfo> = runCatching {
        api.getApp(id).toDomain()
    }
}
