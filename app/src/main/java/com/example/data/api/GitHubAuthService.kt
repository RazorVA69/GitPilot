package com.example.data.api

import com.example.data.model.DeviceCodeRequest
import com.example.data.model.DeviceCodeResponse
import com.example.data.model.OAuthTokenRequest
import com.example.data.model.OAuthTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface GitHubAuthService {

    @POST("login/device/code")
    @Headers("Accept: application/json")
    suspend fun requestDeviceCode(
        @Body payload: DeviceCodeRequest
    ): Response<DeviceCodeResponse>

    @POST("login/oauth/access_token")
    @Headers("Accept: application/json")
    suspend fun pollDeviceToken(
        @Body payload: OAuthTokenRequest
    ): Response<OAuthTokenResponse>
}
