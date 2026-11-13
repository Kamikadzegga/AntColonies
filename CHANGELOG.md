# Changelog

All notable changes to Ant Colonies are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org).
Jars are named `antcolonies-<minecraft version>.<mod version>.jar`.

## [Unreleased]

## [0.2.0] - 2026-11-13

The anthill update.

### Added

- **Grown anthills:** every new anthill's nest grows room by room: small, medium or large (6 to 17 rooms), with winding
  tunnels, loops, dead ends and side entrances beside the mound. Soldiers watch some of the tunnels.
- **Anthills by biome:** termite spires in savannas and banded ones in the badlands, a pebble crater in the desert, a
  thatched dome of needles in the taiga, a snow-capped mound in the cold, a mossy mound in the jungle, a mud mound in
  swamps, mycelium in mushroom fields and petals in cherry groves. Ant trails lead away from the mound, and each look
  carries on underground (packed ice, sandstone, termite clay, moss and glow lichen, mud bricks...).
- **The great Queen:** every anthill's Queen is now twice the size of any other, in a great chamber behind an
  antechamber of her royal guard, bigger and stronger soldiers.
- **New rooms:** the royal guard's antechamber, middens with suspicious gravel (or sand) to brush, and aphid farms.
- **Anthill alarm:** in a full Chitin set the ants still let you pass, but opening or breaking one of the anthill's
  chests, stepping into the Queen's chamber or striking one of its ants raises the alarm: for five minutes every ant of
  that anthill attacks you, workers too. Config `anthillAlarmSeconds`.

### Changed

- Anthills are more common: about one per 20 x 20 chunks (was 32 x 32), the Nether's one per 18 x 18 (was 24) and the
  End's one per 20 x 20 (was 28). They now also appear in swamps, mushroom fields, cherry groves, snowy plains and groves.
- Anthills hold more ants: busier rooms, soldiers out on the mound, guards in treasure and chitin rooms.
- Anthills already in a world keep their old look and layout; only new ones grow.
- Terrariums draw faster: only the glass sides facing you are drawn, and their vertices are written in one go. With 100
  colonies in view the frame rate went from about 120 to about 210 in testing.

## [0.1.0] - 2026-11-12

The colony update.

### Added

- **Founding:** a lone Queen in an empty Terrarium starts a colony on her own once you fill her food bar.
- **Queens grow:** after some time at each new colony stage, a Queen gains two trait levels (up to level 7).
- **Food matters:** colonies low on food slow down. The Terrarium has its own food slot.
- **Site modes and priorities:** focus a Foraging Site on mineral or organic finds; give sites a priority or turn them
  off.
- **Expeditions:** Nether and End Foraging Sites let Nether and End ants bring back that dimension's finds from home.
- **Colony Connectors:** link colonies up to 30 blocks apart to share food and items.
- **Anthills:** seven new layouts, an End anthill, and a Queen to catch in every one.
- **Building blocks:** 26 new blocks in five families (anthill, chitin, amber, fungal, royal) with stairs, slabs and
  walls.
- **Chitin gear tiers:** Hardened, Infernal and Void tools and armor, upgraded at a smithing table. The armor has jaws,
  spines and wings.
- **New colony screen:** eight tabs, with a Visual and a Detailed mode, including production, network and statistics.
- JEI and EMI expeditions category, Jade info for the new blocks, and 8 new advancements.

### Changed

- **Chitin comes only from ants:** wild ants drop it (Infernal Chitin in the Nether, Void Chitin in the End) and
  foraging colonies find it. It can no longer be crafted.
- New look for the Generator (a steel coal generator) and the Genetic Laboratory (white).
- Chitin Bricks redrawn.
- The colony screen is easier to read: no numbers over coloured bars, no text outside the window.

### Fixed

- Some anthills were missing the chest in their chitin storage room.

## [0.0.2] - 2026-11-09

### Added

- Climate blocks take their variants: every terracotta colour (glazed too), every sand and sandstone, every ice, dirt,
  grass, mud and moss block, polished stones and cobblestone, polished and smooth basalt and polished blackstone. The
  Info page shows one icon per family that cycles through its variants, with how many there are in its tooltip.
- More Storage: a Storage starts with 18 slots and each Storage Upgrade tier adds 18 (Storage IV: 90, two Storage IV:
  162). The screen shows six rows and scrolls, and shift-clicking fills the whole Storage, rows off screen too.

### Changed

- Colony upgrade tooltips read like vanilla's: a heading per block ("In Storage:") with short values under it
  ("90 slots (+72)", "5 stacks per slot", "+100,000 food room"), no line long enough to wrap.
- A Storage Upgrade can't be taken out while the Storage holds more than would fit without it (stacks bigger than a
  slot would take, or items in rows that would close); its tooltip says why.
- The config option `storageSlotsPerUpgrade` is now `storageSlotsPerTier` (default 18).

### Fixed

- Genetic Laboratory: the cost card and the Upgrade tooltip showed the first trait's materials whatever trait was
  selected, so other traits seemed impossible to upgrade. They now show the selected trait's cost.

## [0.0.1] - 2026-11-09

The first release, for Minecraft 26.3 and NeoForge 26.3.0.101 (Java 25).

### Added

**Colonies**

- Colonies simulated as numbers on the server: a Queen, eggs, larvae, workers, food and health, with limits on offline
  catch-up and diminishing returns per colony. The ants you see in a Terrarium are pictures of those numbers, so even a
  colony of thousands costs almost nothing.
