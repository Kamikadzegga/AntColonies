# Contributing

Thanks for wanting to help. This page explains how the project is laid out, the one rule about generated files, and
what a pull request needs.

## Requirements

JDK 25. The Gradle wrapper downloads Gradle, NeoForge and Minecraft.

## Project layout

The code is in part folders at the root, compiled together into one mod jar (see `sourceSets` in `build.gradle`).

```
AntColonies/
├── common/                         everything that runs on both the server and the client
│   └── src/
│       ├── main/java/com/antcolonies/
│       │   ├── AntColonies.java    the mod's entry point
│       │   ├── colony/             the colony logic: model, simulation, species, genetics, breeding, evolution
│       │   ├── content/            what is in the world: blocks, block entities, entities, items, menus, worldgen
│       │   └── core/               registries, config, advancements
│       ├── main/resources/         the mod icon and logo
│       └── generated/resources/    assets and data written by the tools (see below)
├── client/                         client only: rendering, models, screens
├── compat/                         JEI, EMI and Jade plugins, loaded only with those mods
├── tools/                          generators for data, textures and branding
└── docs/                           guides and images
```

## Generated files

Every JSON file and every texture in `common/src/generated/resources` is written by a program in [`tools/`](tools):

| Tool | Writes |
|---|---|
| `tools/DataGen.java` | Data and models: species, recipes, loot, tags, world generation, advancements, blockstates, the language file |
| `tools/TexGen.java` | Every block, item, entity, particle and GUI texture |
| `tools/BrandGen.java` | The mod icon and logo, and the banner and social preview in `docs/images` |

**Change the tool, not its output.** The next run of the tool overwrites hand edits. Run the tools from the project
root with JDK 25:

```sh
java tools/DataGen.java
java tools/TexGen.java
java tools/BrandGen.java
```

[`tools/README.md`](tools/README.md) has more, such as how to add a fluid for Liquid Ants.

## Pull requests

1. Keep a change focused on one thing, and describe what it changes for players.
2. Run the tools you touched and commit their output with the change.
3. Check that `./gradlew build` passes, and try the change in the game with `./gradlew runClient`.
4. Add a line to the `[Unreleased]` section of [CHANGELOG.md](CHANGELOG.md).

Code style: 4 spaces, lines up to 150 characters. Keep comments short: one line saying what a class, method or field is
for. How a feature works belongs in [`docs/`](docs), not in the code.

By contributing you agree that your contribution is released under the project's [MIT License](LICENSE).
