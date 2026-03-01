package com.example.rustorecatalog.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.InetAddress

object BackendModule {
    const val BASE_URL = "https://ct195932.tw1.ru"
    private const val STATIC_HOST = "ct195932.tw1.ru"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    val sharedHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (hostname.equals(STATIC_HOST, ignoreCase = true)) {
                        return listOf(
                            InetAddress.getByAddress(
                                hostname,
                                byteArrayOf(87.toByte(), 249.toByte(), 38.toByte(), 179.toByte())
                            )
                        )
                    }
                    return Dns.SYSTEM.lookup(hostname)
                }
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    val api: BackendApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(sharedHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BackendApi::class.java)
    }
}
