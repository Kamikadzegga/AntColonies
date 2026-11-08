<div align="center">

<img src="docs/images/logo.png" alt="Ant Colonies" width="456">

**Build a civilization, one ant at a time.**

A NeoForge mod for Minecraft 26.3: catch wild ants, raise Queens in living Terrariums, put thousands of workers and
soldiers to work, evolve mineral species and engineer their genes.

[![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=flat-square)](https://www.minecraft.net)
[![NeoForge 26.3.0.101](https://img.shields.io/badge/NeoForge-26.3.0.101-D7742F?style=flat-square)](https://neoforged.net)
[![Java 25](https://img.shields.io/badge/Java-25-B07219?style=flat-square)](https://adoptium.net)
[![License: MIT](https://img.shields.io/badge/License-MIT-3DA639?style=flat-square)](LICENSE)
[![Build](https://github.com/Kamikadzegga/AntColonies/actions/workflows/build.yml/badge.svg)](https://github.com/Kamikadzegga/AntColonies/actions/workflows/build.yml)

[Features](#features) · [Screenshots](#screenshots) · [Installation](#installation) ·
[Documentation](#documentation) · [Building](#building-from-source) · [License](#license)

</div>

![A stage 4 Diamond Ant colony: a Terrarium and its chambers, joined into one build](docs/images/screenshots/overview.png)

## Features

- **Living colonies.** A Terrarium holds a whole colony: Queen, eggs, larvae, workers, soldiers, food and health. You
  can watch the ants dig tunnels, carry food and work in the chambers. The simulation is plain numbers on the server,
  so even a colony of thousands costs almost nothing.
- **One build that grows.** A Terrarium works on its own. Chambers placed against it join the colony and merge into one
  glass case: Nest Chambers for a bigger colony, Foraging Sites where workers find resources and soldiers hunt for mob
  drops (no mob is ever spawned), Harvesting Sites for farmers, Storage and Queen Chambers. A block under the Terrarium
  sets its climate.
- **38 species.** Thirteen wild species live in their own biomes, with nests. Mineral species (iron, gold, diamond,
  emerald, netherite, ender and more) evolve from them, and eleven hybrids come from crossing Queens. Ten more species
  turn on when Mekanism, Create, Thermal, Immersive Engineering or AE2 add their ores.
- **Genetics.** Each Queen has ten traits, and daughters inherit them with variation. There are 31 mutations, including
  flaws. The FE-powered **Genetic Laboratory** upgrades traits, rerolls, mutates and purges Queens and copies their
  genes.
- **Giant anthills.** A rare world-generation structure: a mound over tunnels and chambers dug deep into the ground,
  with loot, a Queen to catch and soldiers that defend it.
- **Chitin gear and Guard Ants.** Iron-tier tools and armor made from Chitin Plates; with the full set, wild ants take
  you for one of their own. A colony can also give up its soldiers for a **Guard Ant** that follows you and fights for
  you, as strong as the colony's genetics.
- **Built for automation and modpacks.** Comparator outputs and redstone control. Items and FE move through any mod's
  pipes and cables. Species, evolutions, hybrids, mutations, lab costs, ant food and loot are all data packs, and a
  server config sets every number.
- **Explained in game.** The Terrarium's Info page shows what its ants find and hunt, the climate they need (and the
  block that fixes it) and what they evolve into. Tube tooltips and the Ant Identification Book say the same, and the
  book has how-to-play pages.
- **Integrations.** JEI and EMI recipe categories for the whole progression, Jade tooltips (colonies, wild ants and
  nests), and 16 advancements.

## Screenshots

| | |
|:---:|:---:|
| ![Terrarium](docs/images/screenshots/terrarium.png) | ![Colony screen](docs/images/screenshots/colony-screen.png) |
| A living Terrarium | The colony screen |
| ![Genetic Laboratory](docs/images/screenshots/genetic-laboratory.png) | ![Recipe viewer](docs/images/screenshots/recipe-viewer.png) |
| The Genetic Laboratory at work | The whole progression in JEI and EMI |
| ![Giant anthill](docs/images/screenshots/giant-anthill.png) | ![Inside a giant anthill](docs/images/screenshots/anthill-queen-chamber.png) |
| A giant anthill | The Queen's chamber below it |
| ![Guard Ant and Chitin armor](docs/images/screenshots/guard-ant-and-chitin.png) | ![Jade tooltip](docs/images/screenshots/jade.png) |
| A Guard Ant and a full set of Chitin armor | A Terrarium at a glance with Jade |
| ![Ant Identification Book](docs/images/screenshots/identification-book.png) | |
| The Ant Identification Book: how to play, and every species | |

## Installation

1. Install [NeoForge](https://neoforged.net) **26.3.0.101** or newer for Minecraft **26.3** (Java 25).
2. Put `antcolonies-<version>.jar` into your `mods` folder. The mod is needed on both the server and the client.
3. Optional companions:

| Mod | What it adds |
|---|---|
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) 31.17+ | 13 recipe categories: species, evolutions, hybrids, production, the laboratory and more |
| [EMI](https://modrinth.com/mod/pf-emi) (the 26.3 port, PF-EMI 1.1) | The same 13 categories in EMI |
| [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) 26.3.5+ | Look at a Terrarium, job site or machine for its state |

To start, craft **Tweezers** and **Test Tubes**, catch a wild Queen, and put her in a **Terrarium** with some ant food.
The [gameplay guide](docs/gameplay.md#getting-started) takes it from there.

## Documentation

| Guide | For |
|---|---|
| [Gameplay guide](docs/gameplay.md) | Players: colonies, structures, species, genetics, anthills, gear and settings |
| [Modpack and KubeJS guide](docs/modpack-guide.md) | Pack makers: every data file and field, recipes, food, spawns, advancements, config |
| [Compatibility](docs/compatibility.md) | Recipe viewers, Jade, and how it works with tech mods' pipes, cables and ores |
| [Changelog](CHANGELOG.md) | What changed in each version |

## Building from source

You need JDK 25. Gradle and the right NeoForge version are downloaded by the wrapper.

```sh
git clone https://github.com/Kamikadzegga/AntColonies.git
cd AntColonies
./gradlew build                # the jar ends up in build/libs/
./gradlew runGameTestServer    # runs the game tests
./gradlew runClient            # starts a development client
```

Data files, textures and branding are generated by the programs in [`tools/`](tools). See
[CONTRIBUTING.md](CONTRIBUTING.md) for how the project is laid out and how changes are tested.

## License

Ant Colonies is released under the [MIT License](LICENSE). Copyright (c) 2026 Kamikadzegga.

Minecraft is a trademark of Mojang Studios. This project is not affiliated with Mojang Studios or Microsoft.
