package com.example.contesttracker

import android.util.Log

class ContestRepository(
    private val apiService: ClistApiService = RetrofitClient.apiService
) {
    suspend fun fetchUpcomingContests(): Result<List<ContestModel>> {
        return runCatching {
            val response = apiService.getUpcomingContests()

            // Partition contests into known and unknown platforms.
            // Unknown platforms (null) are logged and discarded here so that
            // no consumer (ViewModel, scheduler, adapters) ever sees a contest
            // whose platform would be misidentified.
            val (known, unknown) = response.contests.partition { it.platform != null }

            if (unknown.isNotEmpty()) {
                Log.w(
                    TAG,
                    "Discarded ${unknown.size} contest(s) with unrecognised platform IDs: " +
                    unknown.joinToString { "[id=${it.id} resource_id=${it.resourceId} name='${it.name}']" }
                )
            }

            known.sortedBy { ContestTimeUtils.startTimeMillis(it.start) ?: Long.MAX_VALUE }
        }
    }

    companion object {
        private const val TAG = "ContestRepository"
    }
}
