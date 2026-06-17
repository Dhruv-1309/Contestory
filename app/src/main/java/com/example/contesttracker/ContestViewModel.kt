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

    fun loadContests(username: String, apiKey: String) {
        if (username.isBlank() || apiKey.isBlank()) {
            _contests.value = emptyList()
            _errorMessage.value = "Add your Clist.by API credentials in Settings to load contests."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            repository.fetchUpcomingContests(username, apiKey)
                .onSuccess { upcoming ->
                    _contests.value = upcoming
                    scheduler.scheduleAll(upcoming)
                    if (upcoming.isEmpty()) {
                        _errorMessage.value = "No upcoming contests found for the selected platforms."
                    }
                }
                .onFailure { exception ->
                    exception.printStackTrace()
                    _contests.value = emptyList()
                    val msg = when {
                        exception is java.net.UnknownHostException || 
                        exception is java.net.ConnectException || 
                        exception is java.net.SocketTimeoutException ||
                        exception is java.io.IOException -> {
                            "Unable to connect. Please check your internet connection and try again."
                        }
                        exception is HttpException -> {
                            when (exception.code()) {
                                401 -> "Invalid API credentials. Please check your CLIST settings."
                                403 -> "Access denied. Please check your CLIST API account permissions."
                                in 500..599 -> "CLIST servers are currently down. Please try again later."
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
