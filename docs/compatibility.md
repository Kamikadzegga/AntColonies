# Compatibility

Ant Colonies needs nothing but NeoForge. Everything on this page is optional, and nothing is missing without it.

## Contents

- [Recipe viewers and Jade](#recipe-viewers-and-jade)
- [Pipes, cables and tanks](#pipes-cables-and-tanks)
- [FTB Teams](#ftb-teams)
- [Ants for other mods' materials](#ants-for-other-mods-materials)
- [Liquid Ants](#liquid-ants)
- [What was tested](#what-was-tested)

## Recipe viewers and Jade

| Mod | What you get |
|---|---|
| **JEI** (31.17+) | A category for every part of the mod: species, evolutions, species mutations, hybrids, production, expeditions, lab costs and upgrades |
| **EMI** | The same categories. EMI has no official 26.3 build; the plugin is made for **PF-EMI** 1.1, an unofficial port on Modrinth. With JEI installed too, EMI takes over and nothing shows twice. |
| **Jade** (26.3.5+) | Look at a Formicarium for its species, stage, adults, food and problems; at a job site for its ants, output and status; at a Colony Connector for its channel and settings; at a Generator or the Genetic Laboratory for what it is doing. Each part can be turned off in Jade's settings. |

WTHIT has no NeoForge build for 26.x, so there is no WTHIT plugin.

## Pipes, cables and tanks

The mod's blocks use NeoForge's shared item, energy and fluid systems, so any mod's pipes, cables and tanks work with
them, from any side.

| | What works |
|---|---|
| **Items** | Hoppers and item pipes empty Storages and take Queens out of Queen Chambers. They fill the Formicarium, Storage, Queen Chamber, Genetic Laboratory, Generator and Droplet Centrifuge. |
| **Energy** | The Genetic Laboratory and the Droplet Centrifuge take FE. The Generator gives FE out. |
| **Fluids** | The Droplet Centrifuge's tank can be emptied by any tank or fluid pipe, never filled. It also pushes its fluid into whatever next to it takes fluid. |

## FTB Teams

With FTB Teams installed, the players of a **party** share their
[genetic research](species-and-genetics.md#genetic-research):

- A discovery made while in a party belongs to the party. Every member can use it at once, and a player who joins
  later gets all of it.
- What a player discovered alone stays their own. Leaving a party takes the party's research away again and leaves the
  personal part.
- A player in no party researches for themselves, exactly as without the mod.

The mod also gives FTB Library an ant head as the face icon of Wild Ants and Guard Ants.

## Ants for other mods' materials

These species load only when a mod that adds their material is installed. They are researched and made in the Genetic
Laboratory like the built-in mineral ants: scan the ingot, gem or dust once, then mutate a Queen with it. Their finds
go through the common `c:` tags, so they work with whichever mod provides the material.

| Species | Made from | Loads with |
|---|---|---|
| Osmium | Stone, osmium ingots | Mekanism, All The Ores |
| Tin | Copper, tin ingots | Mekanism, Thermal, MachinaOpus, All The Ores |
| Zinc | Copper, zinc ingots | Create, All The Ores |
| Lead | Iron, lead ingots | Mekanism, Thermal, Immersive Engineering, MachinaOpus, All The Ores |
| Nickel | Iron, nickel ingots | Thermal, Immersive Engineering, All The Ores |
| Aluminum | Iron, aluminum ingots | Immersive Engineering, All The Ores |
| Silver | Lead, silver ingots | Thermal, Immersive Engineering, MachinaOpus, All The Ores |
| Uranium | Lead, raw uranium | Mekanism, Immersive Engineering, MachinaOpus, All The Ores |
| Fluorite | Amethyst, fluorite | Mekanism, MachinaOpus, All The Ores |
| Certus | Amethyst, certus quartz | Applied Energistics 2 |
| Molybdenum | Iron, molybdenum ingots | MachinaOpus |
| Sulfur | Fire, sulfur | MachinaOpus |
| Rubber | Wood, rubber sheets | MachinaOpus |
| Platinum | Gold, platinum ingots | All The Ores |
| Iridium | Platinum, iridium ingots | All The Ores |
| Ruby | Amethyst, rubies | All The Ores |
| Sapphire | Lapis, sapphires | All The Ores |
| Peridot | Amethyst, peridots | All The Ores |
| Cinnabar | Redstone, cinnabar | All The Ores |
| Salt | Desert, salt | All The Ores |
| Allthemodium | Netherite, allthemodium ingots | Allthemodium |
| Vibranium | Allthemodium, vibranium ingots | Allthemodium |
| Unobtainium | Vibranium, unobtainium ingots | Allthemodium |

The Mechanical Miner Ant also turns up raw tin, osmium, zinc, lead and molybdenum at stage 3 when a mod adds them.

### All The Ores

Ants bring every ore the mod adds. Platinum and Iridium Ants are slow, like Uranium Ants: they find nuggets and raw ore
from the start, and ingots as rare finds at stage 3. Ruby, Sapphire and Peridot Ants find the gem and its dust.
Cinnabar and Salt Ants want a hot climate.

### Allthemodium

Three species follow the Netherite Ant, each made from the one before. They are the very end of the game.

| Ant | Climate | Finds its metal |
|---|---|---|
| Allthemodium | Underground | At ordinary Foraging Sites: nuggets from stage 3, raw ore at stage 4 |
| Vibranium | Nether | Only on Nether expeditions, from stage 3 |
| Unobtainium | End | Only on End expeditions, from stage 3 |

- **Made anywhere.** Unlike the Netherite, Ender and Dragon Ants, the laboratory makes them in any dimension.
- **Slow and long-lived.** Their Queens lay half as many eggs as a Netherite Queen, and an adult lives three times as
  long.
- **The metal is rare.** About 18 allthemodium nuggets and 3 raw ore in every 1,000 finds, about 2 vibranium nuggets in
  1,000 and 1 unobtainium nugget in 1,000. A colony of 6,000 with three sites brought 9 or 10 vibranium nuggets an
  hour.
- Left out: Allthemodium's alloys, molten metals and Piglich Hearts.

### MachinaOpus

- **Tin, Lead and Silver Ants** find its nuggets from the start, raw ore from stage 2 and ingots at stage 3.
- **Uranium Ants** find its raw uranium. **Fluorite Ants** find fluorite.
- **Molybdenum Ants** find molybdenum nuggets, raw molybdenum from stage 2 and ingots at stage 3.
- **Sulfur Ants** (hot climate) find sulfur without an Oil Refinery, with netherrack, magma, basalt, gunpowder from
  stage 2 and the odd petroleum coke at stage 3.
- **Rubber Ants** are farmers. They forage rubber logs, leaves and saplings, and tap rubber sheets at a Harvesting
  Site.

Its pipes and cables work with the mod's blocks: an item pipe set to pull empties a Storage or feeds a Formicarium, and
an energy cable carries a Generator's FE into a Genetic Laboratory.

## Liquid Ants

Ant Colonies alone has Water and Lava Ants. Each row below adds one more, and only when the mod is installed **and**
its fluid is in the game. Otherwise none of it exists: no item, no species, no error.

| Ant | Fluid | Mod | Comes from, fed | One droplet gives |
|---|---|---|---|---|
| Oil | `machinaopus:crude_oil` | MachinaOpus | Water Ant, Crude Oil Bucket | 50 mB, 2,400 FE |
| Blaze | `machinaopus:liquid_blaze` | MachinaOpus | Lava Ant, Liquid Blaze Bucket | 25 mB, 4,000 FE |
| Brine | `mekanism:brine` | Mekanism | Water Ant, Brine Bucket | 50 mB, 800 FE |
| Heavy Water | `mekanism:heavy_water` | Mekanism | Water Ant, Heavy Water Bucket | 25 mB, 3,200 FE |
| Honey | `create:honey` | Create | Honeypot Ant, Honey Bucket | 50 mB, 800 FE |
| Chocolate | `create:chocolate` | Create | Water Ant, Chocolate Bucket | 50 mB, 800 FE |
| Tea | `create:tea` | Create | Water Ant, Builder's Tea | 50 mB, 800 FE |
| Pink Slime | `industrialforegoing:pink_slime` | Industrial Foregoing | Water Ant, Pink Slime Bucket | 25 mB, 2,400 FE |
| Latex | `industrialforegoing:latex` | Industrial Foregoing | Water Ant, Latex Bucket | 50 mB, 1,200 FE |
| Nutrient | `enderio:fluid_nutrient_distillation_still` | Ender IO | Water Ant, Nutrient Distillation Bucket | 50 mB, 1,600 FE |
| Hootch | `enderio:fluid_hootch_still` | Ender IO | Water Ant, Hootch Bucket | 50 mB, 2,000 FE |
| Resin | `thermal:resin` | Thermal Series | Water Ant, Resin Bucket | 50 mB, 1,200 FE |
| Sap | `thermal:sap` | Thermal Series | Water Ant, Sap Bucket | 50 mB, 800 FE |
| Creosote | `immersiveengineering:creosote` | Immersive Engineering | Lava Ant, Creosote Bucket | 50 mB, 1,200 FE |
| Plant Oil | `immersiveengineering:plantoil` | Immersive Engineering | Water Ant, Plant Oil Bucket | 50 mB, 1,200 FE |

- **A different bucket.** If a mod names its bucket differently, add the right item to the item tag
  `antcolonies:liquid_feed/<kind>`. Tea has no bucket, so Tea Ants are fed Create's Builder's Tea.
- **Removing a mod** keeps the colonies of its Liquid Ants. The Formicarium says *These ants need* and the mod's name,
  and they work again when it is back.
- **No ants for:** Mekanism's gases and other chemicals (they are not fluids), Applied Energistics 2 (it adds no fluid
  of its own), and Thermal's crude oil and latex (MachinaOpus and Industrial Foregoing already give ants of those
  names).
- A pack can add a Liquid Ant for any other fluid: see the [modpack guide](modpack-guide.md#liquid-ants).

## What was tested

| Mod | Status on Minecraft 26.3 |
|---|---|
| JEI, PF-EMI, Jade | Tested in game |
| MachinaOpus 0.0.6 | Tested in game: its ants, Oil and Blaze Ants, pipes, cables and fluid tanks |
| FTB Teams, All The Ores, Allthemodium | No 26.3 build yet. Tested on the 26.1.2 version of Ant Colonies (FTB Teams 26.1.2.5, All The Ores 4.0.4, Allthemodium 4.3.0). |
| Mekanism, Create, Thermal, Immersive Engineering, Industrial Foregoing, Ender IO, Applied Energistics 2 | Not run: none had a 26.3 build to try. Their mod and fluid ids were read from their source. If an id differs in a later version, that ant simply does not appear. |
