package com.spoolpainter.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.spoolpainter.app.domain.primitives.ColorSampling
import java.util.concurrent.Executors

/**
 * U17 — full-screen camera color sampler. Shows a live CameraX preview with a
 * fixed center reticle; averages the pixels under the reticle (~5 fps) into a
 * live hex readout. "Use this color" returns the current hex; "Cancel"
 * dismisses without changing anything.
 *
 * The sampled color is deliberately approximate — white balance and lighting
 * dominate raw camera pixels — so the returned hex is a starting point the user
 * can tweak in the Color Wheel, not an exact match.
 *
 * Pure sampling math lives in [ColorSampling] (unit-tested); this composable
 * only wires CameraX frames into it. Permission + preview are verified on-device.
 */
@Composable
fun CameraColorSampler(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Capture the system-bar insets from the ACTIVITY's view (this composable
    // runs before the Dialog, so LocalView here is the activity content view).
    // The dialog's own view reports a zero bottom inset, which is what clipped
    // the action bar behind the gesture-nav pill.
    val activityView = LocalView.current
    var topInset by remember { mutableStateOf(0.dp) }
    var bottomInset by remember { mutableStateOf(0.dp) }
    DisposableEffect(activityView) {
        activityView.rootWindowInsets?.let { root ->
            val bars = WindowInsetsCompat.toWindowInsetsCompat(root).getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            with(density) {
                topInset = bars.top.toDp()
                bottomInset = bars.bottom.toDp()
            }
        }
        onDispose { }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    DisposableEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // A Dialog window "fits system windows" by default, which zeroes out the
        // safeDrawing insets Compose sees — the overlays then render behind the
        // status/navigation bars. Force the dialog window edge-to-edge so the
        // real insets flow to windowInsetsPadding below.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            SideEffect { WindowCompat.setDecorFitsSystemWindows(dialogWindow, false) }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("camera-sampler"),
        ) {
            when {
                permissionDenied -> PermissionDeniedContent(onDismiss)
                hasPermission -> SamplerContent(
                    onPick = onPick,
                    onDismiss = onDismiss,
                    topInset = topInset,
                    bottomInset = bottomInset,
                )
                else -> {
                    // Waiting on the permission dialog result.
                    Text(
                        "Requesting camera permission...",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Camera permission is needed to sample a color. You can still pick a color by hand.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag("camera-sampler-cancel"),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Close") }
    }
}

@Composable
private fun SamplerContent(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hex by remember { mutableStateOf("FFFFFF") }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Reticle diameter = same fraction of the preview's shorter side that
        // the patch uses of the frame's shorter side. FILL_CENTER center-crops,
        // so equal fractions map onto each other at the center: the ring bounds
        // exactly what's sampled (UI-47).
        val reticleSize = minOf(maxWidth, maxHeight) * ColorSampling.DEFAULT_PATCH_FRACTION
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    var lastSampleMs = 0L
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        val now = SystemClock.elapsedRealtime()
                        // Q-U17-3=A: throttle to ~5 fps.
                        if (now - lastSampleMs >= 200L) {
                            lastSampleMs = now
                            sampleCenterHex(image)?.let { hex = it }
                        }
                        image.close()
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        // Center reticle — sized to match the sampled patch (see reticleSize).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(reticleSize)
                .border(3.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
        )

        // Live hex readout chip (top). Inset below the status bar / notch.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        parseHexColor(hex) ?: Color.Transparent,
                        CircleShape,
                    )
                    .border(1.dp, Color.White, CircleShape),
            )
            Text(
                text = "#$hex",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.testTag("camera-sampler-hex"),
            )
        }

        // Instruction + actions (bottom). Inset above the navigation bar so the
        // buttons stay reachable on tall / gesture-nav / small screens.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottomInset + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Center the circle on the filament. Tweak the color if needed.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("camera-sampler-cancel"),
                ) { Text("Cancel") }
                Button(
                    onClick = { onPick(hex) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.testTag("camera-sampler-use"),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("  Use this color")
                }
            }
        }
    }
}

/**
 * Extract the center-patch pixels from an RGBA_8888 [ImageProxy] and average
 * them to a hex string via [ColorSampling]. Returns null if the frame isn't
 * the expected single-plane RGBA layout.
 */
private fun sampleCenterHex(image: ImageProxy): String? {
    if (image.format != android.graphics.PixelFormat.RGBA_8888 && image.planes.size != 1) {
        return null
    }
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null

    // Size the sampled patch to the same fraction of the frame the on-screen
    // reticle uses, so the ring bounds exactly what's averaged (UI-47).
    val patchPx = ColorSampling.patchForFraction(minOf(width, height))
    val bounds = ColorSampling.patchBounds(width, height, patchPx)
    val (left, top, right, bottom) = bounds
    val patchW = right - left
    val patchH = bottom - top
    val patch = IntArray(patchW * patchH)
    var idx = 0
    for (y in top until bottom) {
        val rowBase = y * rowStride
        for (x in left until right) {
            val offset = rowBase + x * pixelStride
            val r = buffer.get(offset).toInt() and 0xFF
            val g = buffer.get(offset + 1).toInt() and 0xFF
            val b = buffer.get(offset + 2).toInt() and 0xFF
            patch[idx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return ColorSampling.averageHex(patch, patchW, patchH, patch = maxOf(patchW, patchH))
}

private fun parseHexColor(hex: String): Color? {
    if (hex.length != 6) return null
    return try {
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(red = r, green = g, blue = b)
    } catch (_: NumberFormatException) {
        null
    }
}
