package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object GitHubApiClient {
    private const val API_BASE_URL = "https://api.github.com/"
    private const val GITHUB_BASE_URL = "https://github.com/"

    // Reliable fallback DNS resolver to prevent UnknownHostException in emulators/restricted networks
    private val fallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                val systemAddresses = Dns.SYSTEM.lookup(hostname)
                if (systemAddresses.isNotEmpty()) return systemAddresses
            } catch (e: Exception) {
                // Fall back to secondary resolution
            }

            try {
                val allAddresses = InetAddress.getAllByName(hostname).toList()
                if (allAddresses.isNotEmpty()) return allAddresses
            } catch (e: Exception) {
                // Fallback to GitHub official Anycast IPs
            }

            return when (hostname.lowercase()) {
                "api.github.com" -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 121.toByte(), 6.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 114.toByte(), 6.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 113.toByte(), 6.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 112.toByte(), 6.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(20.toByte(), 201.toByte(), 28.toByte(), 151.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(20.toByte(), 205.toByte(), 243.toByte(), 166.toByte()))
                )
                "github.com" -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 121.toByte(), 4.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 114.toByte(), 4.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 113.toByte(), 4.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(140.toByte(), 82.toByte(), 112.toByte(), 4.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(20.toByte(), 201.toByte(), 28.toByte(), 151.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(20.toByte(), 205.toByte(), 243.toByte(), 166.toByte()))
                )
                else -> Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(fallbackDns)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/vnd.github.v3+json, application/json")
                .header("User-Agent", "GitExplorer-Android-App/1.0")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val apiService: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    val authService: GitHubAuthService by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubAuthService::class.java)
    }

    fun formatAuthHeader(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val cleanToken = token.trim()
        return if (cleanToken.startsWith("Bearer ", ignoreCase = true) || cleanToken.startsWith("token ", ignoreCase = true)) {
            cleanToken
        } else {
            "Bearer $cleanToken"
        }
    }
}
