ServerEvents.tags('worldgen/biome', event => {
  event.add('mypack:spawns/copper', ['minecraft:dripstone_caves', 'minecraft:stony_peaks'])
  event.add('antcolonies:has_wild_ants', '#mypack:spawns/copper')
})
