// kubejs/server_scripts/antcolonies_recipes.js
ServerEvents.recipes(event => {
  // A harder Terrarium: replace the built-in recipe.
  event.remove({ output: 'antcolonies:terrarium' })
  event.shaped('antcolonies:terrarium', [
    'GGG',
    'G G',
    'III'
  ], {
    G: 'minecraft:glass',
    I: 'minecraft:iron_ingot'
  })

  // No Royal Jelly crafting: it must come from the colony instead.
  event.remove({ output: 'antcolonies:royal_jelly' })
})
