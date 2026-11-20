# Changelog

All notable changes to Ant Colonies are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org).
Jars are named `antcolonies-<minecraft version>.<mod version>.jar`.

## [Unreleased]

### Added

- **A scanner in the Genetic Laboratory.** Put a material in the scanner slot of its Research page and press Scan. The
  right material reveals a species mutation or a gene for good; nothing is used up.
- **Genetic research.** 15 species mutations (12 more with other mods' metals) and 20 genes to discover, in three
  tiers. Research is saved per player and survives death and restarts.
- **Species Mutation in the Genetic Laboratory.** A researched mutation turns a Queen in her Test Tube into the new
  species: it always works and keeps her traits, mutations and lineage. Her colony keeps its ants when she moves back in.
- **Laboratory pages.** Research, Analysis, Mutation, Editing, Hybrids and Stability, named on tabs, each with its own
  slots. The laboratory lists only what you have discovered, and every tab explains its page step by step.
- **Mutation effects.** The laboratory, the Formicarium's Genetics page and a Queen's tooltip now say what each mutation
  does, such as "+25% hunting" for Iron Jaws. A flaw is listed in red with a minus.
- **Scroll bars.** The laboratory's lists, the Ant Identification Book and the Formicarium's Info page, Genetics page
  and Working Sites list have a scroll bar you can drag.
- **Stabilization.** Optional: lock a trait or protect a mutation with Royal Jelly, and it passes to daughters unchanged.
- **Ant Identification Book sections.** Species, Genetic Research, Ant Compatibility and Production fill in as you
  discover things; unknown entries show as question marks with a hint.
- **FTB Teams.** Optional: the players of a party share their research. Tested with FTB Teams 26.1.2.5.
- **FTB Library face icons.** Wild Ants and Guard Ants have a face icon (the ant head) where FTB mods show a mob's face.
- **For modpacks.** A `research` data registry, `lab_amount` on evolutions and a Species Mutation page in JEI and EMI.

### Changed

- **Mineral and endgame species are made in the laboratory.** Feeding a Formicarium no longer evolves a colony into
  them; it keeps the material and says so. `feedingMakesLabSpecies` in the server config brings feeding back.
- **Natural evolutions are unchanged.** Farmer, Fire, the hunters and the Liquid Ants still evolve by feeding, and Fire
  Ants still live in Nether anthills.
- **Existing worlds.** Colonies keep their species. The book fills in when you open a Formicarium or catch an ant.
- **The Queen Chamber explains its Genetic Sample.** Under its slots it names the hybrid and its chance, says when the
  Queens' traits are low for the cross, or says that the two species make no hybrid.
- **A hybrid's chance follows the two Queens' traits.** No trait level is required any more: weak Queens can cross, but
  with every trait at level 1 only a tenth of the listed chance is left. Each level the two Queens average above the
  cross's level adds 15% of it. `hybridWeakChance` and `hybridQualityBonus` in the server config set both numbers.
- **Both Queens' Mutation trait counts for a hybrid.** The chance was multiplied by the colony Queen's Mutation trait
  alone. It now takes the average of hers and the sample Queen's, so a strong Queen helps as much on either side.
- **Four Genetic Sample slots in the Queen Chamber.** It had one. Each new Queen uses up one sample, the left-most
  first, and hoppers can fill all four.

### Removed

- **The Genetic Analyzer.** A Queen's tooltip already showed what its report did, and now shows her lineage too.
  The Genetic Laboratory recipe takes a third Genetic Circuit in its place. Analyzers already in a world disappear.

### Fixed

- **Shift-click in the Queen Chamber.** A Genetic Sample or a Test Tube went into the material slots, where it did
  nothing. They now go to their own slots, and the material slots take only what the colony's species is raised with.
- **Shift-click in the Genetic Laboratory.** Reroll Serum landed among the materials, where Reroll could not find it,
  and any item at all went into the material, mutation and stabilizer slots. Each slot now takes only what its page uses.
- **Shift-click in the Formicarium.** Any item that is not food went into the feed slot and sat there. The slot now
  takes only ants, brood, empty Test Tubes, Evolution Catalysts and the colony's own materials.
- **Shift-click in the Upgrade Station.** A species upgrade could not be shift-clicked in to raise its tier, and any
  item went into the material slot. Both slots now take only what the station works with.
- **Shift-click in job sites and Storages.** Items no longer go into a job site's store, which is for its ants' finds.
  A Storage now also stores upgrades it has no slot for.
- **A log warning for every generated nest.** Each wild nest the world generated left a "Tried to load a block entity"
  warning in the server log. Nests now generate without it; nests already in a world are unchanged.

## [0.3.3] - 2026-11-20

### Fixed

- **Home nest ants searched the land one by one.** Every ant out of a home nest looked at each block around the nest
  every few seconds. The nest now looks once every five seconds for all its ants and skips ground with no crops.

## [0.3.2] - 2026-11-17

### Changed

- **Fewer code comments, no gameplay change.** Comments that only repeated a name were removed from the source.

## [0.3.1] - 2026-11-17

### Fixed

- **Config options with no name.** Twelve of the newer options had no name or description in the config screen; they have
  both now.

### Changed

- **A hunt target that can't be made is logged.** When a datapack names a mob that can't be created for its loot, the
  server log says so once, where before those hunts quietly gave nothing.
- **Code clean-up, no gameplay change.** Imports, named balance numbers, the colony step in named phases, and unused code
  removed.

## [0.3.0] - 2026-11-15

### Added

- **Upright nests.** A colony built upward becomes a vertical formicarium: once more of its blocks are stacked (one
  right over another) than stand flat, every block of it is a slab of nest between two glasses, front and back. With
  more flat blocks it is the glass cases it always was, and with as many of each it stays as it was.
- **Each block of an upright colony shows its own chamber**, carved into the earth of the species' climate: the Queen's chamber,
  shelves with the Storage's real items, a fungus garden, a portal, full Liquid Ants hanging over their pool.
- **Tunnels join the blocks.** Tunnels are dug to the colony's blocks beside, above and below, so a tower or a wall is one
  nest in one frame, the same from both sides. Ants walk from block to block: out to the sites and back with their finds.
- **An upright Formicarium's nest grows with the colony:** a brood chamber at first, a food store from stage 2, a bigger
  chamber for the Queen and a midden from stage 3, crystals at stage 4.

### Changed

- **More in the server config.** Generator and Genetic Laboratory energy storage, the Laboratory's input rate, Royal
  Guard reach and leash, how far a Larva Cluster counts ants, brood per cluster, ants per Tweezers pinch, Queen growth
  cap and traits per stage, and home nest ants and refill time. Every default is the old value.
- **The Terrarium is now called the Formicarium** (and Terrarium Glass is Formicarium Glass), the proper name for an ant
  nest behind glass. Searching "terrarium" still finds it in the creative tab, JEI and EMI. Worlds, configs and pack
  scripts are untouched: the ids (`antcolonies:terrarium`) and config keys stay as they were.
- **Colonies on the ground are one bed of soil.** The Formicarium and every chamber now share one ground height, with no
  step or trench where blocks join. Tunnels behind the outer glass are cut on the pixel grid and run on from block to
  block with ants walking them; worn trails on top lead from chamber to chamber. Storage items are drawn smaller.
- **Clearer Statistics page.** The chart buttons have a colour chip instead of a line under their names, and each chart
  shows its value now, how much it changed and its low and high, on a scale that fits the values. In/Out and the new
  Births chart (ants born and died a minute) show both lines and the net.
- **Working Sites at a glance.** In Visual mode every site is a tile with its own icon and a light: green working, red
  a problem, grey off. Hover a tile for everything about it, click it to open it in Detailed mode. Detailed rows are
  separate panels that say where each site is from the Formicarium ("1 W, 1 N") and number sites of one kind.
- **Droplets match their fluids.** Each droplet keeps the same shape but now has its fluid's own colour, so lava and
  liquid blaze, brine and hootch, and the dark oils are easy to tell apart. Hootch is see-through, like the fluid.

### Fixed

- **Every 3D ant moves on its own.** Ants drawn as models all took the pose of the last ant drawn, so their legs and
  jaws moved in step; now each walks, grooms and bites in its own time.
- **Taking ants out of a changing colony.** Taking workers out of a colony that was turning into a new species counted
  them all as the new species, which could leave the colony finding only its old species' items for hours.
- **A founding Queen fed with small foods starts her colony.** Food that comes 30 at a time stopped short of a full bar,
  so she waited for ever; the last item now tops the bar up.
- **Food is kept when a Storage is unloaded.** A Storage in an unloaded chunk shrank the colony's food room and the food
  over it was deleted; now it is kept and eaten down.
- **Coming back to a colony.** The catch-up after loading now knows the floor's climate, and runs over a few ticks
  instead of all at once, so a big colony with Simulation Upgrades no longer freezes the server.
- **Brood is safe from other species.** Ants of another species put into a Formicarium that holds only eggs or larvae
  used to wipe them; now they wait until the brood is gone.
- **Upgrade Station.** It could give a species upgrade without using anything up when an input was taken out while the
  screen was open (by another player or a hopper).
- **Ants caught from a home nest.** Catching them with Tweezers left the nest empty for good; now it raises new ones,
  and what the ant carried drops.
- **Shift-clicking.** Empty Test Tubes no longer go into the Queen slot (which took the Queen out), tubes go to the
  Genetic Laboratory's catalyst slot, and Upgrade Bases to the Upgrade Station's base slot.
- **Automation.** Hoppers and Storage Automation only put things the Formicarium's feed slot can use into it, so junk
  can't block it, and a full Storage gives a full comparator signal.
- **Jobs page buttons always act on the site in their row**, even if priorities just changed the order.
- **JEI and EMI show species with many finds.** Outputs past the last row used to spill over the text; JEI now cycles
  them in the last slot and EMI makes the entry taller.
- **Smaller fixes.** Production page tooltips for Liquid Ant colonies, the book and Info page no longer scroll past
  the end, the Network page forgets channels a colony left, and the contested-chamber mark goes as soon as it is over.
  Animations stay smooth on old worlds, the Formicarium's surface decor stays the same between sessions, a world's
  memory is freed after leaving it, and ants hatched from Larva Clusters chase intruders as far as other anthill ants.
- **The Queen walks in her Formicarium.** She used to slide to and fro with her legs still; now she walks a few steps
  with her legs stepping, rests (laying), and walks on, in flat and upright Formicariums alike.

## [0.2.0] - 2026-11-14

The Liquid Ants update, with the same content as 0.3.0 for Minecraft 26.3.

### Added

- **Liquid Ants:** a new kind of species that works only at the new **Liquid Chamber**, where its workers give droplets
  of a fluid. Water Ants and Lava Ants come from Honeypot Ants fed a bucket of water or lava (lava in the Nether); the
  bucket comes back.
- **Droplet Centrifuge:** a machine that spins droplets into the real fluid for FE (a Water Droplet gives 100 mB for
  400 FE, a Lava Droplet 50 mB for 2,000 FE). Take the fluid with a bucket, or put a tank, pipe or cauldron next to it.
  A droplet is never lost: without FE or with a full tank the run waits.
- **Liquid Ants for other mods' fluids**, each only while that mod and its fluid are in the game: 15 ants for
  MachinaOpus, Mekanism, Create, Industrial Foregoing, Ender IO, Thermal and Immersive Engineering, listed in
  docs/compatibility.md. Only MachinaOpus's two were run in the game, on the 26.3 version.
- Droplets and Liquid Ants show on the Terrarium's Production page, in two new JEI and EMI categories and in Jade.
- **For modpacks:** Centrifuge recipes are data (recipe type `antcolonies:centrifuging`), and a pack can add Liquid Ants
  for its own fluids. The modpack guide has examples with KubeJS scripts, tested with KubeJS 8.0.8.
- Config: `dropletRolls`, `liquidFoodChance`, `centrifugeEnergy`, `centrifugeTank`, `centrifugePushRate`,
  `centrifugeEnergyMultiplier`, `centrifugeTimeMultiplier`.

### Changed

- **The Terrarium's Production page remembers the last 30 minutes:** it counts what the ants really brought in and
  shows it as items per minute. An item stays listed for 30 minutes after it came in, even when hoppers or pipes empty
  the Storage.
- **The Generator is redrawn** in the white enamel look of the Genetic Laboratory, which the Droplet Centrifuge shares.
  It has a dark burn chamber with a plasma core, and power cells and conduits that light up while it runs. It works as
  before.
- **Colony Connectors are a mid-game (Nether) chamber:** the recipe now takes 4 Infernal Chitin, a Nether Trail
  Pheromone, 2 Blaze Rods, a Magma Block and a Test Tube. The Trail Network advancement now follows Scent of Sulphur.
- **Royal Guard Ants:** the soldiers guarding the great Queen in giant anthills have this name now and stand at their
  posts instead of roaming. They attack only a player who comes within 4 blocks of a post, strikes them or has raised
  the alarm, and follow no further than 10 blocks.
- **Living brood in anthills:** Ant Egg Clusters hatch into Larva Clusters, and those into 1 to 3 workers of the anthill
  (about 10 minutes a stage, config `broodClusterMinutes`). Clusters are no longer craftable: Silk Touch takes a live
  one, for a Terrarium of that species or as a decoration. Clusters in anthills generated before this version are
  decorations.

### Fixed

- A chamber's screen and the ants drawn on it forgot which colony the chamber belongs to about 20 seconds after it last
  changed.
- A special food (Mineral, Crystal or Nether Ant Food) in the Terrarium's food slot was eaten as a whole stack at once.
  It is now eaten only while the colony's special food store has room.
- Text cut short with "..." in the mod's screens now shows whole when you hover over it, everywhere. Some long lines
  wrap onto a second line instead.

## [0.1.0] - 2026-11-13

The first release for Minecraft 26.1.2, with the same content as 0.2.0 for Minecraft 26.3: the anthill update below,
and the colony update and everything before it (the 26.3 versions further down).

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
- A build for Minecraft 26.1.2 (NeoForge 26.1.2.122), with the same content as 0.2.0 for 26.3. Data packs for it use
  data pack format 101 and 26.1.2's loot table, advancement and world generation formats.
- The modpack guide's KubeJS examples are tested with KubeJS 8.0.8, with more examples (new recipes, changed ingredients,
  item tags) and a note that JEI must be on the server to show KubeJS-changed recipes.

