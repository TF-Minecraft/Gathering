# Gathering

> Scatter hidden gathering spots across the world for TF-Minecraft characters to discover and harvest.

Gathering spawns hidden resource spots across configured regions of the world.
Roleplay characters notice nearby spots over time, guided by their attributes and
profession, and can then harvest them for configurable, weighted drops.

## Features

- **World spawning** — spots appear periodically in random chunks within configured
  world bounds, filtered by biome, altitude, and surface block.
- **Character discovery** — each RPCharacters character discovers spots separately,
  with a chance improved by wisdom, intelligence, and an optional MMOCore profession
  level.
- **Visible only once found** — discovered spots are marked by a particle ring that
  only that character can see.
- **Weighted drops** — spot types roll weighted drop categories, and each category
  rolls weighted TLibs item paths with configurable amounts.
- **Chunk cooldowns** — a harvested chunk rests before it can host another spot, and
  chunks with no valid surface are excluded from future spawning.
- **Administrator tools** — reload configuration, inspect status, force a spawn,
  clear excluded chunks, and use an admin mode that reveals every spot.
- **Persistent state** — active spots, character discoveries, chunk cooldowns, and
  excluded chunks survive server restarts.

Originally created by [Drefvelin](https://github.com/Drefvelin).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Gathering/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and pre-existing
material retain their own licenses.
