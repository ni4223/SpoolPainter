Title:

SpoolPainter just got a big v2 update

---

Post body:

I wanted an app to write OpenSpool tags so the U1 extended firmware could read them, and nothing out there really worked for me. So I made my own, clean and easy, no clutter and no bullshit. That was SpoolPainter v1, and today the v2 rewrite is on the Play Store.

First though, thank you. About 500 of you are using it now, mostly from the U1 Discord, and that still kind of blows my mind. That server, and helping out with Paxx's extended firmware, is a big part of why I kept at it. So genuinely, thanks.

I'm a dev but had never built a mobile app, and honestly I just wanted an excuse to ship something and see how far AI could take me outside my usual lane. So I built it for myself, which is also why it does none of the stuff I hate in other apps. No accounts, no cloud, no tracking, no analytics. The only thing it ever talks to is the Spoolman URL you put in, and that stays on your own network.

It's Android only for now, which is funny since I'm mostly an Apple user. NFC on iOS needs some kind of premium paid dev account from what I can tell, and that's a lot more than the Android side cost me, so it's hard to justify for a free project. If enough people want it there, I'll figure something out. :)

v1 was basically just a tag writer. v2 does a lot more:

* Your Spoolman companion. Connected to your self hosted Spoolman server, it helps keep your filament inventory and NFC tagged spools in sync. Browse, create, and edit spools and filaments right from the app. No account and no cloud, just local network sync.
* Vendor tag support. Tap a supported vendor tag and it automatically fills in the spool details for you. Bambu Lab, Snapmaker, Creality, QIDI, Anycubic and Elegoo tags are supported.
* Built for the U1 extended firmware. Use a tag's built in serial number to link it to a spool in Spoolman. The extended U1 firmware can then identify the correct spool automatically, including spools using their original vendor tags. Firmware PR if you want to dig in: https://github.com/paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware/pull/491
* Pairing made easy. Pair both tags for a spool without repeating the setup. Move a tag to a different spool anytime and it updates the pairing for you.
* Scan a color with the camera. Point your camera at a spool and it samples the color for you.
* Fresh new look. Completely redesigned from the ground up with a cleaner, more modern interface. Light and dark themes, improved sorting, and more built right in.

You don't need Spoolman to use it. Plenty of people just write plain OpenSpool tags, fill in the filament and tap a tag, and never touch the inventory side. I tried hard to keep that path simple in v2 instead of burying it under the Spoolman stuff. Almost all my feedback so far is from Spoolman users, so if you're not one, tell me how it's working for you.

It's free, and it's staying free. No ads, no Buy Me a Coffee, no plan to make money off it. It's open source too (GPL-3.0). If you want to poke at it, fork it, or fix something, please do, PRs welcome.

A few things I'm working on, mostly from what people have asked for:

* Type to search your spools and filaments instead of scrolling a long list. In testing now.
* On a tag read, suggest the closest matching spool or filament so you can link it in one tap. In testing now.

Both of the above are live on the Open testing track if you want to try them early: https://play.google.com/apps/testing/com.spoolpainter.app

If something's broken or there's a feature you want, there's a feedback button right in the app's Settings, or open an issue on GitHub, or drop it in the app's thread on the U1 Discord: https://discord.com/channels/1086575708903571536/1456237847391637575

Play Store: https://play.google.com/store/apps/details?id=com.spoolpainter.app

Source and issues: https://github.com/ni4223/SpoolPainter

Try it and tell me what's broken or what you want it to do. You can also reach me on Discord, my username is .nik42

---

Referral and profile links below if you're buying stuff anyway. Skip them if not, no pressure:

Snapmaker referral: https://snapmaker-us.myshopify.com?ref=ni42

Polymaker: [PASTE POLYMAKER URL]

MakerWorld, I've got some U1 models up: https://makerworld.com/en/@nm4223/
