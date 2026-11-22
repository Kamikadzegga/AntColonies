# Gameplay guide

How to start a colony, and what every part of it does. Two more pages cover the rest of the mod:

- [Species and genetics](species-and-genetics.md): the 48 species, research, the Genetic Laboratory, hybrids and traits.
- [World and gear](world-and-gear.md): giant anthills, home nests, Chitin gear, Guard Ants and building blocks.

The numbers on these pages are the defaults. A server can change most of them in the [config](#configuration).

## Contents

- [Getting started](#getting-started)
- [How a colony works](#how-a-colony-works)
- [Food](#food)
- [Climate](#climate)
- [Chambers and machines](#chambers-and-machines)
- [Upgrades](#upgrades)
- [The Formicarium screen](#the-formicarium-screen)
- [Job site settings](#job-site-settings)
- [Upright nests](#upright-nests)
- [Moving a colony](#moving-a-colony)
- [Starting from a Queen alone](#starting-from-a-queen-alone)
- [Redstone and comparators](#redstone-and-comparators)
- [Nether and End Foraging Sites](#nether-and-end-foraging-sites)
- [Colony Connectors](#colony-connectors)
- [Liquid Ants and the Droplet Centrifuge](#liquid-ants-and-the-droplet-centrifuge)
- [Configuration](#configuration)

## Getting started

1. Craft **Tweezers** and a few **Test Tubes**.
2. Find wild ants or a wild **Ant Nest**. They live in plains, forests, taigas, mountains, snowy biomes, deserts and
   jungles. Right-click one with the Tweezers and the ants go into an empty tube from your inventory. Queens are rare;
   a nest gives one now and then.
3. Place a **Formicarium**. Put the **Queen Test Tube** in (right-click, or the Queen slot) and add some **Ant Food**.
4. Open the **Info** tab. If the climate is wrong, put the block it shows under the Formicarium.
5. Add chambers as the colony grows: a **Nest Chamber** for more ants, a **Foraging Site** to put workers to work and a
   **Storage** for what they find.

The **Ant Identification Book** has how-to-play pages and a page for every species. JEI and EMI show every recipe.

## How a colony works

A **Formicarium** is a colony, and it works on its own: with a Queen and food she lays eggs and raises ants.
**Chambers** add to it. Put one against the Formicarium, or against a chamber that already belongs to the colony, on
any side (above and below too), and it joins. No shape is required. A colony takes up to 32 chambers.

- **One closed case.** Where the colony's blocks touch, the glass between them goes, so the whole build is one case
  with one frame around it.
- **Two colonies side by side.** A chamber that touches both goes to the Formicarium it is fewer steps from. A chamber
  exactly as far from both joins neither, and its screen says so.
- **Ants are numbers.** A colony is counted on the server: eggs, larvae, workers, food and health. The ants you see
  behind the glass are a picture of those numbers. No ant mobs are spawned for a colony, so big colonies are cheap to
  run.
- **Unloaded colonies stand still.** When the Formicarium loads again, the colony catches up on at most 5 minutes (more
  with Simulation Upgrades).

### Stages

A colony grows through four stages. Each one unlocks more finds and changes the Formicarium's look.

| Stage | Needs |
|---|---|
| 2 | 100 adults |
| 3 | 1,000 adults |
| 4 | 5,000 adults, and either a species above the basic tier (such as a mineral species) or a Queen whose traits average level 5 |

### Work

Every second, the colony keeps 5% of its adults at home and hands the rest out to its job sites. Each site takes what
it asks for, up to its capacity.

```
finds = base x species x (ants on that job)^0.85 x bonus^0.6 x health
```

- **More ants find more, but each one a little less.** The ants are counted over all sites of a kind, so many small
  sites find no more than one big one.
- **Bonuses are softened.** `bonus` is genetics x mutations x upgrades, so stacked bonuses help less and less.
- **Rare finds come one at a time.** Diamonds, netherite scrap, shells and the like are not multiplied by the Gathering
  trait.
- **Finds go to the colony's Storage.** While it is full, a site keeps up to 9 stacks itself and sends them on when
  there is room.
- **Biomes play no part.** Where a colony stands never changes what it finds.

## Food

Food keeps a colony working. The Formicarium's screen shows how much is left.

| Food left | What happens |
|---|---|
| 10 minutes or more | Work, egg laying and growth at full speed |
| Less than 10 minutes | The colony slows down evenly, to 60% when the food is almost gone, and says "Food running low" |
| None | Starving: everything drops to 20%, and after 3 minutes ants die, the brood first |

The jobs that bring food in never slow below 80%, so a colony that can feed itself always recovers.

Where food comes from:

- **The food slot** of the Formicarium: by hand, by right-clicking the Formicarium with food, or by hopper or pipe.
  Food waits in the slot while the food bar is full.
- **The colony's Storage.** Food items there are eaten when there is room for them. Royal Jelly is kept.
- **Farmer Ants** at Harvesting Sites and **hunters** at Hunting Grounds.
- **Another colony**, through a [Colony Connector](#colony-connectors).

More workers find more but also eat more, so a big colony needs a big food supply.

## Climate

Each species likes one climate. In the wrong one a colony loses health (less with a good Environmental Resistance
trait). A block under the Formicarium gives it a climate, whatever the biome. The Info tab shows which blocks work.

| Climate | Blocks under the Formicarium |
|---|---|
| Cold | Snow block, powder snow, any ice |
| Hot | Any sand, sandstone or terracotta |
| Temperate | Grass block, podzol, mycelium, any dirt, mud or moss block |
| Underground | Stone, granite, diorite, andesite, tuff, deepslate, cobblestone, calcite, dripstone |
| Nether | Netherrack, magma, soul sand and soil, nylium, blackstone, basalt |
| End | End stone, end stone bricks |

A Formicarium standing in the Nether always has the nether climate, and one in the End the end climate.

## Chambers and machines

Chambers join a colony. Job sites that only some species use are left alone by the others.

| Chamber | What it does |
|---|---|
| Formicarium | The colony itself: the Queen, the food and the colony screen. On its own it houses 60 adults and lays eggs at half speed. |
| Nest Chamber | Eggs at full speed, faster larvae, and room for 3,000 adults and 1,500 larvae. More chambers lay faster, each a little less. |
| Foraging Site | Workers find the species' items. Takes species upgrades. |
| Harvesting Site | Farmer species grow food and harvest mushrooms, seeds and berries. |
| Hunting Ground | Hunter species hunt mobs. Each catch gives the mob's own drops and 40 food. No mobs are spawned. |
| Liquid Chamber | [Liquid Ants](#liquid-ants-and-the-droplet-centrifuge) give droplets of their fluid. |
| Storage | Holds the finds in 18 slots and adds 25,000 food room. Hoppers can empty it. |
| Queen Chamber | Raises daughter Queens. With a Genetic Sample it can raise [hybrids](species-and-genetics.md#hybrids). |
| Nether Foraging Site, End Foraging Site | Send ants on [expeditions](#nether-and-end-foraging-sites) to the other dimension. |
| Colony Connector | [Links](#colony-connectors) nearby colonies so they share food and items. |

Machines stand on their own and need no colony.

| Machine | What it does |
|---|---|
| Genetic Laboratory | Research, species mutation and all work on a Queen's genes. Runs on FE (stores 1,000,000) from any side. See [Species and genetics](species-and-genetics.md#the-genetic-laboratory). |
| Generator | Burns furnace fuel into 40 FE a tick (stores 100,000) and gives it to the blocks next to it. Any mod's FE cables work too. |
| Droplet Centrifuge | Turns [droplets](#liquid-ants-and-the-droplet-centrifuge) into fluid, for FE. |
| Colony Upgrade Station | Makes species upgrades from an Upgrade Base and the species' signature material. |

Hoppers and any mod's pipes and cables work with these blocks from any side: see
[Compatibility](compatibility.md#pipes-cables-and-tanks). A slot takes only what its block uses.

## Upgrades

There are ten colony upgrades, each in tiers I to IV. Every upgrade's tooltip says in numbers what it does and where
it goes, and JEI and EMI list them all. Upgrades of one kind in the same block add up their tiers.

| Upgrade | Effect |
|---|---|
| Speed | Faster work in chambers and the laboratory, faster larvae |
| Food Efficiency | Less food eaten |
| Population | More ants housed, or more working in one chamber |
| Breeding | Faster egg laying and Queen rearing |
| Storage | Each tier adds 18 slots, one more stack per slot and 25,000 food room to a Storage |
| Automation | A Storage pushes its finds into the container below |
| Simulation | More offline catch-up (Formicarium) |
| Evolution | Better evolution chances (Formicarium) |
| Genetic | Cheaper laboratory work, more mutations in Queen Chambers |
| Hunting | Stronger hunters at a Hunting Ground |

Tier II takes Hardened Chitin, tier III Infernal Chitin, and tier IV a tier III upgrade with four Void Chitin and two
netherite ingots. See [where chitin comes from](world-and-gear.md#chitin). A Storage Upgrade can't be taken out while
the Storage holds more than would fit without it.

**Species upgrades** are made in the Colony Upgrade Station and go into a Foraging Site. Processing upgrades, for
example, turn raw finds into their processed form.

## The Formicarium screen

| Tab | What it shows |
|---|---|
| Overview | The Queen, the colony's stage and how far it is to the next, health, food, and any problems |
| Colony | Cards for the Queen, eggs, larvae, workers and the ants at work. Click one for details. |
| Jobs | Every job site: its ants, priority, on or off, mode, and how fast it works |
| Production | What the ants brought in over the last 30 minutes, as items per minute, and what the Storage holds |
| Genetics | The Queen's traits and mutations, each with what it does |
| Network | The colonies linked by Colony Connectors and the food moving between them |
| Statistics | Charts of the last half hour: adults, brood, food, eggs and finds |
| Info | The species: its climate, diet, finds and evolutions |

The button at the top left switches between **Visual Mode** (pictures, bars and short notes) and **Detailed Mode**
(exact numbers and rates per minute). The choice is remembered.

## Job site settings

Foraging Sites, Harvesting Sites, Hunting Grounds and the Nether and End Foraging Sites each have three settings, in
their own screen and on the Formicarium's Jobs tab:

- **Priority** (Low, Normal, High). Higher priority sites get their workers first. Within one priority, food jobs come
  before foraging.
- **On / Off.** A site turned off takes no ants; they work elsewhere.
- **Mode** (ordinary Foraging Sites only):

| Mode | Effect |
|---|---|
| Balanced | Every find at its usual share |
| Mineral Gathering | Ores, raw metals, ingots, nuggets, gems, dusts and stone are picked three times as often, everything else a quarter as often |
| Organic Gathering | The other way round: seeds, plants, mushrooms, bones, shells and the like |

The species decides what can be found at all; the mode only shifts the shares. When a species has nothing of the kind
a site looks for, the site says "Nothing to focus" and gathers as if Balanced.

## Upright nests

A colony built upward becomes one tall nest behind glass.

A colony is flat or upright as a whole. A block is *stacked* when another block of the colony is right above or below
it. With more stacked blocks than flat ones, the colony stands upright. With more flat ones, it is glass cases. With as
many of each, it stays as it was.

In an upright colony:

- Every block is a slab of nest between two glasses: one at the front (the side that faced you when you placed it) and
  one at the back.
- Each block shows its own chamber: the Queen's chamber, shelves with the Storage's items, a Harvesting Site's fungus
  garden, a portal, Liquid Ants hanging over their pool.
- Tunnels join the blocks beside, above and below, and the ants walk through them. Blocks in a wall must face the same
  way or the opposite way for their tunnels to meet.
- The nest is drawn in the earth of the species' climate, and the Formicarium's nest grows with the colony's stage.

## Moving a colony

Take the colony out in Test Tubes, break the Formicarium, and put the tubes into a new one.

- An empty tube in the **Queen slot** takes the Queen out.
- Empty tubes in the **feed slot** fill with workers, up to 1,000 a tube.
- The Colony tab's **Take out** buttons do the same with the tubes in your inventory.

**Ants still inside a Formicarium when it is broken are lost**, and so are its eggs, larvae and food.

## Starting from a Queen alone

A Queen put into a **completely empty** Formicarium, with no workers, eggs or larvae, founds her colony the way real
ant Queens do:

1. She waits for food. Feed the Formicarium until its food bar is full (2,000 food, which is ten Ant Food). Until
   then she lays nothing and eats nothing.
2. She lays her first eggs, and the brood grows as usual.
3. When her first 10 workers are grown, the colony is founded and runs like any other.

Adding workers, eggs or larvae at any point ends the founding, and the colony runs normally at once. So a Queen put in
together with a tube of workers never needs the full food bar. The Overview tab shows the founding steps.

## Redstone and comparators

- A redstone signal **pauses** a Foraging Site, a Harvesting Site, a Colony Connector, a Droplet Centrifuge or a
  Generator. A paused site's ants go to the colony's other sites, and a paused Generator keeps its fuel.
- Comparators read:

| Block | Signal |
|---|---|
| Formicarium | How full its food is |
| Foraging Site, Harvesting Site | Its workers, out of the most it takes |
| Storage, Queen Chamber | How full the slots are, like a chest |
| Generator, Genetic Laboratory | Stored FE |
| Droplet Centrifuge | How full its tank is |

## Nether and End Foraging Sites

Two chambers with a little portal each. The colony's ants go through on expeditions and bring back that dimension's
finds.

| | Nether Foraging Site | End Foraging Site |
|---|---|---|
| Ants that go | Warped, Fire, Inferno, Soul, Netherite, Wither and Crimson Ants | Chorus, Ender and Dragon Ants |
| Common finds | Quartz, glowstone dust, nether wart, blackstone, soul sand and soil, basalt, magma cream, fungi | End stone, chorus fruit, obsidian |
| From stage 2 | Blaze powder, shroomlights, gold | Purpur, ender pearls, rarely chorus flowers |
| From stage 3 | Blaze rods; rarely gilded blackstone, ghast tears and Infernal Chitin | End rods; rarely shulker shells and Void Chitin |
| Only some species | Ancient debris (Netherite, Wither), wither roses (Wither, Soul) | Dragon's breath (Dragon) |
| Made from | A Foraging Site, 4 obsidian, 2 Infernal Chitin, 2 Nether Trail Pheromones | A Foraging Site, 3 end stone bricks, 2 Void Chitin, an eye of ender, 2 End Trail Pheromones |

- Nothing really travels and the other dimension is never loaded. The site finds things at the colony's real rate,
  like an ordinary Foraging Site, and uses no power.
- The portal glows while ants are out. Other species leave the site alone, and its screen names the ants it needs.
- **Trail pheromones** are found only by a colony **living in** the Nether or the End: its ordinary Foraging Sites turn
  one up now and then (2% of their finds). So take a colony there, let it forage for a while, and bring the pheromones
  home.

## Colony Connectors

A Colony Connector is a chamber that links its colony to other colonies nearby. It is made from 4 Infernal Chitin, a
Nether Trail Pheromone, 2 blaze rods, a magma block and a Test Tube.

Two connectors link when they are on the same **channel** (one of 16 colours, picked in the screen), in the same
dimension, at most 30 blocks apart on x and on z (height doesn't matter), and both loaded. Nothing is force-loaded: a
colony in an unloaded chunk shows as offline. Connectors use no power.

| Setting | What it does |
|---|---|
| Food: Supply | Gives away the food its colony has above its **Keep** reserve |
| Food: Request | Takes food while its colony is below its **Ask until** target. The hungriest colony is served first. |
| Rate | Caps the food moved: 250, 1,000 or 3,000 a minute, or the config's maximum |
| Items: Send | Moves the items named in its 9 filter slots out of its colony's Storage |
| Items: Receive | Accepts what other connectors on the channel send, as much as fits in its colony's Storage |

Food and items are moved, never made, and nothing is taken that the other side has no room for.

A typical network is a Farmer colony that feeds colonies which mine or hunt. The Formicarium's Network tab draws the
network and the food moving along it.

## Liquid Ants and the Droplet Centrifuge

Liquid Ants carry a crop full of fluid, like honeypot ants. They do one job: at a **Liquid Chamber** they give
**droplets** of their fluid. They work at no other site, and no other species works at a Liquid Chamber.

| Species | Comes from | Fed | One droplet in a Centrifuge |
|---|---|---|---|
| Water Ant | Honeypot Ant | Water Bucket | 100 mB of water, 400 FE, 2 seconds |
| Lava Ant | Honeypot Ant, in the Nether | Lava Bucket | 50 mB of lava, 2,000 FE, 5 seconds |

- **Getting them.** Honeypot Ants are the hybrid of Blossom and Farmer Ants. Feed their Formicarium the bucket at
  stage 2 with 300 adults. Each bucket is one attempt, the 25th always works, and the empty bucket comes back.
- **Droplets come like any other work**: more ants make more, and Speed and Population Upgrades help. A full Liquid
  Chamber of 256 Water Ants gives about 30 droplets a minute (3 buckets of water); of Lava Ants about 3.
- **They must be fed.** Liquid Ants eat more than Common Ants (Water 1.3 times, Lava 1.8 times) and bring in almost no
  food.

The **Droplet Centrifuge** spins droplets into fluid. Put droplets in by hand, hopper or pipe and give it FE on any
side; a Generator next to it will do. It holds 50,000 FE and 8,000 mB.

- **Getting the fluid out:** use a bucket on it, put a tank next to it, or connect a fluid pipe on any side. It also
  pushes up to a bucket a tick into whatever next to it takes fluid.
- **Nothing is lost.** Without FE, or with no room in the tank, it waits and says so.

Other mods add more Liquid Ants for their own fluids: see [Compatibility](compatibility.md#liquid-ants).

## Configuration

In game: **Mods → Ant Colonies → Config**. Every option explains itself when you point at it.

- **Client Settings** (`config/antcolonies/client.toml`): how ants are drawn (3D or 2D, how many, how far), particles,
  sounds, and the Formicarium screen's mode. Drawing surface ants in 2D is far cheaper with many colonies in view.
- **Synced Settings** (`config/antcolonies/server.toml`): the rules of the game. They can be changed in single-player
  and are sent to the players of a server.

The [modpack guide](modpack-guide.md#server-config) lists the server options.