### Changed

- Anthills are more common: about one per 20 x 20 chunks (was 32 x 32), the Nether's one per 18 x 18 (was 24) and the
  End's one per 20 x 20 (was 28). They now also appear in swamps, mushroom fields, cherry groves, snowy plains and groves.
- Anthills hold more ants: busier rooms, soldiers out on the mound, guards in treasure and chitin rooms.
- Anthills already in a world keep their old look and layout; only new ones grow.
- Terrariums draw faster: only the glass sides facing you are drawn, and their vertices are written in one go. With 100
  colonies in view the frame rate went from about 95 to about 140 in testing.

### Fixed

- The modpack guide's KubeJS example for removing biomes from a spawn tag did nothing on KubeJS 8; it now uses a tag file.
- Anthill ants no longer wander round their tunnels while no player is within 32 blocks, as on 26.3. On 26.1.2 they
  never stopped, which tripled the network traffic near anthills and cost the server time for nothing.

## 0.1.0 for Minecraft 26.3 - 2026-11-12

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

## 0.0.2 for Minecraft 26.3 - 2026-11-09

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

## 0.0.1 for Minecraft 26.3 - 2026-11-09

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

[Unreleased]: https://github.com/Kamikadzegga/AntColonies/compare/v0.3.0-26.1.2...26.1.2
[0.3.3]: https://github.com/Kamikadzegga/AntColonies/compare/f90386f...f7d16ce
[0.3.2]: https://github.com/Kamikadzegga/AntColonies/compare/9a1bf3a...0c3306a
[0.3.1]: https://github.com/Kamikadzegga/AntColonies/compare/v0.3.0-26.1.2...d69ef6f
[0.3.0]: https://github.com/Kamikadzegga/AntColonies/releases/tag/v0.3.0-26.1.2
[0.2.0]: https://github.com/Kamikadzegga/AntColonies/compare/v0.1.0-26.1.2...717fdfd
[0.1.0]: https://github.com/Kamikadzegga/AntColonies/releases/tag/v0.1.0-26.1.2
