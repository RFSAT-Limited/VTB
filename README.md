# VTB — Vapor-Trail Ballistic Calculator

Android app that estimates crosswind along a bullet's entire flight path
from vapor-trail video, then computes the scope windage/elevation
adjustment needed for the next shot.

Kotlin/Android, built exclusively via GitHub Actions CI (matches the DBM/
TinyRAD workflow — no local ADB/Android Studio needed). Target: Samsung
Galaxy S24, API 34; minSdk 26.

## Defaults pre-loaded

- **Rifle:** Ruger Precision Rimfire (18" barrel, 1:16" twist, 50 yd zero — edit sight height/zero to match your actual mount)
- **Bullet:** Federal Champion Solid 40gr .22 LR — 1240 fps muzzle velocity, G1 BC 0.139 (manufacturer spec)
- **Scope:** Vector Optics Continental 5-30x56 (VCT-34FFP Tactical MIL) — 0.1 MRAD/click, 26 MRAD elevation / 16 MRAD windage travel (manufacturer spec)

All three are editable in-app (Profiles screen) and persist between sessions.

## How it works

1. **Capture** — mount the phone rigidly on the rifle, parallel to the
   scope, with the app's on-screen crosshair aligned to the scope's own
   crosshair (nudge buttons on the capture screen fine-tune small mounting
   misalignments; the offset is saved per rifle profile). Either record
   manually, tap **Arm (Auto-Trigger)** to start recording automatically on
   hearing the shot (recording length is auto-computed from target
   distance and the bullet's ballistic time-of-flight), or use **Import
   Video** to analyze a pre-recorded clip instead (useful for testing the
   pipeline without firing a live round).
2. **Extract** — `TrailExtractor` diffs each frame after the shot against a
   pre-shot reference frame and tracks the brightest new blob (the trail)
   frame-by-frame.
3. **Calibrate** — `TrailCalibration` converts tracked pixels into real-world
   lateral/vertical angles using the camera's horizontal FOV, referenced to
   the crosshair overlay's pixel position. Downrange distance at each
   timestamp comes from a zero-wind baseline trajectory (`BallisticsEngine`),
   since a single 2D video can't directly measure depth.
4. **Invert wind** — `WindEstimator` fits local quadratic curves to the
   tracked lateral/vertical position vs. time, differentiates to get
   velocity and acceleration, and inverts the equations of motion
   (`a = -K·(v − wind)`) to recover an estimated crosswind (and vertical
   wind) at every point along the flight — not just an average.
5. **Adjust** — `AdjustmentCalculator` re-simulates the trajectory forward
   through that estimated wind field, compares the predicted impact point
   to the point of aim, and reports the windage/elevation correction in
   MOA or MRAD (and turret clicks) for the *next* shot.

## Camera geometry — read this before shooting

The wind-inversion math assumes the phone is mounted rigidly on the rifle
itself, parallel to the scope, boresighted along the line of fire (its
on-screen crosshair aligned to the scope's). A crosswind pushes the
bullet sideways relative to that line — from a camera positioned like this,
that sideways drift shows up directly as horizontal pixel displacement
across the frame, growing as the trail recedes.

A camera positioned **off to the side**, looking across the line of fire,
sees gravity drop (vertical) clearly but crosswind drift mostly as motion
*toward/away from the camera* — which a single 2D video can't resolve.
Side-on footage will not give a usable crosswind estimate with this app as
built.

## Known limitations (read before trusting the numbers)

- **Drag model is approximate.** `BallisticsEngine` uses a hand-built
  Cd-vs-Mach curve (subsonic plateau → transonic rise → supersonic decay),
  not a digitized standard G1 table. It captures the qualitative shape,
  including .22LR's transonic behavior, but isn't drop-table-grade out of
  the box. Use `BulletProfile.dragCalibrationFactor` to tune it against
  your own chronograph and known-distance drop data.
- **No image stabilization.** `TrailExtractor` assumes a static camera
  (rigidly mounted on the rifle, not handheld) between the reference frame
  and the shot — any relative motion of the mount during the shot will
  corrupt the pixel track.
- **Shot-break timing.** Manual recording still needs the time offset
  (seconds into the clip) entered by hand. **Arm (Auto-Trigger)** automates
  this via a mic-based transient detector, but it isn't zero-latency —
  mic buffer reads, the detection loop's polling interval, and CameraX's
  recorder startup add up to a small gap (~0.12s, `AUTO_TRIGGER_LATENCY_S`
  in `CaptureActivity`) before frames actually start landing, during which
  a bit of trail may already be gone. Reasonable for a rimfire at moderate
  range; validate against a known trail length before trusting it further
  out or with faster bullets. If analysis isn't finding a trail, check the
  Log tab (bottom nav) for frame-read/decode diagnostics — it can be saved
  and shared as a text file for debugging.
- **Frame resolution assumption.** The capture screen approximates the
  video's pixel resolution from the live preview view size rather than
  reading it back from the file — fine for footage recorded in-app, but
  worth checking for imported test clips of a different aspect ratio.
- **Range-condition inputs (temperature/pressure/altitude/humidity) aren't
  wired into a UI screen yet** — `Atmosphere()` currently uses sea-level
  standard defaults in `CaptureActivity`. Enter your actual range
  conditions there for anything beyond a rough estimate.
- **Head/tail wind isn't derived from video** (a straight-on trail doesn't
  show it) — the engine supports it as a wind-profile component, but no UI
  exists yet to enter a manually-read range-wind value.
- This is a field-estimation tool for target shooting, not a certified
  ballistic solver — validate against real impacts before trusting it at
  distance.

## Version history

Convention: major version increments when a feature is added; minor
increments on corrections/fixes with no new capability. Shown as
`versionName` in `app/build.gradle.kts` and in the delivered ZIP filename
(`VTB_v<major>.<minor>.zip`).

- **v1.0** — initial feature-complete build: profiles, capture, trail
  extraction, wind inversion, scope adjustment.
- **v1.1** — renamed project to VTB / app to "Vapor-Trail Ballistic
  Calculator" (package `com.rfsat.vtb`); no functional changes.
- **v1.2** — fixed CI workflow: GitHub Actions doesn't allow the `secrets`
  context directly inside a step's `if:` condition ("Unrecognized
  named-value: 'secrets'"). Routed the keystore check through a job-level
  `env` var instead. No app code changes.
- **v1.3** — fixed a build failure in `checkDebugDuplicateClasses`: the
  `graphview` charting library pulls in the legacy
  `com.android.support:support-compat` artifact, which collides with
  `androidx.core`. Excluded that transitive dependency from `graphview`.
  No functional changes.
- **v2.0** — new features: default scope set to the Vector Optics
  Continental 5-30x56 (0.1 MRAD/click, real turret travel); on-screen
  crosshair overlay for the rifle-mounted-phone workflow (mount the phone
  parallel to the scope, align crosshairs, fine-tune with nudge buttons —
  replaces the old tap-to-mark step); video import for testing without a
  live shot; in-app debug log (Log tab) with save/share; About tab with
  RFSAT company info and app description; bottom navigation (Home/Log/
  About). Also fixes the video-analysis crash: the full pipeline (frame
  decode + physics simulation) was running synchronously on the UI thread,
  which is an ANR under load — moved to a background coroutine, added
  bitmap recycling in `TrailExtractor` to reduce memory pressure, and
  wrapped the pipeline so failures log to the Log tab instead of crashing.
- **v3.0** — new feature: **Arm (Auto-Trigger)**. Tap Arm, and the app
  listens on the mic for the muzzle report; when detected, it immediately
  starts recording and auto-stops after a duration computed from the
  target distance and the bullet's own ballistic time-of-flight (with
  margin). A reference frame is grabbed from the live preview at arm-time
  (since the resulting clip starts right at the shot, with no pre-shot
  footage to pull a background frame from). Sensitivity is field-tunable
  (0-100). See `ShotDetector`'s doc comment for the latency caveat — this
  is not a zero-latency trigger.
