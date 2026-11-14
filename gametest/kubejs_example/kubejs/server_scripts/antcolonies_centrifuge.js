// kubejs/server_scripts/antcolonies_centrifuge.js
ServerEvents.recipes(event => {
  // Lava Droplets give half as much for twice the power: replace the built-in recipe.
  event.remove({ id: 'antcolonies:centrifuging/lava' })
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'antcolonies:lava_droplet',
    result: { id: 'minecraft:lava', amount: 25 },
    energy: 4000,
    time: 200
  }).id('mypack:centrifuging/lava')

  // Any item can be spun: snowballs melt into water.
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'minecraft:snowball',
    result: { id: 'minecraft:water', amount: 250 },
    energy: 200
  }).id('mypack:centrifuging/snowball')

  // The Syrup Droplets of the pack's own Liquid Ant (section 5).
  event.custom({
    type: 'antcolonies:centrifuging',
    ingredient: 'mypack:syrup_droplet',
    result: { id: 'mypack:syrup', amount: 50 },
    energy: 1200,
    time: 60
  }).id('mypack:centrifuging/syrup')
})
