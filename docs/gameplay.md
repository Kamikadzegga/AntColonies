# Gameplay guide

Everything a player needs to know about Ant Colonies: how a colony works, how to start one, every structure, the
world content, species, genetics and the settings. For data packs and modpacks see the
[modpack guide](modpack-guide.md); for other mods see [compatibility](compatibility.md).

## Contents

- [How colonies work](#how-colonies-work)
- [Getting started](#getting-started)
- [Queen-only founding](#queen-only-founding)
- [Food and work](#food-and-work)
- [The Terrarium screen](#the-terrarium-screen)
- [Structures](#structures)
- [Foraging Site modes and job settings](#foraging-site-modes-and-job-settings)
- [Nether and End Foraging Sites](#nether-and-end-foraging-sites)
- [Colony Connectors](#colony-connectors)
- [Giant anthills](#giant-anthills)
- [Building blocks](#building-blocks)
- [Home nests](#home-nests)
- [Chitin gear](#chitin-gear)
- [Guard Ants](#guard-ants)
- [Advancements](#advancements)
- [Species](#species)
- [Progression through the dimensions](#progression-through-the-dimensions)
- [Genetics](#genetics)
- [Configuration](#configuration)

## How colonies work

A colony starts with a **Terrarium**, and a Terrarium works on its own: put a Queen in and feed her, and she lays eggs
and raises ants. **Chambers** are additions. Put one against the Terrarium, or against a chamber that is already part
of the colony, on any side (above and below too), and it joins. Any shape works, and none is required. Every block is the same glass case on a wooden base: where blocks touch, the glass between them goes, so the
whole build is one closed case (the ants can't get out) with one frame around it. A colony takes up to 32
chambers (`maxChambers`).

When chambers touch two colonies, each chamber goes to the Terrarium it is fewer steps from. A chamber exactly as far
from both joins neither, and says so in its screen.

A colony's population is plain numbers on the server: eggs, larvae, workers, food, health. The ants you
see in the Terrarium and the chambers are a picture of those numbers (how many, which jobs, hungry or not,
transforming or not). They never decide anything, and no ant entities are spawned for a colony.

Every simulation step (1 second by default) a colony:

1. looks at its chambers (a chamber placed or broken makes it look again at once),
2. keeps 5% of its adults home for maintenance, then hands out workers to Harvesting Sites and Foraging Sites, and
   hunters to Hunting Grounds (each site takes what it asks for, up to its capacity; an ant is never counted twice),
3. eats food, lays eggs, hatches larvae, raises adults, loses the old and (when starving) the weak,
4. produces: `rate = base x species x (ants on that kind of job)^0.85 x bonus^0.6 x health`, where `bonus` is
   genetics x mutations x upgrades. The ants are counted over all of a colony's sites, so many small sites find no
   more than one big one; the bonus is softened so stacked bonuses can't multiply without end. Rare finds (diamonds,
   netherite scrap, shells, sponges...) are one item each: Gathering only multiplies common finds. Finds go into the
   colony's Storage; while it is full they wait in the Foraging Site's own store and move on when there is room,
5. raises Queens in Queen Chambers.

While a Terrarium is unloaded its colony stands still. When it loads again it catches up on at most 5 minutes (more
with Simulation Upgrades), never on the whole time away.

## Getting started

1. Craft **Tweezers** and a few **Test Tubes**. Find wild ants (or a wild **Ant Nest**) in plains, forests, taigas,
   mountains, snowy biomes, deserts, jungles. Right-click an ant (or the nest) with Tweezers: it goes into an empty
   tube from your inventory. Queens are rare; nests give one now and then.
2. Place a **Terrarium**, put the **Queen Test Tube** in (right-click or the Queen slot) and some **Ant Food**. Food
   goes in the Terrarium's **food slot**, beside the feed slot (by hand, right-clicking the Terrarium, or a hopper or
   pipe into it); it is eaten as the food bar has room and waits there while the bar is full. The colony also
   feeds itself from its Storage: food items there are eaten whenever there is room for them (Royal Jelly is kept).
   The Colony page shows how much of the food came from Storage.
3. Check the Terrarium's **Info** page: if the climate is wrong, put the block it shows under the Terrarium (snow
   for cold species, sand for hot ones...).
4. Add chambers as you go: a **Nest Chamber** for a bigger, faster-growing colony, a **Foraging Site** to put workers
   to work, a **Storage** for the finds.
5. Grow: stage 2 needs 100 adults; stage 3 1,000 adults; stage 4 5,000 adults and a mineral species or good genetics.
   Stages unlock outputs and change the Terrarium's look.

To move a colony, take it out in Test Tubes: an empty tube in the Queen slot takes the Queen out, and empty tubes in
the feed slot fill with workers (up to 1,000 a tube). The Colony page's **Take out**
buttons do the same from the tubes in your inventory. Then break the Terrarium and put the tubes into a new one.
**Ants still in a Terrarium when it is broken are lost**, and so are its eggs, larvae and food.

The **Ant Identification Book** has how-to-play pages and a page for every species.

## Queen-only founding

Every anthill has a Queen to catch (see [Giant anthills](#giant-anthills)), and there are two ways to start a colony
with her:

- **With ants**, as always: put the Queen and a tube of workers (or eggs, or larvae) into a Terrarium, or move a whole
  colony across in its tubes. The colony runs normally at once.
- **With the Queen alone**: put her, and nothing else, into a **completely empty** Terrarium. That starts **Queen
  Founding**, as real ant Queens found colonies:
  1. She waits for her food. Feed the Terrarium until its food bar is full (2,000 food, ten Ant Food: the
     `foundingFoodShare` option of the Terrarium's own food room). She lays nothing until then, and she lives on her
     own reserves meanwhile, so she eats nothing.
  2. With the bar full she lays her first eggs. They hatch, the larvae grow, and the colony eats as usual.
  3. When her first 10 workers (`foundingWorkers`) are grown, the colony is founded and runs like any other.

Founding is only for that one case. Workers, eggs or larvae put in at any time end it at once (the colony is no longer
one Queen alone), so a Queen and workers put in together, a colony moved tube by tube, or a Queen added to a colony
that already has ants never need the full food bar. Workers alone, without a Queen, behave as they always did. Taking
the Queen out ends founding too. The Overview page shows the founding steps, the food she waits for and her first brood.
A colony founded this way earns the advancement *From a Single Queen*.

## Food and work

Food is what keeps a colony working. With at least 10 minutes of food left at what it eats (`lowFoodMinutes`), a colony
works, lays eggs and raises its larvae at full speed. Below that it slows down evenly, to 60% (`lowFoodFactor`) when the
food is just about gone, and the Terrarium says "Food running low". Out of food it is starving: work, laying and growth
drop to 20% (`starvedFactor`), and after 3 minutes it starts to lose ants, its brood first.

More workers find more, but they also eat more, so a big colony needs a big food supply: Farmer Ants at Harvesting
Sites, hunters at Hunting Grounds, food in its Storage, or food from another colony through a
[Colony Connector](#colony-connectors). The jobs that bring food in never slow below 80% (`hungryFoodWorkFactor`), however
hungry the colony is, so a colony that can feed itself always recovers. The Overview page shows how much the food is
holding the colony's work up; Detailed Mode gives the food in and out per minute.

## The Terrarium screen

The Terrarium's screen has eight tabs, with an icon each:

| Tab | What it shows |
|---|---|
| Overview | The Queen's portrait, the colony's stage (and how far to the next), health and food, its ants and its problems at a glance |
| Colony | Cards for the Queen, eggs, larvae, workers and the ants at work; click one for its details |
| Jobs | Every working site: its ants, its priority, on or off, a Foraging Site's mode, and how fast it works |
| Production | What the colony finds and about how many a minute (from its sites' real rates), and what its Storage holds |
| Genetics | The Queen's traits (green help, red hurt) and her mutations (green good, red flaws) |
| Network | The colonies its Colony Connectors link it to, their food, and the food moving between them |
| Statistics | Charts of the last half hour: adults, brood, food, eggs, finds, food in against food out |
| Info | The species: climate, diet, finds and evolutions (as before) |

The button at the top left switches between **Visual Mode** (pictures, bars, cards and short notes, the default) and
**Detailed Mode** (exact numbers, rates per minute, consumption, efficiency and the charts' minimum, average and
maximum). The choice is remembered (`detailedColonyView` in the client config). Everything shown is the colony's real
data: the screen only reads what the server sends.

Colonies have no soldiers (they became workers in an earlier version): the Colony page's last card is the ants at work,
by job, instead.

## Structures

| Structure | What it does |
|---|---|
| Terrarium | The colony: Queen, food, evolution feeding, overview GUI (Colony, Genes, Jobs, Stats, Info). Houses 60 adults and lays eggs at half speed on its own. Its Queen and ants leave only in Test Tubes; broken, it keeps nothing. |
| Nest Chamber | Brood and housing: eggs at full speed, faster larvae, room for 3,000 adults and 1,500 larvae. More chambers lay faster (diminishing). |
| Foraging Site | Workers find the species' outputs. Sends its finds to Storage; holds 9 stacks while the Storage is full. Takes species upgrades. |
| Hunting Ground | Hunter species (see below) kill mobs: drops from the hunted mobs' own loot tables (vanilla or modded; no mobs spawn), and 40 food for the colony from each catch. Other species leave it alone. |
| Harvesting Site | Farmer Ants grow food and harvest mushrooms, seeds, berries. Other species can't farm. |
| Storage | More room for finds and 25,000 food room. A Storage starts with 18 slots; each Storage Upgrade tier adds 18 (Storage IV: 90, two Storage IV: 162, the screen scrolls), a stack per slot (Storage II: three stacks in each slot) and 25,000 food room. An upgrade stays in while the Storage holds more than would fit without it. The colony eats the food items in it (not Royal Jelly). Hoppers can empty it; Automation pushes it down. |
| Queen Chamber | Raises daughter Queens; a Genetic Sample crossed in can make hybrids. |
| Nether Foraging Site | Nether-adapted ants go on simulated expeditions through its little portal and bring back the Nether's finds. No power. |
| End Foraging Site | The same for End-adapted ants and the End's finds. No power. |
| Colony Connector | Links its colony to colonies with a connector on the same channel up to 30 blocks away: food and filtered items move between them. No power. |
| Genetic Laboratory | One block. Upgrades one trait at a time, rerolls, mutates, purges, copies genes. Runs on FE (1,000,000 stored) from any side: a Generator next to it or any mod's FE cable. |
| Generator | Burns furnace fuel into FE (40 FE/t, 100k buffer) and gives it to the blocks next to it; any mod's FE cables work too. |
| Colony Upgrade Station | Makes species upgrades (Upgrade Base + the species' signature material). |

Shift-clicking an upgrade puts it only into an upgrade slot, never into a store or the feed slot. Each colony upgrade's tooltip
says in numbers what it does at its tier and where it goes, and JEI and EMI have a Colony Upgrades page for every
upgrade and tier. Upgrades of a kind in one block add up their tiers.

### Climate

Each species likes a climate: temperate, cold, hot, underground or nether. In the wrong one a colony loses health
(less with good Environmental Resistance). A block under the Terrarium gives it a climate, whatever the biome. Every variant of a block counts (any
terracotta colour, glazed too; any sandstone; any ice), and the Info page shows one icon per family, cycling through them:

| Climate | Blocks (block tag `antcolonies:climate/<name>`) |
|---|---|
| Cold | Snow block, powder snow, every ice |
| Hot | Every sand, every sandstone, every terracotta (all colours, glazed too) |
| Temperate | Grass block, podzol, mycelium, every dirt, mud and moss block |
| Underground | Stone, granite, diorite, andesite, tuff and deepslate (polished too), cobblestone, calcite, dripstone |
| Nether | Netherrack, magma, soul sand and soil, nylium, blackstone and basalt (polished and smooth too) |

### Redstone and comparators

- **Pausing a site**: a redstone signal into a Foraging Site or Harvesting Site stops it. It takes no ants (its status
  says "Paused by a redstone signal"), and its ants go to the colony's other sites.
- **Generator**: a redstone signal stops it from burning fuel; the fuel waits in the slot.
- **Comparators** read:

| Block | Comparator signal |
|---|---|
| Terrarium | How full its food is (0 when empty, 15 when full) |
| Foraging Site, Harvesting Site | Its workers out of the most it takes |
| Storage, Queen Chamber | How full the slots are, like a chest |
| Generator, Genetic Laboratory | Stored FE |

## Foraging Site modes and job settings

Every working site (Foraging Sites, Harvesting Sites, Hunting Grounds, and the Nether and End Foraging Sites) has three
settings, in its own screen and on the Terrarium's Jobs page:

- **Priority** (Low, Normal, High): the colony hands out its workers to higher priority sites first. Within a priority,
  food first (Harvesting Sites, Hunting Grounds), then foraging.
- **On / Off**: a site turned off takes no ants (they work elsewhere) and says so. A redstone signal still pauses a site
  as before.
- **Mode** (ordinary Foraging Sites only), what its workers look for:

| Mode | Effect |
|---|---|
| Balanced | Every find at its usual share (as before) |
| Mineral Gathering | Mineral finds (item tag `antcolonies:foraging/mineral`: ores, raw metals, ingots, nuggets, gems, dusts, stone...) are picked three times as often (`focusedForagingBoost`), the rest a quarter as often (`focusedForagingPenalty`) |
| Organic Gathering | The other way round: seeds, plants, mushrooms, bones, shells and the like |

The species decides what can be found at all; the mode only shifts the shares. A species with nothing of the kind a
site looks for gathers as if Balanced, and the site says "Nothing to focus". **Where a colony is never changes what it
finds**: biomes play no part in production (they decide only where wild ants and anthills appear).

## Nether and End Foraging Sites

Two chambers of their own, each with a little portal standing on its floor:

| | Nether Foraging Site | End Foraging Site |
|---|---|---|
| Ants that go | Nether-adapted: Warped, Fire, Inferno, Soul, Netherite, Wither, Crimson (species tag `antcolonies:nether_adapted`) | End-adapted: Chorus, Ender, Dragon (`antcolonies:end_adapted`) |
| Finds | Quartz, glowstone dust, nether wart, blackstone, soul sand and soil, basalt, magma cream, fungi; from stage 2 blaze powder, shroomlights, gold; from stage 3 blaze rods and, rarely, gilded blackstone, ghast tears and Infernal Chitin; Netherite and Wither Ants rarely ancient debris, Wither and Soul Ants wither roses | End stone, chorus fruit, obsidian; from stage 2 purpur, ender pearls and, rarely, chorus flowers; from stage 3 end rods and, rarely, shulker shells and Void Chitin; Dragon Ants rarely dragon's breath |
| Made from | A Foraging Site, 4 obsidian, 2 Infernal Chitin, 2 **Nether Trail Pheromones** | A Foraging Site, 3 end stone bricks, 2 Void Chitin, an eye of ender, 2 **End Trail Pheromones** |

Nothing actually travels: the ants are the colony's numbers, the other dimension is never loaded, and the site uses no
power. Its finds come from the expedition's table (`data/<namespace>/antcolonies/expedition/*.json`), at the colony's
real rate: its workers, genetics, health and food all count, as at an ordinary Foraging Site. The portal glows and
throws motes while ants are out on expeditions, and stands dark otherwise. A colony of any other species leaves the
site alone, and its screen names the ants it needs.

**Trail pheromones** are the scent of a route through a dimension. Only a colony **living in** the Nether (or the End)
finds them: its ordinary Foraging Sites turn one up now and then (2% of their finds). So the way to a Nether Foraging
Site is to take a colony to the Nether, let it forage there for a while, and bring the pheromones home; the same for
the End.

## Colony Connectors

A Colony Connector is a chamber: put it against a colony like any other. Connectors on the same **channel** (one of 16
colours, picked in its screen) in the same dimension link their colonies when
the connectors are at most 30 blocks apart on x and on z (height does not matter; config `connectorRange`), as long as both are loaded.
Nothing is force-loaded: a colony whose chunk is unloaded is shown as offline and takes no part. Connectors use no power.

Each connector's settings:

- **Food: Supply** gives the food its colony has above its **Keep** reserve (a share of its food room).
  **Food: Request** takes food while its colony is below its **Ask until** target. The hungriest requesting colony is
  served first, from the colony with the most to spare. A **rate** limit (250, 1,000 or 3,000 food a minute, or the
  config's maximum) caps each connector. Food is moved, never made: what one colony gives, another gets, and a colony
  never gives food in the same moment it asks for it.
- **Items: Send** moves the items named in its filter (put one of each in its 9 filter slots) out of its colony's
  Storage, to colonies whose connector is set to **Receive**, as much as fits in their Storage (checked before anything
  is taken, so nothing is lost; up to `maxItemsPerMinute`).
- A **redstone signal** stops a connector.

The typical network is a Farmer colony supplying food to colonies that mine or hunt: they can then put more ants to
work than their own food would keep. The Terrarium's Network page draws the network (food moving in green and orange
along the lines), and Jade shows a connector's channel, settings and what it moved.

## Giant anthills

A rare world-generation structure, about one per 32 x 32 chunks, in plains, forests, taigas, savannas, jungles, deserts,
badlands and meadows (never next to a village). A mound of earth (sandy in deserts and badlands) has a shaft down
through it into a nest of rooms and tunnels.

**Every anthill has exactly one Queen**, in her chamber, and she can always be caught: an anthill's Queen can't be hurt
(lava, falls, mobs and players alike), never despawns and isn't pushed out of her chamber, so the only way she leaves is
in a Test Tube, caught with Tweezers. Her chamber is always joined to the entrance.

No two anthills are dug alike. Each is planned from the world seed in one of seven layouts:

| Layout | |
|---|---|
| Branching | Rooms dug off the shaft in every direction, deeper as they go |
| Deep | A long spine straight down, rooms off both sides, the Queen at its foot (some 30 blocks down) |
| Multi-level | Floors of rooms joined by ramps |
| Spiral | Rooms down a spiral around the shaft |
| Central chamber | A great hall under the shaft, rooms all round it, the Queen beneath |
| Wings | Three wings off a gallery, the Queen at the end of one |
| Abandoned | A small, shallow nest its colony left: empty rooms, cobwebs and bones, and its old Queen, who stayed, alone |

And a dozen kinds of room, each furnished for its purpose: the **Queen's chamber** (royal chitin floor, her nest, a
barrel of the anthill's loot, a pheromone lamp, her guard), **nurseries**, **egg stores** and **larval chambers** (piles
of eggs and larvae), **food stores** (a barrel of food), **fungus gardens** (fungal beds and mushrooms, glowing fungus),
**barracks** (soldiers), **chitin stores** (chitin blocks and a chest), **treasure chambers** (a chest of the ants'
hoard: gold, fragments, Royal Jelly, sometimes a Chitin Shield or a Crystal Core), **rare-find chambers** (where the
ants dug into ore, crystal or amber), galleries and halls. The walls are packed anthill soil, so anthills are also where
you first find the [building blocks](#building-blocks).

The ants are the local wild species: the Queen, workers and soldiers. They stay at the anthill, and its soldiers attack
players who come close; a full set of Chitin armor keeps them peaceful. `/locate structure antcolonies:giant_anthill`
finds one.

The **Nether anthill** is a nest dug into the Nether's floor (nether wastes, the crimson and warped forests and
soul sand valleys; about one per 24 x 24 chunks): a mound of soul soil and netherrack over tunnels walled in blackstone,
with any lava around them walled off, always planned to stay above the lava sea. Its ants are Fire Ants (Warped Ants in
the fungus forests), so its Queen is a way to Fire Ants without evolving them. Its chests hold the Nether's materials.
`/locate structure antcolonies:nether_anthill` finds one.

The **End anthill** is a Chorus Ant nest in the End's outer islands (highlands and midlands; about one per 28 x 28
chunks): end stone, end stone bricks and purpur, its tunnels walled in even where they reach the island's underside.
Its chests hold chorus fruit, ender pearls, Chitin and End materials. `/locate structure
antcolonies:end_anthill` finds one.

Anthills generated before this version keep the layout they started with: the old fixed nest.

## Building blocks

A palette for players who build their own anthills, underground bases and laboratories, from the mod's materials:

| Family | Blocks | Made from |
|---|---|---|
| Anthill | Anthill Soil, Packed Anthill Soil, Anthill Bricks, Polished Anthill Bricks, Nest Pillar | Coarse dirt and packed mud (soil), soil and wheat (packed), then 2x2 each |
| Chitin | Chitin Block, Polished Chitin, Chitin Bricks (dark overlapping carapace plates), Chitin Tiles | Chitin Plates 2x2, then 2x2 each |
| Amber | Amber Block (an ant caught inside), Amber Bricks, Amber Glass (see-through), Reinforced Amber | **Ant Resin**, which Wood Ants find; Hardened Chitin for Reinforced Amber |
| Fungal | Fungal Block, Fungal Bricks, Glowing Fungal Block (light 13) | Mushrooms and leaves; glow berries and glowstone dust to glow |
| Royal | Royal Chitin Block, Royal Chitin Bricks, Royal Chitin Pillar, Chiseled Royal Chitin (the Queen's crown) | Chitin Blocks, gold nuggets and Royal Jelly |

Bricks, polished blocks and tiles have stairs and slabs (also from the stonecutter); the bricks have walls. Besides: the
**Nest Door** and **Nest Trapdoor** (Chitin and packed soil, with round tunnel windows), the **Pheromone Lamp** (light 15),
**Ant Egg Clusters** and **Larva Clusters** for nurseries (only a look: they never hatch), and **Terrarium Glass**.
The resin family is called Amber so its names never clash with vanilla's Resin Block and Resin Bricks.

## Home nests

Pick up a wild nest with **Silk Touch** and place it anywhere: three worker ants of its species move in, like bees in a
beehive, and never attack you. In daylight and dry weather they come out (two at a time at most) and work the land
within 10 blocks of the nest:

- **Tending**: an ant standing by a growing crop, stem, berry bush, nether wart or sapling now and then grows it a stage,
  a few per trip (the `nestCropGrowth` option).
- **Harvesting**: an ant finds a ripe crop (wheat, carrots, potatoes, beetroot, nether wart, sweet berries, or a pumpkin
  or melon on its stem), harvests it, replants it with one of the seeds and carries the rest home in its mandibles (the
  `nestHarvest` option).

The harvest goes into the nest's store (9 stacks). **Hoppers and pipes** take it out on any side; nothing can be put in.
At night and in rain the ants stay home. An ant that dies is replaced after a few minutes. Breaking the nest drops the
store, and the ants inside come out and go wild. With Jade, looking at a home nest shows its ants and how much it holds.

## Chitin gear

Tools (sword, pickaxe, axe, shovel, hoe) and armor made from Chitin Plates in the usual shapes. They are iron level: they
mine what iron mines, and the armor has iron's defense. They last a little longer (300 uses for tools), enchant better,
and Chitin Plates repair them. **Wearing the full armor set**:

- Wild ants never attack you, including the defenders of giant anthills and the soldiers a raided nest sends out.
- You dig 20% faster.

The **Chitin Shield** (five Chitin Plates around a plank: `CPC`, `CCC`, ` C `) blocks like a shield but is lighter: it
is up in 0.15 seconds instead of 0.25, an axe knocks it down for three quarters as long, and it lasts 450 blocks instead
of 336. Chitin Plates mend it, and it takes Unbreaking and Mending.

### Higher tiers

Three more tiers follow, each from the next chitin of the [Chitin ladder](#progression-through-the-dimensions) and each stronger:

| Tier | How | Armor (head/chest/legs/feet), toughness | Tools: uses, speed, damage bonus, mines | Full set |
|---|---|---|---|---|
| Chitin | Chitin Plates (from ants), crafted | 2/6/5/2, 0.5 | 300, 6.5, +2, like iron | Dig 20% faster |
| Hardened | Hardened Chitin, crafted (the same shapes) | 2/7/5/2, 1 | 900, 7, +2.5, like iron | Dig 25% faster |
| Infernal | Smithing table: Infernal Reinforcement + Hardened piece + Infernal Chitin | 3/8/6/3, 2.5 | 1800, 8.5, +3.5, like diamond | Dig 30% faster, Fire Resistance |
| Void | Smithing table: Void Reinforcement + Infernal piece + netherite ingot | 3/8/7/3, 3.5, knockback resistance 0.15 | 2400, 9.5, +4.5, like netherite | Dig 40% faster, Fire Resistance |

- **Reinforcements** are the smithing templates, crafted two at a time: the Infernal Reinforcement from 4 Infernal Chitin,
  2 blaze rods, 2 magma cream and a ghast tear (`IBI`, `MTM`, `IBI`); the Void Reinforcement from 4 Void Chitin, 2 shulker
  shells, 2 popped chorus fruit and an eye of ender (`VSV`, `PEP`, `VSV`). Each upgrade uses one; enchantments stay.
- Infernal and Void gear doesn't burn in fire or lava. Each tier is repaired with its own chitin.
- **Looks.** Every helmet is an ant's head with antennae and a pair of jaws. Hardened and Infernal chestplates have two
  spines on the back, like a spiny ant's; the Void chestplate has a Queen's wings folded down the back.
- **Mixed sets count.** Any four chitin armor pieces are a full set, so wild ants leave you alone; the dig bonus and Fire
  Resistance follow the lowest tier you wear. Fire Resistance is renewed every second while the set is worn and runs out
  about ten seconds after you take a piece off.

## Guard Ants

A hunter colony can give up some of its ants for a Guard Ant that follows you and fights for you:

1. Craft a **Rally Pheromone** (Test Tube, Chitin Plate, sugar, redstone).
2. Use it on the Terrarium of a hunter colony with a Queen and at least 50 ants (config `guards.antCost`). The colony
   loses the ants, and you get a **Guard Ant** in a tube.
3. Use the tube on a block to let the ant out. It is tamed to you.

Its strength comes from the colony's genetics:

| Stat | From | Range |
|---|---|---|
| Health | Queen's Hunting Strength level | 24 (level 1) to 60 (level 10) |
| Bite | Queen's Hunting Strength level | 3.6 to 9 damage |
| Armor | The species' tier | 2 per tier, up to 10 |

It fights whatever attacks you, whatever you attack, and hostile mobs near it (not creepers). It follows you, and
teleports to you when it falls far behind. Right-click it to make it stay or follow. Feed it ant food to heal it.
Sneak-right-click it with an empty hand to put it back in its tube; it keeps its health and name. It is drawn half again
as big as a wild soldier.

## Advancements

Thirty-four, under their own tab: from catching your first ants and Queen, through Home Sweet Home (a Terrarium
with a Queen), colony stages 2 and 4, evolving into a mineral species and raising a hybrid, finding a giant anthill, a
full Chitin set and a Guard Ant, to three challenges: a Queen with a level-10 trait, a Netherite Ant colony and an Ender
Ant colony. A second path leads through the dimensions: Hardened Chitin, a colony in the Nether, a Warped Ant colony, a
Nether anthill, Infernal Chitin, a colony in the End, a Chorus Ant colony, Void Chitin, a tier IV upgrade, and two
challenges, the Wither Ant and the Dragon Ant. The colony update added From a Single Queen (a colony founded by its Queen
alone), Trail Network (two colonies linked by Colony Connectors), the trail pheromones and the Nether and End Foraging
Sites, and finding an End anthill. Colony advancements are checked when you open the Terrarium and every few seconds while you stand within 16
blocks of it.

## Species

46 species.

- Wild, with nests in their biomes: Common, Wood, Stone, Snow, Desert, Farmer, Jungle, Swamp, Savanna, Shore (beaches),
  Blossom (cherry groves and flower meadows), Pale (pale garden), Badlands, Army (plains and savannas; a hunter); and in
  the other dimensions Warped (the Nether's warped and crimson forests) and Chorus (the End's outer islands).
- Hunters, which work at a Hunting Ground: Army (wild: zombies, skeletons, spiders), Bullet (from Jungle, fed gunpowder:
  creepers, witches), Trap-Jaw (from Swamp, fed slime balls: slimes, drowned), Inferno (from Fire, fed blaze powder: blazes,
  magma cubes, wither skeletons, ghasts).
- Evolved: Iron, Copper, Coal, Deepslate, Lapis, Amethyst (from Stone); Redstone and Gold (from Iron; Gold also from
  Desert); Diamond (from Gold or Lapis); Emerald and Netherite (from Diamond); Fire (from Desert or Warped); Soul (from
  Fire); Ender (from Amethyst or Chorus).
- Endgame, gated by bosses: Wither (Soul Ants fed a Nether Star) and Dragon (Ender Ants fed dragon's breath).
- Hybrids: Crystal Wood (Wood x Diamond), Frost Emerald (Snow x Emerald), Mechanical Miner (Stone x Redstone),
  Obsidian (Stone x Fire), Crimson (Farmer x Fire; farms Nether fungus), Honeypot (Blossom x Farmer; farms honey),
  Harvester (Farmer x Savanna; farms crops), Reef (Shore x Jungle; corals), Abyssal (Shore x Lapis; prismarine, sea
  lanterns, sponges), Sculk (Deepslate x Soul), Excavator (Desert x Deepslate; bones, pottery sherds, sniffer eggs).

Species decide what is produced; genetics decide how well. Everything is in JEI or EMI and in the
Ant Identification Book.

## Progression through the dimensions

The Overworld gets a colony going; the Nether and the End take it further.

- **Evolutions bound to a dimension.** The Nether's line evolves only in a Terrarium standing in the Nether: Fire Ants
  (from Desert or Warped), Inferno, Soul and Netherite, and the Wither Ant. The End's line evolves only in the End: Ender
  Ants (from Amethyst or Chorus) and the Dragon Ant. Bring the colony there in its Test Tubes, evolve it, and bring it
  home: an evolved colony lives anywhere. Fed in the wrong dimension, the Queen keeps the material and the Terrarium
  says why; the Info page, the tooltips and JEI/EMI show which dimension each evolution needs.
- **The Chitin ladder.** Chitin is never crafted: it comes only from ants.
  - **Chitin Plates**: wild ants leave one about a third of the time, and every foraging colony brings some home with
    its finds (`chitinFindChance`, 4% of finds).
  - **Hardened Chitin**: wild ants leave it now and then (5%), and colonies bring it home from stage 2 (a tenth of
    their chitin, a quarter from stage 3).
  - **Infernal Chitin**: only from the ants of the Nether: wild ants there (15%) and colonies living there (some of
    their chitin), and now and then from Nether expeditions.
  - **Void Chitin**: only from the ants of the End, the same way (wild ants 12%).
  - Looting raises every wild ant's chance. Anthill chests hold some too.
- **What needs it.** Tier II upgrades take Hardened Chitin and tier III Infernal Chitin; tier IV, the endgame, takes
  four Void Chitin and two netherite ingots on top of tier III; the Genetic Laboratory takes
  Hardened Chitin; raising a trait to level 8 takes Infernal Chitin and to level 9 Void Chitin (level 10 a Nether Star).
- **Endgame.** Soul Ants fed a Nether Star in the Nether become **Wither Ants** (soul sand, coal, bones, wither roses,
  skulls and, very rarely, a Nether Star). Ender Ants fed dragon's breath in the End become **Dragon Ants** (pearls,
  purpur, shulker shells, dragon's breath, a rare dragon head). Their Queens are reared with Infernal and Void Chitin.

## Genetics

Ten traits (Worker Productivity, Gathering Efficiency, Food Efficiency, Egg Production, Larvae Growth, Hunting
Strength, Farmer Productivity, Mutation Probability, Environmental Resistance, Specialization), levels 1 to 10, each a
multiplier used by the simulation. Wild Queens roll random levels; daughters inherit with variation and may mutate.
The Genetic Laboratory raises one trait by one level per operation (permanent; no other trait changes), so every
Queen can be perfected.

**A Queen grows with her colony, in her own time.** While her colony is at a bigger stage than she has grown into,
the time counts: after 10 minutes she grows into stage 2, after 20 more into stage 3 and after 30 more into stage 4
(`queenGrowthMinutes`; one stage at a time, so a new Queen in a big colony takes an hour to catch up, and time already
counted is kept while the colony is smaller). Each stage raises two of her traits (picked at random) by one, never past level 7 (higher is the Genetic Laboratory's work: Infernal Chitin, Void
Chitin, a Nether Star). She remembers how far she has grown, in her Test Tube too, so moving her to a new colony
doesn't grow her again. Her tooltip and the Genetics page's header show it (and the minutes to go), and players nearby get a note.
Queens from before this version take their colony's stage the first time without growing for it.

31 mutations. 18 are given on purpose in the Genetic Laboratory by their catalyst (each catalyst gives exactly
one), for example Industrious (redstone block), Keen Senses (spyglass), Fertile (golden carrots), Armored Carapace
(armadillo scutes), Lucky Strike (rabbit's foot), Resonant (echo shard), Void Touched (shulker shell). Rare ones
(Royal Vigor, Adaptive, Hive Mind) only come at random with high Mutation Probability. Nine are flaws (Sluggish,
Gluttony, Weak Mandibles, Infertile...) that only come at random; the lab's Purge removes them. A Queen carries at most
3 beneficial mutations (flaws don't count; `maxBeneficialMutations` in the config). A Reroll never lowers a trait and
never takes it above level 6: the top levels have to be upgraded.

## Configuration

In game: **Mods → Ant Colonies → Config**. **Client Settings** has how the ants are drawn (3D or 2D models, how many, how
far), particles and sounds; **Synced Settings** has the server options below, which can be changed there in
single-player. Every option explains itself when you point at it. The same options are in the config files:

`config/antcolonies/server.toml` (synced to players): simulation interval, offline catch-up, capacities, egg and growth
times, food use, starvation, production rates and diminishing returns, upgrade strengths, trait multipliers and
starting weights, mutation and evolution chances, wild spawn and Queen chances, Generator output
(`[power]`), the ants a Guard Ant costs (`[guards] antCost`), how much stacked bonuses are softened
(`bonusExponent`), the mutation cap, Queen-only founding (`[founding]`), how low food slows a colony (`lowFoodMinutes`,
`lowFoodFactor`, `hungryFoodWorkFactor`), the Foraging Site modes (`focusedForagingBoost`, `focusedForagingPenalty`), expeditions
(`expeditionRolls`), Colony Connectors (`[connectors]`), and `rareFindMultiplier` (scales the chance of every rare find at once, for modpacks: 0.5 halves diamonds, netherite scrap, shells and the like,
0 turns them off; common finds and hunting loot are not changed).
`config/antcolonies/client.toml`: how many ants a Terrarium and its chambers draw, particles, sounds, draw
distance; `ant3DModels` (false draws the surface ants as 2D sprites, far cheaper with many
colonies in view), `detailedColonyView` (the Terrarium screen's mode) and `antDetailDistance` (beyond it, default 8 blocks, surface ants are 2D anyway, and the ants inside chambers are only drawn closer than it with 3D models on).

