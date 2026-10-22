# ThaiMoney v3.2.1 — Build verification report

## Target
- Minecraft Java Edition 26.3
- Java 25
- Fabric Loader 0.19.5
- Fabric Loom 1.17.21
- Fabric API 0.164.0+26.3 (verified published in Fabric Maven)
- Gradle 9.6.0 (Fabric's recommended version for Minecraft 26.3 at release)

## Changes in this package
- Switched Fabric Loader and Fabric API dependencies to Loom's `modImplementation` configuration.
- Added Fabric Maven to project dependency repositories.
- Added Windows `build.ps1` bootstrap: verifies Java 25, downloads official Gradle 9.6.0 if Gradle is not installed, then runs `gradle build`.
- Connected four existing plain-Java core regression suites to Gradle's `check` task.
- Updated README and ignored build outputs, local Gradle cache, and compiler argument dumps.

## Tests actually run in this environment
- Compiled `src/main/java/com/thaimoney/core/*.java` and `src/test/java/*.java` with OpenJDK 21: PASS.
- Ran `CoreTest`: PASS.
- Ran `CoreTest2`: PASS.
- Ran `CoreTest3`: PASS.
- Ran `CoreTest4`: PASS.
- Parsed all 30 JSON files: PASS.
- Checked 13 texture references from block/item models: PASS (no missing textures).

## Not verified
A full Fabric/Minecraft 26.3 mod build was **not run**. This environment has OpenJDK 21, no installed Gradle, and cannot resolve external network hosts to download JDK 25, Gradle, Minecraft, Loom, or Fabric dependencies. Therefore this package intentionally does not include a fabricated `.jar` and does not claim the full mod compiles. Run `Set-ExecutionPolicy -Scope Process Bypass; .\build.ps1` on Windows with internet access and JDK 25 to perform the real build.
