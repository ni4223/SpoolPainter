package com.spoolpainter.app.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.spoolpainter.app.domain.models.OpenSpoolData
import com.spoolpainter.app.domain.models.FilamentSpool
import com.spoolpainter.app.data.remote.spoolman.SpoolmanService

class MainViewModel : ViewModel() {
    
    // UI State
    var readData by mutableStateOf<OpenSpoolData?>(null)
        private set
    var currentSpoolId by mutableStateOf<String?>(null)
        private set
    var dataVersion by mutableStateOf(0)
        private set
    var snackbarMessage by mutableStateOf("")
        private set
    var showSnackbar by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
        private set
    
    // Spoolman State
    var spoolmanUrl by mutableStateOf("")
        private set
    var spools by mutableStateOf<List<FilamentSpool>>(emptyList())
        private set
    var selectedSpool by mutableStateOf<FilamentSpool?>(null)
        private set
    var isLoadingSpools by mutableStateOf(false)
        private set
    var spoolmanSortBy by mutableStateOf("")
        private set

    fun loadSpoolmanUrl(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        spoolmanUrl = prefs.getString(SPOOLMAN_URL_KEY, DEFAULT_URL) ?: DEFAULT_URL
        spoolmanSortBy = prefs.getString(SPOOLMAN_SORT_KEY, "") ?: ""
        
        if (isValidSpoolmanUrl(spoolmanUrl)) {
            loadSpoolmanFilaments()
        }
    }

    fun handleNfcTagDetected(data: String?) {
        Log.d("MainViewModel", "handleNfcTagDetected called with data: $data")
        data?.let { 
            Log.d("MainViewModel", "Parsing JSON data: $it")
            OpenSpoolData.fromJson(it)?.let { openSpoolData ->
                Log.d("MainViewModel", "Parsed OpenSpoolData: $openSpoolData")
                readData = openSpoolData
                currentSpoolId = openSpoolData.spoolId
                Log.d("MainViewModel", "Set currentSpoolId to: $currentSpoolId")
                
                // Clear selected spool if currentSpoolId is null
                if (currentSpoolId == null) {
                    selectedSpool = null
                    Log.d("MainViewModel", "Cleared selectedSpool because currentSpoolId is null")
                }
                
                dataVersion++
                Log.d("MainViewModel", "Updated readData and dataVersion to: $dataVersion")
            } ?: Log.e("MainViewModel", "Failed to parse OpenSpoolData from JSON")
        } ?: Log.w("MainViewModel", "No data provided to handleNfcTagDetected")
    }

    fun showSnackbarMessage(message: String) {
        snackbarMessage = message
        showSnackbar = true
    }

    fun dismissSnackbar() {
        showSnackbar = false
    }

    fun showSettings() {
        showSettings = true
    }

    fun hideSettings() {
        showSettings = false
    }

    fun handleSettingsSave(context: Context, newUrl: String, newSort: String) {
        spoolmanUrl = newUrl
        spoolmanSortBy = newSort
        saveSpoolmanUrl(context, newUrl)
        saveSpoolmanSort(context, newSort)
        
        if (isValidSpoolmanUrl(newUrl)) {
            loadSpoolmanFilaments()
        }
        
        showSettings = false
    }

    fun handleFilamentSelection(filament: FilamentSpool?) {
        selectedSpool = filament
        if (filament != null) {
            val existingSubtype = readData?.subtype
            val openSpoolData = OpenSpoolData.toOpenSpoolData(filament)
            readData = if (existingSubtype != null && existingSubtype != "Basic" && openSpoolData.subtype == "Basic") {
                openSpoolData.copy(subtype = existingSubtype)
            } else {
                openSpoolData
            }
        } else {
            readData = null
        }
        dataVersion++
    }

    fun refreshSpools() {
        if (isValidSpoolmanUrl(spoolmanUrl)) {
            loadSpoolmanFilaments()
        }
    }

    private fun saveSpoolmanUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SPOOLMAN_URL_KEY, url).apply()
    }
    
    private fun saveSpoolmanSort(context: Context, sort: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SPOOLMAN_SORT_KEY, sort).apply()
    }

    private fun isValidSpoolmanUrl(url: String): Boolean {
        return url != DEFAULT_URL && url.isNotEmpty()
    }

//    private fun createOpenSpoolDataFromFilament(filament: FilamentSpool): OpenSpoolData {
//        return OpenSpoolData.toOpenSpoolData(filament)
//    }

    private fun loadSpoolmanFilaments() {
        isLoadingSpools = true
        viewModelScope.launch {
            try {
                val service = SpoolmanService(spoolmanUrl)
                spools = service.getFilaments(spoolmanSortBy.ifEmpty { null }, forceRefresh = true)
            } catch (e: Exception) {
                spools = emptyList()
            } finally {
                isLoadingSpools = false
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "spoolpainter_prefs"
        private const val SPOOLMAN_URL_KEY = "spoolman_url"
        private const val SPOOLMAN_SORT_KEY = "spoolman_sort"
        private const val DEFAULT_URL = "http://192.168.1."
    }
}
