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

Nothing here needs another mod, and nothing was tested with one: none of the mods below had a build for Minecraft 26.3
when this was written (AE2, Refined Storage 3 and Ender IO had 26.1.2 builds; Create and Mekanism only unofficial
ports). Everything goes through NeoForge's shared systems, which those mods use:

- **Items** (Create funnels, chutes and arms; Mekanism transporters; Ender IO conduits; AE2 and Refined Storage
  import, export and storage buses; hoppers): Resource Piles can be emptied, Queen Chamber Queens taken out, and the
  Terrarium, Food Storage, Queen Chamber, Genetic Laboratory and Generator filled, from any side.
- **Energy** (Mekanism cables, Ender IO conduits, any FE network): the Genetic Laboratory takes FE at its controller and
  Genetic Energy Core; the Generator gives FE out.
- **Tags**: Diamond and Emerald Fragments are in `c:nuggets/diamond` and `c:nuggets/emerald`.
- **Species for other mods' materials**, which only load when a mod that adds the material is installed, and find it
  through the common `c:` tags (whichever mod provides it):

| Species | Comes from | Loads with |
|---|---|---|
| Osmium Ant | Stone (osmium ingot) | Mekanism |
| Tin Ant | Copper (tin ingot) | Mekanism, Thermal |
| Zinc Ant | Copper (zinc ingot) | Create |
| Lead Ant | Iron (lead ingot) | Mekanism, Thermal, Immersive Engineering |
| Nickel Ant | Iron (nickel ingot) | Thermal, Immersive Engineering |
| Aluminum Ant | Iron (aluminum ingot) | Immersive Engineering |
| Silver Ant | Lead (silver ingot) | Thermal, Immersive Engineering |
| Uranium Ant | Lead (uranium ingot) | Mekanism, Immersive Engineering |
| Fluorite Ant | Amethyst (fluorite) | Mekanism |
| Certus Ant | Amethyst (certus quartz) | Applied Energistics 2 |

  The Mechanical Miner Ant also turns up raw tin, osmium, zinc and lead at stage 3 when a mod adds them. Ender IO and
  Refined Storage add no ores of their own; the vanilla materials they use (quartz, redstone, gold, ender pearls) come
  from Fire, Redstone, Gold and Ender Ants.
