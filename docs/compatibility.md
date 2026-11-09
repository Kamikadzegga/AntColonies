# Compatibility

Ant Colonies needs nothing but NeoForge. Everything below is optional.

## Recipe viewers and look-at tooltips

- **JEI** (31.17+): thirteen categories, from species and evolutions to production rates and lab costs.
- **EMI**: the same thirteen categories, with the same layout and workstations. EMI itself has no official 26.3 build; the
  plugin was made against **PF-EMI** 1.1 ("EMI - Port Forward", an unofficial port on Modrinth). Tested in game with it
  (and with JEI installed alongside: EMI takes over and nothing shows twice).
- **Jade** (26.3.5+): look at a Terrarium for its species, stage, adults, food and problems; at a job site for its ants,
  output per minute and status; at a Generator or the Genetic Laboratory for what it is doing (Jade draws the FE bar).
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
- **Tags**: Diamond and Emerald Fragments are in `c:nuggets/diamond` and `c:nuggets/emerald`.
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
