# SpoolPainter — Play Store Listing (v2)

Copy-paste source for the Google Play Console listing. No em dashes (project
convention). Play does not render Markdown, so bullets use the `•` character.

---

## App name (max 30 chars)

```
SpoolPainter
```

## Short description (max 80 chars)

```
NFC-based 3D printer filament spool manager with Spoolman integration
```

## Full description (max 4000 chars)

```
SpoolPainter is a companion app for your self-hosted Spoolman server. Browse, create, and edit your filament spools and filaments right from your phone, and pair them to NFC tags so your inventory and your physical spools stay in sync.

No account. No cloud. No analytics. The only thing the app talks to is the Spoolman URL you configure on your local network.

YOUR SPOOLMAN, ON YOUR PHONE

Connect to your self-hosted Spoolman server and your whole filament inventory comes with you. Pick a spool from the dropdown, edit its color, weights, density, temperatures, and per-spool price, and Save patches the record straight back to Spoolman. Create new filaments and spools without opening the web UI. A stale-prefill guard keeps you from overwriting values your printer firmware updated in the background.

TAGS THAT MATCH YOUR INVENTORY

Tap a blank tag while a spool is selected and SpoolPainter writes an OpenSpool payload and links the tag to that Spoolman spool in one motion. Tap a tag later and the app finds its spool for you and prefills the form. Pair both sides of a two-sided spool so a printer reading either tag gets the same answer. Tap a tag already bound to a different spool and the app asks before moving it, then updates Spoolman for you.

VENDOR TAG SUPPORT

Read vendor tags from six brands: Bambu Lab, Snapmaker, QIDI, Anycubic, Elegoo, and Creality. Snapmaker, QIDI, Anycubic, and Elegoo work out of the box. Bambu Lab and Creality need a one-time per-brand setup in Settings. Vendor tags prefill the form and pair to your Spoolman spool by serial number. Nothing is written back to the vendor chip.

BUILT FOR U1 FIRMWARE

Link a tag by its built-in serial number so the latest Snapmaker U1 firmware can identify the right Spoolman spool automatically, including spools that still wear their original vendor tags.

SCAN A COLOR WITH THE CAMERA

In the color picker, tap Scan color, point the phone at a spool, and the app samples the color under the center reticle into the form. It is an approximate starting point you can fine tune, not an exact match.

SETTINGS

• Spoolman URL with a connectivity check on save
• Independent sort orders for the spool dropdown and the filament picker
• Light and dark theme
• Currency picker for the price field
• Vendor tag support with a per-brand status glyph

WHO IT IS FOR

3D printing hobbyists who run a Spoolman server on their LAN and want a fast phone client for it, plus OpenSpool-compatible NFC tags on their spools so printer firmware and other tools can read material, brand, color, and temperatures straight off the tag.

WORKS WITHOUT A SERVER TOO

No Spoolman URL configured? SpoolPainter still writes OpenSpool tags directly, so any OpenSpool-aware tool can read them. Spoolman just unlocks the inventory sync.

NFC NOTES

Works with NDEF-formattable tags. NTAG215 and NTAG216 give the most room; NTAG213 works but is tighter.

PRIVACY

No login, no account, no analytics, no telemetry, no crash reporting. The Spoolman URL you configure is the only network destination. The app uses cleartext HTTP by default because Spoolman is usually self-hosted on a local network.
```

## What's new / release notes (max 500 chars)

Only place the v2-vs-v1 story belongs. Shown to existing v1 users on update.

```
SpoolPainter v2 is a full redesign and is now a proper Spoolman companion.

• Browse, create, and edit your Spoolman spools and filaments in the app
• Pair tags to spools, both sides of a spool, and move tags between spools
• Read vendor tags: Bambu Lab, Snapmaker, QIDI, Anycubic, Elegoo, Creality
• Built for Snapmaker U1 firmware serial-number linking
• Scan a color with the camera
• Fresh new look with light and dark themes
```

---

## Privacy policy (host at a public URL)

```
Privacy Policy for SpoolPainter

Last updated: July 2026

SpoolPainter does not collect, store, or share any personal data.

Data collection
SpoolPainter has no user accounts, no login, and no cloud services. It does
not collect analytics, telemetry, crash reports, advertising identifiers, or
any personal information.

Network use
The only network destination the app contacts is the Spoolman server URL you
enter in Settings. That connection stays on your own local network and is used
solely to read and update your filament inventory on your self-hosted Spoolman
server. SpoolPainter does not send your data anywhere else.

NFC
The app reads and writes NFC tags you tap to it. Tag data (filament type,
brand, color, temperatures, and a tag serial number) is processed on your
device and, if you have configured Spoolman, sent only to your Spoolman server.

Camera
The optional "Scan color" feature uses your device camera to sample a color
from the live preview. Camera frames are processed on-device in real time to
read the color under the reticle. No images are captured, stored, or
transmitted.

Contact
Questions: <your-email@example.com>
```

## Camera permission justification (Play app-content declaration)

```
The CAMERA permission powers an optional "Scan color" feature in the color
picker. The user points the camera at a filament spool and the app samples the
color under an on-screen reticle to prefill the color field. Camera frames are
processed on-device in real time; no images are captured, stored, or
transmitted. The feature is optional and the camera hardware is not required to
use the app.
```

## Data safety form answers

• Does your app collect or share any user data? → No
• All data categories (location, personal info, financial, photos, files,
  contacts, etc.) → not collected
• Data encrypted in transit? → N/A (no data collected/shared). Note: Spoolman
  traffic is cleartext HTTP on the user's LAN by design.
• Data deletion mechanism? → N/A

---

## Graphic assets

• App icon — 512 x 512 px, 32-bit PNG, < 1 MB, no transparency/rounded corners.
  Reuse the launcher artwork at 512px.
• Feature graphic — 1024 x 500 px, PNG or JPG, no transparency. REQUIRED and the
  only net-new asset. Suggested: logo + spool with a tag + "Your Spoolman
  companion"; keep essential text away from edges and center.
• Phone screenshots — min 2, up to 8, 1080px+ long edge. Suggested order:
  01-main → 02-spool-dropdown → 03-form-expanded → 07-vendor-chip (or
  08-vendor-read) → 10-camera-color → 05-settings (files in repo screenshots/).
• Tablet screenshots — optional, skip.

## Store settings

• Category: Tools (or Productivity).
• Contact email: required, public. Website: optional (GitHub repo). Phone: skip.
• App access: "All functionality is available without special access."
• Ads: No.
• Content rating: complete IARC questionnaire (utility → Everyone).
• Target audience: not directed at children.
