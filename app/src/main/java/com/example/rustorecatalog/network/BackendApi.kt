package com.example.rustorecatalog.network

import com.example.rustorecatalog.model.AppInfo
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

@Serializable
data class AppDto(
    val id: String,
    val name: String,
    val developer: String,
    val category: String,
    val shortDescription: String,
    val fullDescription: String,
    val ageRating: String,
    val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val apkUrl: String? = null,
    val packageName: String? = null,
    val isPopular: Boolean = false
)

fun AppDto.toDomain(): AppInfo {
    val fullApkUrl = when {
        apkUrl == null -> null
        apkUrl.startsWith("http") -> apkUrl
        else -> BackendModule.BASE_URL.trimEnd('/') + apkUrl
    }

    return AppInfo(
        id = id,
        name = name,
        developer = developer,
        category = com.example.rustorecatalog.model.AppCategory.valueOf(category),
        shortDescription = shortDescription,
        fullDescription = fullDescription,
        ageRating = com.example.rustorecatalog.model.AgeRating.fromLabel(ageRating),
        iconUrl = iconUrl,
        screenshots = screenshots,
        isPopular = isPopular,
        apkUrl = fullApkUrl,
        packageName = packageName
    )
}

interface BackendApi {

    @Headers("Accept: application/json")
    @GET("/backend/apps.json")
    suspend fun getApps(): List<AppDto>

    @Headers("Accept: application/json")
    @GET("/backend/apps/{id}.json")
    suspend fun getApp(@Path("id") id: String): AppDto
}
