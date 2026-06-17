package com.example.contesttracker

class ContestRepository(
    private val apiService: ClistApiService = RetrofitClient.apiService
) {
    suspend fun fetchUpcomingContests(username: String, apiKey: String): Result<List<ContestModel>> {
        return runCatching {
            // Clist v4 strictly requires this exact format in the header
            val authHeader = "ApiKey ${username.trim()}:${apiKey.trim()}"
            
            val response = apiService.getUpcomingContests(
                authorization = authHeader
            )
            response.contests.sortedBy { ContestTimeUtils.startTimeMillis(it.start) ?: Long.MAX_VALUE }
        }
    }
}
