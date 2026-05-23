# Component Inventory

## Application Packages
- `com.spoolpainter.app` — single Android module (`:app`); the entire
  application

## Sub-packages (logical components)
- `com.spoolpainter.app.ui.activity` — Activity host
- `com.spoolpainter.app.ui` — `MainViewModel`
- `com.spoolpainter.app.ui.screens` — Compose screens
- `com.spoolpainter.app.ui.components` — Compose components
- `com.spoolpainter.app.ui.theme` — Material 3 theme
- `com.spoolpainter.app.hardware.nfc` — NFC adapter
- `com.spoolpainter.app.domain.models` — wire & presentation models
- `com.spoolpainter.app.data.local` — static presets
- `com.spoolpainter.app.data.remote.spoolman` — Spoolman REST client

## Infrastructure Packages
- None (mobile app — no CDK/Terraform/CloudFormation)

## Shared Packages
- None (single module)

## Test Packages
- `app/src/test/` — empty
- `app/src/androidTest/` — empty (only stray `.DS_Store`)

## Total Count
- **Total Modules**: 1
- **Application**: 1
- **Infrastructure**: 0
- **Shared**: 0
- **Test**: 0 (no tests exist)
