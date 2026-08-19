package com.example.data.api

import com.example.data.model.CommitResultResponse
import com.example.data.model.CreateOrUpdateFilePayload
import com.example.data.model.DeleteFilePayload
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubRepository
import com.example.data.model.GitHubUser
import com.example.data.model.GitTreeResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {

    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") authHeader: String
    ): Response<GitHubUser>

    @GET("user/repos")
    suspend fun getUserRepositories(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member"
    ): Response<List<GitHubRepository>>

    @GET("users/{username}/repos")
    suspend fun getPublicRepositories(
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated"
    ): Response<List<GitHubRepository>>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100
    ): Response<List<GitHubBranch>>

    @GET("repos/{owner}/{repo}/git/trees/{tree_sha}")
    suspend fun getGitTreeRecursive(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tree_sha") treeSha: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GitTreeResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<FileContentResponse>

    @GET("repos/{owner}/{repo}/raw/{branch}/{path}")
    suspend fun getRawFile(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Path(value = "path", encoded = true) path: String
    ): Response<ResponseBody>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body payload: CreateOrUpdateFilePayload
    ): Response<CommitResultResponse>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body payload: DeleteFilePayload
    ): Response<CommitResultResponse>
}