- **v4.0** — crash fix + several features. **Crash fix:** analysis of
  imported videos was killing the process (app "resetting to start
  screen") — prime suspect was decoding full-resolution (up to 4K) frames
  per sample, ~33 MB per bitmap; frames are now decoded at <=640 px wide
  via `getScaledFrameAtTime`, OutOfMemoryError is caught and logged, and a
  global uncaught-exception handler now writes crashes to a persistent
  log file, since the previous in-memory-only log evaporated with the
  process — exactly why it showed up empty. The Log tab now restores the
  previous session's tail on launch, classifies entries as Info / Warning /
  Error with color coding and filter buttons, and calibration now uses the
  extractor's own decoded-frame pixel space (fixes wrong pixel-to-angle
  scaling for imported clips whose resolution differs from the preview).
  **Features:** one-word buttons (Capture / Import / Analyse); scope
  pull-down with presets (defaulting to the Continental 5-30x56) plus
  zoom range, objective, focal length, and height-above-barrel parameters
  (the latter now feeds the ballistic solver, replacing the rifle's
  sight-height field); refraction-aware trail detection — the trail bends
  light, so the detector now scores |brightness delta| plus a Sobel
  gradient-distortion term instead of bright-pixels-only; dark theme is
  the default (OLED battery savings, low-light suitability), with a
  high-contrast Day mode for direct sunlight and Night modes rendering
  the UI in green or red only (red best preserves dark adaptation).

## Build

Builds automatically on push to `main` via
`.github/workflows/android-ci.yml` (debug APK always; signed release
APK + AAB if the usual `ANDROID_KEYSTORE_BASE64` /
`ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`
secrets are set, same convention as the DBM app).

## Next steps

- Wire up a range-conditions (temperature/pressure/altitude/humidity) input screen
- Swap the placeholder scope profile for your actual scope's specs once you send them over
- Optional: automatic shot-break detection from muzzle flash/report in the audio track
- Optional: multi-shot log / history (currently one shot's results live in memory only)
