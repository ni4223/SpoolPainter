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
