// Not from the guide: other things modpacks commonly do with KubeJS, checked by KubejsCheckServer.
ServerEvents.recipes(event => {
  let seen = 0
  event.forEachRecipe({ mod: 'antcolonies' }, recipe => { seen++ })
  console.info('[AntColonies KubeJS check] antcolonies recipes seen by KubeJS: ' + seen)

  // A new recipe that makes a mod item.
  event.shapeless(Item.of('antcolonies:ant_food', 2), ['minecraft:sugar', 'minecraft:wheat_seeds', 'minecraft:bone_meal'])
    .id('mypack:cheap_ant_food')
  // Changing an ingredient inside a mod recipe.
  event.replaceInput({ id: 'antcolonies:tweezers' }, 'minecraft:iron_ingot', 'minecraft:copper_ingot')
})

ServerEvents.tags('item', event => {
  // Mineral Gathering also favours amethyst shards.
  event.add('antcolonies:foraging/mineral', 'minecraft:amethyst_shard')
})
