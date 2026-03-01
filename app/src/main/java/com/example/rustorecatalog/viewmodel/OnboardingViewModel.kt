package com.example.rustorecatalog.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DATASTORE_NAME = "rustore_prefs"
private val Application.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class OnboardingViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val hasSeenOnboardingKey = booleanPreferencesKey("has_seen_onboarding")

    private val _hasSeenOnboarding = MutableStateFlow<Boolean?>(null)
    val hasSeenOnboarding: StateFlow<Boolean?> = _hasSeenOnboarding

    fun loadOnboardingState() {
        if (_hasSeenOnboarding.value != null) return

        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            _hasSeenOnboarding.value = prefs[hasSeenOnboardingKey] ?: false
        }
    }

    fun setOnboardingSeen() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[hasSeenOnboardingKey] = true
            }
            _hasSeenOnboarding.value = true
        }
    }
}

