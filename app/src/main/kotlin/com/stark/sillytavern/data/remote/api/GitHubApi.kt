package com.stark.sillytavern.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/Starkka15/PocketTavern/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    @SerialName("html_url")
    val htmlUrl: String,
    val body: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null
)
