# Changelog

All notable changes to Ant Colonies are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org).
Jars are named `antcolonies-<minecraft version>.<mod version>.jar`.

## [Unreleased]

### Added

- An in-game config screen: Mods → Ant Colonies → Config, with readable names and explanations for every option.
- An Info page in the Terrarium: what the colony's ants find (with chances) and hunt, the climate they need and the
  blocks that fix it, and what they evolve into.
- Climate blocks: a block under the Terrarium gives it a climate, whatever the biome (snow or ice cold, sand hot, grass
  or dirt temperate, stone or deepslate underground, netherrack or magma nether). Block tags `antcolonies:climate/*`.
- The Ant Identification Book has how-to-play pages, and its species pages show the same as the Info page.
- Queen and ant tube tooltips show the species' climate, finds, best traits, and with Shift its evolutions.
- Jade tooltips for wild ants and wild nests.
- The creative tab shows the ant's head from the mod's logo.

### Changed

- Chambers are additions, not requirements: a Terrarium works on its own. A chamber joins when it touches the Terrarium
  or another chamber of the colony, on any side, in any shape (up to 32). Every block is the same glass case on a wooden
  base; touching blocks open into each other, so the whole build is one closed glass case with one frame around it. A chamber exactly as near to two Terrariums joins neither.
- Twelve structures became five chambers: Nest Chamber (was Breeding Chamber, Ant Nursery, Worker Housing and Soldier
  Barracks), Foraging Site (was Scavenging Site and Fighting Site: workers find, soldiers hunt, and it holds 9
  stacks of finds while the Storage is full), Harvesting Site (was Farmer Ant Garden), Storage (was Resource Pile and Food Storage) and Queen Chamber.
  Glass Ant Tubes, Underground Tunnels, the Colony Connector and the Resource Processing Chamber are gone; Processing
  species upgrades still turn finds into their processed form.
- The Genetic Laboratory is one block that takes FE from any side. Its five parts and the Genetic Energy Core are gone.
- Food goes in only through the Terrarium. Storage adds food room.
- A Nest Chamber's screen shows the room it adds, with its Population Upgrades.
- The Terrarium's Colony page takes workers or soldiers into a Test Tube (up to 1,000 a tube) to add to another colony.
  A broken Terrarium keeps its whole colony; its tooltip and the book now say so.
- Foraging Sites send their finds to Storage, keeping them only while the Storage is full.
- Shift-clicking an upgrade puts it only into an upgrade slot, never into a store or the feed slot.
- Ants inside chambers are drawn only up close (`antDetailDistance`) with 3D models on: a view of 25 colonies runs
  about 80% faster.
- Worlds from 0.1.0: the removed and renamed blocks disappear. Rebuild colonies by placing chambers against the
  Terrarium.

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
