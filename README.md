# CubeTechnic Neon Arenas

Plugin source for Purpur/Paper 1.21.10 (Java 21). The build uses the real Paper API; do not replace it with Bukkit stub classes.

## Features
- Generates 45×45×18 enclosed arenas only in `CubeTechnic_Neon`, above the terrain surface.
- Enforces a minimum 3,000-block center-to-center distance from previously registered arenas.
- Closes the entry when a player enters and invokes `/orbitfx start` as that player.
- Displays Corrupt501 dialogue as centered titles at entry, +4 seconds, and +7 seconds.
- Protects arena blocks against player modification and explosions.

## Build
Requires Java 21 and Gradle 8.10.2 or later:

```sh
gradle --no-daemon clean build
```

The plugin JAR is created under `build/libs/`.

## GitHub Actions
The workflow at `.github/workflows/build.yml` builds the project against the actual Paper API and uploads the JAR as an artifact named `CubeTechnicNeonArenas`.
