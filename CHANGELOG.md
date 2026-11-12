# Changelog

All notable changes to Ant Colonies are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org).
Jars are named `antcolonies-<minecraft version>.<mod version>.jar`.

## [Unreleased]

## [0.1.0] - 2026-11-12

The colony update: founding colonies from a single Queen, food as what keeps colonies working, Foraging Site modes and
job priorities, expeditions into the Nether and the End, Colony Connectors between colonies, anthills planned in seven
layouts, a building-block palette, and a new Terrarium screen.

### Added

**Founding and food**

- Queen-only founding: a single Queen put into a completely empty Terrarium waits (eating nothing) until its food bar is
  full, then lays her first brood; once her first 10 workers are grown the colony is founded. Only that case: a Queen
  put in with workers, eggs or larvae, a colony moved tube by tube, or a Queen added to a colony with ants runs normally
  at once (ants put in at any time end founding). Saved with the colony; colonies from older worlds load as normal
  colonies. Config `[founding] foundingFoodShare` (1.0) and `foundingWorkers` (10).
- Food holds up work: below `lowFoodMinutes` (10) of food at what the colony eats, work, egg laying and larvae growth
  slow evenly to `lowFoodFactor` (60%), and the Terrarium says "Food running low". The jobs that bring food in
  (Harvesting Sites, Hunting Grounds) never slow below `hungryFoodWorkFactor` (80%), so a hungry colony can always feed
  itself again.

**Production**

- Foraging Site modes: Balanced (as before), Mineral Gathering and Organic Gathering, which pick finds of their kind
  three times as often (item tag `antcolonies:foraging/mineral`; `focusedForagingBoost`, `focusedForagingPenalty`). The
  species still decides what can be found; biomes never change production.
- Every working site has a priority (Low, Normal, High: higher ones get their ants first) and can be turned off, from its
  own screen or the Terrarium's Jobs page.
- Nether Foraging Site and End Foraging Site: two new chambers with a little portal on their floor. Nether-adapted ants
  (species tag `antcolonies:nether_adapted`: Warped, Fire, Inferno, Soul, Netherite, Wither, Crimson) and End-adapted
  ants (`antcolonies:end_adapted`: Chorus, Ender, Dragon) go on simulated expeditions and bring back finds from the
  expedition tables (`data/<namespace>/antcolonies/expedition/*.json`, a new datapack registry). No power, no chunk
  loading, no ant entities; other species leave them alone and the site names the ants it needs. Their portals glow
  while ants are out.
- Nether Trail Pheromone and End Trail Pheromone: found now and then by the ordinary Foraging Sites of a colony living
  in the Nether or the End (2% of finds); the expedition sites are made from them, with Infernal or Void Chitin.
- Ant Resin, which Wood Ants now find, for the Amber building blocks.

**Colony Connectors**

- A new chamber that links colonies on the same channel (16 colours) in one dimension, up to 30 blocks apart on x and z (`connectorRange`): Supply gives the food above a
  reserve, Request takes food up to a target, the hungriest colony first, up to a rate limit; Send moves the items in
  its filter out of its colony's Storage to colonies that Receive. Food and items are moved, never made or lost; only
  loaded colonies take part; a redstone signal stops a connector; no power. Config `[connectors]`.

**Anthills**

- Every anthill has exactly one Queen, who can't be hurt, never despawns and can only leave in a Test Tube.
- Anthills are planned in seven layouts (branching, deep, multi-level, spiral, central chamber, wings, abandoned) from a
  dozen kinds of room (Queen's chamber, nursery, egg store, larval chamber, food store, fungus garden, barracks, chitin
  store, treasure chamber, rare-find chamber, gallery, hall), always joined to the entrance. New loot tables
  `chests/anthill_treasure` and `chests/anthill_food`.
- The End anthill: a Chorus Ant nest in the End's outer islands (`antcolonies:end_anthill`, loot `chests/end_anthill`).

**Building blocks**

- 26 blocks in five families (Anthill, Chitin, Amber, Fungal, Royal) plus the Nest Door and Trapdoor, the Pheromone
  Lamp, Ant Egg and Larva Clusters and Terrarium Glass; stairs and slabs for 8 of them and walls for 4; crafting and
  stonecutter recipes.

**Chitin gear tiers**