- The Terrarium works on its own. Chambers join the colony when they touch the Terrarium or another of its chambers, on
  any side and in any shape (up to 32), and the whole build becomes one glass case: Nest Chambers (room and eggs),
  Foraging Sites (workers bring finds), Harvesting Sites (farmer species grow food), Hunting Grounds (hunter species kill
  mobs for their drops; no mob is ever spawned), Storage and Queen Chambers (raising and crossing Queens).
- Climate: each species likes temperate, cold, hot, underground or nether conditions, and a block under the Terrarium
  sets its climate (block tags `antcolonies:climate/*`).
- Colony stages 1 to 4: bigger colonies find rarer things.
- Colonies move in Test Tubes: an empty tube takes the Queen out, others fill with up to 1,000 ants each.
- Colonies feed themselves from their stores: food items in Storage and Foraging Sites are eaten whenever there is
  room for them (feeding comes first), worth what the feed slot gives. Royal Jelly is kept for raising Queens (item tag
  `antcolonies:kept_in_storage`); the `storageFeeding` option turns it off. The Colony page shows the food from Storage.
- Storage compacts: each Storage Upgrade tier adds slots, a stack per slot (tier II: three stacks in each slot) and
  25,000 food room. Hoppers, pipes and the ants use the bigger slots; the screen hands out a normal stack at a time.

**Species and progression**

- 46 species: 16 wild species with nests in their biomes (among them the Army Ant, a hunter, the Nether's Warped Ant and
  the End's Chorus Ant), mineral species that evolve from them, 11 hybrids from crossing Queens, and the boss-gated
  Wither Ant (Soul Ants fed a Nether Star) and Dragon Ant (Ender Ants fed the dragon's breath).
- 13 more species that load when MachinaOpus, Mekanism, Create, Thermal, Immersive Engineering or AE2 add their
  materials; the Molybdenum, Sulfur and Rubber Ants load only with MachinaOpus.
- Evolutions bound to a dimension (`"dimension"` in the evolution JSON): the Nether's line (Fire, Inferno, Soul,
  Netherite, Wither) evolves only in a Terrarium in the Nether, the End's (Ender, Dragon) only in the End.
- The Chitin ladder: Hardened Chitin (Blast Furnace), Infernal Chitin (ghast tear, blaze powder, nether wart) and Void
  Chitin (shulker shells, popped chorus, eye of ender).
- Ten colony upgrades (speed, food efficiency, simulation, storage, breeding, genetic, hunting, automation, population,
  evolution) in tiers I to IV: tier II takes Hardened Chitin, III Infernal Chitin, IV four Void Chitin and two netherite
  ingots. Species upgrades come from the Colony Upgrade Station.

**Genetics**

- Ten traits per Queen, inherited with variation, and 31 mutations including flaws.
- The FE-powered Genetic Laboratory raises, rerolls, mutates and purges traits and samples Queens; trait levels 8 and 9
  take Infernal and Void Chitin, level 10 a Nether Star. The Generator burns fuel to power it, or any mod's FE does.

**The world**

- Wild nests in their species' biomes, including the Nether's fungus forests and the End's outer islands. Tweezers and
  an empty Test Tube take ants from them; disturbed nests send soldiers.
- Giant anthills: rare mounds over tunnels dug deep into the ground, with loot, defenders and a Queen to catch.
- Nether anthills: the same nest dug into the Nether's floor, in soul soil and blackstone, home to Fire Ants (Warped
  Ants in the fungus forests), with the Nether's loot.
- Home nests: a wild nest picked up with Silk Touch and placed becomes home to three ants, like bees in a beehive. In
  daylight they tend crops and saplings within 10 blocks, harvest ripe crops, replant them and carry the harvest into
  the nest, where hoppers and pipes take it out. Options `nestCropGrowth` and `nestHarvest`.

**Gear and companions**

- Chitin tools, armor and shield, iron tier, in overlapping scales. The helmet is an ant's head with antennae that
  stand out in 3D when worn; the full set makes wild ants ignore you and lets you dig 20% faster. The Chitin Shield is
  raised in 0.15 s, lasts 450 uses and is mended with Chitin Plates.
- Guard Ants: a hunter colony gives up 50 of its ants for a tamed Guard Ant that follows you and fights for you, as
  strong as the colony's genetics.

**Interface and integration**

- The Terrarium's Info page (finds, hunts, climate, evolutions), the Ant Identification Book with how-to-play pages,
  detailed tooltips, and an in-game config screen with an explanation for every option.
- JEI and EMI recipe categories for the whole progression, including a Colony Upgrades page for every upgrade and tier
  (where it goes and what it does), and Jade tooltips for colonies, machines, wild ants and nests.
- Colony upgrade tooltips say in numbers what the upgrade does at its tier in each block it goes in.
- 27 advancements, including a path through the Nether and the End, and the `antcolonies:colony` trigger for modpacks
  and quest mods.
- Comparator outputs and redstone control. Items and FE move through any mod's pipes and cables.
- Data-driven species, evolutions, hybrids, mutations, lab costs, ant food and loot, a full server config, and a modpack
  and KubeJS guide.
- Tested together with MachinaOpus and its Tools Addon: no clashing recipes or names.

[Unreleased]: https://github.com/Kamikadzegga/AntColonies/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Kamikadzegga/AntColonies/releases/tag/v0.2.0
[0.1.0]: https://github.com/Kamikadzegga/AntColonies/commit/340502d
[0.0.2]: https://github.com/Kamikadzegga/AntColonies/commit/541da99
[0.0.1]: https://github.com/Kamikadzegga/AntColonies/commit/70e9760
