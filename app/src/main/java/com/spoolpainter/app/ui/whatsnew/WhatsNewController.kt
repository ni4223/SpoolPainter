package com.spoolpainter.app.ui.whatsnew

import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one-time "What's new" showcase decision.
 *
 * Lives outside MainViewModel because the trigger is app-lifecycle scoped and
 * depends on PackageManager install/update times (a Context concern), not on
 * the NFC / Spoolman state MainViewModel orchestrates.
 */
@Singleton
class WhatsNewController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @AppScope private val externalScope: CoroutineScope,
) {
    private val _visible = MutableStateFlow(false)

    /** True while the showcase sheet should be shown. */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private var pendingVersion: Int = 0

    /**
     * Evaluate the trigger once at cold start. Records the decision so neither
     * the shown nor the suppressed path re-triggers for this version: a
     * fresh-install user who is suppressed here won't see the sheet on their
     * second launch, and a user who is shown it marks it seen on dismiss.
     */
    fun onColdStart(contentVersion: Int, isFreshInstall: Boolean) {
        pendingVersion = contentVersion
        externalScope.launch {
            // settings is store.data.stateIn(initialValue = Settings()), so
            // .value returns the default (lastSeen = 0) until DataStore's async
            // first read lands. Reading .value synchronously here raced that
            // load and saw 0 every cold start, re-showing the sheet on every
            // launch. awaitSettings() reads the raw DataStore flow, which
            // suspends until the real persisted value arrives.
            val lastSeen = settingsRepository.awaitSettings().lastSeenWhatsNewVersion
            if (shouldShow(contentVersion, lastSeen, isFreshInstall)) {
                _visible.value = true
            } else {
                // Suppressed (fresh install, or already current). Persist so we
                // don't re-evaluate-and-show on a later version-equal relaunch.
                markSeen()
            }
        }
    }

    /** Called when the user dismisses the sheet. */
    fun onDismiss() {
        _visible.value = false
        markSeen()
    }

    private fun markSeen() {
        val version = pendingVersion
        if (version <= 0) return
        externalScope.launch {
            settingsRepository.setLastSeenWhatsNewVersion(version)
        }
    }

    companion object {
        /**
         * Pure trigger logic.
         *
         * [contentVersion] is `WHATS_NEW_CONTENT_VERSION` — the versionCode in
         * which the highlights last changed — NOT the app's versionCode. That
         * distinction is the point: comparing against the app version re-showed
         * the sheet on every release, including the majority that changed no
         * copy at all.
         *
         * - lastSeen == 0 (never recorded): show only if this install has been
         *   updated at least once (a v1 -> v2 in-place updater). A genuinely
         *   fresh install is suppressed so brand-new users don't see a
         *   "what's new" for features they never had an old version of.
         * - lastSeen > 0: show when the copy has changed since the user last
         *   dismissed it. A relaunch, or an app update that carries no new
         *   copy, shows nothing.
         */
        fun shouldShow(
            contentVersion: Int,
            lastSeenVersion: Int,
            isFreshInstall: Boolean,
        ): Boolean = if (lastSeenVersion == 0) {
            !isFreshInstall
        } else {
            lastSeenVersion < contentVersion
        }
    }
}
