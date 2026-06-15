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
    fun onColdStart(currentVersion: Int, isFreshInstall: Boolean) {
        val lastSeen = settingsRepository.settings.value.lastSeenWhatsNewVersion
        pendingVersion = currentVersion
        if (shouldShow(currentVersion, lastSeen, isFreshInstall)) {
            _visible.value = true
        } else {
            // Suppressed (fresh install, or already current). Persist so we
            // don't re-evaluate-and-show on a later version-equal relaunch.
            markSeen()
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
         * - lastSeen == 0 (never recorded): show only if this install has been
         *   updated at least once (a v1 -> v2 in-place updater). A genuinely
         *   fresh install is suppressed so brand-new users don't see a
         *   "what's new" for features they never had an old version of.
         * - lastSeen > 0: show when the current build is newer than last seen
         *   (a v2.x -> newer updater). Same-version relaunch shows nothing.
         */
        fun shouldShow(
            currentVersion: Int,
            lastSeenVersion: Int,
            isFreshInstall: Boolean,
        ): Boolean = if (lastSeenVersion == 0) {
            !isFreshInstall
        } else {
            lastSeenVersion < currentVersion
        }
    }
}
