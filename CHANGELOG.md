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
- Progression through the Nether and the End:
  - Evolutions can be bound to a dimension (`"dimension"` in the evolution JSON). The Nether's line (Fire, Inferno, Soul,
    Netherite, Wither) evolves only in a Terrarium in the Nether, the End's (Ender, Dragon) only in the End.
  - The Chitin ladder: Hardened Chitin (Blast Furnace), Infernal Chitin (ghast tear, blaze powder, nether wart), Void
    Chitin (shulker shells, popped chorus, eye of ender).
  - Two wild species with nests outside the Overworld: the Warped Ant (the Nether's fungus forests; becomes Fire Ants)
    and the Chorus Ant (the End's outer islands; becomes Ender Ants).
  - Two boss-gated endgame species: the Wither Ant (Soul Ants fed a Nether Star) and the Dragon Ant (Ender Ants fed
    dragon's breath).
- Tier IV upgrades, the endgame, for all ten upgrade types: each takes the tier III upgrade, four Void Chitin and two
  netherite ingots. A tier IV Simulation Upgrade allows 12 hours of offline catch-up.
- The Nether anthill: the giant anthill's nest dug into the Nether's floor, in soul soil and blackstone with the lava
  walled off, home to Fire Ants (Warped Ants in the fungus forests), with the Nether's loot.
- Eleven advancements on a path through the dimensions: the Chitin ladder, colonies in the Nether and the End, Warped
  and Chorus Ant colonies, the Nether anthill, a tier IV upgrade, and the Wither and Dragon Ants.
- Home nests: a nest picked up with Silk Touch and placed becomes home to three ants of its species, like bees in a
  beehive. In daylight they work the land within 10 blocks, growing crops and saplings on and harvesting ripe crops
  (replanting them), and carry the harvest into the nest, where hoppers and pipes take it out. They never attack you.
  Options `nestCropGrowth` and `nestHarvest`; Jade shows a home nest's ants and store.
- The Chitin Shield: blocks like a shield, raised faster (0.15 s instead of 0.25 s), knocked down by axes for less
  time, lasts longer (450 uses), mended with Chitin Plates.
- Chitin armor on baby zombies and other baby mobs (it was invisible on them).
- MachinaOpus support. The Tin, Lead, Silver, Uranium and Fluorite Ants also load with MachinaOpus and bring its
  materials. Three new species load only with it: the Molybdenum Ant (from Iron Ants fed molybdenum), the Sulfur Ant
  (from Fire Ants fed sulfur) and the Rubber Ant (from Wood Ants fed rubber), a farmer that taps rubber at a Harvesting
  Site. The Mechanical Miner Ant turns up raw molybdenum at stage 3.

### Changed

- Chambers are additions, not requirements: a Terrarium works on its own. A chamber joins when it touches the Terrarium
  or another chamber of the colony, on any side, in any shape (up to 32). Every block is the same glass case on a wooden
  base; touching blocks open into each other, so the whole build is one closed glass case with one frame around it. A chamber exactly as near to two Terrariums joins neither.
- Twelve structures became five chambers: Nest Chamber (was Breeding Chamber, Ant Nursery, Worker Housing and Soldier
  Barracks), Foraging Site (was Scavenging Site: it holds 9 stacks of finds
  while the Storage is full), Harvesting Site (was Farmer Ant Garden), Storage (was Resource Pile and Food Storage) and Queen Chamber.
  Glass Ant Tubes, Underground Tunnels, the Colony Connector and the Resource Processing Chamber are gone; Processing
  species upgrades still turn finds into their processed form.
- The Genetic Laboratory is one block that takes FE from any side. Its five parts and the Genetic Energy Core are gone.
- Food goes in only through the Terrarium. Storage adds food room.
- A Nest Chamber's screen shows the room it adds, with its Population Upgrades.
- Colonies move in Test Tubes: an empty tube in the Queen slot takes the Queen out, empty tubes in the feed slot fill
  with ants (a tube holds up to 1,000), and the Colony page's Take out buttons do the same from your inventory. A
  broken Terrarium no longer keeps its colony: the ants left inside are lost.
- When the Queen moves in, her empty tube goes to the output slot. The Queen's X button is gone.
- Hunters replace soldiers. Colonies have no soldiers any more (old ones join the workers; a caught wild soldier comes as
  a worker). Four hunter species work at the new Hunting Ground, killing mobs for their loot and feeding the colony on
  the catch: the Army Ant lives wild in plains and savannas; the Bullet, Trap-Jaw and Inferno Ants evolve from Jungle,
  Swamp and Fire Ants fed gunpowder, slime balls and blaze powder. The Soldier Upgrade is now the Hunting Upgrade, and
  Soldier Strength is Hunting Strength. Guard Ants come from hunter colonies, for 50 of their ants.
- Chitin gear redrawn. Chitin is now overlapping scales, like a pangolin's, on the armor, its icons and the Chitin
  Plate. The Chitin Helmet is an ant's head with compound eyes, jaws and two elbowed antennae that stand out from the
  helmet in 3D when worn. The chestplate has an ant's narrow waist, shoulder plates and studded cuffs; the leggings a
  studded belt and knee caps; the boots dark soles. The tools are drawn like vanilla's, with straight handles.
- Harder recipes: tier II upgrades take Hardened Chitin (was gold), tier III Infernal Chitin (was diamonds), the
  Genetic Laboratory Hardened Chitin (was iron); raising a trait to level 8 takes Infernal Chitin (was emeralds) and to
  level 9 Void Chitin (was netherite scrap).
- Foraging Sites send their finds to Storage, keeping them only while the Storage is full.
- Lead Ants evolve into Uranium Ants when fed raw uranium (was a uranium ingot), and Uranium Ants find raw uranium
  from stage 1.
- Shift-clicking an upgrade puts it only into an upgrade slot, never into a store or the feed slot.
- Ants inside chambers are drawn only up close (`antDetailDistance`) with 3D models on: a view of 25 colonies runs
  about 80% faster.
- Worlds from 0.1.0: the removed and renamed blocks disappear. Rebuild colonies by placing chambers against the
  Terrarium.

### Fixed

- EMI showed the Chitin repair tag (`#antcolonies:repairs_chitin_gear`) without a name.

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
