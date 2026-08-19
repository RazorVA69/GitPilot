package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceCodeRequest(
    @Json(name = "client_id") val clientId: String,
    @Json(name = "scope") val scope: String = "repo,read:org,user,workflow"
)

@JsonClass(generateAdapter = true)
data class DeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_uri") val verificationUri: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "interval") val interval: Long
)

@JsonClass(generateAdapter = true)
data class OAuthTokenRequest(
    @Json(name = "client_id") val clientId: String,
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
)

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "scope") val scope: String?,
    @Json(name = "error") val error: String?,
    @Json(name = "error_description") val errorDescription: String?
)
