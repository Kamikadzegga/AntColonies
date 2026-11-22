# Scripting: KubeJS and KubeGroovy

Scripts for changing Ant Colonies in a modpack. Scripts are optional: everything in the
[modpack guide](modpack-guide.md) works as plain JSON files, and those files load from `kubejs/data/` or
`kubegroovy/data/` unchanged.

> **Status.** Neither KubeJS nor KubeGroovy has a build for Minecraft 26.3 yet. Every script on this page was tested
> on the 26.1.2 version of the mod, with KubeJS 8.0.8 and KubeGroovy 1.0.3, on a dedicated server with a player. They
> may need small changes when 26.3 builds come out.

## Contents

- [KubeJS and KubeGroovy side by side](#kubejs-and-kubegroovy-side-by-side)
- [Crafting recipes](#crafting-recipes)
- [Droplet Centrifuge recipes](#droplet-centrifuge-recipes)
- [Item tags](#item-tags)
- [Wild ant biomes](#wild-ant-biomes)
- [A Liquid Ant for a pack's own fluid](#a-liquid-ant-for-a-packs-own-fluid)
- [Limits](#limits)

## KubeJS and KubeGroovy side by side

[KubeGroovy](https://www.curseforge.com/minecraft/mc-mods/kubegroovy) is a fork of KubeJS that runs Groovy scripts.
It can't be installed together with KubeJS. Each example below is given in JavaScript, with the Groovy version folded
under it.

| | KubeJS | KubeGroovy |
|---|---|---|
| Folder | `kubejs/` | `kubegroovy/` |
| Data files and names | `kubejs/data/...`, `kubejs/assets/...` | `kubegroovy/data/...`, `kubegroovy/assets/...` |
| Scripts | `server_scripts/*.js`, `startup_scripts/*.js` | `server_scripts/*.groovy`, `startup_scripts/*.groovy` |
| An event | `ServerEvents.recipes(event => { ... })` | `$ServerEvents.recipes { event -> ... }` |
| A filter or a JSON object | `{ id: '...' }` | `[id: '...']` |
| Script names such as `Item` | `Item.of(...)` | `$Item.of(...)` (`console` has no `$`) |
| Id of a recipe the script did not name | `minecraft:kjs/...` | `minecraft:kgy/...` |

**What reloads.** Server scripts run again on `/reload`. Startup scripts need a restart, and the same script on the
server and on every client. Species, evolution and research files need a restart too.

## Crafting recipes

The mod's recipe ids are its file names, such as `antcolonies:terrarium` (the Formicarium) or
`antcolonies:speed_upgrade_2`.

```js
// kubejs/server_scripts/antcolonies_recipes.js
ServerEvents.recipes(event => {
  // A harder Formicarium: replace the built-in recipe.
  event.remove({ output: 'antcolonies:terrarium' })
  event.shaped('antcolonies:terrarium', [
    'GGG',
    'G G',
    'III'
  ], {
    G: 'minecraft:glass',
    I: 'minecraft:iron_ingot'
  })

  // No Royal Jelly crafting: it must come from the colony.
  event.remove({ output: 'antcolonies:royal_jelly' })

  // Remove one recipe by its id.
  event.remove({ id: 'antcolonies:speed_upgrade_2' })

  // A new recipe that makes a mod item.
  event.shapeless(Item.of('antcolonies:ant_food', 2), ['minecraft:sugar', 'minecraft:wheat_seeds', 'minecraft:bone_meal'])
    .id('mypack:cheap_ant_food')

  // Change an ingredient inside one of the mod's recipes.
  event.replaceInput({ id: 'antcolonies:tweezers' }, 'minecraft:iron_ingot', 'minecraft:copper_ingot')
})
```

<details>
<summary>KubeGroovy</summary>

```groovy
// kubegroovy/server_scripts/antcolonies_recipes.groovy
$ServerEvents.recipes { event ->
  // A harder Formicarium: replace the built-in recipe.
  event.remove([output: 'antcolonies:terrarium'])
  event.shaped('antcolonies:terrarium', [
    'GGG',
    'G G',
    'III'
  ], [
    G: 'minecraft:glass',
    I: 'minecraft:iron_ingot'
  ])

  // No Royal Jelly crafting: it must come from the colony.
  event.remove([output: 'antcolonies:royal_jelly'])

  // Remove one recipe by its id.
  event.remove([id: 'antcolonies:speed_upgrade_2'])

  // A new recipe that makes a mod item.
  event.shapeless($Item.of('antcolonies:ant_food', 2), ['minecraft:sugar', 'minecraft:wheat_seeds', 'minecraft:bone_meal'])
    .id('mypack:cheap_ant_food')

  // Change an ingredient inside one of the mod's recipes.
  event.replaceInput([id: 'antcolonies:tweezers'], 'minecraft:iron_ingot', 'minecraft:copper_ingot')
}
```

</details>

## Droplet Centrifuge recipes

The type `antcolonies:centrifuging` has no shortcut of its own, so a recipe is written out whole with `event.custom`.
Its fields are in the [modpack guide](modpack-guide.md#droplet-centrifuge-recipes).

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

  // The Syrup Droplets of the pack's own Liquid Ant (below).
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'mypack:syrup_droplet',
    result: { id: 'mypack:syrup', amount: 50 },
    energy: 1200,
    time: 60
  }).id('mypack:centrifuging/syrup')
})
```

<details>
<summary>KubeGroovy</summary>

```groovy
// kubegroovy/server_scripts/antcolonies_centrifuge.groovy
$ServerEvents.recipes { event ->
  // Lava Droplets give half as much for twice the power: replace the built-in recipe.
  event.remove([id: 'antcolonies:centrifuging/lava'])
  event.custom([
    type: 'antcolonies:centrifuging',
    ingredient: 'antcolonies:lava_droplet',
    result: [id: 'minecraft:lava', amount: 25],
    energy: 4000,
    time: 200
  ]).id('mypack:centrifuging/lava')

  // Any item can be spun: snowballs melt into water.
  event.custom([
    type: 'antcolonies:centrifuging',
    ingredient: 'minecraft:snowball',
    result: [id: 'minecraft:water', amount: 250],
    energy: 200
  ]).id('mypack:centrifuging/snowball')

  // The Syrup Droplets of the pack's own Liquid Ant (below).
  event.custom([
    type: 'antcolonies:centrifuging',
    ingredient: 'mypack:syrup_droplet',
    result: [id: 'mypack:syrup', amount: 50],
    energy: 1200,
    time: 60
  ]).id('mypack:centrifuging/syrup')
}
```

</details>

Centrifuge recipes from a script are sent to the players like the built-in ones, so the recipe viewer's Droplet
Centrifuge category lists them.

## Item tags

```js
ServerEvents.tags('item', event => {
  // Mineral Gathering also favours amethyst shards.
  event.add('antcolonies:foraging/mineral', 'minecraft:amethyst_shard')
})
```

<details>
<summary>KubeGroovy</summary>

```groovy
$ServerEvents.tags('item') { event ->
  // Mineral Gathering also favours amethyst shards.
  event.add('antcolonies:foraging/mineral', 'minecraft:amethyst_shard')
}
```

</details>

The mod's tags are listed in the [modpack guide](modpack-guide.md#tags).

## Wild ant biomes

Adding biomes to a spawn tag (see [Wild ants and nests](modpack-guide.md#wild-ants-and-nests)):

```js
ServerEvents.tags('worldgen/biome', event => {
  event.add('mypack:spawns/copper', ['minecraft:dripstone_caves', 'minecraft:stony_peaks'])
  event.add('antcolonies:has_wild_ants', '#mypack:spawns/copper')
})
```

<details>
<summary>KubeGroovy</summary>

```groovy
$ServerEvents.tags('worldgen/biome') { event ->
  event.add('mypack:spawns/copper', ['minecraft:dripstone_caves', 'minecraft:stony_peaks'])
  event.add('antcolonies:has_wild_ants', '#mypack:spawns/copper')
}
```

</details>

**Removing biomes does not work from a script** (see [Limits](#limits)). Use a tag file, which loads from
`kubejs/data/` or `kubegroovy/data/` like any data pack. No Desert Ant nests or wild Desert Ants:

`kubejs/data/antcolonies/tags/worldgen/biome/spawns/desert.json`

```json
{ "replace": true, "values": [] }
```

To keep some biomes, list them in `values`.

## A Liquid Ant for a pack's own fluid

Syrup Ants evolve from Water Ants fed a Syrup Bucket and give Syrup Droplets, which a Centrifuge spins into syrup.

**1. A startup script** makes the fluid, its bucket and the droplet item:

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

<details>
<summary>KubeGroovy</summary>

```groovy
// kubegroovy/startup_scripts/mypack_syrup.groovy
$StartupEvents.registry('fluid') { event ->
  // The fluid mypack:syrup; its bucket is mypack:syrup_bucket.
  event.create('mypack:syrup').tint(0xC98A2B).noBlock()
}

$StartupEvents.registry('item') { event ->
  // What Syrup Ants give. It borrows the picture of the mod's Honey Droplet.
  event.create('mypack:syrup_droplet').texture('antcolonies:item/honey_droplet')
}
```

</details>

**2. The species**, `kubejs/data/mypack/antcolonies/species/syrup.json`: the Liquid Ant example of the
[modpack guide](modpack-guide.md#liquid-ants).

**3. The evolution**, `kubejs/data/mypack/antcolonies/evolution/water_to_syrup.json`:

```json
{ "from": "antcolonies:water", "to": "mypack:syrup", "item": "mypack:syrup_bucket", "chance": 0.08, "guaranteed_after": 24, "min_stage": 2 }
```

**4. The Centrifuge recipe** from the droplet into the fluid: the last recipe of the
[script above](#droplet-centrifuge-recipes).

**5. The name**, `"species.mypack.syrup": "Syrup Ant"` in `kubejs/assets/mypack/lang/en_us.json`. The fluid and the
items are named by the script mod itself.

After a restart the Syrup Ant is in the Ant Identification Book and the recipe viewer, its colony fills a Liquid
Chamber with Syrup Droplets, and the Centrifuge draws the syrup in the fluid's own colour.

## Limits

- **Biome tags can be added to, not removed from.** In KubeJS 8.0.8, `remove`, `removeAll` and `replace` in a
  `worldgen/biome` tag event change nothing and give no error; the same calls work on item tags. In KubeGroovy 1.0.3 a
  removal shows only after a `/reload`. Use a tag file with `"replace": true`.
- **JEI on a server.** Since Minecraft 26.1 the server no longer sends crafting recipes to players. JEI shows the
  default recipes, with a red notice, unless JEI is installed on the server too.
- **No colony bindings.** Scripts can't read or change a colony's population, food or genetics.
