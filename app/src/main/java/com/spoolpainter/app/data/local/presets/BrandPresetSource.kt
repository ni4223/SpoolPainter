package com.spoolpainter.app.data.local.presets

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrandPresetSource @Inject constructor() {
    val brands: List<String> = PRESETS

    companion object {
        // "Other" stays as the last entry — selecting it reveals an inline
        // typed-name field. The typed value auto-persists to the user store
        // on Save & Write so it shows up in the dropdown next session.
        //
        // Spelling here is what gets written to the tag and to Spoolman, and
        // it sticks: vendor resolution matches an existing Spoolman vendor
        // case-insensitively and reuses that vendor's stored name
        // (SpoolmanRepository.resolveOrCreateVendor), so the first spelling to
        // reach a server wins permanently.
        //
        // The casings below are each brand's real styling, confirmed by the
        // maintainer 2026-08-22 (UI-61) — TECBEARS and GEEETECH are genuinely
        // all-caps, eSUN genuinely starts lowercase, 3DHoJor is genuinely camel
        // case. Do NOT "normalise" them to title case.
        //
        // "Elegoo" is deliberately NOT "ELEGOO" despite the brand styling it
        // all-caps. Changing it would strand every vendor row the app has
        // already created on users' Spoolman servers as "Elegoo": nothing
        // breaks (vendor + filament matching are case-insensitive, so no
        // duplicates) but selecting an existing spool would show "Elegoo"
        // while the picker showed "ELEGOO". Not worth a cosmetic gain.
        // If this ever changes, it must change in lockstep with
        // ElegooProcessor.displayName / brand and VendorTagChipRow.
        val PRESETS: List<String> = listOf(
            "Generic",
            "3DHoJor",
            "Bambu Lab",
            "eSUN",
            "Kingroon",
            "SUNLU",
            "Polymaker",
            "TECBEARS",
            "GEEETECH",
            "Elegoo",
            "JAYO",
            "Other",
        )
    }
}
