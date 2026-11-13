# Changelog

## [1.4.0+1.21.1] - Unreleased

### New
- Stick a tool into the cutting board at the station: hold a knife, a sword or an axe on the cursor and Shift + click the
  bare board. A click takes it out again, and food you put on the board takes it out into your inventory.
- A knife in your off hand cuts at the board, as a knife in your inventory does.

### Fixed
- A right click on the cutting board with a knife no longer puts the knife on your cursor: it stays in your hand and
  shows over the food, so the first click cuts.
- A right click on the board with food lays it where a click at the station would: as the next layer of a dish being
  put together, or in place of a finished dish or a tool stuck in the board, which go to your inventory. Anything the
  board cannot use stays in your hand.
- A right click on the stove with a knife, a bowl or food that is not grilled opens the station instead of doing
  nothing, so you can go over from the board to the stove with the knife in hand.
- On Fabric the stations open on structures that [Sable](https://modrinth.com/mod/sable) moves, such as builds of
  [Sable Beyond](https://modrinth.com/mod/sable_beyond), instead of Farmer's Delight's menus.
- Things that are cut with a knife into what meals are served on are cut at the board, with their marks and a page in
  its book: [Fright's Delight](https://www.curseforge.com/minecraft/mc-mods/frights-delight)'s bone becomes the shards
  for its kebabs.
- Picking a dish in the board's book no longer takes the food lying there to be cut when the rest of the dish is not
  in your inventory.
- A right click on a dish being put together takes its top layer back, also when the dish is complete. A stack whose
  recipe does not take what lies there is no longer shown as ready, so a click gives its layers back.
- A knife carved into the board and taken off at the station no longer leaves the board stuck: the station opens on it
  again and food put there can be cut.
- [Real Camera](https://www.curseforge.com/minecraft/mc-mods/real-camera) and
  [Leawind's Third Person](https://www.curseforge.com/minecraft/mc-mods/leawind-third-person) no longer turn the
  stations' camera away: you see the pot, the board, the skillet and the stove as without them.
- Food a hopper or a pipe brings into the cooking pot while you stand at it cooks by itself, as it does when you are
  away. Only what you put in at the station waits for the spoon.
- Players whose game has no Hands-On Delight can get the food laid on a stove at the station: when they use the stove,
  Farmer's Delight takes it over, cooks it and throws it off the grill as usual.
- With the skillet station turned off in the config, food put into a skillet at the station before cooks the Farmer's
  Delight way instead of waiting for a toss.
- The stoves of [End's Delight](https://www.curseforge.com/minecraft/mc-mods/ends-delight) and
  [My Nether's Delight](https://www.curseforge.com/minecraft/mc-mods/my-nethers-delight) no longer puff dark smoke
  over the food while you grill at them.
- With [Modest Meals](https://www.curseforge.com/minecraft/mc-mods/modest-meals) the cooking pot takes at the station
  whatever its meals are made of, such as smoked ham for a soup or fruit and sugar for jam, and a pot of its meals
  cooks as one batch like any other dish.

Needs [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.8.

## [1.3.0+1.21.1] - 2026-11-11
### New
- The stations work on the flying ships of [Create Aeronautics](https://modrinth.com/mod/create-aeronautics): the pot, the board, the skillet
  and the stove open there instead of Farmer's Delight's menus, and the camera and the food stay with the ship as it
  flies and turns.
- Milk and other drinks are poured as a hand would: the bottle or bucket tips over the pot and the liquid runs out in a
  stream that colours the soup. On the board honey and milk lie on the food in drizzled lines.
- Eggs are knocked on the rim of the pot or the skillet, or on the board beside the food. Sugar, cocoa and the like are
  shaken out over the soup.
- Milk, eggs, sugar and the rest of what sinks into the cooking pot can be taken back out: click the soup with an empty
  hand, off the floating food. The container dips into the soup and comes back to you full, milk in its bucket.
- Food done in the skillet glows in a steady beat and steams until you take it, so you see at a glance that it is ready.
- The pot's dish and stars stay in sight over its walls and the blocks beside it, and the first thing put in floats
  across from the spoon.
- Food fried or grilled at the stations is cooked by Farmer's Delight's own skillet and stove, so mods that add to their
  cooking work at the stations too: [Project MMO: Farmer's Delight Compat](https://modrinth.com/mod/project-mmo-farmers-delight) experience,
  [Quality Food](https://modrinth.com/mod/quality-food) quality, [Create: Central Kitchen](https://modrinth.com/mod/create-central-kitchen)
  and others.

### Fixed
- Showing a dish made on the cutting board in [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) no longer crashes
  the game with older JEI versions, which many modpacks keep.
- [EMI](https://www.curseforge.com/minecraft/mc-mods/emi) no longer stops with "EMI experienced a critical error" in big
  modpacks.
- A broken recipe in another mod or a datapack no longer stops the game or the server from loading recipes.
- More than a stack of rice in the cooking pot cooks. The pot keeps one stack and gives the rest back.
- Stirring food that was left to cook alone in the pot no longer finishes the whole batch in a few seconds, and a batch
  you walk away from keeps the time it has cooked. A pot holding more than a stack of soup no longer gives a free serving.
- Buckets and bottles emptied into the pot after you walked away land beside it, as in Farmer's Delight, and ingredients
  your stars saved stay in the pot for you.
- Picking a dish in the board's book grows it from what already lies on the board, such as a cheeseburger over
  hamburgers. Ingredients that stack to fewer than sixteen no longer make oversized stacks there.
- The board's recipe book keeps its tabs on the page with many addons: one tab for every tool besides the knife.
- The skillet no longer stops moving when the pan goes down to three pieces while one is still landing.
- Food laid on the stove by hand comes off the grill when the stove station is turned off or the mod is removed. The
  stove's recipe book no longer shows the fried egg, which falls through the grill.
- The knife and the bowl the stations offer from your inventory are see-through again instead of solid.
- `config/handson_delight.properties` keeps its values when saved in another encoding, and takes yes/no, on/off and 1/0.
- With [Vanilla Delights](https://modrinth.com/mod/vanilla-delights)' dyed pots the cooking pot keeps its colour and no
  longer shows a second spoon and a floor over the soup.
- Food on the cutting board, the skillet or the stove no longer shows twice when another mod draws these blocks itself.
- On Fabric it asks for [Fabric API](https://modrinth.com/mod/fabric-api) 0.116.2 or newer, without which Farmer's
  Delight Refabricated crashes on start.

Needs [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.7.

## [1.2.0+1.21.1] - 2026-11-10
- Food the knife cuts lies down to be cut again, even when a dish could start from it. With some addons bread, dough,
  chorus fruit or peppers went onto a sandwich pile and could not be cut; now the dish starts when its next layer goes
  on top.
- A knife clicked on a dish in the making stays in your hand instead of jumping onto the pile.
- [Farmer's Delight](https://www.curseforge.com/minecraft/mc-mods/farmers-delight) addons work at the stations: addon pies and cakes are cut in wedges, dough
  in strips, liquids are poured and eggs cracked. The stoves of [End's Delight](https://www.curseforge.com/minecraft/mc-mods/ends-delight) and
  [My Nether's Delight](https://www.curseforge.com/minecraft/mc-mods/my-nethers-delight) open as stations, the pot still cooks a batch with
  [Autochef's Delight](https://www.curseforge.com/minecraft/mc-mods/autochefs-delight), and [Pizza Delight](https://www.curseforge.com/minecraft/mc-mods/pizza-delight)'s rolling pin rolls dough on the
  board. Crafts made of ingots, gems or obsidian stay in the crafting table.
- The camera no longer ends up inside wall cabinets, a low ceiling or a wall over a station.
- A resource pack that remodels the cooking pot ([Excalibur](https://www.curseforge.com/minecraft/texture-packs/excalibur))
  no longer gives it a second spoon and a floor over the soup. The pack's textures stay.
- Food that a 3D item resource pack turns into a model lies flat on the board, the skillet and the stove and gets its
  cut marks.
- [Armourer's Workshop](https://www.curseforge.com/minecraft/mc-mods/armourers-workshop) skins no longer turn invisible after cooking.
- Joining a world on Fabric no longer crashes.
- Needs [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.6.

## [1.1.0+1.21.1]

- Plated meals and salads are made on the cutting board instead of the crafting table: put the bowl down, then what
  goes into it. A salad is tossed in the bowl; bacon and eggs, a steak or a fish is laid in its place.
- So are the rest of Farmer's Delight's food crafts. The kelp roll is rolled up, cod and salmon rolls are pressed onto
  their rice, a barbecue stick is skewered from the tip down, a melon popsicle freezes on its stick, a sandwich or a
  burger is closed with the top half of its bread and a mutton wrap is rolled. Dough, pie crust, cookies and the cake
  start from wheat and are kneaded, pies go into their crust, melon juice into its bottle. The five feasts are laid out
  in their bowl.
- Milk, honey and water are poured over the food instead of lying on it in their bucket or bottle, which goes back to
  you. Eggs are cracked open. Dough from water keeps the water.
- The cutting board cuts food along its own length: a kelp roll into even round slices, a fish into steaks, never
  lengthwise. Pies and cakes are cut in wedges through the middle, dough in strips. Food takes as many cuts as the
  pieces it falls into: one for beef or a cabbage, two for a kelp roll or a ham, three at most.
- An egg goes into the skillet cracked, with a clear white that turns white as it fries, never in its shell. It fries
  sunny side up: no toss needed, it is done in the time both sides of other food take. The
  stove's grill does not take eggs: they belong in the skillet. Other food on the skillet and stove takes on the colour
  it cooks to, so kelp darkens green and a potato turns golden.
- In the cooking pot, milk is poured in and colours the soup, an egg is cracked into it, and sugar, cocoa and ink
  dissolve instead of floating whole. A soup shows egg ribbons and grains of rice, never slices of eggshell, sugar or
  bone. The empty bucket or bottle goes back to you instead of dropping beside the pot.
- Item tags under `handson_delight` let modpacks say how their food is cut, poured, cracked or dissolved.
- A dish picked in the board's recipe book is laid from your inventory, one at a time or sixteen with Shift.
- The board's recipe book has tabs: everything, dishes only, or cuts only. It remembers your choice.
- The stove opens as a station too: lay up to six pieces on its grill, and each one smokes when its lower side is done.
  Click it to turn it over; once both sides are done it waits on the grill until you take it. Food put on by sneaking
  cooks the Farmer's Delight way.
- The recipe book is drawn with smooth edges instead of jagged steps.
- The recipe book shows its page arrows again, and the food on its pages is as bright as in the inventory.
- Two quick presses of A or D go two stations on at once; the recipe book flies along.
- A dish picked in the recipe book no longer appears in the station at once: its ingredients jump in from you one
  after the other, into the soup, onto the board and into the pan. A stack put into the pan does the same.
- With an empty hand over a finished soup, a bowl from your inventory shows over it: click to scoop a serving, Shift to
  scoop all you have bowls for.
- With an empty hand over food on the cutting board, a knife from your inventory shows over it: click on the mark to
  cut. The knife stays in your inventory.
- What you get at a station shows how many came, "+1" or more, as it goes into the inventory.
- The skillet tells you how the food is doing. The sizzle grows as the side on the pan browns. When that side is done
  the fat pops, the food gives a little jump and the handle is outlined until you toss it. A done batch settles with a
  last deep sizzle and a crackle of the fire.
- Food put into the skillet quickly no longer lands on top of another piece: every piece finds its place, and those
  already there move over.
- The oil in the skillet seethes amber instead of pale green, finished salads no longer throw green crumbs, and the
  sparkle over finished food is golden instead of green.
- Needs [Diegesis](https://www.curseforge.com/minecraft/mc-mods/diegesis) 1.0.0-beta.4.

## [1.0.0+1.21.1]

Hands-On Delight on Minecraft 1.21.1: NeoForge with Farmer's Delight, and Fabric with Farmer's Delight Refabricated. Everything
1.0.0 does on 1.20.1 works the same way here: the cooking pot, the cutting board, the skillet, the recipe books, the
config and the recipe viewers. Needs Diegesis 1.0.0-beta.3 for 1.21.1.

## [1.0.0+1.20.1]

The first full release, with everything from the beta: the cooking pot, the cutting board and the skillet of
Farmer's Delight open right in the world.

- Cooking pot: put food into the water, take the spoon and stir by moving the mouse, then ladle the soup out with a
  bowl. Everything in the pot cooks as one batch, sixteen servings in about 27 seconds. Stir in wide, steady rounds to
  earn up to three stars and extra servings.
- Cutting board: lay food down and chop on the dashed marks with the knife in hand, sixteen at a time. Sandwiches,
  burgers and wraps are stacked on the board layer by layer instead of being made in the crafting table.
- Skillet: up to sixteen pieces fry side by side. Toss the pan by its handle to turn them over. Hot oil seethes under
  the food and a finished side smokes.
- A recipe book next to each station shows what you can make now and puts the ingredients in with a click.
- A pot and a board side by side are one kitchen: A, D or the arrow keys move the camera between them.
- Hoppers, pipes, autocrafters and players without this mod keep Farmer's Delight's own cooking and crafting.
- JEI, EMI and REI show sandwiches on the cutting board.
- `config/handson_delight.properties` turns off each station, the crafting table change and the required stirring.
  Each player can hide the recipe book there.
- Forge, and Fabric with Farmer's Delight Refabricated.

## [0.1.0-beta.1+1.20.1]

First beta.
