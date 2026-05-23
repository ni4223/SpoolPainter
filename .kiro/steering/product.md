---
inclusion: always
---

# SpoolPainter — Product Context

## What it is
Android app for managing 3D printer filament spools via NFC tags. Reads/writes
filament metadata in OpenSpool format and syncs with a self-hosted
[Spoolman](https://github.com/Donkie/Spoolman) server.

## Primary jobs
1. **Write filament metadata to NFC tags** stuck on the spool — material type,
   brand, color, print/bed temps, etc., in OpenSpool format so other tools
   (e.g., printer firmware integrations) can read them.
2. **Read NFC tags** to identify a spool quickly.
3. **Sync with Spoolman**: pull the user's filament inventory from a Spoolman
   server URL, let the user pick a filament, and write its data to a tag.
   Conversely, write tag changes back to Spoolman.

## Users
3D printing hobbyists running Bambu/Prusa/Voron/etc. who maintain a Spoolman
inventory and want OpenSpool-compatible NFC tags on their spools.

## Status
v1.x is shipping (versionCode 8, versionName 1.7). v2 is a planned rewrite to
clean up architecture and refresh UI — same package
(`com.spoolpainter.app`), in-place update, no migration needed since data
lives on tags + Spoolman, not in the app.

## Constraints
- NFC only — no QR fallback in scope.
- Spoolman is the only inventory backend in scope.
- Single-user app; no auth, no cloud account.
- Distribution: sideload + (eventually) Play Store. Signing key is local.
