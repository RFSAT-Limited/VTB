// v1.20.29 — toolchain raised for Google Play's Android 16 (API 36) requirement.
//
//   AGP 8.5.2 -> 8.9.1   compileSdk 36 requires AGP 8.9.0-rc01 or higher
//                        (developer.android.com/about/versions/16/setup-sdk)
//   Kotlin 1.9.24 -> 2.1.0
//                        the Kotlin plugin must be contemporaneous with this
//                        AGP/Gradle pair; 2.x also uses the K2 compiler, which
//                        resolves the mixed-type indexed compound assignment
//                        that K1 rejected in this codebase (see v20.5).
//
// CI must run Gradle 8.11.1 or newer for AGP 8.9.x — see .github/workflows/android-ci.yml.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
