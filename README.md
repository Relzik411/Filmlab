# FilmLab

A SwiftUI prototype of a film-look photo editor for iPhone. Photos only.

- Pick a photo, then tap a **look** (a 3D LUT) and set its strength.
- **Adjust**: exposure, contrast, saturation, warmth, fade, vignette, grain.
- Press and hold the photo to compare with the original.
- **Save** writes a full-resolution HEIC to Photos.

Edits are never baked in while you work. A 1600px copy is used for the live preview, and the same
Core Image recipe runs on the full-size photo when you save.

## Run it

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

## Code

| File | What it does |
|---|---|
| `LUT.swift` | Reads `.cube` files and packs them for `CIColorCubeWithColorSpace`. |
| `FilterPipeline.swift` | Builds the edit: corrections, then the look, then fade, vignette and grain. |
| `Renderer.swift` | Shared `CIContext`: preview rendering, downscaling, HEIC/JPEG encoding. |
| `EditorModel.swift` | Photo loading, merged preview renders, thumbnails, saving to Photos. |
| `EditorView.swift` | The editor screen. |
| `CreditsView.swift` | Attribution the LUT licence requires. |

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
