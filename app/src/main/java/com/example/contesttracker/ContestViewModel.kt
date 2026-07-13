package com.example.contesttracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ContestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContestRepository = ContestRepository()
    private val scheduler = NotificationScheduler(application)

    private val _contests = MutableLiveData<List<ContestModel>>(emptyList())
    val contests: LiveData<List<ContestModel>> = _contests

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * True when the UI is displaying cached (potentially stale) data because
     * the most recent network fetch failed. The UI should show a clear banner
     * so the user knows the schedule may not reflect the latest contests.
     * False whenever fresh data is being displayed.
     */
    private val _isUsingCachedData = MutableLiveData(false)
    val isUsingCachedData: LiveData<Boolean> = _isUsingCachedData

    fun loadContests() {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            repository.fetchUpcomingContests()
                .onSuccess { upcoming ->
                    _contests.value = upcoming
                    _isUsingCachedData.value = false
                    scheduler.scheduleAll(upcoming)
                    if (upcoming.isEmpty()) {
                        _errorMessage.value = "No upcoming contests found for the selected platforms."
                    }
                }
                .onFailure { exception ->
                    exception.printStackTrace()

                    // ── Cache fallback ────────────────────────────────────────
                    // Do NOT wipe the current UI state on a network error.
                    // Try the on-disk cache first; only fall back to empty if
                    // the cache is also empty (e.g. first-ever launch offline).
                    val cached = scheduler.getCachedContests()
                    if (cached.isNotEmpty()) {
                        _contests.value = cached
                        _isUsingCachedData.value = true
                    } else {
                        // Genuinely no data available — leave list empty.
                        _contests.value = emptyList()
                        _isUsingCachedData.value = false
                    }
                    // ─────────────────────────────────────────────────────────

                    val msg = when {
                        exception is java.net.UnknownHostException ||
                        exception is java.net.ConnectException ||
                        exception is java.net.SocketTimeoutException ||
                        exception is java.io.IOException -> {
                            "Unable to connect. Please check your internet connection and try again."
                        }
                        exception is HttpException -> {
                            when (exception.code()) {
                                403  -> "Contest data is temporarily unavailable. Please try again later."
                                404  -> "Contest feed is not available yet. Please try again later."
                                in 500..599 -> "Contest service is currently down. Please try again later."
                                else -> "Could not retrieve contests (Error ${exception.code()}). Please try again."
                            }
                        }
                        else -> "Something went wrong. Please check your connection and try again."
                    }
                    _errorMessage.value = msg
                }

            _isLoading.value = false
        }
    }
}
