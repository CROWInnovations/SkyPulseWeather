package com.crowinnovations.skypulseweather.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.crowinnovations.skypulseweather.data.local.AppPreferences
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.data.repository.WeatherRepository
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.LocationSearchCandidate
import com.crowinnovations.skypulseweather.sync.WeatherSyncWorker
import com.crowinnovations.skypulseweather.ui.theme.SkyPulseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var repository: WeatherRepository
    @Inject lateinit var prefsRepository: UserPreferencesRepository

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.filterTouchesWhenObscured = true
        enableEdgeToEdge()

        appWidgetId = runCatching {
            intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

        // Default cancel result if user backs out before confirming
        val cancelResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, cancelResult)

        if (appWidgetId <= AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val prefs by prefsRepository.preferencesFlow.collectAsState(initial = AppPreferences())
            val locations by repository.locationsFlow.collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            var selectedId by remember {
                mutableLongStateOf(UserPreferencesRepository.FOLLOW_PRIMARY_LOCATION)
            }
            var searchQuery by remember { mutableStateOf("") }
            var searchResults by remember { mutableStateOf<List<LocationSearchCandidate>>(emptyList()) }
            var searching by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                repository.ensureInitialSeedIfNeeded()
                selectedId = prefsRepository.getPreferencesOnce().activeLocationId
            }

            fun finishWithSelection(chosenLocationId: Long) {
                scope.launch {
                    if (chosenLocationId > 0L) {
                        prefsRepository.setActiveLocationId(chosenLocationId)
                    }
                    prefsRepository.setWidgetLocation(
                        appWidgetId,
                        UserPreferencesRepository.FOLLOW_PRIMARY_LOCATION
                    )
                    WeatherSyncWorker.schedulePeriodicSync(applicationContext)
                    repository.notifyWidgetsChanged()
                    val okIntent = Intent().putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        appWidgetId
                    )
                    setResult(Activity.RESULT_OK, okIntent)
                    finish()
                }
            }

            SkyPulseTheme(themeMode = prefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        // Single-line non-wrapping header bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Choose Widget Location",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel"
                                )
                            }
                        }

                        Text(
                            text = "Select which location this widget displays on your home screen, or add a new ZIP code / city.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick ZIP / City search to add directly from widget picker
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = InputSecurityValidator.sanitizeSearchInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = {
                                Text(
                                    text = "Add ZIP code (e.g. 21201) or city…",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                if (searching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(8.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            val valid = InputSecurityValidator.validateLocationSearchQuery(searchQuery)
                                            if (valid != null) {
                                                scope.launch {
                                                    searching = true
                                                    searchResults = repository
                                                        .searchLocations(valid)
                                                        .getOrDefault(emptyList())
                                                    searching = false
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val valid = InputSecurityValidator.validateLocationSearchQuery(searchQuery)
                                    if (valid != null) {
                                        scope.launch {
                                            searching = true
                                            searchResults = repository
                                                .searchLocations(valid)
                                                .getOrDefault(emptyList())
                                            searching = false
                                        }
                                    }
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (searchResults.isNotEmpty()) {
                                itemsIndexed(searchResults) { idx, cand ->
                                    if (idx > 0) {
                                        HorizontalDivider(thickness = 0.5.dp, color = hairline)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 50.dp)
                                            .clickable {
                                                scope.launch {
                                                    val newId = repository.addManualLocation(cand)
                                                    searchQuery = ""
                                                    searchResults = emptyList()
                                                    finishWithSelection(newId)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddLocationAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cand.displayName,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cand.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                item {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = hairline
                                    )
                                }
                            }

                            // Option 1: Follow Primary / Active App Location
                            item {
                                val isChosen =
                                    selectedId == UserPreferencesRepository.FOLLOW_PRIMARY_LOCATION
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isChosen) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            selectedId =
                                                UserPreferencesRepository.FOLLOW_PRIMARY_LOCATION
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Follow Checked App Location",
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Displays whichever saved or GPS location is checked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isChosen) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = hairline)
                            }

                            // Saved Locations
                            itemsIndexed(locations, key = { _, it -> it.id }) { idx, loc ->
                                if (idx > 0) {
                                    HorizontalDivider(thickness = 0.5.dp, color = hairline)
                                }
                                val isChosen = selectedId == loc.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isChosen) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable { selectedId = loc.id }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (loc.isGps) {
                                            Icons.Default.MyLocation
                                        } else {
                                            Icons.Default.LocationOn
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loc.name,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = loc.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isChosen) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { finishWithSelection(selectedId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Save Widget Location",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
