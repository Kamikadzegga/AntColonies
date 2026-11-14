# Compatibility

Ant Colonies needs nothing but NeoForge. Everything below is optional.

## Recipe viewers and look-at tooltips

- **JEI** (31.17+): fifteen categories, from species and evolutions to production rates, expeditions and lab costs.
- **EMI**: the same fifteen categories, with the same layout and workstations. EMI itself has no official 26.3 build; the
  plugin was made against **PF-EMI** 1.1 ("EMI - Port Forward", an unofficial port on Modrinth). Tested in game with it
  (and with JEI installed alongside: EMI takes over and nothing shows twice).
- **Jade** (26.3.5+): look at a Terrarium for its species, stage, adults, food and problems; at a job site for its ants,
  output per minute and status (and a Foraging Site's mode, whether an expedition site's portal is active); at a Colony Connector
  for its channel, settings and what it moved; at a Generator or the Genetic Laboratory for what it is doing (Jade draws the FE bar).
  Each part can be turned off in Jade's plugin settings. **WTHIT** has no NeoForge build for 26.x, so there is no WTHIT
  plugin.

## Other mods

Nothing here needs another mod. Only MachinaOpus (below) was tested with Ant Colonies: none of the other mods had a
build for Minecraft 26.3 when this was written (AE2, Refined Storage 3 and Ender IO had 26.1.2 builds; Create and
Mekanism only unofficial ports). Everything goes through NeoForge's shared systems, which those mods use:

- **Items** (Create funnels, chutes and arms; Mekanism transporters; Ender IO conduits; AE2 and Refined Storage
  import, export and storage buses; hoppers): Storage chambers can be emptied, Queen Chamber Queens taken out, and the
  Terrarium, Storage, Queen Chamber, Genetic Laboratory and Generator filled, from any side.
- **Energy** (Mekanism cables, Ender IO conduits, any FE network): the Genetic Laboratory takes FE from any side; the
  Generator gives FE out.
- **Fluids** (any tank or fluid pipe): the Droplet Centrifuge's tank is an ordinary fluid handler on every side. Fluid
  can be taken from it, never put in, and it hands its fluid to whatever next to it accepts fluid.
- **Tags**: Diamond and Emerald Fragments are in `c:nuggets/diamond` and `c:nuggets/emerald`; every droplet is in
  `antcolonies:droplets`.
- **Species for other mods' materials**, which only load when a mod that adds the material is installed, and find it
  through the common `c:` tags (whichever mod provides it):

| Species | Comes from | Loads with |
|---|---|---|
| Osmium Ant | Stone (osmium ingot) | Mekanism |
| Tin Ant | Copper (tin ingot) | Mekanism, Thermal, MachinaOpus |
| Zinc Ant | Copper (zinc ingot) | Create |
| Lead Ant | Iron (lead ingot) | Mekanism, Thermal, Immersive Engineering, MachinaOpus |
| Nickel Ant | Iron (nickel ingot) | Thermal, Immersive Engineering |
| Aluminum Ant | Iron (aluminum ingot) | Immersive Engineering |
| Silver Ant | Lead (silver ingot) | Thermal, Immersive Engineering, MachinaOpus |
| Uranium Ant | Lead (raw uranium) | Mekanism, Immersive Engineering, MachinaOpus |
| Fluorite Ant | Amethyst (fluorite) | Mekanism, MachinaOpus |
| Certus Ant | Amethyst (certus quartz) | Applied Energistics 2 |
| Molybdenum Ant | Iron (molybdenum ingot) | MachinaOpus |
| Sulfur Ant | Fire (sulfur) | MachinaOpus |
| Rubber Ant | Wood (rubber sheet) | MachinaOpus |

  The Mechanical Miner Ant also turns up raw tin, osmium, zinc, lead and molybdenum at stage 3 when a mod adds them.
  Ender IO and Refined Storage add no ores of their own; the vanilla materials they use (quartz, redstone, gold, ender
  pearls) come from Fire, Redstone, Gold and Ender Ants.

### Liquid Ants for other mods' fluids

Ant Colonies alone has two Liquid Ants, Water and Lava. Each row below adds one more, and only when **both** hold: the
mod is installed, and that fluid is in the game's fluid registry. Then its droplet item is registered and its species,
its evolution and its Centrifuge recipe load. Otherwise none of it exists: no item, no species in the book, the creative
tab, JEI or EMI, and no error. A mod that is installed but has no such fluid in its version gives nothing.

