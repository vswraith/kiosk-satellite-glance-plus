# Glance Plus for Kiosk Satellite

[Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite)'s **At a Glance** row shows a few Home Assistant entities as chips on the screensaver. Each chip is capped at about 250 dp wide, with its name and value limited to one line each, so long values end in an ellipsis.

**Glance Plus** draws the same kind of chip (an icon circle, a name and a bold value) but lets you choose how big it is:

- **Chip width**, 120–1000 dp, or **fit to content** up to that width
- **Chip minimum height**, up to 400 dp
- **Value lines** (1–8) and **name lines** (1–3), so long text wraps instead of being cut off
- **Chips per row** (1–4) and up to **12 chips** (the stock row shows 4)
- **Scale** (50–250 %), **background opacity**, **hide names**, **monochromatic icons**
- **Position** (nine places) and **distance from the edge**
- Show **only on the screensaver**, or always
- Entity picker, icon picker and a name override per chip, with the same Home Assistant entity picker the stock settings use

Like the stock row, the icon circle takes the entity's state colour (lights glow amber, unlocked locks turn red, open covers purple) and the text itself never changes colour. A chip whose value is blank is hidden until it has one again.

The chips are one visual-only overlay: every touch passes straight through to whatever is underneath.

## Install

You need Kiosk Satellite **2026.10.36 or newer** with **Plugin Manager** enabled.

**From a release (normal path):** Settings → Plugin Manager → Add plugin, paste this repository's URL, Preview, then Trust and install.

**From a ZIP (for testing):** build it (below), then Plugin Manager → Developer Tools → Install from ZIP.

Then open the plugin's settings, add your chips and pick a position.

## Build

You need Python 3, JDK 17 or newer and an Android SDK with a platform and build tools (for example `platforms;android-35` and `build-tools;35.0.0`).

```sh
export ANDROID_HOME=/path/to/android-sdk
python3 tools/test.py
python3 tools/build.py --android-platform 35
```

The package lands in `dist/glance-plus-<version>.zip`.

## Status

Compiles and its device-free tests pass. It is young: check how it looks on your own panel and report anything odd as an issue. It needs a Kiosk Satellite build with native overlays.

## Licence

The plugin code in `src/` and `tests/` is MIT licensed (see `LICENSE`). The files in `sdk/` and `tools/` come from the [Kiosk Satellite plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins) and keep their Apache-2.0 headers (see `NOTICE`). This project is not affiliated with Kiosk Satellite.
