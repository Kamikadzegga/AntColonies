# Compatibility

Ant Colonies needs nothing but NeoForge. Everything on this page is optional, and nothing is missing without it.

## Contents

- [Recipe viewers and Jade](#recipe-viewers-and-jade)
- [Pipes, cables and tanks](#pipes-cables-and-tanks)
- [FTB Teams](#ftb-teams)
- [All the Mods 11](#all-the-mods-11)
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

## All the Mods 11

The pack runs on Minecraft 26.1.2, so it takes the
[26.1.2 version](https://github.com/Kamikadzegga/AntColonies/tree/26.1.2) of the mod, which is made to fit it. This
version carries the same support, for the day its mods come to 26.3:

- **Wild nests and ants** live in the biomes of Terralith and Regions Unexplored, in Nullscape's End, and in the pack's
  other worlds: the Twilight Forest, Aether II, the Undergarden and The Other. In worlds with a roof they stand on cave
  floors. The Mining Dimension has none.
- **The Other's nests** hold Allthemodium or Vibranium Ants: catch them there, or make them in the laboratory.
- **Ants for the pack's materials.** Every ore of All The Ores and Allthemodium, every gem of Silent's Gems, and the
  ores, gems and metals of the mods in the [table below](#ants-for-other-mods-materials), and All The Ores' nine
  alloys and silicon: 90 species in the pack.
- **The pack's own items.** Where several mods add the same material, ants bring All The Ores' item, and otherwise
  that of the mod the game loaded first. The [server config](modpack-guide.md#outputs) changes both.
- **Liquid Ants:** 37 in the pack. Water and Lava; Oil, Honey, Sap, Creosote, Experience and Acid; Ender IO's seven;
  Blood, Soul Lava, Menril, Liquid Chorus, Solvent, Liquid Force, Etherium, Virulent and Polymorphic Ants; and the
  fuels of Oritech, Stellaris and Just Dire Things. See [Liquid Ants](#liquid-ants).

Biomes are matched by the common biome tags, so other world generation mods get nests too, as long as they tag their
biomes.

## Ants for other mods' materials

These species load only when a mod that adds their material is installed. They are researched and made in the Genetic
Laboratory like the built-in mineral ants: scan the ingot, gem or dust once, then mutate a Queen with it. Their finds
go through the common `c:` tags, so they work with whichever mod provides the material.

| Species | Made from | Loads with |
|---|---|---|
| Osmium | Stone, osmium ingots | Mekanism, All The Ores |
| Tin | Copper, tin ingots | Mekanism, Thermal, MachinaOpus, All The Ores, Railcraft, Energized Power, Modern Industrialization |
| Zinc | Copper, zinc ingots | Create, All The Ores, Railcraft |
| Lead | Iron, lead ingots | Mekanism, Thermal, Immersive Engineering, MachinaOpus, All The Ores, Railcraft, Modern Industrialization |
| Nickel | Iron, nickel ingots | Thermal, Immersive Engineering, All The Ores, Railcraft, Oritech, Modern Industrialization |
| Aluminum | Iron, aluminum ingots | Immersive Engineering, All The Ores |
| Silver | Lead, silver ingots | Thermal, Immersive Engineering, MachinaOpus, All The Ores, Railcraft, Occultism, Ice and Fire |
| Uranium | Lead, raw uranium | Mekanism, Immersive Engineering, MachinaOpus, All The Ores, Oritech, Modern Industrialization, Extreme Reactors (its yellorite) |
| Fluorite | Amethyst, fluorite | Mekanism, MachinaOpus, All The Ores |
| Certus | Amethyst, certus quartz | Applied Energistics 2 |
| Molybdenum | Iron, molybdenum ingots | MachinaOpus |
| Sulfur | Fire, sulfur | MachinaOpus, All The Ores, Railcraft, Neo Vitae |
| Rubber | Wood, rubber sheets | MachinaOpus |
| Platinum | Gold, platinum ingots | All The Ores, Oritech |
| Iridium | Platinum, iridium ingots | All The Ores |
| Ruby | Amethyst, rubies | All The Ores, Silent's Gems |
| Sapphire | Lapis, sapphires | All The Ores, Silent's Gems, Ice and Fire |
| Peridot | Amethyst, peridots | All The Ores, Silent's Gems |
| Cinnabar | Redstone, cinnabar | All The Ores |
| Salt | Desert, salt | All The Ores |
| Allthemodium | Netherite, allthemodium ingots | Allthemodium |
| Vibranium | Allthemodium, vibranium ingots | Allthemodium |
| Unobtainium | Vibranium, unobtainium ingots | Allthemodium |
| Uraninite | Redstone, raw uraninite | Powah |
| Inferium | Stone, inferium essence | Mystical Agriculture |
| Arcane | Amethyst, arcane crystals | Forbidden and Arcanus |
| Iesnium | Silver, iesnium ingots | Occultism |
| Dark Gem | Coal, dark gems | EvilCraft |
| Bort | Deepslate, bort | Silent Gear |
| Crimson Iron | Iron, crimson iron ingots | Silent Gear |
| Azure Silver | Chorus, azure silver ingots | Silent Gear |
| Cloggrum | Iron, cloggrum ingots | The Undergarden |
| Froststeel | Cloggrum, froststeel ingots | The Undergarden |
| Utherium | Redstone, utherium crystals | The Undergarden |
| Regalium | Amethyst, regalium crystals | The Undergarden |
| Dimensional | Lapis, dimensional shards | RFTools |
| Fluxite | Lapis, fluxite | Oritech |
| Force | Gold, force gems | Forcecraft |
| Chimerite | Amethyst, chimerite | Ars Magica: Legacy |
| Amber | Stone, amber | Thaumaturge |
| Saltpeter | Desert, saltpeter | Railcraft, Neo Vitae, Eternal Starlight |
| Sal Ammoniac | Amethyst, sal ammoniac crystals | Theurgy |
| Desh | Iron, desh ingots | Stellaris, Ad Astra |
| Titanium | Iron, titanium ingots | Stellaris, Modern Industrialization |
| Ambrosium | Coal, ambrosium shards | Aether II |
| Zanite | Amethyst, zanite gemstones | Aether II |
| Arkenium | Iron, arkenium plates | Aether II |
| Gravitite | Arkenium, gravitite plates | Aether II |
| Corrobonite | Zanite, corrobonite crystals | Aether II |
| Alexandrite, Ammolite, Moldavite, Opal, Pearl, Rose Quartz | Amethyst, the gem | Silent's Gems |
| Aquamarine, Iolite, Kyanite, Tanzanite | Lapis, the gem | Silent's Gems (Aquamarine: also Astral Sorcery) |
| Citrine, Heliodor, Topaz | Gold, the gem | Silent's Gems (Topaz: also Ars Magica: Legacy, Iron's Jewelry) |
| Carnelian, Garnet | Redstone, the gem | Silent's Gems (Garnet: also Iron's Jewelry) |
| Black Diamond, Turquoise, White Diamond | Coal, Copper, Diamond; the gem | Silent's Gems |
| Chaos | Amethyst, chaos essence | Silent's Gems |
| Bronze | Tin, bronze ingots | All The Ores, Thermal, Mekanism, Railcraft, Modern Industrialization |
| Brass | Zinc, brass ingots | All The Ores, Create, Railcraft |
| Steel | Iron, steel ingots | All The Ores, Thermal, Mekanism, Immersive Engineering, Railcraft, Oritech, Energized Power, Modern Industrialization |
| Invar | Nickel, invar ingots | All The Ores, Thermal, Railcraft, Modern Industrialization |
| Constantan | Copper, constantan ingots | All The Ores, Thermal, Immersive Engineering |
| Electrum | Gold, electrum ingots | All The Ores, Thermal, Immersive Engineering |
| Signalum | Redstone, signalum ingots | All The Ores, Thermal |
| Lumium | Tin, lumium ingots | All The Ores, Thermal |
| Enderium | Lead, enderium ingots | All The Ores |
| Silicon | Desert, silicon | Applied Energistics 2, Ender IO, Energized Power, Oritech, Refined Storage, Stellaris |
| Ferricore | Iron, ferricore ingots | Just Dire Things |
| Blazegold | Ferricore, blazegold ingots | Just Dire Things |
| Celestigem | Blazegold, celestigems | Just Dire Things |
| Eclipse Alloy | Celestigem, eclipse alloy ingots | Just Dire Things |
| Ironwood | Wood, ironwood ingots | The Twilight Forest |
| Steeleaf | Ironwood, steeleaf | The Twilight Forest |
| Knightmetal | Iron, knightmetal ingots | The Twilight Forest |
| Rogdorium | Froststeel, rogdorium | The Undergarden |
| Soulium | Soul, soulium dust | Mystical Agriculture |
| Novite | Desh, novite ingots | Stellaris |
| Firestone | Fire, raw firestone | Railcraft |
| Demonite | Soul, hellforged ingots | Neo Vitae |
| Infinity | Deepslate, grains of infinity | Ender IO |
| Neptunium | Reef, neptunium ingots | Aquaculture |
| Antimony | Iron, antimony ingots | Modern Industrialization |
| Tungsten | Iron, tungsten ingots | Modern Industrialization |
| Monazite | Redstone, monazite dust | Modern Industrialization |
| Bauxite | Iron, bauxite dust | Modern Industrialization |
| Lignite | Coal, lignite coal | Modern Industrialization |
| Anglesite | Amethyst, anglesite crystals | Extreme Reactors |
| Benitoite | Lapis, benitoite crystals | Extreme Reactors |
| Draconium | Diamond, draconium ingots | Draconic Evolution |
| Black Quartz | Coal, black quartz | Actually Additions |
| Mithril | Deepslate, mithril ingots | Iron's Spells 'n Spellbooks |
| Starmetal | Iron, starmetal ingots | Astral Sorcery |
| Xychorium | Amethyst, xychorium gems | XyCraft |
| Ostrum | Desh, ostrum ingots | Ad Astra |
| Calorite | Ostrum, calorite ingots | Ad Astra |
| Ice Shard | Snow, ice shards | Ad Astra |
| Deepsilver | Iron, deepsilver ingots | Eternal Starlight |
| Starcore, Starlit Diamond, Malarite, Thioquartz | Redstone, Diamond, Amethyst, Amethyst; the gem | Eternal Starlight |

The Mechanical Miner Ant also turns up raw tin, osmium, zinc, lead and molybdenum at stage 3 when a mod adds them.

Some of these bring more than their own material:

- **Inferium Ants** bring prosperity shards from stage 2.
- **Arcane Ants** bring runes from stage 2 and, rarely, a piece of stellarite at stage 4.
- **Chimerite Ants** bring vinteum dust and topaz, and at stage 4 a rare moonstone or sunstone.
- **Amber Ants** bring raw cinnabar, and quicksilver from stage 2.
- **Ironwood Ants** bring liveroot too. **Knightmetal Ants** bring armor shards, nine to an ingot.
- **Xychorium Ants** bring all five colours.
- **Alloy ants** bring their alloy ready mixed: nuggets, dust (an alloy has no ore) and the odd ingot. An alloy's
  research waits for that of its other metal: Bronze for Tin, Constantan for Nickel, Enderium for Platinum.
- Left out: what is only crafted (Mystical Agriculture's higher essences, the alloys of Ender IO, Allthemodium,
  Silent Gear and Oritech), The Aether's ores (only Aether II's have ants), and boss drops.

### How fast they work

An ant for another mod's material works at the pace of how hard that material is to get in its own mod and in a big
pack: how rare its ore is, which pickaxe mines it, and what dimension, boss or machine stands before it.

Every number here and elsewhere in these pages is measured the same way: **one Foraging Site filled with ants (256, its
size without Population Upgrades), no upgrades of any kind, a grown and fed colony, a plain Queen.** Upgrades, more
sites and a better Queen all raise it.

| As hard as | An hour, about | Ants |
|---|---|---|
| Iron or gold | 400 ingots' worth and more | Tin, Zinc, Aluminum, Lead, Silver, Nickel, Uranium, Titanium, Crimson Iron, Cloggrum, Antimony, Deepsilver, Ferricore, Ironwood; the gems Ruby, Sapphire, Peridot, Fluorite, Certus, Topaz, Aquamarine, Garnet, Bort, Dark Gem, Fluxite, Force, Chimerite, Amber, Zanite, Black Quartz, Malarite, Xychorium; Salt, Sulfur, Saltpeter, Sal Ammoniac, Inferium, Uraninite, Dimensional, Ambrosium, Soulium, Infinity, Chaos, Bauxite, Lignite, Benitoite |
| A diamond pickaxe, a rare vein, a first rocket | 50 | Osmium (30), Froststeel (25), Utherium, Regalium (30), Cinnabar, Arcane, Desh, Arkenium, Gravitite (30), Opal, Alexandrite, Iolite, Heliodor, Turquoise, Carnelian, Citrine, Moldavite, Tanzanite, Black Diamond (25), Blazegold, Steeleaf, Tungsten, Monazite, Anglesite, Starmetal, Starcore, Thioquartz, Ice Shard (100) |
| Diamond | 4 | Platinum, Iridium, Azure Silver, Iesnium (each about 5 ingots' worth, nearly all as nuggets); Pearl, Ammolite, Kyanite, Rose Quartz, Novite, Corrobonite, Draconium, Starlit Diamond; White Diamond and Celestigem (2); Knightmetal (28 shards, about 3 ingots); Rogdorium (17 nuggets, about 2 crystals); Ostrum |
| Netherite | 1 in two hours | Eclipse Alloy, Demonite; Mithril (raw, a quarter ingot each); Calorite (a few nuggets) |
| Found by luck | | Firestone (1 or 2 raw), Neptunium (3 nuggets) |

**Alloys come a little slower than their metals.** An alloy ant saves the work of mixing, so it works at seven tenths
of the pace of the ant of the hardest metal in its alloy: Bronze, Brass and Steel about 440 ingots' worth an hour,
Invar, Constantan, Electrum, Signalum and Lumium about 330, Enderium about 3.5. The Silicon Ant follows the same rule
against the Certus Ant: about 540 silicon an hour.

For scale, measured the same way: an Iron Ant colony brings about 490 ingots' worth an hour, a Diamond Ant colony 3 or
4 diamonds, a Netherite Ant colony a scrap in three hours.

### All The Ores

Ants bring every ore the mod adds, and its nine alloys, each a little slower than the ants of the metals in it.
Platinum and Iridium Ants are as slow as Diamond Ants, and Osmium and Cinnabar Ants
slow too: their ores are rare in a big pack or take a diamond pickaxe. Ruby, Sapphire and Peridot Ants find the gem and
its dust. Cinnabar and Salt Ants want a hot climate.

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
- **The metal comes slowly.** One full site with no upgrades brings about 6 allthemodium nuggets an hour and a raw ore
  every ten hours, about 6 vibranium nuggets from a Nether Foraging Site, or about 4 unobtainium nuggets from an End
  Foraging Site. More sites add less each: with three, each brings about a fifth less.
- **Wild in The Other.** The nests there hold Allthemodium or Vibranium Ants.
- Left out: Allthemodium's alloys, molten metals and Piglich Hearts.

### MachinaOpus

- **Tin, Lead and Silver Ants** find its nuggets from the start, raw ore from stage 2 and ingots at stage 3.
- **Uranium Ants** find its raw uranium. **Fluorite Ants** find fluorite.
- **Molybdenum Ants** find molybdenum nuggets, raw molybdenum from stage 2 and ingots at stage 3.
- **Sulfur Ants** (hot climate) find sulfur without an Oil Refinery, with netherrack, magma, basalt, gunpowder from
  stage 2 and the odd petroleum coke at stage 3. With other mods they find that mod's sulfur, and no coke.
- **Rubber Ants** are farmers. They forage rubber logs, leaves and saplings, and tap rubber sheets at a Harvesting
  Site.

Its pipes and cables work with the mod's blocks: an item pipe set to pull empties a Storage or feeds a Formicarium, and
an energy cable carries a Generator's FE into a Genetic Laboratory.

## Liquid Ants

Ant Colonies alone has Water and Lava Ants. Each row below adds one more, and only when one of its mods is installed
**and** that mod's fluid is in the game. Otherwise none of it exists: no item, no species, no error.

- **Fed a bucket of the fluid.** The ant in the third column becomes the new one when its Queen is fed the fluid's
  bucket.
- **Several mods, one ant.** Where more than one mod adds the same liquid, the ants make the fluid of the first mod in
  the row that is installed.

| Ant | Makes | Mods, in order | Comes from | One droplet gives |
|---|---|---|---|---|
| Oil | Crude Oil | MachinaOpus, PneumaticCraft, Modern Industrialization, Oritech, Ad Astra, Stellaris | Water | 50 mB, 2,400 FE |
| Blaze | Liquid Blaze | MachinaOpus | Lava | 25 mB, 4,000 FE |
| Brine | Brine | Mekanism | Water | 50 mB, 800 FE |
| Heavy Water | Heavy Water | Mekanism, Modern Industrialization | Water | 25 mB, 3,200 FE |
| Acid | Sulfuric Acid | Mekanism, Modern Industrialization, Oritech | Water | 50 mB, 2,000 FE |
| Bioethanol | Bioethanol | Mekanism Generators, Create Crafts & Additions | Water | 50 mB, 1,600 FE |
| Honey | Honey | Create, Productive Bees, Extended Industrialization, The Bumblezone | Honeypot | 50 mB, 800 FE |
| Chocolate | Chocolate | Create | Water | 50 mB, 800 FE |
| Tea | Builder's Tea | Create | Water | 50 mB, 800 FE |
| Pink Slime | Pink Slime | Industrial Foregoing | Water | 25 mB, 2,400 FE |
| Latex | Latex | Industrial Foregoing | Water | 50 mB, 1,200 FE |
| Biofuel | Biofuel | Oritech, Industrial Foregoing | Water | 50 mB, 1,600 FE |
| Experience | Liquid Experience | Ender IO, Industrial Foregoing, PneumaticCraft, Mob Grinding Utils, Create: Enchantment Industry, Just Dire Things, Energized Power, Neo Vitae, Sophisticated Core | Water | 20 mB (one point), 2,000 FE |
| Nutrient | Nutrient Distillation | Ender IO | Water | 50 mB, 1,600 FE |
| Hootch | Hootch | Ender IO | Water | 50 mB, 2,000 FE |
| Fire Water | Fire Water | Ender IO | Hootch | 50 mB, 2,400 FE |
| Sunshine | Liquid Sunshine | Ender IO | Hootch | 50 mB, 2,000 FE |
| Void Dew | Dew of the Void | Ender IO | Nutrient | 50 mB, 2,400 FE |
| Cloud Seed | Cloud Seed | Ender IO | Water | 50 mB, 1,200 FE |
| Resin | Resin | Thermal Series | Water | 50 mB, 1,200 FE |
| Sap | Sap | Thermal Series, Productive Trees (maple sap) | Water | 50 mB, 800 FE |
| Creosote | Creosote | Immersive Engineering, Railcraft, Modern Industrialization | Lava | 50 mB, 1,200 FE |
| Plant Oil | Plant Oil | Immersive Engineering, PneumaticCraft, Create Crafts & Additions, Modern Industrialization | Water | 50 mB, 1,200 FE |
| Canola | Canola Oil | Actually Additions | Water | 50 mB, 1,200 FE |
| Blood | Blood | EvilCraft | Water | 50 mB, 1,200 FE |
| Soul Lava | Soul Lava | Allthemodium | Lava | 25 mB, 3,200 FE |
| Menril | Menril Resin | Integrated Dynamics | Water | 50 mB, 1,200 FE |
| Liquid Chorus | Liquid Chorus | Integrated Dynamics | Water | 50 mB, 1,600 FE |
| Solvent | Sal Ammoniac | Theurgy | Water | 50 mB, 800 FE |
| Liquid Force | Liquid Force | Forcecraft | Water | 50 mB, 1,600 FE |
| Etherium | Liquid Etherium | Ars Magica: Legacy | Water | 50 mB, 2,000 FE |
| Virulent | Virulent Mix | The Undergarden | Water | 50 mB, 1,200 FE |
| Polymorphic | Polymorphic Fluid | Just Dire Things | Water | 50 mB, 1,600 FE |
| Heavy Oil | Heavy Oil | Oritech | Oil | 50 mB, 2,400 FE |
| Diesel | Diesel | Oritech, Stellaris | Oil | 50 mB, 2,400 FE |
| Naphtha | Light Naphtha | Oritech | Oil | 50 mB, 2,400 FE |
| Turbofuel | Turbofuel | Oritech | Diesel | 50 mB, 3,200 FE |
| Fuel | Fuel | Stellaris | Oil | 50 mB, 2,400 FE |
| Hydrogen | Hydrogen | Stellaris | Water | 50 mB, 1,200 FE |
| Oxygen | Oxygen | Stellaris | Water | 50 mB, 1,200 FE |
| Rocket Fuel | Rocket Fuel | Ender IO | Hootch | 50 mB, 2,400 FE |
| Blaze Ember | Blaze Ember Fuel | Just Dire Things | Polymorphic | 25 mB, 3,200 FE |
| Voidflame | Voidflame Fuel | Just Dire Things | Blaze Ember | 25 mB, 4,000 FE |
| Eclipse Ember | Eclipse Ember Fuel | Just Dire Things | Voidflame | 25 mB, 6,000 FE |
| Time | Time Fluid | Just Dire Things | Polymorphic | 25 mB, 6,000 FE |
| Portal | Portal Fluid | Just Dire Things | Polymorphic | 25 mB, 4,000 FE |
| Ether | Ether | Eternal Starlight | Water | 50 mB, 1,600 FE |
| Starlight | Liquid Starlight | Astral Sorcery | Water | 50 mB, 2,000 FE |

- **Milk** needs no other mod: [Cow Ants](species-and-genetics.md#ants-for-fun) give Milk Droplets, 50 mB for 600 FE each.
- **A different bucket.** If a mod names its bucket differently, add the right item to the item tag
  `antcolonies:liquid_feed/<kind>`. Tea has no bucket, so Tea Ants are fed Create's Builder's Tea. Experience Ants
  take a Bottle o' Enchanting too. Oxygen has none either: Oxygen Ants are fed Stellaris' Fluid Cell.
- **Fuels come slower the more work they are in their own mod.** A full Liquid Chamber with no upgrades gives in an
  hour about 16 buckets of hydrogen, 11 of oxygen, 5 or 6 of diesel, naphtha, fuel or rocket fuel, 5 of heavy oil, 3 of
  turbofuel, 1.4 of Blaze Ember Fuel, two thirds of a bucket of Voidflame Fuel or Portal Fluid, and a quarter of a
  bucket of Eclipse Ember Fuel or Time Fluid.
- **Four buckets at most.** A Queen fed Voidflame Fuel, Eclipse Ember Fuel, Time Fluid or Portal Fluid changes after
  four buckets at the latest, where other liquids take up to twenty-four.
- **Removing a mod** keeps the colonies of its Liquid Ants. The Formicarium says *These ants need* and the mod's name,
  and they work again when it is back.
- **No ants for:** molten metals, other chemicals, half-made fluids (Just Dire Things' unrefined fuels and unstable
  portal fluid), and fluids that are a mod's own progress (Neo Vitae's life essence, a pack's own fluids).
- A pack can add a Liquid Ant for any other fluid: see the [modpack guide](modpack-guide.md#liquid-ants).

## What was tested

| Mod | Status on Minecraft 26.3 |
|---|---|
| JEI, PF-EMI, Jade | Tested in game |
| MachinaOpus 0.0.6 | Tested in game: its ants, Oil and Blaze Ants, pipes, cables and fluid tanks |
| FTB Teams, All The Ores, Allthemodium | No 26.3 build yet. Tested on the 26.1.2 version of Ant Colonies (FTB Teams 26.1.2.5, All The Ores 4.0.4, Allthemodium 4.3.0). |
| The mods of All the Mods 11 | No 26.3 builds. Their ants were tested in the pack on the 26.1.2 version of Ant Colonies. |
| Mekanism, Create and its addons, Thermal, Immersive Engineering, Industrial Foregoing, Applied Energistics 2, PneumaticCraft, Modern Industrialization, Ad Astra, Draconic Evolution, Extreme Reactors, Actually Additions, Astral Sorcery, Eternal Starlight, Iron's Spells 'n Spellbooks, XyCraft, Mob Grinding Utils | Not run: none had a 26.3 build to try. Their mod, item and fluid ids were read from their sources. If an id differs in a later version, that ant simply does not appear. |
