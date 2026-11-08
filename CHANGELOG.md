# Changelog

All notable changes to Ant Colonies are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org).
Jars are named `antcolonies-<minecraft version>.<mod version>.jar`.

## [Unreleased]

### Added

- An in-game config screen: Mods → Ant Colonies → Config, with readable names and explanations for every option.
- Upgrade items show their tier (I, II or III) on their icon in every slot and inventory.

### Changed

- A colony is now one 3x3 build: the Terrarium in the middle and any 8 chambers around it (sides and corners, same
  level). It works once all 8 places are filled. Glass Ant Tubes, Underground Tunnels and the Colony Connector are gone.
- Twelve structures became seven chambers: Brood Chamber (was Breeding Chamber and Ant Nursery), Housing (was Worker
  Housing and Soldier Barracks), Storage (was Resource Pile and Food Storage), Foraging Chamber (was Scavenging Site),
  Fungus Garden (was Farmer Ant Garden), Hunting Chamber (was Fighting Site) and Queen Chamber. The Resource Processing
  Chamber is gone; Processing species upgrades still turn finds into their processed form.
- The Genetic Laboratory is one block that takes FE from any side. Its five parts and the Genetic Energy Core are gone.
- Food goes in only through the Terrarium. Storage adds food room.
- Ants inside chambers are drawn only up close (`antDetailDistance`) with 3D models on: a view of 25 colonies runs
  about 80% faster.
- Worlds from 0.1.0: the removed and renamed blocks disappear. Rebuild colonies as rings.

### Fixed

- Terrariums never scanned for their structures in a real game, so nothing linked to them; climate was never read and
  evolution was never attempted. A timer started at the lowest possible value and "now - then" overflowed.
- The "not linked" line on colony structure screens ran past the edge of the panel.
- Upgrades in a Terrarium vanished from its screen a moment after being put in: the updates sent to players left them
  out and emptied the slots (the upgrades kept working).

## [0.1.0] - 2026-11-08

The first version, for Minecraft 26.3 and NeoForge 26.3.0.101.

### Added

- Colonies simulated as numbers on the server: eggs, larvae, workers, soldiers, food and health, with offline catch-up
  limits and diminishing returns per colony.
- The Terrarium and 12 colony structures, joined by glass Ant Tubes and Underground Tunnels, with a shared console-style
  interface.
- 38 species: 13 wild species with nests, 14 evolved mineral species and 11 hybrids, plus 10 species that load when
  Mekanism, Create, Thermal, Immersive Engineering or AE2 provide their materials.
- Genetics: ten traits, inheritance with variation, and 31 mutations including flaws. The FE-powered Genetic Laboratory
  upgrades, rerolls, mutates, purges and samples Queens, and a Generator powers it.
- Giant anthills: a rare world-generation structure with tunnels, loot, a Queen and defending soldiers.
- Chitin tools and armor, with a full-set bonus: wild ants leave you alone and you dig 20% faster.
- Guard Ants: a colony gives up soldiers for a tamed defender whose stats come from its Queen's genetics.
- Comparator outputs and redstone control for Terrariums, job sites and machines.
- 16 advancements and the `antcolonies:colony` advancement trigger for modpacks and quest mods.
- JEI and EMI recipe categories, and Jade tooltips.
- Data-driven species, evolutions, hybrids, mutations, lab costs, ant food and loot, a full server config, and a
  modpack and KubeJS guide.

[Unreleased]: https://github.com/Kamikadzegga/AntColonies/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Kamikadzegga/AntColonies/releases/tag/v0.1.0
