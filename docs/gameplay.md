# Gameplay guide

Everything a player needs to know about Ant Colonies: how a colony works, how to start one, every structure, the
world content, species, genetics and the settings. For data packs and modpacks see the
[modpack guide](modpack-guide.md); for other mods see [compatibility](compatibility.md).

## Contents

- [How colonies work](#how-colonies-work)
- [Getting started](#getting-started)
- [Structures](#structures)
- [Giant anthills](#giant-anthills)
- [Chitin gear](#chitin-gear)
- [Guard Ants](#guard-ants)
- [Advancements](#advancements)
- [Species](#species)
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

A colony's population is plain numbers on the server: eggs, larvae, workers and soldiers, food, health. The ants you
see in the Terrarium and the chambers are a picture of those numbers (how many, which jobs, hungry or not,
transforming or not). They never decide anything, and no ant entities are spawned for a colony.

Every simulation step (1 second by default) a colony:

1. looks at its chambers (a chamber placed or broken makes it look again at once),
2. keeps 5% of its adults home for maintenance, then hands out workers to Harvesting Sites and Foraging Sites, and
   soldiers to Foraging Sites (each site takes what it asks for, up to its capacity; an ant is never counted twice),
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
   only ever goes in through the Terrarium's feed slot (by hand, or a hopper or pipe into the Terrarium).
3. Check the Terrarium's **Info** page: if the climate is wrong, put the block it shows under the Terrarium (snow
   for cold species, sand for hot ones...).
4. Add chambers as you go: a **Nest Chamber** for a bigger, faster-growing colony, a **Foraging Site** to put workers
   and soldiers to work, a **Storage** for the finds.
5. Grow: stage 2 needs 100 adults; stage 3 1,000 adults; stage 4 5,000 adults and a mineral species or good genetics.
   Stages unlock outputs and change the Terrarium's look.

To move a colony, break its Terrarium: it keeps the Queen, the ants and the food. The Colony page's **Take out**
buttons put workers or soldiers into a Test Tube (up to 1,000), to add to another colony.

The **Ant Identification Book** has how-to-play pages and a page for every species.

## Structures

| Structure | What it does |
|---|---|
| Terrarium | The colony: Queen, food, evolution feeding, overview GUI (Colony, Genes, Jobs, Stats, Info). Houses 60 adults and lays eggs at half speed on its own. Keeps the colony when broken. |
| Nest Chamber | Brood and housing: eggs at full speed, faster larvae, room for 3,000 adults (1,000 of them soldiers) and 1,500 larvae. More chambers lay faster (diminishing). |
| Foraging Site | Workers find the species' outputs; soldiers hunt, with drops from the hunted mobs' own loot tables (vanilla or modded; no mobs spawn). Sends its finds to Storage; holds 9 stacks while the Storage is full. Takes species upgrades. |
| Harvesting Site | Farmer Ants grow food and harvest mushrooms, seeds, berries. Other species can't farm. |
| Storage | More room for finds, 9 to 54 slots (Storage Upgrades), and 25,000 food room. Hoppers can empty it; Automation pushes it down. |
| Queen Chamber | Raises daughter Queens; a Genetic Sample crossed in can make hybrids. |
| Genetic Laboratory | One block. Upgrades one trait at a time, rerolls, mutates, purges, copies genes. Runs on FE (1,000,000 stored) from any side: a Generator next to it or any mod's FE cable. |
| Generator | Burns furnace fuel into FE (40 FE/t, 100k buffer) and gives it to the blocks next to it; any mod's FE cables work too. |
| Upgrade Station | Makes species upgrades (Upgrade Base + the species' signature material). |

Shift-clicking an upgrade puts it only into an upgrade slot, never into a store or the feed slot.

### Climate

Each species likes a climate: temperate, cold, hot, underground or nether. In the wrong one a colony loses health
(less with good Environmental Resistance). A block under the Terrarium gives it a climate, whatever the biome:

| Climate | Blocks (block tag `antcolonies:climate/<name>`) |
|---|---|
| Cold | Snow block, ice, packed ice, blue ice, powder snow |
| Hot | Sand, red sand, sandstones, terracotta |
| Temperate | Grass block, dirt, coarse dirt, podzol, rooted dirt, moss, mud |
| Underground | Stone, deepslate, cobbled deepslate, tuff, calcite, dripstone |
| Nether | Netherrack, magma, soul sand and soil, nylium, blackstone, basalt |

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

## Giant anthills

A rare world-generation structure, about one per 32 x 32 chunks, in plains, forests, taigas, savannas, jungles, deserts,
badlands and meadows (never next to a village). A mound of earth (sandy in deserts and badlands) has a shaft down
through it into a nest dug about 18 blocks deep. Tunnels lead to a gallery, a storage chamber with a chest, and the
Queen's chamber at the bottom, which has a barrel and a wild nest of the local species.

The ants are the local wild species: a Queen to catch with Tweezers, workers and six soldiers. They stay at the anthill,
and its soldiers attack players who come close. A full set of Chitin armor keeps them peaceful. The loot includes Chitin
Plates, ant food, nuggets, Royal Jelly and Rally Pheromones, sometimes a Diamond Fragment, an Evolution Catalyst or a
piece of Chitin gear. `/locate structure antcolonies:giant_anthill` finds one.

## Chitin gear

Tools (sword, pickaxe, axe, shovel, hoe) and armor made from Chitin Plates in the usual shapes. They are iron level: they
mine what iron mines, and the armor has iron's defense. They last a little longer (300 uses for tools), enchant better,
and Chitin Plates repair them. **Wearing the full armor set**:

- Wild ants never attack you, including the defenders of giant anthills and the soldiers a raided nest sends out.
- You dig 20% faster.

## Guard Ants

A colony can give up soldiers for a Guard Ant that follows you and fights for you:

1. Craft a **Rally Pheromone** (Test Tube, Chitin Plate, sugar, redstone).
2. Use it on a Terrarium whose colony has a Queen and at least 50 soldiers (config `guards.soldierCost`). The colony
   loses the soldiers, and you get a **Guard Ant** in a tube.
3. Use the tube on a block to let the ant out. It is tamed to you.

Its strength comes from the colony's genetics:

| Stat | From | Range |
|---|---|---|
| Health | Queen's Soldier Strength level | 24 (level 1) to 60 (level 10) |
| Bite | Queen's Soldier Strength level | 3.6 to 9 damage |
| Armor | The species' tier | 2 per tier, up to 10 |

It fights whatever attacks you, whatever you attack, and hostile mobs near it (not creepers). It follows you, and
teleports to you when it falls far behind. Right-click it to make it stay or follow. Feed it ant food to heal it.
Sneak-right-click it with an empty hand to put it back in its tube; it keeps its health and name. It is drawn half again
as big as a wild soldier.

## Advancements

Sixteen, under their own tab: from catching your first ants and Queen, through Home Sweet Home (a Terrarium with a
Queen), colony stages 2 and 4, evolving into a mineral species and raising a hybrid, finding a giant anthill, a full
Chitin set and a Guard Ant, to three challenges: a Queen with a level-10 trait, a Netherite Ant colony and an Ender Ant
colony. Colony advancements are checked when you open the Terrarium and every few seconds while you stand within 16
blocks of it.

## Species

38 species.

- Wild, with nests in their biomes: Common, Wood, Stone, Snow, Desert, Farmer, Jungle, Swamp, Savanna, Shore (beaches),
  Blossom (cherry groves and flower meadows), Pale (pale garden), Badlands.
- Evolved: Iron, Copper, Coal, Deepslate, Lapis, Amethyst (from Stone); Redstone and Gold (from Iron; Gold also from
  Desert); Diamond (from Gold or Lapis); Emerald and Netherite (from Diamond); Fire (from Desert); Soul (from Fire);
  Ender (from Amethyst).
- Hybrids: Crystal Wood (Wood x Diamond), Frost Emerald (Snow x Emerald), Mechanical Miner (Stone x Redstone),
  Obsidian (Stone x Fire), Crimson (Farmer x Fire; farms Nether fungus), Honeypot (Blossom x Farmer; farms honey),
  Harvester (Farmer x Savanna; farms crops), Reef (Shore x Jungle; corals), Abyssal (Shore x Lapis; prismarine, sea
  lanterns, sponges), Sculk (Deepslate x Soul), Excavator (Desert x Deepslate; bones, pottery sherds, sniffer eggs).

Species decide what is produced; genetics decide how well. Everything is in JEI or EMI and in the
Ant Identification Book.

## Genetics

Ten traits (Worker Productivity, Gathering Efficiency, Food Efficiency, Egg Production, Larvae Growth, Soldier
Strength, Farmer Productivity, Mutation Probability, Environmental Resistance, Specialization), levels 1 to 10, each a
multiplier used by the simulation. Wild Queens roll random levels; daughters inherit with variation and may mutate.
The Genetic Laboratory raises one trait by one level per operation (permanent; no other trait changes), so every
Queen can be perfected.

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
(`[power]`), the soldiers a Guard Ant costs (`[guards] soldierCost`), how much stacked bonuses are softened
(`bonusExponent`), the mutation cap, and `rareFindMultiplier` (scales the chance of every rare find at once, for modpacks: 0.5 halves diamonds, netherite scrap, shells and the like,
0 turns them off; common finds and hunting loot are not changed).
`config/antcolonies/client.toml`: how many ants a Terrarium and its chambers draw, particles, sounds, draw
distance; `ant3DModels` (false draws the surface ants as 2D sprites, far cheaper with many
colonies in view) and `antDetailDistance` (beyond it, default 8 blocks, surface ants are 2D anyway, and the ants inside chambers are only drawn closer than it with 3D models on).