- Three new tiers of chitin tools (sword, pickaxe, axe, shovel, hoe) and armor, each harder to make and stronger:
  - **Hardened Chitin**: crafted like Chitin gear from Hardened Chitin. Past iron: armor 2/7/5/2 with toughness 1,
    900-use tools, still no obsidian. Set bonus: dig 25% faster.
  - **Infernal Chitin**: a Hardened piece, an **Infernal Reinforcement** and an Infernal Chitin at a smithing table.
    Diamond level (3/8/6/3, toughness 2.5, 1800 uses, mines obsidian); doesn't burn. Set bonus: dig 30% faster and Fire
    Resistance.
  - **Void Chitin**: an Infernal piece, a **Void Reinforcement** and a netherite ingot at a smithing table. Past
    netherite (3/8/7/3, toughness 3.5, knockback resistance 0.15, 2400 uses, speed 9.5, +4.5 damage); doesn't burn.
    Set bonus: dig 40% faster and Fire Resistance.
- Reinforcements (smithing templates) are crafted two at a time: Infernal from Infernal Chitin, blaze rods, magma cream
  and a ghast tear; Void from Void Chitin, shulker shells, popped chorus fruit and an eye of ender.
- A full set of any chitin, tiers mixed, still makes wild ants leave you alone; its other bonuses follow its lowest
  tier. Each tier is repaired with its own chitin. Every helmet is the 3D ant's head, now with a pair of jaws, in its
  tier's colours; Hardened and Infernal chestplates carry two spines on the back and the Void chestplate a Queen's
  folded wings.
- An advancement for a full Void set: Carapace of the Void (35 in all).

**Queens grow, and a food slot**

- A Queen grows with her colony, in her own time: after 10, 20 and 30 minutes (config `queenGrowthMinutes`) at a
  bigger colony stage she grows into stage 2, 3 and 4, one at a time, and two random traits go up by one, up to level 7. Saved with her (moving her doesn't grow her again); shown in her tooltip and on the Genetics page,
  with a note to players nearby.
- The Terrarium has a food slot beside the feed slot. Food goes there (by hand, shift-click, right-click or hopper) and
  waits quietly while the food bar is full, so Test Tubes, ants and evolution materials always fit in the feed slot.

**Colony GUI 2.0**

- The Terrarium's screen has eight tabs with icons: Overview, Colony (cards for the Queen, eggs, larvae, workers and the
  ants at work), Jobs, Production (finds per minute from the sites' real rates, and the Storage's contents), Genetics,
  Network (the linked colonies and the food between them), Statistics (charts of the last half hour) and Info.
- Visual Mode (pictures, bars, short notes) and Detailed Mode (exact numbers and rates), switched at the top left and
  remembered (client config `detailedColonyView`).
- The Colony Connector's screen; mode, priority and on/off buttons on every working site's screen.
- JEI and EMI: an Expeditions category. Jade: connectors (channel, settings, flow), expedition sites (active or dark),
  Foraging Site modes.
- Seven advancements: From a Single Queen, Trail Network, Scent of Sulphur, Portal Foragers, Scent of the Void, Beyond the
  Rift, Island Hoppers.

### Changed

- **Chitin comes only from ants.** Chitin Plates, Hardened, Infernal and Void Chitin can no longer be crafted or
  smelted. Wild ants drop Chitin Plates (35%) and Hardened Chitin (5%) anywhere, Infernal Chitin (15%) only in the
  Nether and Void Chitin (12%) only in the End, more with Looting. Foraging colonies bring chitin home with their
  finds (config `chitinFindChance`, 4%): plates, Hardened Chitin from stage 2, Infernal or Void Chitin when they live
  in the Nether or the End.
- Chitin Bricks redrawn: dark, glossy carapace plates overlapping like an ant's abdomen, instead of a basket weave.
- The Generator and the Genetic Laboratory redrawn in a modern look of their own: brushed steel and white enamel,
  shaded evenly. The Generator is a coal generator with a firebox window (coal glowing and flames while it burns); the
  lab has a double-helix display, a glowing sample cylinder and a glass sample hatch.
- Anthill walls use the new Anthill Soil blocks. Anthills generated before this version keep their old layout.
- The Terrarium screen is larger (252 x 238) and its slots for Queen, food and upgrades stay where they were.
- Easier to read: status chips and numbers sit on solid dark plates or have a shadow, never straight on a coloured bar;
  the Production cards put the stored count under the icon and the rate bar under the count; the Statistics chart
  keeps its scale outside the chart (top value and time span above it, bottom value under it) and fills evenly.

### Fixed

- An anthill's chitin storage room sometimes had no chest (a Chitin Block was placed on its spot).

### Notes

- Colonies still have no soldiers (they became workers in 0.0.1); the Colony page shows the ants at work by job instead.
- The resin blocks are named Amber (Amber Block, Amber Bricks, Amber Glass, Reinforced Amber) so they never clash with
  vanilla's Resin Block and Resin Bricks.

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

[Unreleased]: https://github.com/Kamikadzegga/AntColonies/compare/v0.0.2...HEAD
[0.0.2]: https://github.com/Kamikadzegga/AntColonies/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/Kamikadzegga/AntColonies/releases/tag/v0.0.1
