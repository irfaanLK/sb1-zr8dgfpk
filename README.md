# P.E.T: petanque measuring

An Android app that measures petanque distances with the back camera. Point it at the
wooden jack (cochonnet) and the boules around it, and P.E.T draws a line from the jack
to each of the closest boules with the distance in centimetres.

## Features

- **Automatic detection** of the wooden jack and the metal boules, on gravel, sand, earth, grass or indoors.
- **Edge-to-edge distances** (closest surface to closest surface, like a tape measure), measured in 3D with ARCore.
- **Only the closest boules** get a line (1–6, 3 by default). The nearest one is highlighted in gold.
- **Teams**: tap a boule to cycle between Team A (blue), Team B (terracotta) and no team. Doublette (2 v 2) and triplette (3 v 3) formats.
- **Manual fixes**: long-press to add a missed boule, place the jack, or remove a wrong detection.
- **Lock** a measurement, then **capture** a photo with the lines and distances drawn on it.
- **History** of captured measurements, with sharing (and an optional copy to the gallery).
- English and French, with a dark Provence-style theme that stays readable in the sun.

## Install

Every push to the development branch builds a signed APK with GitHub Actions:

1. Open the repository's **Releases** page and pick **P.E.T latest build**, or the latest run under **Actions** (artifact `PET-apk`).
2. Download `PET.apk` on the phone and open it. Allow installs from your browser or file manager when Android asks.
3. On first launch, allow camera access. If Google Play Services for AR is missing, the Play Store opens to install it.

All builds are signed with the same key (`app/pet-signing.keystore`), so a new APK installs over the old one.

## Getting good measurements

- Hold the phone **flat, about 1 m above the balls**, and move it slowly for a second so ARCore finds the ground.
- Keep the jack and the boules in view. Detection runs several times a second, and each ball must be seen a few times before it's shown.
- Use **Lock** before a tight call, then capture. Precision is about ±1 cm. Switch to 0.1 cm in Settings if you want to see the decimals.
- If your boules aren't the default 74 mm, set their diameter in Settings: the edge-to-edge distance depends on it.

## How it works

1. **ARCore** tracks the phone and finds the ground plane, so the app knows how big a ball of a given size should look at every pixel.
2. The **detector** (`app/src/main/java/com/pet/measure/vision/BallDetector.kt`) runs a fixed-radius circular Hough transform for both ball sizes on the camera image. It refines each candidate with a least-squares circle fit, then checks colour (warm wood for the jack, grey metal for boules) and ARCore depth (a boule sticks out of the ground).
3. Each ball's image centre is cast back onto the ground (at the height of the ball's centre) to get its 3D position. The **tracker** smooths the positions over frames.
4. Distance = distance between centres − jack radius − boule radius.

## Build locally

Requires JDK 17+ and the Android SDK (API 35).

```
./gradlew testDebugUnitTest   # detector tests on synthetic terrain images
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```
