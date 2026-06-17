package com.example.contesttracker

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ClistApiService {
    @GET("contest/")
    suspend fun getUpcomingContests(
        @Header("Authorization") authorization: String,
        @Query("upcoming") upcoming: Boolean = true,
        @Query("resource_id__in") resourceIds: String = RetrofitClient.SUPPORTED_RESOURCE_IDS,
        @Query("limit") limit: Int = 100
    ): ContestResponse
}

object RetrofitClient {
    const val BASE_URL = "https://clist.by/api/v4/"
    // Official IDs: 1: Codeforces, 102: LeetCode, 2: CodeChef, 93: AtCoder
    const val SUPPORTED_RESOURCE_IDS = "1,102,2,93"

    val apiService: ClistApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ClistApiService::class.java)
    }
}
