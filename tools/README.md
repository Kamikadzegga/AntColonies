# Tools

Single-file Java programs that generate the mod's resources. Run them from the project root with JDK 25. Each one
overwrites its output, so change the program, not the files it writes.

| Program | Writes | Run |
|---|---|---|
| `DataGen.java` | Every JSON file in `common/src/generated/resources`: species, evolutions, recipes, loot, tags, world generation, advancements, models and `en_us.json` | `java tools/DataGen.java` |
| `TexGen.java` | Every texture in `common/src/generated/resources`: blocks, items, ant skins, armor, particles and GUI sprites | `java tools/TexGen.java` |
| `BrandGen.java` | The mod icon and logo, and the banner and social preview in `docs/images` | `java tools/BrandGen.java` |

All art is pixel art painted in code, so it can be changed, rescaled or recoloured and generated again at any time.

## Adding a fluid for Liquid Ants

One row in each of three places, with the same name. Then run `DataGen` and `TexGen`.

| Where | What the row holds | What it writes |
|---|---|---|
| `DataGen.java`, `LIQUIDS` | The ant's name, the mod and fluid id, the fluid's English name, what it evolves from and is fed, its colours, rate and Centrifuge recipe | The species, evolution, feed tag, recipe, item model and names |
| `TexGen.java`, `LIQUIDS` | The droplet's colour and finish (clear, glass, thick, oil, slime or molten) | `item/<name>_droplet.png` |
| `compat/.../compat/liquid/`, listed in `LiquidIntegrations` | A `LiquidKinds.offer(LiquidKind.of(name, modId, fluidId))` call | Registers the droplet item |

The droplet item is registered, and its data files load, only when that mod is installed and the fluid exists.
Another mod can offer a fluid from its own constructor with the same call.

## Adding genetic research

A species is made in the Genetic Laboratory, and not by feeding, when it has a research entry. In `DataGen.java`:

1. `research()`: a row with the species, the material the scanner reads, the tier, the Evolution Catalysts a mutation
   takes, the research it waits for (or `null`) and its hint.
2. `LAB_AMOUNTS`: a row for each evolution into that species, with how many of its material one mutation takes.

A gene gets its research from its row in `mutations()`, and its tier and hint from `GENE_RESEARCH`. DataGen stops when
a catalyst mutation has no row there.

## The nest behind the glass

What is carved into a block of an upright colony is worked out in the game by
`client/src/main/java/com/antcolonies/client/nest/NestLayout.java`. Each chamber is drawn there by hand as rows of
text (`TEMPLATES`), and the tunnels are dug by rule. `TexGen` compiles that class to paint the blocks' nest pictures
(`block/<block>_front.png`), so the pictures always match the game. Run `TexGen` again after changing a template.

A new kind of colony block needs:

1. a row in `NestLayout.Kind` and in `TEMPLATES`;
2. the same row in TexGen's `NEST_BLOCKS`;
3. its features drawn in `NestRenderer.drawFeatures`;
4. a `colonyPart` call in `DataGen.java`, which writes its upright model.
