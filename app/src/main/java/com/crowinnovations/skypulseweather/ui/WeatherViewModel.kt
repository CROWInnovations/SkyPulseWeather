package com.crowinnovations.skypulseweather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowinnovations.skypulseweather.data.local.AppPreferences
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.data.repository.SyncResult
import com.crowinnovations.skypulseweather.data.repository.WeatherRepository
import com.crowinnovations.skypulseweather.domain.DateDisplayPosition
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.LocationSearchCandidate
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.ThemeMode
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.WindUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransientUiState(
    val isRefreshing: Boolean = false,
    val offlineMessage: String? = null,
    val showLocationsSheet: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LocationSearchCandidate> = emptyList(),
    val isSearching: Boolean = false,
    val isLocatingGps: Boolean = false,
    val searchError: String? = null
)

data class WeatherUiState(
    val locations: List<SavedLocation> = emptyList(),
    val weatherByLocation: Map<Long, WeatherSnapshot> = emptyMap(),
    val preferences: AppPreferences = AppPreferences(),
    val activeLocationId: Long = -1L,
    val isRefreshing: Boolean = false,
    val offlineMessage: String? = null,
    val showLocationsSheet: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LocationSearchCandidate> = emptyList(),
    val isSearching: Boolean = false,
    val isLocatingGps: Boolean = false,
    val searchError: String? = null
) {
    val activeLocation: SavedLocation?
        get() = locations.firstOrNull { it.id == activeLocationId } ?: locations.firstOrNull()

    val activeSnapshot: WeatherSnapshot?
        get() = activeLocation?.let { weatherByLocation[it.id] }
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val transientState = MutableStateFlow(TransientUiState())
    private var debounceSearchJob: Job? = null

    val uiState: StateFlow<WeatherUiState> = combine(
        repository.locationsFlow,
        repository.weatherMapFlow,
        prefsRepository.preferencesFlow,
        transientState
    ) { locations, weatherMap, prefs, transient ->
        val resolvedActiveId = locations.firstOrNull { it.id == prefs.activeLocationId }?.id
            ?: locations.firstOrNull()?.id
            ?: -1L

        WeatherUiState(
            locations = locations,
            weatherByLocation = weatherMap,
            preferences = prefs,
            activeLocationId = resolvedActiveId,
            isRefreshing = transient.isRefreshing,
            offlineMessage = transient.offlineMessage,
            showLocationsSheet = transient.showLocationsSheet,
            showSettingsDialog = transient.showSettingsDialog,
            searchQuery = transient.searchQuery,
            searchResults = transient.searchResults,
            isSearching = transient.isSearching,
            isLocatingGps = transient.isLocatingGps,
            searchError = transient.searchError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeatherUiState(isRefreshing = true)
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialSeedIfNeeded()
            refreshWeather(force = false, updateGps = true)
        }
    }

    fun refreshWeather(force: Boolean = true, updateGps: Boolean = true) {
        viewModelScope.launch {
            transientState.update { it.copy(isRefreshing = true) }
            val result = repository.refreshAllLocations(
                force = force,
                updateGpsIfPermitted = updateGps
            )
            transientState.update {
                it.copy(
                    isRefreshing = false,
                    offlineMessage = when (result) {
                        is SyncResult.OfflineFallback -> "Offline · Showing cached forecast"
                        else -> null
                    }
                )
            }
        }
    }

    fun selectLocation(locationId: Long) {
        if (locationId <= 0L) return
        transientState.update { it.copy(showLocationsSheet = false) }
        repository.selectActiveLocation(locationId)
    }

    fun openLocationsSheet() {
        transientState.update {
            it.copy(showLocationsSheet = true, searchError = null)
        }
    }

    fun closeLocationsSheet() {
        transientState.update {
            it.copy(
                showLocationsSheet = false,
                searchError = null
            )
        }
    }

    fun openSettingsDialog() {
        transientState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettingsDialog() {
        transientState.update { it.copy(showSettingsDialog = false) }
    }

    fun onSearchQueryChange(newQuery: String) {
        val sanitized = InputSecurityValidator.sanitizeSearchInput(newQuery)
        transientState.update { it.copy(searchQuery = sanitized, searchError = null) }
        debounceSearchJob?.cancel()
        val trimmed = sanitized.trim()
        if (trimmed.length < 2) {
            transientState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        debounceSearchJob = viewModelScope.launch {
            delay(350L)
            executeSearch()
        }
    }

    fun executeSearch() {
        val raw = transientState.value.searchQuery.trim()
        if (raw.isBlank()) return
        val validated = InputSecurityValidator.validateLocationSearchQuery(raw)
        if (validated == null) {
            transientState.update {
                it.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    searchError = "Enter at least 2 valid letters or digits (e.g. 21201 or Baltimore)."
                )
            }
            return
        }
        debounceSearchJob?.cancel()
        viewModelScope.launch {
            transientState.update { it.copy(isSearching = true, searchError = null) }
            val result = repository.searchLocations(validated)
            result.fold(
                onSuccess = { list ->
                    transientState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = list,
                            searchError = if (list.isEmpty()) {
                                "No matching ZIP code or city found for \"$validated\"."
                            } else null
                        )
                    }
                },
                onFailure = { err ->
                    val msg = if (err is IllegalArgumentException) {
                        err.message ?: "Invalid search input."
                    } else {
                        "Search unavailable offline (check network connection)."
                    }
                    transientState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = emptyList(),
                            searchError = msg
                        )
                    }
                }
            )
        }
    }

    fun addCandidateLocation(candidate: LocationSearchCandidate) {
        viewModelScope.launch {
            transientState.update { it.copy(isRefreshing = true, searchError = null) }
            repository.addManualLocation(candidate)
            transientState.update {
                it.copy(
                    isRefreshing = false,
                    showLocationsSheet = false,
                    searchQuery = "",
                    searchResults = emptyList()
                )
            }
        }
    }

    fun enableOrRefreshGpsLocation() {
        viewModelScope.launch {
            transientState.update { it.copy(isLocatingGps = true, searchError = null) }
            val res = repository.upsertAndRefreshGpsLocation()
            res.fold(
                onSuccess = {
                    transientState.update {
                        it.copy(
                            isLocatingGps = false,
                            showLocationsSheet = false,
                            offlineMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    transientState.update {
                        it.copy(
                            isLocatingGps = false,
                            searchError = err.message ?: "Unable to determine GPS location."
                        )
                    }
                }
            )
        }
    }

    fun onLocationPermissionDenied() {
        transientState.update {
            it.copy(
                isLocatingGps = false,
                searchError = "Location permission denied. Enter a 5-digit ZIP code or city name instead."
            )
        }
    }

    fun deleteLocation(locationId: Long) {
        viewModelScope.launch {
            repository.deleteLocation(locationId)
        }
    }

    fun setTempUnit(unit: TempUnit) {
        viewModelScope.launch {
            prefsRepository.setTempUnit(unit)
            repository.notifyWidgetsChanged()
        }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch {
            prefsRepository.setWindUnit(unit)
            repository.notifyWidgetsChanged()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepository.setThemeMode(mode)
            repository.notifyWidgetsChanged()
        }
    }

    fun setShowTimeDisplay(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setShowTimeDisplay(enabled)
            repository.notifyWidgetsChanged()
        }
    }

    fun setDateDisplayPosition(position: DateDisplayPosition) {
        viewModelScope.launch {
            prefsRepository.setDateDisplayPosition(position)
            repository.notifyWidgetsChanged()
        }
    }

    fun resetWidgetSliderToStart() {
        viewModelScope.launch {
            repository.notifyWidgetsChanged()
        }
    }

    fun adjustTimeFontSize(deltaSp: Int) {
        viewModelScope.launch {
            prefsRepository.adjustTimeFontSize(deltaSp)
            repository.notifyWidgetsChanged()
        }
    }

    fun setTimeFontStyle(style: DisplayFontStyle) {
        viewModelScope.launch {
            prefsRepository.setTimeFontStyle(style)
            repository.notifyWidgetsChanged()
        }
    }

    fun setTimeTextColor(color: DisplayTextColor) {
        viewModelScope.launch {
            prefsRepository.setTimeTextColor(color)
            repository.notifyWidgetsChanged()
        }
    }

    fun adjustWidgetTempFontSize(deltaSp: Int) {
        viewModelScope.launch {
            prefsRepository.adjustWidgetTempFontSize(deltaSp)
            repository.notifyWidgetsChanged()
        }
    }

    fun setWidgetTempFontStyle(style: DisplayFontStyle) {
        viewModelScope.launch {
            prefsRepository.setWidgetTempFontStyle(style)
            repository.notifyWidgetsChanged()
        }
    }

    fun setWidgetTempTextColor(color: DisplayTextColor) {
        viewModelScope.launch {
            prefsRepository.setWidgetTempTextColor(color)
            repository.notifyWidgetsChanged()
        }
    }

    fun setGatewayBaseUrl(url: String) {
        viewModelScope.launch {
            prefsRepository.setGatewayBaseUrl(url)
            refreshWeather(force = true, updateGps = false)
        }
    }
}
