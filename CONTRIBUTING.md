# Contributing

Thanks for wanting to help. This page explains how the project is laid out, the one rule about generated files, and how
changes are tested.

## Requirements

- JDK 25 (Gradle finds or downloads it through the toolchain).
- Nothing else: the Gradle wrapper downloads Gradle, NeoForge and Minecraft.

## Project layout

The code is in part folders at the root. They are compiled together into one mod jar (see `sourceSets` in
`build.gradle`), and each follows the usual `src/main/java` layout.

```
AntColonies/
├── common/                         everything that runs on both the server and the client
│   └── src/
│       ├── main/
│       │   ├── java/com/antcolonies/
│       │   │   ├── AntColonies.java      the mod's entry point
│       │   │   ├── colony/               the colony logic
│       │   │   │   ├── model/            a colony's numbers, statuses and structure kinds
│       │   │   │   ├── simulation/       one simulation step, ant allocation, production math
│       │   │   │   ├── species/          species data and categories
│       │   │   │   ├── genetics/         traits, genomes, mutations, the laboratory's rules
│       │   │   │   ├── breeding/         Queen rearing and hybrids
│       │   │   │   ├── evolution/        species evolution
│       │   │   │   ├── upgrade/          structure and species upgrades
│       │   │   │   └── resource/         colony storage and Hunting Chamber loot
│       │   │   ├── content/              what is in the world
│       │   │   │   ├── block/            blockentity/  entity/  item/  menu/  worldgen/
│       │   │   └── core/                 registry/  config/  advancement/
│       │   ├── resources/                the mod icon and logo (from tools/BrandGen.java)
│       │   └── templates/                neoforge.mods.toml, filled in from gradle.properties
│       └── generated/resources/          assets and data written by the tools (see below)
├── client/                         client only: rendering, models, screens
│   └── src/main/java/com/antcolonies/client/
├── compat/                         JEI, EMI and Jade plugins, loaded only with those mods
│   └── src/main/java/com/antcolonies/compat/
├── gametest/                       game tests and the in-game screenshot showcase (not in the jar)
│   └── src/main/
├── tools/                          generators for data, textures and branding
└── docs/                           guides and images
```

## Generated files

Every JSON file and every texture in `common/src/generated/resources` is written by a program in [`tools/`](tools):

| Tool | Writes |
|---|---|
| `tools/DataGen.java` | Species, evolutions, hybrids, mutations, lab costs, recipes, loot tables, tags, world generation, advancements, models, blockstates, sounds and the English language file |
| `tools/TexGen.java` | Every block, item, entity, particle and GUI texture |
| `tools/BrandGen.java` | The mod icon and logo in the jar, and the logo, banner and social preview in `docs/images` |

**Change the tool, not its output.** The next run of the tool overwrites hand edits. Run the tools from the project
root with JDK 25:

```sh
java tools/DataGen.java
java tools/TexGen.java
java tools/BrandGen.java
```

## Testing

- `./gradlew runGameTestServer` runs the game tests on a dedicated server: persistence, ant allocation, production and
  its limits, starvation, breeding, farming, fighting, evolution, Queen rearing, hybrids, the laboratory and its power,
  the colony ring, offline catch-up, tweezers, world generation, advancements, redstone, Chitin gear, Guard Ants, the giant
  anthill, data loading, and the modpack guide's examples loaded as a data pack. All of them must pass.
- `./gradlew runClient` with an empty file named `ingame-showcase` in `run/` builds a demo world, takes screenshots of
  the colonies, every screen, the recipe viewers, the anthill and the new items into `run/screenshots`, writes what it
  saw to `run/ingame-showcase-result.txt`, and quits. Use it to check anything visual.

## Pull requests

1. Keep a change focused on one thing, and describe what it changes for players.
2. Run the tools you touched and commit their output with the change.
3. Run the game tests.
4. Add a line to the `[Unreleased]` section of [CHANGELOG.md](CHANGELOG.md).
5. Code style: 4 spaces, lines up to 150 characters, Javadoc on public classes and on anything that isn't obvious.

By contributing you agree that your contribution is released under the project's [MIT License](LICENSE).
