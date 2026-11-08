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
