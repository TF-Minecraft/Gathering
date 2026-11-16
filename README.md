# Gathering

> Search the land for gathering spots: an active, Mental Point–driven activity for TF-Minecraft characters.

Players turn on gathering mode with `/gather`. While it is on, it costs Mental Points
(RPCharacters focus), and gathering spots appear around them. Ground that has not been
worked recently turns up spots more often. Anyone in gathering mode can see and harvest
the spots nearby for configurable, weighted drops.

## Features

- **Gathering mode** — `/gather` toggles it. It charges Mental Points on entry and on a
  fixed interval, and ends when the player turns it off or runs out.
- **Spawning around gatherers** — spots appear near players in the mode, only in chunks
  that are already loaded, filtered by biome, altitude, surface block, and optionally
  SimpleFactions province. Wisdom and intelligence raise the spawn chance.
- **Shared spots** — every player in gathering mode sees the particle rings, and the
  first to harvest a spot gets its drops. A cap per area keeps groups from multiplying
  output.
- **Chunk freshness** — each harvest depletes its chunk, and depletion fades with a
  configurable half-life, so worked-over areas spawn less until they recover.
- **Weighted drops** — spot types roll weighted drop categories, and each category
  rolls weighted TLibs item paths with configurable amounts.
- **Temporary spots** — spots live in memory and disappear when nobody gathering is
  near them, when they get old, or when their chunk unloads. Only chunk depletion is
  saved.
- **Administrator tools** — reload configuration, inspect status, force a spawn,
  check or reset chunk freshness, and use an admin mode that reveals every spot.

Originally created by [Drefvelin](https://github.com/Drefvelin).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Gathering/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests

With Java 21 and the pinned plugin dependencies installed, run `mvn clean verify`.
Tests use JUnit 5, Mockito, and MockBukkit. Surefire test results are in
`target/surefire-reports/`; JaCoCo HTML and XML reports are in `target/site/jacoco/`.
CI uploads both. Verification requires 100% line, branch, and instruction
coverage of production code, with no coverage exclusions.
Live world spawning, client particles, and the complete server plugin stack
require separate in-game checks.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and pre-existing
material retain their own licenses.
