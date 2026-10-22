# ThaiMoney v3.2.1 — Build status & GitHub Actions Integration

## Target Specifications
- Minecraft Java Edition: 26.3
- Java Version: JDK 25
- Fabric Loader: >=0.19.5
- Fabric Loom: 1.17.21 (unobfuscated mode)
- Fabric API: 0.164.0+26.3
- Gradle: 9.6.0

## Verified Updates in v3.2.1
- Fixed `build.gradle` dependency configuration: switched to standard `implementation` required by unobfuscated Loom for Minecraft 26.1+.
- Bumped project version to `3.2.1` in `gradle.properties`.
- Added GitHub Actions CI/CD workflow (`.github/workflows/build.yml`) for automated cloud building of `thaimoney-3.2.1.jar`.
- Updated all Thai currency textures via `tools_make_textures.py`:
  - 10 Baht coin: authentic bimetallic ring (silver outer, golden-brass inner).
  - 2 Baht coin: golden aluminium-bronze.
  - 1 Baht coin: nickel silver.
  - 1,000 Baht note: authentic warm taupe/brown-grey with gold holographic security foil strip.
  - 20, 50, 100, 500 Baht notes: enhanced security foil threads.
