package com.example.data.api

import com.example.data.model.CommitResultResponse
import com.example.data.model.CompareResponse
import com.example.data.model.CreateOrUpdateFilePayload
import com.example.data.model.CreateRefPayload
import com.example.data.model.DeleteFilePayload
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubCommitItem
import com.example.data.model.GitHubRepository
import com.example.data.model.GitHubTagItem
import com.example.data.model.GitHubUser
import com.example.data.model.GitTreeResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
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

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") sha: String? = null,
        @Query("path") path: String? = null,
        @Query("per_page") perPage: Int = 30
    ): Response<List<GitHubCommitItem>>

    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommitDetail(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String
    ): Response<GitHubCommitItem>

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body payload: CreateRefPayload
    ): Response<Any>

    @DELETE("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun deleteBranchRef(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<ResponseBody>

    @GET("repos/{owner}/{repo}/tags")
    suspend fun getTags(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 50
    ): Response<List<GitHubTagItem>>

    @GET("repos/{owner}/{repo}/compare/{basehead}")
    suspend fun compareBranches(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("basehead") basehead: String
    ): Response<CompareResponse>

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 50
    ): Response<List<com.example.data.model.GitHubPullRequest>>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun getPullRequest(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): Response<com.example.data.model.GitHubPullRequest>

    @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
    suspend fun mergePullRequest(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body payload: com.example.data.model.MergePullRequestPayload
    ): Response<com.example.data.model.GitHubMergeResponse>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 50
    ): Response<List<com.example.data.model.GitHubIssue>>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): Response<List<com.example.data.model.GitHubRelease>>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): Response<com.example.data.model.GitHubWorkflowRunsResponse>
}
