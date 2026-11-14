# Tools

Single-file Java programs that generate the mod's resources. Run them from the project root with JDK 25; each one
overwrites its output, so change the program rather than the files it writes.

| Program | Writes | Run |
|---|---|---|
| `DataGen.java` | Every JSON file in `common/src/generated/resources`: species, evolutions, hybrids, mutations, lab costs, recipes, loot tables, tags, world generation, advancements, models, blockstates, sounds and `en_us.json` | `java tools/DataGen.java` |
| `TexGen.java` | Every texture in `common/src/generated/resources`: blocks, items, the ant model's skins, worn armor, particles and GUI sprites | `java tools/TexGen.java` |
| `BrandGen.java` | `common/src/main/resources/icon.png` and `logo.png` (the mod list), and `docs/images/icon.png`, `logo.png`, `banner.png` and `social-preview.png` | `java tools/BrandGen.java` |

All art is pixel art painted in code: shapes, palettes and shading are in the programs, so it can be changed, rescaled or
recolored and regenerated at any time.

## Adding a fluid for Liquid Ants

One row in each of three tables, with the same name:

1. `tools/DataGen.java`, `LIQUIDS`: the ant's name, the mod and fluid id, the fluid's English name (the droplet is named
   "<fluid> Droplet"), what it evolves from and is fed, its colours, its rate and its Centrifuge recipe. This writes the
   species, the evolution, the feed tag, the recipe, the item model and the names.
2. `tools/TexGen.java`, `LIQUIDS`: the droplet's colour and finish (clear, thick, slime or molten). This paints
   `item/<name>_droplet.png` from the shared droplet shape.
3. A `LiquidKinds.offer(LiquidKind.of(name, modId, fluidId))` call in the mod: a class in
   `compat/src/main/java/com/antcolonies/compat/liquid/` listed in `LiquidIntegrations` (or, from another mod, its own
   constructor).

Then run both programs. The droplet item is registered, and the data files load, only when that mod is installed and the
fluid is in the fluid registry; the `liquid_species_follow_their_fluids` game test checks every row either way.
