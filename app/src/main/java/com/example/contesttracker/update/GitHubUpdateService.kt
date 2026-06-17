package com.example.contesttracker.update

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

data class GitHubRelease(
    @SerializedName("tag_name")  val tagName: String,
    @SerializedName("name")      val releaseName: String?,
    @SerializedName("body")      val releaseNotes: String?,
    @SerializedName("prerelease")val isPreRelease: Boolean,
    @SerializedName("assets")    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name")                 val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size")                 val size: Long
)

// ---------------------------------------------------------------------------
// Retrofit interface
// ---------------------------------------------------------------------------

interface GitHubApiService {

    /** Returns the latest stable release for the repo. */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo")  repo: String
    ): GitHubRelease

    /** Returns all releases (including pre-releases), newest first. */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getAllReleases(
        @Path("owner") owner: String,
        @Path("repo")  repo: String
    ): List<GitHubRelease>
}

// ---------------------------------------------------------------------------
// Singleton service
// ---------------------------------------------------------------------------

object GitHubUpdateService {
    val api: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(UpdateConfig.GITHUB_API_BASE)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }
}
