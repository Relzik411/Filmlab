# FilmLab

A prototype film-look photo editor for iPhone (SwiftUI) and Android (Jetpack Compose). Photos only.

## Download

Get the latest build from [Releases](../../releases).

- **Android** (10 or later): download `FilmLab.apk` on the phone, open it, and allow installs from
  your browser when asked.
- **iPhone** (iOS 17 or later): `FilmLab-unsigned.ipa` has to be signed before it will install. Use
  [AltStore](https://altstore.io) or [Sideloadly](https://sideloadly.io) with your Apple ID. With a free
  Apple ID the app must be refreshed every 7 days. Wider distribution needs an Apple Developer account
  ($99/year) and TestFlight.

Every push to `main` builds both apps in GitHub Actions (download them from the run's artifacts).
To publish a release, open Actions → Build → **Run workflow** and enter a version such as `0.2.0`,
or push a tag such as `v0.2.0`.

## Features

- Pick a photo, then tap a **look** (a 3D LUT) and set its strength.
- **My presets**: tap **New** in the Looks strip to save the current look, strength, adjustments and light
  leak under a name (crop is left out, since it belongs to each photo). Saved presets sit next to **New**,
  previewed on the open photo; tap one to apply it (undoable). Long-press to rename or delete.
- **Adjust**: exposure, contrast, highlights, shadows, saturation, warmth, tint, fade, sharpen, vignette,
  grain, and **HSL**: hue, saturation and luminance for eight colour bands (red, orange, yellow, green,
  aqua, blue, purple, magenta). Highlights, shadows and HSL are baked into a 33-point LUT whenever they
  change, so both platforms run the same maths and each pixel needs one lookup. `docs/adjustments.jpg`
  shows examples.
- **Effects**: five light leaks (Amber, Rose, Sunset, Haze, Prism) with an amount slider. **Shift** moves the
  leak to another corner. The leaks are drawn by code from soft coloured glows, so there are no image files
  to license.
- **Crop**: drag the box's corners or move it; ratios Free, Original, 1:1, 4:5, 9:16 and 16:9; straighten
  ±20° (the photo is scaled up so no empty corners show); rotate 90° and flip.
- Press and hold the photo to compare with the original.
- **Undo / redo** in the top bar. A step is recorded when you pause for half a second, so one slider drag
  is one step. **Revert to Original** (in the ••• menu) is a step too, so it can be undone.
- **Edits are saved automatically.** Open the same photo again and its edits come back; Undo then returns
  to the original. Photos are recognised by a fingerprint (SHA-256) of their contents, so it works however
  the photo is opened. Edits are stored in the app's own storage, never in the photo.
- **Save** writes a full-resolution copy: HEIC to Photos on iPhone, JPEG to Pictures/FilmLab on Android.

Edits are never baked in while you work. A 1600px copy is used for the live preview, and the same
edit runs on the full-size photo when you save.

## Run the iOS app from source

Requires a Mac with Xcode 15 or later. The app targets iOS 17.

```sh
brew install xcodegen
xcodegen            # creates FilmLab.xcodeproj from project.yml
open FilmLab.xcodeproj
```

Select your team under Signing & Capabilities, then run on a device or simulator.

Without XcodeGen: create a new iOS App project in Xcode named FilmLab (SwiftUI, iOS 17), delete
its generated `ContentView.swift` and app file, drag in the `FilmLab/` folder (including `LUTs/`),
and add the Info.plist key `NSPhotoLibraryAddUsageDescription`.

## Run the Android app from source

Open `android/` in Android Studio and run, or `cd android && ./gradlew installDebug` with a device
connected. The Android app reads the same `.cube` files from `FilmLab/LUTs/`.

Release APKs are signed with the key in these repository secrets, if set: `ANDROID_KEYSTORE_BASE64`
(the keystore file, base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Without them CI signs with a throwaway debug key, so installing a newer build means removing the old one
first. Create a key once with `keytool -genkeypair -keystore filmlab.keystore -alias filmlab -keyalg RSA
-validity 10000`, keep it safe, and add the secrets under Settings → Secrets and variables → Actions.

## iOS code

| File | What it does |
|---|---|
| `LUT.swift` | Reads `.cube` files and packs them for `CIColorCubeWithColorSpace`. |
| `FilterPipeline.swift` | Builds the edit: corrections, then the look, then fade, vignette and grain. |
| `Renderer.swift` | Shared `CIContext`: preview rendering, downscaling, HEIC/JPEG encoding. |
| `EditorModel.swift` | Photo loading, merged preview renders, thumbnails, saving to Photos. |
| `EditorView.swift` | The editor screen. |
| `CreditsView.swift` | Attribution the LUT licence requires. |
| `Crop.swift` | Crop state, crop-box arithmetic, and the rotate/flip/straighten/crop geometry. |
| `CropOverlay.swift` | The draggable crop box. |
| `LightLeak.swift` | Light leak styles and how they are drawn. |
| `History.swift` | Undo and redo. |
| `ToneColor.swift` | Builds the highlights/shadows/HSL LUT; the HSL bands. |
| `EditStore.swift` | Saves each photo's edits as JSON in Application Support. |
| `PresetStore.swift` | Saves your presets as JSON in Application Support. |

## Android code

In `android/app/src/main/java/com/example/filmlab/`:

| File | What it does |
|---|---|
| `Lut.kt` | Reads `.cube` files from the app's assets. |
| `Processor.kt` | Applies an edit on the CPU across all cores, mirroring the iOS pipeline. |
| `PhotoIO.kt` | Decodes photos upright in sRGB; saves JPEGs to Pictures/FilmLab. |
| `EditorViewModel.kt` | Loading, cancellable preview renders, thumbnails, saving. |
| `MainActivity.kt` | The editor screen, including the crop box. |
| `Crop.kt` | Crop state, crop-box arithmetic, and the rotate/flip/straighten/crop geometry. |
| `LightLeak.kt` | Light leak styles (drawn in `Processor.kt`). |
| `History.kt` | Undo and redo. |
| `ToneColor.kt` | Builds the highlights/shadows/HSL LUT; the HSL bands. |
| `EditStore.kt` | Saves each photo's edits as JSON in the app's files. |
| `PresetStore.kt` | Saves your presets as JSON in the app's files. |

Crop arithmetic, the light leak styles and the tone/HSL maths are written twice, once per platform; keep them
in step.

## Looks

The 15 bundled looks are adapted from the
[RawTherapee Film Simulation Collection](https://rawpedia.rawtherapee.com/Film_Simulation)
(CC BY-SA 4.0). See `FilmLab/LUTs/LICENSE.txt`. `docs/lut-preview.jpg` shows each one on a sample photo.

| Look | Based on |
|---|---|
| Golden | Kodak Portra 400 |
| Portrait | Kodak Portra 160 NC |
| Mint | Fuji 400H |
| Everyday | Fuji Superia 400 |
| Summer | Agfa Vista 200 |
| Vivid | Kodak Ektar 100 |
| Classic | Kodak Kodachrome 64 |
| Warm | CreativePack SoftWarming |
| Dusk | CreativePack LateSunset |
| Winter | CreativePack CrispWinter |
| Instant | Polaroid 690 Warm |
| Faded | Polaroid Polachrome |
| Cross | Lomography X-Pro Slide 200 |
| Mono | Ilford HP5 Plus 400 |
| Grit | Kodak Tri-X 400 |

To add a look, put any 0–1 range 3D `.cube` file in `FilmLab/LUTs/`. To convert a Hald CLUT PNG:

```sh
pip install pillow numpy
python3 scripts/hald_to_cube.py "Some Hald.png" FilmLab/LUTs/Name.cube --title Name
```

Then add the name to `LUT.preferredOrder` if it should not be listed last.
