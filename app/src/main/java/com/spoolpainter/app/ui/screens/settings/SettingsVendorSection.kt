package com.spoolpainter.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.hardware.nfc.vendor.VendorId

/**
 * Vendor tag support section. Vendor list with a colored power glyph next
 * to each brand name + a key icon button on the keyable vendors. Tapping
 * the key icon mounts the matching key field(s) in a fixed bottom slot,
 * scrolls that slot into view, and auto-focuses the first field. The list
 * itself never shifts.
 *
 * Creality has two keys (HKDF salt + AES enc key) but a single Save button
 * commits both at once.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsVendorSection(
    bambuSalt: String,
    crealitySalt: String,
    crealityEncKey: String,
    onBambuSaltSaved: (String) -> Unit,
    onCrealitySaltSaved: (String) -> Unit,
    onCrealityEncKeySaved: (String) -> Unit,
    testTag: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var openVendor by rememberSaveable { mutableStateOf<VendorId?>(null) }
    // Collapsing the section should fully reset the key-tap state so the
    // user reopens to a clean slate (no pre-highlighted key button, no
    // pre-mounted field at the bottom).
    LaunchedEffect(expanded) {
        if (!expanded) openVendor = null
    }

    val keySlotBringIntoView = remember { BringIntoViewRequester() }
    val firstFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .testTag("$testTag-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Vendor tag support",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            VendorTagSupportList(
                bambuSalt = bambuSalt,
                crealitySalt = crealitySalt,
                crealityEncKey = crealityEncKey,
                selected = openVendor,
                onKeyTap = { openVendor = if (openVendor == it) null else it },
            )
            // Bottom slot — empty until a key icon is tapped.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(keySlotBringIntoView)
                    .testTag("$testTag-key-slot"),
            ) {
                when (openVendor) {
                    VendorId.Bambu -> SingleKeyForm(
                        initial = bambuSalt,
                        label = "Bambu Lab tag key",
                        fieldTag = "$testTag-bambu-field",
                        saveTag = "$testTag-bambu-save",
                        onSave = onBambuSaltSaved,
                        firstFieldFocusRequester = firstFieldFocusRequester,
                    )
                    VendorId.Creality -> CrealityKeyForm(
                        salt = crealitySalt,
                        encKey = crealityEncKey,
                        onSave = { newSalt, newEnc ->
                            onCrealitySaltSaved(newSalt)
                            onCrealityEncKeySaved(newEnc)
                        },
                        testTag = testTag,
                        firstFieldFocusRequester = firstFieldFocusRequester,
                    )
                    else -> Unit
                }
            }
        }
    }

    // When a vendor is opened, scroll the key slot into view + focus the first
    // field so the user sees what was mounted even if they tapped from far up.
    LaunchedEffect(openVendor) {
        if (openVendor != null) {
            keySlotBringIntoView.bringIntoView()
            runCatching { firstFieldFocusRequester.requestFocus() }
            // Suppress the IME — we want focus to land on the field so it's
            // visually highlighted, but the user shouldn't be force-shifted
            // into typing the moment they tap a key icon.
            keyboardController?.hide()
        }
    }
}

@Composable
private fun SingleKeyForm(
    initial: String,
    label: String,
    fieldTag: String,
    saveTag: String,
    onSave: (String) -> Unit,
    firstFieldFocusRequester: FocusRequester,
) {
    var draft by rememberSaveable(initial) { mutableStateOf(initial) }
    val saved = draft.trim() == initial.trim()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFieldFocusRequester)
                .testTag(fieldTag),
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { onSave(draft) },
                enabled = !saved,
                modifier = Modifier
                    .wrapContentWidth()
                    .testTag(saveTag),
            ) {
                Text(if (saved) "Saved" else "Save")
            }
        }
    }
}

@Composable
private fun CrealityKeyForm(
    salt: String,
    encKey: String,
    onSave: (String, String) -> Unit,
    testTag: String,
    firstFieldFocusRequester: FocusRequester,
) {
    var draftSalt by rememberSaveable(salt) { mutableStateOf(salt) }
    var draftEnc by rememberSaveable(encKey) { mutableStateOf(encKey) }
    val unchanged = draftSalt.trim() == salt.trim() && draftEnc.trim() == encKey.trim()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draftSalt,
            onValueChange = { draftSalt = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFieldFocusRequester)
                .testTag("$testTag-creality-salt-field"),
            label = { Text("Creality tag key") },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )
        OutlinedTextField(
            value = draftEnc,
            onValueChange = { draftEnc = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("$testTag-creality-enc-field"),
            label = { Text("Creality encryption key") },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { onSave(draftSalt, draftEnc) },
                enabled = !unchanged,
                modifier = Modifier
                    .wrapContentWidth()
                    .testTag("$testTag-creality-save"),
            ) {
                Text(if (unchanged) "Saved" else "Save")
            }
        }
    }
}
