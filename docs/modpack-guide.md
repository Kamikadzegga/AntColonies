# Modpack and KubeJS guide

How to change Ant Colonies for a modpack: recipes, ant food, species, evolutions, hybrids, mutations, Genetic Laboratory
costs, wild ant spawns and the server config. Everything here is plain JSON (a data pack) or a config file; KubeJS is
optional.

> **KubeJS status (November 2026):** KubeJS has no build for Minecraft 26.3 yet (the newest is for 26.1.2), so the
> KubeJS parts of this guide could not be tested. They are written for the KubeJS 7/8 API and may need small changes
> when a 26.3 build comes out. Everything done with plain JSON files works today with any data pack.

## Contents

1. [Where files go](#1-where-files-go)
2. [What reloads and what needs a restart](#2-what-reloads-and-what-needs-a-restart)
3. [Recipes](#3-recipes)
4. [Ant food](#4-ant-food)
5. [Species](#5-species)
6. [Evolutions](#6-evolutions)
7. [Hybrids](#7-hybrids)
8. [Mutations](#8-mutations)
9. [Genetic Laboratory costs](#9-genetic-laboratory-costs)
10. [Powering the Genetic Laboratory (FE)](#10-powering-the-genetic-laboratory-fe)
11. [Wild ants, nests and spawns](#11-wild-ants-nests-and-spawns)
12. [Names (language files)](#12-names-language-files)
13. [Server config](#13-server-config)
14. [Worked example: a Copper Ant](#14-worked-example-a-copper-ant)
15. [Advancements and the colony trigger](#15-advancements-and-the-colony-trigger)
16. [What cannot be changed with data](#16-what-cannot-be-changed-with-data)
17. [Checking your changes](#17-checking-your-changes)

---

## 1. Where files go

All paths below start with `data/`. Put that `data/` folder in one of these:

| Method | Folder | Notes |
|---|---|---|
| World data pack | `<world>/datapacks/<your_pack>/data/...` | Needs a `pack.mcmeta` in `<your_pack>/`. |
| Global data pack mod | e.g. a "global packs" mod's folder | Same layout, applies to every world. |
| KubeJS | `kubejs/data/...` | KubeJS loads this folder as a data pack; no `pack.mcmeta` needed. |
| Language files (names) | `assets/<namespace>/lang/en_us.json` | In a resource pack, or `kubejs/assets/...` with KubeJS. |
| KubeJS startup scripts | `kubejs/startup_scripts/` | For new items and fluids (the Liquid Ant example in section 5). Read when the game starts: needs a restart, and the same script on the server and every client. |

A world data pack's `pack.mcmeta` for Minecraft 26.3 (data pack format 121):

```json
{
  "pack": {
    "description": "My Ant Colonies changes",
    "min_format": 121,
    "max_format": 121
  }
}
```

Use **your own namespace** (e.g. `mypack`) for new things, and the `antcolonies` namespace only when you replace a
built-in file. A file with the same path as a built-in one replaces it; a new path adds something.

The mod's own files are the best reference. They live in the mod jar under `data/antcolonies/` and in the source under
`common/src/generated/resources/data/antcolonies/`.

## 2. What reloads and what needs a restart

| Content | Folder | `/reload` | Restart needed |
|---|---|---|---|
| Recipes | `data/<ns>/recipe/` | yes | no |
| Item and biome tags | `data/<ns>/tags/...` | yes (see the spawn note) | for spawn changes |
| Ant food values | `data/antcolonies/data_maps/item/ant_food.json` | yes | no |
| Block loot (what a broken block drops) | `data/<ns>/loot_table/blocks/` | yes | no |
| Species, evolutions, hybrids, mutations, lab costs | `data/<ns>/antcolonies/...` | **no** | **yes** |
| Wild ant spawns and nest generation | `data/<ns>/neoforge/biome_modifier/` | no | yes (nests: new chunks only) |
| Server config | `config/antcolonies/server.toml` | — | restart to be safe |

**Species, evolutions, hybrids, mutations and lab costs are synced registries.** Minecraft reads them once, when the
world is loaded (singleplayer: when you open the world; server: at startup), and sends them to every player who joins.
`/reload` does not touch them. This is a Minecraft rule for this kind of data, not something the mod or KubeJS controls.

## 3. Recipes

Every crafted item (Terrarium, chambers, upgrades, tools, foods, the lab) uses ordinary vanilla recipe types
(`minecraft:crafting_shaped`, `crafting_shapeless`), 72 recipes in all, in `data/antcolonies/recipe/`. Change them like
any vanilla recipe: replace the file with the same name, or remove and add recipes with KubeJS.

The mod has **one recipe type of its own**, the Droplet Centrifuge's (below). The lab, Queen rearing, evolution and the
Colony Upgrade Station read their costs from the files in sections 5 to 9, not from recipes. JEI shows those costs in
its own categories.

### Droplet Centrifuge recipes

`antcolonies:centrifuging`: one item spun into a fluid for FE. Files go in `data/<ns>/recipe/` like any recipe, and
`neoforge:conditions` work on them as on any other. A KubeJS script adds, removes and replaces them (example below).

```json
{
  "type": "antcolonies:centrifuging",
  "ingredient": "antcolonies:lava_droplet",
  "result": { "id": "minecraft:lava", "amount": 50 },
  "energy": 2000,
  "time": 100
}
```

| Field | Default | Meaning |
|---|---|---|
| `ingredient` | required | The item (or `#tag`) that goes in, one per run. Any item works, not only droplets. |
| `result` | required | The fluid's id and how many millibuckets one item gives. |
| `energy` | `400` | FE one run takes in all, spread over its time. |
| `time` | `40` | Ticks one run takes. |

The built-in ones are in `data/antcolonies/recipe/centrifuging/`. The server config's `centrifugeEnergyMultiplier` and
`centrifugeTimeMultiplier` scale them all at once. Also there: `centrifugeEnergy` (FE it stores, 50,000),
`centrifugeTank` (mB its tank holds, 8,000) and `centrifugePushRate` (the most mB a tick it hands to the tanks and pipes
next to it, 1,000; what a pipe takes out by itself has no limit).

With KubeJS (untested on 26.3, see the note at the top): the type has no shortcut of its own in KubeJS, so a recipe is written out whole with
`event.custom`; `.id(...)` names it, and `event.remove({ id: ... })` takes a built-in one out.

```js
// kubejs/server_scripts/antcolonies_centrifuge.js
ServerEvents.recipes(event => {
  // Lava Droplets give half as much for twice the power: replace the built-in recipe.
  event.remove({ id: 'antcolonies:centrifuging/lava' })
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'antcolonies:lava_droplet',
    result: { id: 'minecraft:lava', amount: 25 },
    energy: 4000,
    time: 200
  }).id('mypack:centrifuging/lava')

  // Any item can be spun: snowballs melt into water.
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'minecraft:snowball',
    result: { id: 'minecraft:water', amount: 250 },
    energy: 200
  }).id('mypack:centrifuging/snowball')

  // The Syrup Droplets of the pack's own Liquid Ant (section 5).
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'mypack:syrup_droplet',
    result: { id: 'mypack:syrup', amount: 50 },
    energy: 1200,
    time: 60
  }).id('mypack:centrifuging/syrup')
})
```

Script recipes are sent to the players like the built-in ones: JEI's Droplet Centrifuge category lists them, and they
are read again on `/reload`.

KubeJS (untested on 26.3, see the note at the top):

```js
// kubejs/server_scripts/antcolonies_recipes.js
ServerEvents.recipes(event => {
  // A harder Terrarium: replace the built-in recipe.
  event.remove({ output: 'antcolonies:terrarium' })
  event.shaped('antcolonies:terrarium', [
    'GGG',
    'G G',
    'III'
  ], {
    G: 'minecraft:glass',
    I: 'minecraft:iron_ingot'
  })

  // No Royal Jelly crafting: it must come from the colony instead.
  event.remove({ output: 'antcolonies:royal_jelly' })
})
```

The recipe ids are the file names, e.g. `antcolonies:terrarium`, `antcolonies:speed_upgrade_2`. To remove by id use
`event.remove({ id: 'antcolonies:speed_upgrade_2' })`.

## 4. Ant food

What colonies can eat, and how much food each item is worth, is the data map `antcolonies:ant_food`. Items that are not
in it can't be fed to a Terrarium.

File: `data/antcolonies/data_maps/item/ant_food.json`. Data maps from several packs **merge**, so your file adds to and
overrides single entries of the built-in one; it doesn't have to repeat them.

```json
{
  "values": {
    "minecraft:cookie": { "food": 60 },
    "minecraft:glow_lichen": { "food": 20, "special_kind": "crystal", "special": 20 },
    "#minecraft:flowers": { "food": 10 }
  },
  "remove": ["minecraft:rotten_flesh"]
}
```

| Field | Meaning |
|---|---|
| `food` | Food points the colony gets per item. For scale: Ant Food 200, Royal Jelly 400, sugar 20, bread 80. |
| `special_kind` | Optional. A special food kind it also gives: the built-in kinds are `mineral`, `crystal` and `nether`. Any word works, as long as a species' `diet.special_food` uses the same word. |
| `special` | Special food points per item. |

A species with `"diet": {"special_food": "crystal", "special_per_egg": 1.0}` needs 1 point of crystal food for every
egg, on top of normal food. Keys can be item ids or `#tags`. `"replace": true` at the top of the file throws away the
built-in values first.

## 5. Species

File: `data/<ns>/antcolonies/species/<id>.json`. The species id is `<ns>:<id>`. Only `look` (with `body` and `gaster`) is
required; every other field has a default.

```json
{
  "tier": 2,
  "category": "mineral",
  "look": { "body": "#5A3A2A", "gaster": "#C8743A", "accent": "#F0A060", "pattern": "metallic", "size": 1.0 },
  "climate": "underground",
  "spawn_biomes": "mypack:spawns/copper",
  "diet": { "consumption": 1.2, "special_food": "mineral", "special_per_egg": 0.5 },
  "production": {
    "rate": 0.4,
    "outputs": [
      { "item": "minecraft:raw_copper", "weight": 60, "processed": "minecraft:copper_ingot", "ratio": 1 },
      { "tag": "c:raw_materials/tin", "weight": 15 },
      { "item": "minecraft:copper_block", "weight": 2, "min_stage": 3, "special": true }
    ]
  },
  "combat": { "strength": 1.1 },
  "rearing": {
    "cost": [
      { "ingredient": "antcolonies:royal_jelly", "count": 2 },
      { "ingredient": "minecraft:copper_ingot", "count": 8 }
    ],
    "time": 4800
  },
  "breeding": { "eggs": 0.9, "growth": 1.0 },
  "signature_item": "minecraft:copper_ingot"
}
```

| Field | Default | Meaning |
|---|---|---|
| `tier` | `0` | 0–10, shown in JEI and the book. A tier 0 species reaches colony stage 4 only with average genetics 5 or more; tier 1+ reaches it with population alone. |
| `category` | `base` | `base`, `farmer`, `mineral`, `hybrid` or `endgame`. Mutations can be limited to some categories. |
| `look.body`, `look.gaster` | required | Colors `"#RRGGBB"`. No textures are needed: ants are tinted from these. |
| `look.accent` | black | Legs, mandibles. |
| `look.pattern` | `plain` | `plain`, `striped`, `banded`, `speckled`, `faceted` or `metallic`. |
| `look.crystal` | none | A color adds crystal spikes. |
| `look.glow` | `false` | Crystals glow in the dark. |
| `look.size` | `1.0` | 0.25–4. |
| `climate` | `temperate` | `temperate`, `cold`, `hot`, `underground`, `nether` or `any`. A Terrarium in the wrong climate loses health, unless it stands on a block of the block tag `antcolonies:climate/<climate>` (add your own blocks to those tags; snow and ice for cold, sand for hot...). |
| `spawn_biomes` | none | A biome tag where wild ants of this species appear (section 11). Without it the species is never wild. |
| `diet.consumption` | `1.0` | Food use multiplier. |
| `diet.special_food`, `diet.special_per_egg` | none, `0` | Special food the eggs need (section 4). |
| `production.rate` | `1.0` | Finds per worker multiplier at job sites. |
| `production.outputs` | none | What workers find (output fields below). |
| `farming.food`, `farming.harvest` | `0`, none | A `food` above 0 lets the species tend Harvesting Sites: food per minute per farmer, plus `harvest` outputs. |
| `combat.strength` | `1.0` | Hunting strength at a Hunting Ground (hunter species only: `"category": "hunter"`). |
| `combat.targets` | zombie, skeleton, spider, creeper, slime | List of `{"entity": "minecraft:blaze", "weight": 10}`. Hunters roll that mob's own loot table (no mob is spawned). |
| `rearing.cost` | none | What a Queen Chamber uses to rear a new Queen of this species. |
| `rearing.time` | `2400` | Rearing time in ticks (20 ticks = 1 second). |
| `breeding.eggs`, `breeding.growth` | `1.0` | Egg laying and larvae growth speed. |
| `signature_item` | none | The item the Colony Upgrade Station combines into species upgrades (4, 8 or 16 of it). Without one, the species has no species upgrades. |
| `look.crop` | none | Liquid Ants: the colour of the liquid in their crop. With it the ants are drawn with a swollen, full crop instead of a gaster (always full: it is a look, not a gauge). `look.glow` makes it glow. |
| `liquid.fluid`, `liquid.droplet`, `liquid.rate` | none, none, `1.0` | For `"category": "liquid"`: the fluid's id (shown in the screens), the item its workers give at a Liquid Chamber, and droplets per minute compared to Water Ants. |

**Liquid Ants** (`"category": "liquid"`) work only at Liquid Chambers and make only their `liquid.droplet`: their
`production` and `farming` are ignored. A pack can add one for any fluid with three files: the species (its `droplet`
can be any item that exists, one of the mod's droplets or a pack's own), an evolution into it (section 6), and a
Centrifuge recipe from that item into the fluid (section 3). Guard the files with `neoforge:conditions` if the fluid or
item comes from a mod that may be missing, as the built-in ones do:

```json
"neoforge:conditions": [
  { "type": "neoforge:mod_loaded", "modid": "create" },
  { "type": "neoforge:registered", "registry": "minecraft:fluid", "value": "create:chocolate" },
  { "type": "neoforge:registered", "registry": "minecraft:item", "value": "antcolonies:chocolate_droplet" }
]
```

**A Liquid Ant for a pack's own fluid, with KubeJS (untested on 26.3, see the note at the top).** Syrup Ants evolve from Water Ants fed a Syrup
Bucket and give Syrup Droplets, which a Centrifuge spins into syrup. A startup script makes the fluid (with its bucket)
and the droplet item. Startup scripts run when the game starts, so they need a restart, and the same script has to be
on the server and on every client.

`kubejs/startup_scripts/mypack_syrup.js`:

```js
// kubejs/startup_scripts/mypack_syrup.js
StartupEvents.registry('fluid', event => {
  // The fluid mypack:syrup; its bucket is mypack:syrup_bucket.
  event.create('mypack:syrup').tint(0xC98A2B).noBlock()
})

StartupEvents.registry('item', event => {
  // What Syrup Ants give. It borrows the picture of the mod's Honey Droplet.
  event.create('mypack:syrup_droplet').texture('antcolonies:item/honey_droplet')
})
```

`kubejs/data/mypack/antcolonies/species/syrup.json`:

```json
{
  "tier": 2,
  "category": "liquid",
  "look": { "body": "#5A3A1E", "gaster": "#3E2814", "accent": "#3E2814", "crop": "#C98A2B" },
  "diet": { "consumption": 1.5 },
  "liquid": { "fluid": "mypack:syrup", "droplet": "mypack:syrup_droplet", "rate": 0.5 },
  "rearing": {
    "cost": [{ "ingredient": "antcolonies:royal_jelly", "count": 2 }, { "ingredient": "minecraft:sugar", "count": 16 }],
    "time": 3600
  }
}
```

`kubejs/data/mypack/antcolonies/evolution/water_to_syrup.json`:

```json
{ "from": "antcolonies:water", "to": "mypack:syrup", "item": "mypack:syrup_bucket", "chance": 0.08, "guaranteed_after": 24, "min_stage": 2 }
```

The Centrifuge recipe from the droplet into the fluid is the last one of the script in section 3, and the ant's name
is `"species.mypack.syrup": "Syrup Ant"` in the language file (section 12). KubeJS names the fluid and the items
itself ("Syrup", "Syrup Bucket", "Syrup Droplet"). The Syrup Ant then shows in the Ant Identification Book and in JEI's
Liquid Ants category, its colony fills a Liquid Chamber with Syrup Droplets, and the Centrifuge's tank and screen draw
the syrup in the fluid's own colour.

What a Queen is fed to become a built-in Liquid Ant is an item tag, `antcolonies:liquid_feed/<kind>` (the fluid's bucket
by default): add items to it to give players another way.

**Output fields** (in `outputs` and `harvest`): either `item` or `tag`, plus

| Field | Default | Meaning |
|---|---|---|
| `count` | `1` | Items per find. |
| `weight` | `10` | How often it is picked compared to the other outputs. Any positive number: rare finds use small ones (the built-in Diamond Ant's whole diamonds are 3 against 85; a sniffer egg is 0.006). |
| `min_stage` | `1` | Colony stage (1–4) needed. |
| `special` | `false` | A rare signature find: Specialization genetics make it more likely, and it always comes as `count` items (Gathering genetics don't multiply it). |
| `processed` | none | What Processing species upgrades turn it into. |
| `ratio` | `9` | How many of it make one `processed` item. |

A `tag` output uses the first item in the tag and is skipped while the tag is empty, so materials from other mods only
appear when a mod provides them. This is the safe way to reference modded items.

**Costs** (`rearing.cost`, lab `cost`, mutation `catalyst`): `{"ingredient": ..., "count": n}`. The ingredient is an item
id (`"minecraft:diamond"`), a tag (`"#c:gems/diamond"`) or a list of either.

**Getting the species in game.** A new species needs a way in: wild ants in some biome (`spawn_biomes`), an evolution
from another species (section 6) or a hybrid (section 7). Without any of these, players can't get it except through
commands.

**Taking a built-in species out of play.** A data pack can replace a built-in file but can't delete it, so take away
the ways in instead:

- override its evolutions with `"chance": 0` and a very large `guaranteed_after` (section 6);
- override hybrids that produce it the same way (section 7);
- if it is wild, empty its spawn tag with `{"replace": true, "values": []}` (section 11).

Colonies that already have it keep working. A species of your own you can simply delete: colonies of a species that no
longer exists stop and show *Unknown species (missing data)* until it is back; nothing is lost.

**Species that need another mod.** Any species, evolution, hybrid or mutation file can carry NeoForge conditions, so it
only loads when a mod is installed. The built-in Osmium, Tin, Zinc, Lead, Nickel, Aluminum, Silver, Uranium, Fluorite
and Certus Ants work this way (`data/antcolonies/antcolonies/species/<id>.json`, and their evolutions):

```json
{
  "look": { "body": "#5A6E80", "gaster": "#A8C0D0" },
  "production": { "outputs": [{ "tag": "c:nuggets/osmium", "weight": 40 }] },
  "neoforge:conditions": [
    { "type": "neoforge:or", "values": [
      { "type": "neoforge:mod_loaded", "modid": "mekanism" },
      { "type": "neoforge:mod_loaded", "modid": "some_other_mod" }
    ] }
  ]
}
```

Give the evolution (or hybrid) that leads to such a species the same conditions, or it points at a species that isn't
there. Use `tag` outputs (`c:nuggets/...`, `c:raw_materials/...`, `c:gems/...`) rather than item ids from the other mod:
they work with whichever mod provides the material, and an empty tag is simply skipped. To support a mod the built-in
species don't list (another mod with tin, say), replace the species file and its evolution with your own conditions.

## 6. Evolutions

File: `data/<ns>/antcolonies/evolution/<id>.json`. Feeding `item` to a Terrarium of species `from` gives a chance per
attempt to turn the colony into `to`.

```json
{
  "from": "antcolonies:stone",
  "to": "mypack:copper",
  "item": "minecraft:copper_block",
  "chance": 0.08,
  "guaranteed_after": 24,
  "min_stage": 2,
  "min_population": 200
}
```

| Field | Default | Meaning |
|---|---|---|
| `from`, `to` | required | Species ids. |
| `item` | required | The ingredient fed to the Terrarium. |
| `chance` | required | 0–1 per attempt (multiplied by the config's evolution chance multiplier). |
| `guaranteed_after` | `32` | Attempts after which it always works. |
| `min_stage` | `1` | Colony stage needed. |
| `min_population` | `0` | Adults needed. |
| `dimension` | none | If set (e.g. `"minecraft:the_nether"`), the Terrarium must be in that dimension. |

To remove a built-in evolution, replace its file (for example `data/antcolonies/antcolonies/evolution/stone_to_iron.json`)
with one whose `chance` is `0` and `guaranteed_after` is very large (`100000`).

## 7. Hybrids

File: `data/<ns>/antcolonies/hybrid/<id>.json`. When a Queen Chamber rears a Queen with a Genetic Sample of another
species in its sample slot, a listed pair can produce `result`. The order of `first` and `second` doesn't matter. Pairs
not listed never cross.

```json
{
  "first": "antcolonies:iron",
  "second": "mypack:copper",
  "result": "mypack:bronze",
  "chance": 0.2,
  "min_average_level": 4.0
}
```

`chance` (0–1) is multiplied by the mother's Mutation Probability genetics and the config's hybrid multiplier. Both
parents need an average trait level of at least `min_average_level` (1–10, default 1).

## 8. Mutations

File: `data/<ns>/antcolonies/mutation/<id>.json`. A Queen gets a random mutation when she is reared, or a chosen one
in the Genetic Laboratory by feeding its `catalyst`.

```json
{
  "effects": { "production": 1.2, "food": 1.1 },
  "random_weight": 5,
  "negative": false,
  "rare": false,
  "catalyst": { "ingredient": "minecraft:copper_block", "count": 4 },
  "categories": ["mineral"]
}
```

| Field | Default | Meaning |
|---|---|---|
| `effects` | required | Multipliers (1 = unchanged): `production`, `gathering`, `food`, `eggs`, `growth`, `soldiers`, `farming`, `special`; and `cold_resistant`, `heat_resistant` (true/false). |
| `random_weight` | `10` | How often it is rolled at random; `0` = never at random. |
| `negative` | `false` | A harmful one: the lab can't give it on purpose, and the lab's Purge removes it. |
| `rare` | `false` | Shown as rare; never rolled at random below Mutation Probability 5. |
| `catalyst` | none | What the lab needs to give it on purpose. Without one, it is random only. |
| `categories` | all | Species categories it can appear in. |

The mutation's name in game is `mutation.<ns>.<id>` in the language file.

A Queen carries at most 3 beneficial mutations (`maxBeneficialMutations` in the server config; flaws don't count),
because their effects multiply. The lab won't add a fourth, and daughters don't inherit or roll more than
the cap. Effects also pass through the production formula's softening (`bonusExponent`), so keep single multipliers
modest: the built-in ones are 0.8 to 1.5.

## 9. Genetic Laboratory costs

File: `data/<ns>/antcolonies/trait_upgrade/<trait>.json`, one per trait. To change a built-in trait, replace the file in
the `antcolonies` namespace, e.g. `data/antcolonies/antcolonies/trait_upgrade/worker_productivity.json`.

The traits are `worker_productivity`, `gathering_efficiency`, `food_efficiency`, `egg_production`, `larvae_growth`,
`soldier_strength`, `farmer_productivity`, `mutation_probability`, `environmental_resistance` and `specialization`.

```json
{
  "trait": "worker_productivity",
  "steps": [
    { "cost": [{ "ingredient": "antcolonies:royal_jelly", "count": 1 }, { "ingredient": "minecraft:redstone", "count": 8 }], "energy": 10000, "time": 200 },
    { "cost": [{ "ingredient": "antcolonies:royal_jelly", "count": 2 }], "energy": 20000, "time": 300 }
  ]
}
```

`steps` are the upgrades in order: the first raises level 1 to 2, the ninth raises 9 to 10. Each has `cost`, `energy`
(FE, default 0; the lab holds 1,000,000) and `time` (ticks, default 200). With fewer than 9 steps, the last step is used again for
every higher level, so give all 9 to control the whole range. Copy a built-in file as a starting point.

Keep exactly one file per trait. If no file names a trait, its upgrades cost nothing. If two files name the same trait
(for example one in `antcolonies` and one in `mypack`), which one is used is not defined, so replace the built-in file
rather than adding a second one.

## 10. Powering the Genetic Laboratory (FE)

The Genetic Laboratory runs on **Forge Energy (FE)**, the energy most NeoForge tech mods use (often called RF). It holds
1,000,000 FE and takes up to 4,000 FE per transfer. Energy goes in at any side of the lab, which is a single block.
The lab never gives energy out.

Any mod's cables, batteries or generators can power it. Ant Colonies adds one simple source, the **Generator**:

- It burns anything a furnace burns, for as long as a furnace would (one coal: 1,600 ticks; a lava bucket leaves its
  bucket behind).
- It makes 40 FE per tick while burning (one coal = 64,000 FE) and stores up to 100,000 FE.
- Every tick it gives up to 1,000 FE to the blocks next to it that take FE, so put it against the lab, or connect it with another mod's cables (they can also pull from it).
- It only starts a new item when it has room, and pauses when full, so no fuel is wasted. Hoppers can fill it.
- Recipe: iron ingots around a furnace, copper ingots and redstone underneath (`antcolonies:generator`; change it like
  any recipe, section 3).

The two Generator numbers are in the server config (section 13), under `[power]`: `generatorOutput` (FE per tick,
default 40) and `generatorPush` (most FE given out per tick, default 1000).

What lab work costs, in FE:

| Work | FE |
|---|---|
| Trait upgrades | per step, from the trait's file (section 9); built in: 10,000 for level 1→2 up to 400,000 for 9→10 |
| Reroll | 40,000 |
| Purge | 60,000 |
| Mutate | 80,000 |
| Copy (genetic sample) | 10,000 |

Energy is only used when the work finishes; cancelling costs nothing.

## 11. Wild ants, nests and spawns

There are two separate things:

- **Wild ants** (the walking mobs) spawn in every biome of the tag `#antcolonies:has_wild_ants`. Each one takes a species
  whose `spawn_biomes` tag contains the biome it spawned in (a random one if several match), or Common Ants if none
  does.
- **Nests** (the anthill blocks) exist for the 13 built-in wild species: common, wood, stone, snow, desert, farmer, jungle,
  swamp, savanna, shore, blossom, pale and badlands.
  They are placed by the biome modifiers in `data/antcolonies/neoforge/biome_modifier/add_<species>_ant_nests.json`,
  in the biomes of `#antcolonies:spawns/<species>`.

So, to make a new species wild:

1. Make a biome tag, e.g. `data/mypack/tags/worldgen/biome/spawns/copper.json`:
   ```json
   { "values": ["minecraft:dripstone_caves", "minecraft:stony_peaks"] }
   ```
2. Point the species at it: `"spawn_biomes": "mypack:spawns/copper"`.
3. Make sure those biomes spawn wild ants at all: add the tag to `has_wild_ants` with
   `data/antcolonies/tags/worldgen/biome/has_wild_ants.json`:
   ```json
   { "values": ["#mypack:spawns/copper"] }
   ```
   (Tags merge; this adds to the built-in list.)

Players catch wild ants with Tweezers into empty Test Tubes; some are Queens (the chance is in the config).

To move or stop built-in nests, add biomes to or replace `data/antcolonies/tags/worldgen/biome/spawns/<species>.json`
(with `"replace": true` to drop the built-in biomes). Nest changes only affect newly generated chunks.

With KubeJS (untested on 26.3):

```js
ServerEvents.tags('worldgen/biome', event => {
  event.add('mypack:spawns/copper', ['minecraft:dripstone_caves', 'minecraft:stony_peaks'])
  event.add('antcolonies:has_wild_ants', '#mypack:spawns/copper')
  event.removeAll('antcolonies:spawns/desert') // no Desert Ant nests or wild Desert Ants
})
```


### Giant anthills

The giant anthill structure is ordinary world-generation data, so a data pack can change all of it:

| File | What it sets |
|---|---|
| `data/antcolonies/worldgen/structure_set/giant_anthills.json` | How often: `spacing` 32 and `separation` 10 (in chunks). Raise `spacing` for fewer anthills. |
| `data/antcolonies/tags/worldgen/biome/has_structure/giant_anthill.json` | Which biomes. An empty tag (`"replace": true, "values": []`) turns them off. |
| `data/antcolonies/loot_table/chests/giant_anthill.json` | What its chest and barrel hold. |

The ants in an anthill are the local wild species (the same `spawns/...` biome tags as above), and the nest block in the
Queen's chamber is that species' nest when it has one. The layouts (seven styles of rooms and tunnels, picked from the
world seed) are built in code and can't be changed with data; the Nether anthill (`nether_anthills`, `has_structure/nether_anthill`,
`chests/nether_anthill`) and the End anthill (`end_anthills`, `has_structure/end_anthill`, `chests/end_anthill`) work the same way.
Their rooms also use `chests/anthill_treasure` and `chests/anthill_food`.

### Expeditions (Nether and End Foraging Sites)

The Nether and End Foraging Sites take their finds from **expedition tables**, a datapack registry like species:
`data/<namespace>/antcolonies/expedition/<name>.json`. The mod ships `nether.json` and `end.json`; replace them at the
same path to change their finds.

```json
{
  "site": "nether_foraging_site",
  "adapted": "antcolonies:nether_adapted",
  "rate": 0.8,
  "home": "minecraft:the_nether",
  "trail": { "item": "antcolonies:nether_trail_pheromone", "chance": 0.02 },
  "outputs": [
    { "item": "minecraft:quartz", "weight": 20 },
    { "item": "minecraft:blaze_rod", "weight": 1.5, "min_stage": 3 },
    { "item": "minecraft:ancient_debris", "weight": 0.08, "min_stage": 4, "special": true,
      "species": ["antcolonies:netherite", "antcolonies:wither"] }
  ]
}
```

| Field | Meaning |
|---|---|
| `site` | `nether_foraging_site` or `end_foraging_site`: the chamber that runs it |
| `adapted` | A **species tag** (`data/<namespace>/tags/antcolonies/species/<name>.json`): only these species go |
| `rate` | Finds compared to an ordinary Foraging Site (times `expeditionRolls` in the config) |
| `home` | The dimension: an ordinary Foraging Site of a colony **living** there finds the `trail` item |
| `trail` | The trail pheromone and its chance per find (leave it out for none) |
| `outputs` | Finds, with the same fields as species outputs (`item` or `tag`, `count`, `weight`, `min_stage`, `special`), and an optional `species` list: only those species find it |

To let your own species go on the Nether's expeditions, add it to the tag:

```json
// data/antcolonies/tags/antcolonies/species/nether_adapted.json
{ "values": ["mypack:copper"] }
```

### Foraging Site modes

Mineral Gathering favours the finds in the item tag **`antcolonies:foraging/mineral`** (it includes `#c:ores`,
`#c:raw_materials`, `#c:ingots`, `#c:nuggets`, `#c:gems`, `#c:dusts` and stone-like items); Organic Gathering favours
everything else. Add your modded finds to the tag if they don't fall under those common tags. How strongly a mode
favours its kind is `focusedForagingBoost` and `focusedForagingPenalty` in the server config.

## 12. Names (language files)

New species and mutations need names in a language file, or they show as raw keys. File
`assets/mypack/lang/en_us.json` (in a resource pack, or `kubejs/assets/mypack/lang/en_us.json`):

```json
{
  "species.mypack.copper": "Copper Ant",
  "species.mypack.bronze": "Bronze Ant",
  "mutation.mypack.oxidized": "Oxidized"
}
```

The key is `species.<namespace>.<id>` and `mutation.<namespace>.<id>`, matching the file's id.

## 13. Server config

`config/antcolonies/server.toml` is created on first launch and synced to players. It holds the numbers that are not
per species: simulation interval and offline catch-up, capacities, egg and growth times, food use and starvation,
production rates, upgrade strengths, trait multipliers and starting weights, mutation, evolution and hybrid chance
multipliers, wild Queen and soldier chances, the Generator's output (`[power]`), the ants a Guard Ant
costs (`[guards] antCost`), Queen-only founding (`[founding]`), how low food slows a colony (`lowFoodMinutes`, `lowFoodFactor`,
`hungryFoodWorkFactor`), the Foraging Site modes and expeditions (`focusedForagingBoost`, `focusedForagingPenalty`,
`expeditionRolls`) and Colony Connectors (`[connectors]`: `transferInterval`, `maxFoodPerMinute`, `maxItemsPerMinute`, `connectorRange`). To make every rare
find (outputs marked `special`) rarer or more common at once, change `rareFindMultiplier` under `[production]` (default
1.0; 0.5 halves them, 0 turns them off) instead of editing each species. Every option has a comment in the file. For a modpack,
ship the edited file as `config/antcolonies/server.toml` in your pack. It is a global file (not per world).

`config/antcolonies/client.toml` is per player (how many ants are drawn, particles, sounds); leave it to players.

## 14. Worked example: a Copper Ant

The mod has its own Copper Ant (`antcolonies:copper`) since this example was written; the example still works because it
lives in your namespace (`mypack:copper`), next to it. Use it as a pattern for your own species.

A new species that evolves from Stone Ants with copper blocks and finds raw copper. Four files:

`kubejs/data/mypack/antcolonies/species/copper.json` (or the same path in a data pack):

```json
{
  "tier": 1,
  "category": "mineral",
  "look": { "body": "#4A2E22", "gaster": "#C8743A", "accent": "#F0A060", "pattern": "metallic" },
  "climate": "underground",
  "diet": { "consumption": 1.2 },
  "production": {
    "rate": 0.5,
    "outputs": [
      { "item": "minecraft:raw_copper", "weight": 70, "processed": "minecraft:copper_ingot", "ratio": 1 },
      { "item": "minecraft:cobblestone", "weight": 25 },
      { "item": "minecraft:copper_block", "weight": 3, "min_stage": 3, "special": true }
    ]
  },
  "rearing": {
    "cost": [{ "ingredient": "antcolonies:royal_jelly", "count": 2 }, { "ingredient": "minecraft:copper_ingot", "count": 8 }],
    "time": 3600
  },
  "signature_item": "minecraft:copper_ingot"
}
```

`kubejs/data/mypack/antcolonies/evolution/stone_to_copper.json`:

```json
{ "from": "antcolonies:stone", "to": "mypack:copper", "item": "minecraft:copper_block", "chance": 0.1, "guaranteed_after": 20, "min_stage": 2 }
```

`kubejs/assets/mypack/lang/en_us.json`:

```json
{ "species.mypack.copper": "Copper Ant" }
```

To make it wild too, add `"spawn_biomes": "mypack:spawns/copper"` and the two tag files from section 11.

These files, the food example from section 4, the spawn tags from section 11 and two conditional species (one whose
condition holds, one that needs Mekanism) are loaded as a separate data pack by the mod's game test
`guide_examples_load`, which checks that they all load, merge or stay out as described here.

Then restart the server (or reopen the world). The Copper Ant appears in JEI's *Ant Species*, *Queen Evolution*,
*Resource Production*, *Queen Rearing*, *Egg Production* and *Species Upgrades* categories, and in the Ant
Identification Book.

## 15. Advancements and the colony trigger

The mod's 35 advancements are ordinary data files in `data/antcolonies/advancement/`. Replace one with a data pack
file at the same path to change it.

For your own advancements, and for quest mods that complete quests from advancements (FTB Quests, Heracles), use the
mod's trigger **`antcolonies:colony`**. It fires when a player opens a Terrarium, and every few seconds for players
within 16 blocks of one. It checks that Terrarium's colony. All conditions are optional; leave them all out and any
colony counts.

| Condition | Type | Meaning |
|---|---|---|
| `has_queen` | true / false | The colony has a Queen |
| `stage` | number or `{"min": .., "max": ..}` | Colony stage, 1 to 4 |
| `adults` | number or `{"min": .., "max": ..}` | Adult ants |
| `species` | species id | The colony's species, e.g. `"antcolonies:diamond"` or one of yours |
| `category` | string | The species' category: `"base"`, `"farmer"`, `"mineral"`, `"hybrid"` or `"endgame"` |
| `best_trait` | number or `{"min": .., "max": ..}` | The Queen's highest trait level, 1 to 10 |
| `queen_founded` | true / false | The colony grew from a single Queen through Queen-only founding |
| `linked` | true / false | Another colony is online on one of its Colony Connector channels |
| `player` | predicate | The usual player conditions of any trigger |

A pack's "1,000 Diamond Ants" goal (`data/mypack/advancement/diamond_thousand.json`):

```json
{
  "parent": "antcolonies:evolution",
  "criteria": {
    "big_diamonds": {
      "trigger": "antcolonies:colony",
      "conditions": { "species": "antcolonies:diamond", "adults": { "min": 1000 } }
    }
  },
  "display": {
    "icon": { "id": "minecraft:diamond" },
    "title": "Diamond Hands",
    "description": "Grow a Diamond Ant colony to 1,000 adults",
    "frame": "goal"
  }
}
```

Redstone and comparators work the same in every pack (see the [gameplay guide](gameplay.md#structures)): a redstone signal pauses a job site or a
Generator, and comparators read a Terrarium's food, a job site's ants, storage fullness and stored FE.

## 16. What cannot be changed with data

These are fixed in the mod's code today:

- **Colony Upgrade Station amounts**: species upgrades always take 4, 8 or 16 of the signature item for tiers 1 to 3.
- **Droplet items**: the mod registers a droplet item only for the fluids it knows (water, lava, and those of the mods
  in docs/compatibility.md that are installed). A pack's Liquid Ants for other fluids give an existing item, or one a
  script registers (section 5 has an example). Another mod can offer a fluid from its constructor with
  `LiquidKinds.offer`.
- **Nest blocks**: only the 13 built-in wild species have nest blocks; species added by a data pack can be wild ants but
  can't have nests.
- **Structures, upgrades and their effects**: what each structure and upgrade does (the numbers are in the config).
- **No KubeJS bindings**: scripts can't read or change a colony's population, food or genetics directly; there is no
  KubeJS plugin yet.

## 17. Checking your changes

- **JEI** shows every species, evolution, hybrid, mutation, lab cost, food and output after a restart: the fastest way
  to see that a file was read.
- **The game log** (`logs/latest.log`) names any file that failed to load and why (a misspelled field, a wrong value
  range, an unknown item). A species, evolution or other registry file with an error stops that world from loading,
  with the error on screen; fix the file and try again.
- **In game**: the Ant Identification Book lists every species with its diet and finds; the Colony Scanner and Genetic
  Analyzer report a colony's state in chat.