| Ant | Fluid | Mod (id) | Comes from, fed | Droplet gives |
|---|---|---|---|---|
| Oil Ant | `machinaopus:crude_oil` | MachinaOpus (`machinaopus`) | Water Ant, Crude Oil Bucket | 50 mB, 2,400 FE |
| Blaze Ant | `machinaopus:liquid_blaze` | MachinaOpus | Lava Ant, Liquid Blaze Bucket | 25 mB, 4,000 FE |
| Brine Ant | `mekanism:brine` | Mekanism (`mekanism`) | Water Ant, Brine Bucket | 50 mB, 800 FE |
| Heavy Water Ant | `mekanism:heavy_water` | Mekanism | Water Ant, Heavy Water Bucket | 25 mB, 3,200 FE |
| Honey Ant | `create:honey` | Create (`create`) | Honeypot Ant, Honey Bucket | 50 mB, 800 FE |
| Chocolate Ant | `create:chocolate` | Create | Water Ant, Chocolate Bucket | 50 mB, 800 FE |
| Tea Ant | `create:tea` | Create | Water Ant, Builder's Tea | 50 mB, 800 FE |
| Pink Slime Ant | `industrialforegoing:pink_slime` | Industrial Foregoing (`industrialforegoing`) | Water Ant, Pink Slime Bucket | 25 mB, 2,400 FE |
| Latex Ant | `industrialforegoing:latex` | Industrial Foregoing | Water Ant, Latex Bucket | 50 mB, 1,200 FE |
| Nutrient Ant | `enderio:fluid_nutrient_distillation_still` | Ender IO (`enderio`) | Water Ant, Nutrient Distillation Bucket | 50 mB, 1,600 FE |
| Hootch Ant | `enderio:fluid_hootch_still` | Ender IO | Water Ant, Hootch Bucket | 50 mB, 2,000 FE |
| Resin Ant | `thermal:resin` | Thermal Series (`thermal`) | Water Ant, Resin Bucket | 50 mB, 1,200 FE |
| Sap Ant | `thermal:sap` | Thermal Series | Water Ant, Sap Bucket | 50 mB, 800 FE |
| Creosote Ant | `immersiveengineering:creosote` | Immersive Engineering (`immersiveengineering`) | Lava Ant, Creosote Bucket | 50 mB, 1,200 FE |
| Plant Oil Ant | `immersiveengineering:plantoil` | Immersive Engineering | Water Ant, Plant Oil Bucket | 50 mB, 1,200 FE |

What is and isn't checked:

- **MachinaOpus** was run with Ant Colonies in the game: with it, Oil and Blaze Ants exist and their droplets spin into
  its fluids, and a Centrifuge fills a MachinaOpus Fluid Tank next to it and one behind two fluid pipes; without it,
  only Water and Lava Ants exist. The game tests check both.
- **The other mods were not run**: none had a NeoForge build for Minecraft 26.3 to try. Their mod ids and fluid ids were
  read from their source (Mekanism and Ender IO from their 26.3 branches, Create, Industrial Foregoing and Immersive
  Engineering from 1.21.1, Thermal from 1.20.x, its newest). If an id differs in a later version, that ant simply does
  not appear. The item each Queen is fed is the fluid's bucket by the mods' naming (for Tea, which has no bucket,
  Create's Builder's Tea item, an id that was not confirmed): if a mod names it differently, add the right item to the
  tag `antcolonies:liquid_feed/<kind>`.
- **Mekanism's gases and other chemicals** live in Mekanism's own chemical registry, not the fluid registry, and get no
  ants. Brine and heavy water are ordinary fluids.
- **Applied Energistics 2** stores and moves fluids but registers none of its own, so it has no Liquid Ant.
- Thermal's crude oil and latex are left out: MachinaOpus and Industrial Foregoing already give ants of those names.
- Removing a mod from a world keeps colonies of its Liquid Ants as they were: the Terrarium says *These ants need*
  and the mod's name, and they work again when it is back.

### MachinaOpus

Tested together (MachinaOpus 0.0.6): the `machinaopus_materials` game test runs when MachinaOpus is in `run/mods`, and
passes at once without it. With MachinaOpus installed, eight species bring its materials:

- **Tin, Lead and Silver Ants**: its nuggets from the start, raw ore from stage 2, ingots as rare finds at stage 3.
- **Uranium Ant**: MachinaOpus's raw uranium from the start (it has no uranium nuggets or ingots). Lead Ants evolve into
  it when fed raw uranium.
- **Fluorite Ant**: fluorite, for hydrofluoric acid and uranium enrichment.
- **Molybdenum Ant** (Iron Ants fed a molybdenum ingot): molybdenum nuggets (a Processing upgrade turns them into
  ingots), raw molybdenum from stage 2, ingots at stage 3.
- **Sulfur Ant** (Fire Ants fed sulfur; hot climate): sulfur without an Oil Refinery, plus netherrack, magma, basalt,
  gunpowder from stage 2 and the odd petroleum coke at stage 3.
- **Rubber Ant** (Wood Ants fed a rubber sheet; a farmer): forages rubber logs, leaves and saplings, and at a Harvesting
  Site taps rubber sheets (and slime balls from stage 2).

Molybdenum, Sulfur and Rubber Ants name MachinaOpus's items directly, so they have species upgrades made from a
molybdenum ingot, sulfur and a rubber sheet.

MachinaOpus's pipes and cables work with Ant Colonies' blocks (the `machinaopus_pipes` game test): an item pipe set to
pull ("output" on the side touching it) empties a Storage; a pipe delivers Ant Food into a Terrarium, which feeds the
colony; an energy cable carries a Generator's FE into a Genetic Laboratory (the Generator pushes it, so the cable needs
no setting).
