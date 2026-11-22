# Modpack guide

How to change Ant Colonies for a modpack. Everything on this page is a plain JSON file in a data pack, or the config
file. For KubeJS and KubeGroovy scripts see [Scripting](scripting.md).

## Contents

- [Quick start: a Copper Ant](#quick-start-a-copper-ant)
- [Where files go](#where-files-go)
- [What reloads and what needs a restart](#what-reloads-and-what-needs-a-restart)
- [Recipes](#recipes)
- [Ant food](#ant-food)
- [Species](#species)
- [Evolutions](#evolutions)
- [Genetic research](#genetic-research)
- [Hybrids](#hybrids)
- [Mutations](#mutations)
- [Genetic Laboratory costs](#genetic-laboratory-costs)
- [Wild ants and nests](#wild-ants-and-nests)
- [Giant anthills](#giant-anthills)
- [Expeditions](#expeditions)
- [Tags](#tags)
- [Names](#names)
- [Server config](#server-config)
- [Advancements and quests](#advancements-and-quests)
- [What data can't change](#what-data-cant-change)
- [Checking your changes](#checking-your-changes)

## Quick start: a Copper Ant

A new species that is made from Stone Ants with copper blocks and finds raw copper. The examples on this page build
it step by step, in the namespace `mypack`:

| File | What it is | Section |
|---|---|---|
| `data/mypack/antcolonies/species/copper.json` | The species | [Species](#species) |
| `data/mypack/antcolonies/evolution/stone_to_copper.json` | Stone Ants become Copper Ants | [Evolutions](#evolutions) |
| `data/mypack/antcolonies/research/copper.json` | Optional: makes it a laboratory species | [Genetic research](#genetic-research) |
| `assets/mypack/lang/en_us.json` | Its name | [Names](#names) |
| Two biome tags | Optional: makes it wild | [Wild ants and nests](#wild-ants-and-nests) |

Restart the server or reopen the world, and the Copper Ant is in JEI, EMI and the Ant Identification Book. The mod has
its own `antcolonies:copper`; yours lives next to it as `mypack:copper`.

## Where files go

All data paths start with `data/`. Put that folder in one of these:

| Method | Folder |
|---|---|
| World data pack | `<world>/datapacks/<your_pack>/data/...`, with a `pack.mcmeta` in `<your_pack>/` |
| A global data pack mod | The same layout, in that mod's folder |
| KubeJS | `kubejs/data/...` and `kubejs/assets/...` (no `pack.mcmeta` needed) |
| KubeGroovy | `kubegroovy/data/...` and `kubegroovy/assets/...` |

A `pack.mcmeta` for Minecraft 26.3 (data pack format 121):

```json
{
  "pack": {
    "description": "My Ant Colonies changes",
    "min_format": 121,
    "max_format": 121
  }
}
```

- Use **your own namespace** (`mypack`) for new things. Use `antcolonies` only to replace a built-in file: a file with
  the same path replaces it.
- **The mod's own files are the best reference.** They are in the jar under `data/antcolonies/`, and in the source
  under `common/src/generated/resources/data/antcolonies/`.

## What reloads and what needs a restart

| Content | Folder | `/reload` is enough |
|---|---|---|
| Recipes | `data/<ns>/recipe/` | Yes |
| Item tags | `data/<ns>/tags/item/` | Yes |
| Ant food | `data/antcolonies/data_maps/item/ant_food.json` | Yes |
| Loot tables | `data/<ns>/loot_table/` | Yes |
| Species, evolutions, research, hybrids, mutations, lab costs, expeditions | `data/<ns>/antcolonies/...` | **No, restart** |
| Wild ant spawns and nests | Biome tags and `data/<ns>/neoforge/biome_modifier/` | No, restart (nests: new chunks only) |
| Server config | `config/antcolonies/server.toml` | Restart to be safe |

Species and the other files in `data/<ns>/antcolonies/` are synced registries. Minecraft reads them once, when the
world loads, and sends them to every player who joins. `/reload` does not touch them.

## Recipes

Crafted items use ordinary vanilla recipe types, in `data/antcolonies/recipe/`. Change them like any vanilla recipe:
replace the file of the same name. The recipe ids are the file names, such as `antcolonies:terrarium` (the Formicarium)
or `antcolonies:speed_upgrade_2`.

The laboratory, Queen rearing, evolution and the Colony Upgrade Station don't use recipes. Their costs come from the
species, evolution, research and lab cost files below.

### Droplet Centrifuge recipes

The mod's one recipe type of its own, `antcolonies:centrifuging`, spins one item into a fluid for FE:

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
| `ingredient` | required | The item or `#tag` that goes in, one per run. Any item works, not only droplets. |
| `result` | required | The fluid's id, and how many millibuckets one item gives |
| `energy` | `400` | FE one run takes |
| `time` | `40` | Ticks one run takes |

The built-in ones are in `data/antcolonies/recipe/centrifuging/`. `neoforge:conditions` work on them as on any recipe.

## Ant food

What colonies can eat is the data map `antcolonies:ant_food`. Items that are not in it can't be fed to a Formicarium.

`data/antcolonies/data_maps/item/ant_food.json`:

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
| `food` | Food points per item. For scale: Ant Food 200, Royal Jelly 400, sugar 20, bread 80. |
| `special_kind` | Optional. A special food it also gives. The built-in kinds are `mineral`, `crystal` and `nether`; any word works when a species' `diet.special_food` uses the same word. |
| `special` | Special food points per item |

Data maps **merge**: your file adds to the built-in one and overrides single entries. `"replace": true` at the top
throws the built-in values away first.

## Species

`data/<ns>/antcolonies/species/<id>.json`. The species id is `<ns>:<id>`. Only `look` with `body` and `gaster` is
required.

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
      { "tag": "c:raw_materials/tin", "weight": 15 },
      { "item": "minecraft:copper_block", "weight": 3, "min_stage": 3, "special": true }
    ]
  },
  "rearing": {
    "cost": [
      { "ingredient": "antcolonies:royal_jelly", "count": 2 },
      { "ingredient": "minecraft:copper_ingot", "count": 8 }
    ],
    "time": 3600
  },
  "signature_item": "minecraft:copper_ingot"
}
```

| Field | Default | Meaning |
|---|---|---|
| `tier` | `0` | 0 to 10, shown in JEI and the book. A tier 0 species reaches colony stage 4 only with traits averaging 5 or more. |
| `category` | `base` | `base`, `farmer`, `hunter`, `mineral`, `liquid`, `hybrid` or `endgame` |
| `look.body`, `look.gaster` | required | Colours as `"#RRGGBB"`. Ants are tinted from these; no textures are needed. |
| `look.accent` | black | Legs and mandibles |
| `look.pattern` | `plain` | `plain`, `striped`, `banded`, `speckled`, `faceted` or `metallic` |
| `look.crystal` | none | A colour adds crystal spikes |
| `look.glow` | `false` | Crystals (or a Liquid Ant's crop) glow in the dark |
| `look.size` | `1.0` | 0.25 to 4 |
| `look.crop` | none | Liquid Ants: the colour of the fluid in their crop |
| `climate` | `temperate` | `temperate`, `cold`, `hot`, `underground`, `nether`, `end` or `any` |
| `spawn_biomes` | none | A biome tag where the species is wild. See [Wild ants and nests](#wild-ants-and-nests). |
| `diet.consumption` | `1.0` | Food use multiplier |
| `diet.special_food`, `diet.special_per_egg` | none, `0` | A special food each egg needs on top of normal food. See [Ant food](#ant-food). |
| `production.rate` | `1.0` | Finds per worker, as a multiplier |
| `production.outputs` | none | What workers find (fields below) |
| `farming.food`, `farming.harvest` | `0`, none | A `food` above 0 lets the species work at Harvesting Sites: food per minute per farmer, plus `harvest` outputs |
| `combat.strength` | `1.0` | Hunting strength, for `"category": "hunter"` |
| `combat.targets` | zombie, skeleton, spider, creeper, slime | A list of `{"entity": "minecraft:blaze", "weight": 10}`. Hunters roll that mob's own loot table. |
| `liquid.fluid`, `liquid.droplet`, `liquid.rate` | none, none, `1.0` | For `"category": "liquid"`: the fluid's id, the item its workers give, and droplets per minute compared to Water Ants |
| `rearing.cost` | none | What a Queen Chamber uses to rear a Queen of this species |
| `rearing.time` | `2400` | Rearing time in ticks |
| `breeding.eggs`, `breeding.growth` | `1.0` | Egg laying and larvae growth speed |
| `breeding.lifespan` | `1.0` | How long adults live, as a multiple of the config's `adultLifespanMinutes` (240) |
| `signature_item` | none | The item the Colony Upgrade Station turns into species upgrades. Without one the species has none. |

### Outputs

Used in `outputs`, `harvest` and expedition tables. Each has either `item` or `tag`, plus:

| Field | Default | Meaning |
|---|---|---|
| `count` | `1` | Items per find |
| `weight` | `10` | How often it is picked compared to the others. Rare finds use small weights: the Diamond Ant's whole diamonds are 3 against 85. |
| `min_stage` | `1` | Colony stage needed, 1 to 4 |
| `special` | `false` | A rare find: the Specialization trait makes it more likely, and the Gathering trait doesn't multiply it |
| `processed` | none | What Processing species upgrades turn it into |
| `ratio` | `9` | How many of it make one `processed` item |

A `tag` output gives the first item in the tag and is skipped while the tag is empty. **Use tags for other mods'
materials** (`c:nuggets/...`, `c:raw_materials/...`, `c:gems/...`): they work with whichever mod provides the item.

### Costs

`rearing.cost`, lab `cost` and mutation `catalyst` entries are `{"ingredient": ..., "count": n}`. The ingredient is an
item id (`"minecraft:diamond"`), a tag (`"#c:gems/diamond"`) or a list of either.

### Getting a species into the game

A new species needs a way in: wild ants in some biome (`spawn_biomes`), an [evolution](#evolutions) from another
species, or a [hybrid](#hybrids).

### Taking a built-in species out

A data pack can't delete a built-in file, so take away the ways in:

- override its evolutions with `"chance": 0` and a very large `guaranteed_after`;
- override the hybrids that produce it the same way;
- if it is wild, empty its spawn tag with `{"replace": true, "values": []}`.

Colonies that already have the species keep working. A species file of your own you can simply delete: its colonies
stop and show *Unknown species (missing data)* until the file is back. Nothing is lost.

### Species that need another mod

Any species, evolution, hybrid, mutation or research file can carry NeoForge conditions, so it loads only when a mod is
installed:

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

Give the evolution or hybrid that leads to such a species the same conditions, or it points at a species that isn't
there. The mod's own [ants for other mods](compatibility.md#ants-for-other-mods-materials) work this way. To support a
mod they don't list, replace the species file and its evolution with your own conditions.

### Liquid Ants

A species with `"category": "liquid"` works only at Liquid Chambers and gives only its `liquid.droplet`. Its
`production` and `farming` are ignored. A Liquid Ant for any fluid takes three files:

1. the species, whose `droplet` can be any item that exists;
2. an [evolution](#evolutions) into it;
3. a [Centrifuge recipe](#droplet-centrifuge-recipes) from that item into the fluid.

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

If the fluid or the item comes from a mod that may be missing, guard the files as the built-in ones do:

```json
"neoforge:conditions": [
  { "type": "neoforge:mod_loaded", "modid": "create" },
  { "type": "neoforge:registered", "registry": "minecraft:fluid", "value": "create:chocolate" },
  { "type": "neoforge:registered", "registry": "minecraft:item", "value": "antcolonies:chocolate_droplet" }
]
```

[Scripting](scripting.md#a-liquid-ant-for-a-packs-own-fluid) shows how a script makes the fluid and the droplet item
for this Syrup Ant.

## Evolutions

`data/<ns>/antcolonies/evolution/<id>.json`. Feeding `item` to a Formicarium of species `from` gives a chance per
attempt to turn the colony into `to`.

```json
{
  "from": "antcolonies:stone",
  "to": "mypack:copper",
  "item": "minecraft:copper_block",
  "chance": 0.1,
  "guaranteed_after": 20,
  "min_stage": 2,
  "min_population": 200
}
```

| Field | Default | Meaning |
|---|---|---|
| `from`, `to` | required | Species ids |
| `item` | required | The ingredient fed to the Formicarium |
| `chance` | required | 0 to 1 per attempt, multiplied by the config's `chanceMultiplier` |
| `guaranteed_after` | `32` | Attempts after which it always works |
| `min_stage` | `1` | Colony stage needed |
| `min_population` | `0` | Adults needed |
| `dimension` | none | If set (such as `"minecraft:the_nether"`), the Formicarium or the laboratory must stand in that dimension |
| `lab_amount` | `4` | Species mutations only: how many of `item` one laboratory mutation takes, 1 to 64 |

**When the species `to` has a [research file](#genetic-research), the evolution is a species mutation instead.** It is
made in the Genetic Laboratory, always works, and feeding no longer makes it. Then `min_stage` is the stage the Queen
must have grown to, and `chance`, `guaranteed_after` and `min_population` are not used.

To remove a built-in evolution, replace its file:

- an evolution by feeding: `"chance": 0` and a very large `guaranteed_after` (`100000`);
- a species mutation: a `from` species that does not exist (the file must still load).

## Genetic research

`data/<ns>/antcolonies/research/<id>.json`. One discovery: scanning `material` in the Genetic Laboratory reveals a
species or a Queen mutation.

```json
{
  "material": "minecraft:copper_block",
  "species": "mypack:copper",
  "tier": "basic",
  "energy": 30000,
  "time": 400,
  "catalysts": [{ "ingredient": "antcolonies:royal_jelly", "count": 1 }],
  "requires": ["antcolonies:copper"]
}
```

| Field | Default | Meaning |
|---|---|---|
| `material` | required | What the scanner reads: an item, a `#tag` or a list of items |
| `species` | one of the two | The species it unlocks. Every evolution into it becomes a laboratory mutation. |
| `mutation` | one of the two | The Queen mutation it reveals. Knowledge only: the laboratory's Mutate works without it. |
| `tier` | `basic` | `basic`, `advanced` or `experimental`: where it is listed, and the default cost |
| `energy` | by tier | FE one species mutation takes: 40,000 / 120,000 / 400,000 |
| `time` | by tier | Ticks one species mutation takes: 600 / 1200 / 2400 |
| `catalysts` | none | What a species mutation takes on top of the evolution's `item`. One kind at most. |
| `requires` | none | Research ids that must be discovered first |

- **This file alone decides the route.** Add one and the species' evolutions move to the laboratory. To send a built-in
  species back to feeding, override its research file with a condition that never holds, such as
  `"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "none" }]`.
- **Both routes at once:** `feedingMakesLabSpecies = true` in the server config lets feeding make laboratory species
  too.
- **Hints.** An undiscovered entry shows the line `research.<ns>.<id>.hint` from the language file, or its tier's
  general hint.
- Research is saved per player (and per FTB Teams party) in the Overworld's `data/antcolonies/genetic_research.dat`.

## Hybrids

`data/<ns>/antcolonies/hybrid/<id>.json`. A listed pair can produce `result` when a Queen Chamber rears a Queen with a
Genetic Sample of the other species. The order of `first` and `second` doesn't matter. Pairs that are not listed never
cross.

```json
{
  "first": "antcolonies:iron",
  "second": "mypack:copper",
  "result": "mypack:bronze",
  "chance": 0.2,
  "min_average_level": 4.0
}
```

`chance` (0 to 1) is the chance for two parents whose traits average `min_average_level` (1 to 10, default 1). Despite
its name the level is not a requirement: any two Queens of the pair can cross.

- Each level the parents average above it adds `hybridQualityBonus` (default 0.15) of the chance.
- Below it the chance falls in a straight line, to `hybridWeakChance` (default 0.1) of itself with every trait at
  level 1.
- The result is multiplied by the average of the parents' Mutation Probability and by `hybridChanceMultiplier`, and
  capped at 1.

## Mutations

`data/<ns>/antcolonies/mutation/<id>.json`. A Queen gets a random mutation when she is reared, or a chosen one in the
Genetic Laboratory from its `catalyst`.

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
| `effects` | required | Multipliers, 1 = unchanged: `production`, `gathering`, `food`, `eggs`, `growth`, `soldiers`, `farming`, `special`; and `cold_resistant`, `heat_resistant` (true or false) |
| `random_weight` | `10` | How often it is rolled at random. `0` = never. |
| `negative` | `false` | A flaw: the lab can't give it on purpose, and Purge removes it |
| `rare` | `false` | Shown as rare; never rolled at random below Mutation Probability 5 |
| `catalyst` | none | What the lab needs to give it on purpose. Without one it is random only. |
| `categories` | all | Species categories it can appear in |

A Queen carries at most 3 beneficial mutations (`maxBeneficialMutations`), because their effects multiply. Keep single
multipliers modest: the built-in ones are 0.8 to 1.5.

## Genetic Laboratory costs

`data/<ns>/antcolonies/trait_upgrade/<trait>.json`, one per trait. To change a built-in one, replace its file, such as
`data/antcolonies/antcolonies/trait_upgrade/worker_productivity.json`.

```json
{
  "trait": "worker_productivity",
  "steps": [
    { "cost": [{ "ingredient": "antcolonies:royal_jelly", "count": 1 }, { "ingredient": "minecraft:redstone", "count": 8 }], "energy": 10000, "time": 200 },
    { "cost": [{ "ingredient": "antcolonies:royal_jelly", "count": 2 }], "energy": 20000, "time": 300 }
  ]
}
```

- **Traits:** `worker_productivity`, `gathering_efficiency`, `food_efficiency`, `egg_production`, `larvae_growth`,
  `soldier_strength`, `farmer_productivity`, `mutation_probability`, `environmental_resistance`, `specialization`.
- **`steps`** are the upgrades in order: the first raises level 1 to 2, the ninth 9 to 10. Each has `cost`, `energy`
  (FE, default 0) and `time` (ticks, default 200). With fewer than 9 steps the last one is used for every higher level.
- **Keep exactly one file per trait.** With none, its upgrades cost nothing. With two, which one wins is not defined.

The laboratory's other work has fixed FE costs: Reroll 40,000, Purge 60,000, Mutate 80,000, Copy genes 10,000. The
config's `labCostMultiplier` and `labTimeMultiplier` scale the laboratory's material costs and times as a whole.

## Wild ants and nests

Two separate things:

- **Wild ants**, the walking mobs, spawn in every biome of the tag `#antcolonies:has_wild_ants`. Each takes a species
  whose `spawn_biomes` tag contains that biome, or Common Ants if none does.
- **Nests**, the blocks, exist for the 13 built-in wild species of the Overworld. They generate in the biomes of
  `#antcolonies:spawns/<species>`.

To make a new species wild:

1. Make a biome tag, `data/mypack/tags/worldgen/biome/spawns/copper.json`:
   ```json
   { "values": ["minecraft:dripstone_caves", "minecraft:stony_peaks"] }
   ```
2. Point the species at it: `"spawn_biomes": "mypack:spawns/copper"`.
3. Add the tag to `data/antcolonies/tags/worldgen/biome/has_wild_ants.json`, so those biomes spawn wild ants at all:
   ```json
   { "values": ["#mypack:spawns/copper"] }
   ```

To move or stop built-in nests, change `data/antcolonies/tags/worldgen/biome/spawns/<species>.json`. With
`"replace": true` the built-in biomes are dropped, so `{ "replace": true, "values": [] }` removes that species' nests
and wild ants. Nest changes only show in newly generated chunks.

## Giant anthills

The anthills are ordinary world-generation data:

| File in `data/antcolonies/` | What it sets |
|---|---|
| `worldgen/structure_set/giant_anthills.json` | How often: `spacing` 32 and `separation` 10, in chunks |
| `tags/worldgen/biome/has_structure/giant_anthill.json` | Which biomes. An empty tag with `"replace": true` turns them off. |
| `loot_table/chests/giant_anthill.json` | What its chests and barrels hold |
| `loot_table/chests/anthill_treasure.json`, `anthill_food.json` | The treasure chambers and food stores |

The Nether and End anthills have the same three files, named `nether_anthill(s)` and `end_anthill(s)`. An anthill's
ants are the local wild species. The room layouts are built in code and can't be changed with data.

## Expeditions

The Nether and End Foraging Sites take their finds from `data/<ns>/antcolonies/expedition/<name>.json`. The mod ships
`nether.json` and `end.json`; replace them at the same path to change their finds.

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
| `adapted` | A species tag: only these species go |
| `rate` | Finds compared to an ordinary Foraging Site (times `expeditionRolls` in the config) |
| `home` | The dimension. An ordinary Foraging Site of a colony living there finds the `trail` item. |
| `trail` | The trail pheromone and its chance per find. Leave it out for none. |
| `outputs` | [Outputs](#outputs), with an optional `species` list: only those species find it |

To let your own species go on the Nether's expeditions, add it to the tag
`data/antcolonies/tags/antcolonies/species/nether_adapted.json`:

```json
{ "values": ["mypack:copper"] }
```

## Tags

| Tag | Kind | What it does |
|---|---|---|
| `antcolonies:climate/<climate>` | Block | Blocks that give a Formicarium standing on them that climate |
| `antcolonies:foraging/mineral` | Item | What Mineral Gathering favours. It includes `#c:ores`, `#c:raw_materials`, `#c:ingots`, `#c:nuggets`, `#c:gems`, `#c:dusts` and stone. Organic Gathering favours everything else. |
| `antcolonies:liquid_feed/<kind>` | Item | What a Queen is fed to become that built-in Liquid Ant: the fluid's bucket by default |
| `antcolonies:droplets` | Item | Every droplet |
| `antcolonies:nether_adapted`, `antcolonies:end_adapted` | Species | Who goes on [expeditions](#expeditions) |
| `antcolonies:has_wild_ants`, `antcolonies:spawns/<species>` | Biome | [Wild ants and nests](#wild-ants-and-nests) |

Diamond and Emerald Fragments are in `c:nuggets/diamond` and `c:nuggets/emerald`.

## Names

New species and mutations need names, or they show as raw keys. `assets/mypack/lang/en_us.json`, in a resource pack or
under `kubejs/assets/`:

```json
{
  "species.mypack.copper": "Copper Ant",
  "species.mypack.bronze": "Bronze Ant",
  "mutation.mypack.oxidized": "Oxidized",
  "research.mypack.copper.hint": "Something green grows on old roofs."
}
```

## Server config

`config/antcolonies/server.toml` is made on first launch and synced to players. Every option has a comment in the
file. For a modpack, ship the edited file in the pack; it is one file for all worlds.

| Section | What it holds | Options worth knowing |
|---|---|---|
| `[simulation]` | Step length, offline catch-up, colony size | `interval`, `offlineCatchUpMinutes`, `maxChambers` |
| `[population]` | Housing, egg and growth times, lifespan, stages | `stagePopulations`, `adultLifespanMinutes`, `tweezersAnts`, `clusterBrood` |
| `[food]` | Food use, food room, starvation | `lowFoodMinutes`, `lowFoodFactor`, `starvedFactor`, `hungryFoodWorkFactor` |
| `[founding]` | Queen-only founding | `foundingFoodShare`, `foundingWorkers` |
| `[production]` | Rates, diminishing returns, upgrade strengths | `rareFindMultiplier`, `bonusExponent`, `chitinFindChance`, `focusedForagingBoost`, `focusedForagingPenalty`, `expeditionRolls`, `liquidFoodChance` |
| `[connectors]` | Colony Connectors | `transferInterval`, `maxFoodPerMinute`, `maxItemsPerMinute`, `connectorRange` |
| `[genetics]` | Trait multipliers, Queen growth, mutations, lab scaling | `queenGrowthMinutes`, `queenGrowthCap`, `queenGrowthTraits`, `maxBeneficialMutations`, `labCostMultiplier` |
| `[evolution]` | Evolution and hybrid chances | `chanceMultiplier`, `hybridChanceMultiplier`, `hybridQualityBonus`, `hybridWeakChance`, `feedingMakesLabSpecies` |
| `[wild]` | Wild ants, home nests, anthills | `queenChance`, `nestQueenChance`, `homeNestAnts`, `nestCropGrowth`, `nestHarvest`, `anthillAlarmSeconds`, `broodClusterMinutes`, `royalGuardReach` |
| `[guards]` | Guard Ants | `antCost` |
| `[power]` | Generator, laboratory and Centrifuge energy and tanks | `generatorOutput`, `generatorCapacity`, `labEnergy`, `labEnergyInput`, `centrifugeEnergyMultiplier`, `centrifugeTimeMultiplier` |

**`rareFindMultiplier`** scales the chance of every rare find (outputs marked `special`) at once: 0.5 halves diamonds,
netherite scrap and the like, 0 turns them off. Use it in place of editing each species.

`config/antcolonies/client.toml` is per player (how ants are drawn, particles, sounds). Leave it to the players.

## Advancements and quests

The mod's advancements are ordinary files in `data/antcolonies/advancement/`. Replace one at the same path to change
it.

For your own advancements, and for quest mods that complete quests from advancements, use the trigger
**`antcolonies:colony`**. It fires when a player opens a Formicarium, and every few seconds for players within 16
blocks of one, and checks that Formicarium's colony. Every condition is optional.

| Condition | Type | Meaning |
|---|---|---|
| `has_queen` | true / false | The colony has a Queen |
| `stage` | number or `{"min": .., "max": ..}` | Colony stage, 1 to 4 |
| `adults` | number or `{"min": .., "max": ..}` | Adult ants |
| `species` | species id | The colony's species |
| `category` | string | The species' category |
| `best_trait` | number or `{"min": .., "max": ..}` | The Queen's highest trait level |
| `queen_founded` | true / false | The colony was founded by a Queen alone |
| `linked` | true / false | Another colony is online on one of its Colony Connector channels |
| `player` | predicate | The usual player conditions of any trigger |

A "1,000 Diamond Ants" goal, `data/mypack/advancement/diamond_thousand.json`:

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

## What data can't change

- **Colony Upgrade Station amounts.** Species upgrades always take 4, 8 or 16 of the signature item for tiers 1 to 3.
- **Droplet items.** The mod registers droplets only for the fluids it knows. A pack's Liquid Ant gives an existing
  item or one a [script](scripting.md#a-liquid-ant-for-a-packs-own-fluid) registers.
- **Nest blocks.** Only the 13 built-in species have them. A data pack species can be wild but has no nest.
- **What each chamber and upgrade does.** Their numbers are in the config.
- **Colonies from scripts.** KubeJS and KubeGroovy can't read or change a colony's population, food or genetics.

## Checking your changes

- **JEI or EMI** shows every species, evolution, hybrid, mutation, lab cost, food and output after a restart. It is
  the fastest way to see that a file was read.
- **The game log** (`logs/latest.log`) names any file that failed to load and why. A broken species, evolution or other
  registry file stops the world from loading, with the error on screen.
- **In game**, the Ant Identification Book lists every species with its diet and finds.
