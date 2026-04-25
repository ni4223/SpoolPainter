package com.spoolpainter.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.spoolpainter.app.ui.components.SpoolPainterLogo
import com.spoolpainter.app.ui.components.SpoolmanFilamentDropdown
import com.spoolpainter.app.ui.components.FilamentForm
import com.spoolpainter.app.ui.components.CustomSnackbar
import com.spoolpainter.app.data.local.MaterialDatabase
import com.spoolpainter.app.ui.components.TemperatureControl
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextAlign
import com.spoolpainter.app.domain.models.OpenSpoolData
import com.spoolpainter.app.domain.models.FilamentSpool

@Composable
fun SpoolPainterScreen(
    onWriteTag: (String) -> Unit,
    onReadTag: () -> Unit,
    readData: OpenSpoolData? = null,
    dataVersion: Int = 0, // Force updates when this changes
    snackbarMessage: String = "",
    showSnackbar: Boolean = false,
    onSnackbarDismiss: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    spools: List<FilamentSpool> = emptyList(),
    selectedSpool: FilamentSpool? = null,
    isLoadingSpools: Boolean = false,
    onSpoolSelected: (FilamentSpool?) -> Unit = {},
    onRefreshSpools: () -> Unit = {},
    spoolmanUrl: String = "",
    currentSpoolId: String? = null
) {
    val defaultMaterial = MaterialDatabase.getMaterial("PLA")!!
    var filamentType by remember { mutableStateOf("PLA") }
    var customMaterial by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf<String?>("FFFFFF") }
    var brand by remember { mutableStateOf("Generic") }
    var customBrand by remember { mutableStateOf("") }
    var minTemp by remember { mutableStateOf(defaultMaterial.defaultMinTemp.toString()) }
    var maxTemp by remember { mutableStateOf(defaultMaterial.defaultMaxTemp.toString()) }
    var bedMinTemp by remember { mutableStateOf(defaultMaterial.defaultBedMinTemp.toString()) }
    var bedMaxTemp by remember { mutableStateOf(defaultMaterial.defaultBedMaxTemp.toString()) }

    // Update UI when readData changes
    LaunchedEffect(readData, dataVersion) {
        Log.d("SpoolPainterScreen", "LaunchedEffect triggered - readData: $readData, dataVersion: $dataVersion")
        readData?.let { data ->
            Log.d("SpoolPainterScreen", "Processing readData: $data")
            val spool = FilamentSpool.fromOpenSpool(data)
            Log.d("SpoolPainterScreen", "Converted spool: $spool")
            filamentType = spool.material
            variant = spool.variant
            colorHex = spool.colorHex
            brand = spool.brand
            minTemp = spool.minTemp?.toString() ?: minTemp
            maxTemp = spool.maxTemp?.toString() ?: maxTemp
            bedMinTemp = spool.bedMinTemp?.toString() ?: bedMinTemp
            bedMaxTemp = spool.bedMaxTemp?.toString() ?: bedMaxTemp
            Log.d("SpoolPainterScreen", "Updated UI - filamentType: $filamentType, brand: $brand, colorHex: $colorHex")
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Logo with settings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp)) // Balance the layout
                
                SpoolPainterLogo(
                    color = colorHex?.let { Color(android.graphics.Color.parseColor("#$it")) }
                        ?: MaterialTheme.colorScheme.outline
                )
                
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .offset(y = (-60).dp)
                        .offset(x = (20).dp)

                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            @OptIn(ExperimentalMaterial3Api::class)
            PullToRefreshBox(
                isRefreshing = isLoadingSpools,
                onRefresh = onRefreshSpools,
                indicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .absolutePadding(5.dp,15.dp,5.dp,5.dp,),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Spoolman section
                    if (spools.isNotEmpty()) {
                        SpoolmanFilamentDropdown(
                            filaments = spools,
                            selectedFilament = selectedSpool,
                            onFilamentSelected = onSpoolSelected,
                            spoolmanUrl = spoolmanUrl,
                            currentSpoolId = currentSpoolId,
                            isLoading = isLoadingSpools,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (isLoadingSpools) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading Spoolman filaments...")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "💡 Connect Spoolman server in settings for easy filament selection",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    FilamentForm(
                        filamentType = filamentType,
                        customMaterial = customMaterial,
                        variant = variant,
                        colorHex = colorHex,
                        brand = brand,
                        customBrand = customBrand,
                        onFilamentTypeChange = { material, min, max, bedMin, bedMax ->
                            filamentType = material
                            minTemp = min
                            maxTemp = max
                            bedMinTemp = bedMin
                            bedMaxTemp = bedMax
                        },
                        onCustomMaterialChange = { customMaterial = it },
                        onVariantChange = { variant = it },
                        onColorChange = { colorHex = it },
                        onBrandChange = { brand = it },
                        onCustomBrandChange = { customBrand = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Temperature section - reusing TemperatureCard content
                    key(filamentType) {
                        TemperatureSection(
                            nozzleMin = minTemp,
                            nozzleMax = maxTemp,
                            bedMin = bedMinTemp,
                            bedMax = bedMaxTemp,
                            onNozzleMinChange = { minTemp = it },
                            onNozzleMaxChange = { maxTemp = it },
                            onBedMinChange = { bedMinTemp = it },
                            onBedMaxChange = { bedMaxTemp = it }
                        )
                    }
                }
            }

            WriteTagButton(
                onClick = {
                    val materialName = when {
                        filamentType == "Other" && customMaterial.isNotEmpty() -> customMaterial
                        else -> filamentType
                    }
                    val finalBrandName =
                        if (brand == "Other" && customBrand.isNotEmpty()) customBrand else brand
                    val finalSubtype = variant.ifEmpty { "Basic" }

                    val data = OpenSpoolData(
                        type = materialName,
                        colorHex = colorHex,
                        brand = finalBrandName,
                        minTemp = minTemp,
                        maxTemp = maxTemp,
                        bedMinTemp = bedMinTemp,
                        bedMaxTemp = bedMaxTemp,
                        subtype = finalSubtype,
                        spoolId = selectedSpool?.id?.toString()
                    ).toJson()
                    onWriteTag(data)
                }
            )

            ReadTagButton(
                onClick = { onReadTag() }
            )

            Text(
                text = "OpenSpool tag format",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            InstructionText()
            }
            }
        }
        // Custom snackbar popup
        CustomSnackbar(
            message = snackbarMessage,
            isVisible = showSnackbar,
            onDismiss = onSnackbarDismiss
        )
    }
}

