package com.example.rustorecatalog.model

data class AppInfo(
    val id: String,
    val name: String,
    val developer: String,
    val category: AppCategory,
    val shortDescription: String,
    val fullDescription: String,
    val ageRating: AgeRating,
    val iconUrl: String?,
    val screenshots: List<String>,
    val isPopular: Boolean = false,
    val apkUrl: String? = null,
    val packageName: String? = null
)

