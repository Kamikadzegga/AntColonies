// kubejs/startup_scripts/mypack_syrup.js
StartupEvents.registry('fluid', event => {
  // The fluid mypack:syrup; its bucket is mypack:syrup_bucket.
  event.create('mypack:syrup').tint(0xC98A2B).noBlock()
})

StartupEvents.registry('item', event => {
  // What Syrup Ants give. It borrows the picture of the mod's Honey Droplet.
  event.create('mypack:syrup_droplet').texture('antcolonies:item/honey_droplet')
})