@Composable
private fun ReadTagButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = "Read NFC Tag",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WriteTagButton(enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp),

        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = "Write to NFC",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun InstructionText() {
    Text(
        text = "• Configure your filament settings above\n• Tap 'Write to NFC'\n• Use 'Read NFC Tag' to load existing settings",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Preview(showBackground = true)
@Composable
fun SpoolPainterScreenPreview() {
    MaterialTheme {
        SpoolPainterScreen(
            onWriteTag = { },
            onReadTag = { },
            dataVersion = 0
        )
    }
}

@Composable
fun TemperatureSection(
    nozzleMin: String,
    nozzleMax: String,
    bedMin: String,
    bedMax: String,
    onNozzleMinChange: (String) -> Unit,
    onNozzleMaxChange: (String) -> Unit,
    onBedMinChange: (String) -> Unit,
    onBedMaxChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Temperature",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        // Nozzle Temperature
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nozzle",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )

            TemperatureControl(
                value = nozzleMin,
                onValueChange = onNozzleMinChange,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            TemperatureControl(
                value = nozzleMax,
                onValueChange = onNozzleMaxChange,
                modifier = Modifier.weight(1f)
            )
        }

        // Bed Temperature
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bed",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )

            TemperatureControl(
                value = bedMin,
                onValueChange = onBedMinChange,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            TemperatureControl(
                value = bedMax,
                onValueChange = onBedMaxChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
