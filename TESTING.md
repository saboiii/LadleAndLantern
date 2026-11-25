# Testing Ladle & Lantern

## Install
The built jar is `build/libs/ladle_and_lantern-1.21.1-0.1.0.jar`. A copy is already in your `mods` folder.

1. Open the Minecraft Launcher and pick the **NeoForge** profile (`neoforge-21.1.264`). Your mods are 1.21.1 builds, so this is the right profile, not 1.21.11.
2. Play, create a new singleplayer world in Creative with cheats on, and open the **Ladle & Lantern** creative tab.

## Rebuilding after changes
`./gradlew build` (or `./gradlew build --offline` once the dependencies are cached), then copy `build/libs/ladle_and_lantern-1.21.1-0.1.0.jar` into `mods`. `./gradlew runClient` starts a separate dev client with only this mod loaded.

## Test run, in order
Each step checks one system. If a step fails, note what you saw and the step number.

### 1. Setup
1. Build a small room with a door. Place a **Cash Register** inside, facing where customers should queue. You get a chat message saying a restaurant opened.
2. Take a **Restaurant Planner**. Sneak-right-click the cash register to link it.
3. Right-click the air to open the planner's tool menu and pick a tool.
   - **Bounds:** a floor block in one corner, then a ceiling block in the opposite corner.
   - **Seat:** right-click a chair (try a Let's Do chair and a plain block) to make it a seat.
   - **Table:** right-click 1–4 blocks next to that seat. They must be within 4 blocks.
   - Make a second seat that shares one of those table blocks. It becomes table 1A and 1B.
   - **Counter:** mark a row of blocks.
   - **Entrance:** right-click the floor just outside the door.
   - **Waiting line:** click up to 5 floor blocks outside, front of the line first.
4. With the planner in hand, gold particles mark seats, brown mark tables and green marks the entrance.
5. Build a second floor with stairs and add a seat up there.
6. Quit to title, reload the world, and check the particles are all still there.

### 2. Menu
1. Right-click the cash register and open **Menu**. Search "pie", "stew" or "juice" and click a few dishes to add them, including some Farmer's Delight and Let's Do foods.
2. Check each course guess and click the course chip to change any that are wrong.
3. **Overview** shows a to-do list until setup is complete.

### 3. Customers
1. Run `/time set 900`. The shift opens at 07:00 by default with a big banner and a bell.
2. A few seconds later the first customer walks in from the entrance with a player skin and a nametag like `Priya ★☆☆☆☆`.
3. Dine-in customers should walk to a free seat on either floor and sit, facing their table, never two to a seat. Once seats run out, new arrivals wait on the waiting line and get seated in order. Takeout customers queue on the customer side of the cash register and stay in line.
4. Seated customers get a coloured bar above their name.
5. When customers leave, they should walk out of the door, stroll on 10 to 16 blocks, and vanish. They should take the stairs, not jump off.

### 4. Orders and serving
1. Hand a seated customer a written menu card (see round four below). Once they say "Ready to order.", hold the **Order Notebook** and right-click them to take their order. Right-click the air with the notebook to read it like a book.
2. In the notebook, click **Recipe** on a dish. EMI should open on its recipes.
3. Right-click them while holding each dish. Display Delight dishes, cakes and feasts are placed on the table the way their own mod places them. Anything else goes on a plate. The action bar lists what's still missing. You can also place dishes or drop items onto the table yourself.
4. Once everything is there, they start eating on their own, toss you the payment and some XP, and leave. Chat shows the score.
5. Try a wrong dish, then sneak-right-click with an empty hand to serve it anyway. They should leave angry and pay nothing.
6. **Takeout:** craft Paper Bags (one paper each), fill one like a bundle, and right-click the waiting takeout customer with it. The bag should disappear. Two of a dish for a one-dish order scores lower.

### 5. Progression
1. Serve a few orders and watch your points in **Overview**. Rank 2 (Spoon Polisher) is at 300 points.
2. `/time set 12400` and wait. At 18:30 you get a "Closing Time" banner, and everyone still waiting leaves angry and loses a star. Opening at 07:00 shows a banner too.
3. In **Schedule**, turn a day off or move the hours.

### 6. Calendar and specials
1. In **Seasons**, pick Christmas and add a dish. With the default 14-day seasons, Christmas is day 45 of the 56-day year, so `/time set 1081000` puts you at 07:00 on Christmas. The Seasons tab marks it with a dot.
2. Place a Specials Board near the restaurant and right-click it with a menu dish. The dish is chalked on the board and shows up more often.

### 7. Staff
1. Place a **Serving Tray** near the cash register.
2. **Staff → Hire waiter.** Put a seated customer's whole order on the tray. The waiter carries it to them.
3. **Hire cashier.** They take takeout orders automatically, and serve them from a tray within 3 blocks of their till.
4. The next in-game day the staff show `unpaid` and stop working until you click **Pay wages**.

### 8. Digital clock
1. Craft one (clock, glass pane, redstone) or take it from the creative tab. Holding it shows the time above the hotbar.
2. Place it on a table, on a wall and on a ceiling. The screen faces you and the time updates live.

### 9. Round-three checks
1. Break a chair that's a seat. The planner particles disappear.
2. Use a stair as a chair. The customer sits on the low step.
3. Serve a Let's Do beer and a second dish to a seat with one table block. Both go on one plate, and they eat both. With two table blocks, the beer stands upright as a mug.
4. Customers must be taken with the Order Notebook in hand. Empty-hand clicks just show the order.
5. Waiters take dine-in orders on their own and pick up full orders from trays. Remove one dish from a tray order and watch them call it out with a red "!".
6. Open a tray with an empty hand (one-row chest). Take a dish and watch the pile on the tray drop.
7. Craft paper bags (1 paper each), hang a bag rack on a counter side near the till, and stock it. The cashier NPC uses one per takeout order and complains when it runs out.
8. Hire a level 1 waiter and leave it a while. If it starts dancing, right-click it.
9. Close a shift with staff-served orders. A staff member throws the takings at you.
10. Put a Specials Board outside and right-click it with a dish. Hang a Blue Ribbon on a wall and watch it sway.
11. `/ladle currency cash` switches pay to dollar bills, and `/ladle currency diamonds` switches back.

### 10. Round-four checks
Before you start, run `/kill @e[type=ladle_and_lantern:customer]` once, because customers saved by the older version don't know about menus. Your config file keeps its old `basePerDay` of 4; customers still come much more often now, and you can raise it if you like.

1. **Menus.** Craft menu cards (paper + leather), right-click the cash register with the stack, then read one. It opens like a book. Put one on a lectern and read it there too.
2. Seat a customer. They show a menu icon. Right-click them with a card: they read it, then say "Ready to order." Take the order with the notebook and the card comes back to you.
3. Put a **Menu Stand** on the counter with some cards in it and hire a waiter (64 diamonds or bills). The waiter hands out menus, writes orders down with a scribbling sound, and puts the cards back.
4. **Food stays on the table.** Serve a dish they can place (a Let's Do beer on a seat with two or more table blocks). It stays visible while they say "Mmm!" and eat, and only goes when they've finished.
5. **Takeout.** Hand over a bag. They nod, murmur thanks and walk out slowly. A cashier NPC takes a few seconds to bag it, with a paper rustle.
6. **Leaving.** Customers walk out of the entrance and away from the building, never up to the open top floor.
7. **Seasons.** `/ladle season` shows the date. `/ladle season autumn` jumps to autumn, a banner says so, and the clock shows AUTUMN. Put a dish under autumn only and check it's ordered now but not after `/ladle season spring`.
8. **Weather.** `/weather rain`. Customers shake off water at the door, order more soup, and someone mentions umbrellas. Place an **Umbrella Stand** and watch it fill. Same with a **Coat Rack** in autumn.
9. **Tip jar.** Place one by the till and serve well. It fills up, showing bills or diamonds to match the currency. Right-click to empty it.
10. **Closing.** At closing time, once everyone's gone, the screen dims and the day's takings card appears. Then the staff sit down to dinner and chat. With one member of staff, click a reply in chat. Feed one of them by right-clicking with food.
11. **Regulars.** The cash register's Regulars tab shows a card per customer with their head, stars, visits and favourites (greyed out when not on today).
12. **Staff tab.** Hover a name to see their birthday and favourite cake. Hearts fill up over a few dinners.
13. **Lunch rush.** Around 12:00, groups of friends arrive together and sit at one table, sometimes with a child.
14. **Cat.** A cat now and then, napping on a **Cat Bed**. Right-click it.
15. **Gramophone.** Put a record on. It loops until you take it off.
16. **Notes.** A happy customer sometimes leaves a thank-you note on the table. Pin it on a **Corkboard**.
17. **Bubbles.** Speech bubbles are white with an outline and a tail, with pictures or words in them.
18. **Ribbon.** Hang a blue ribbon on a wall. It swings out from the wall and never through it.
19. **Two tills.** Add a second cash register and hire two cashier NPCs, one at each. Each works its own till and nobody serves the same customer twice.

### 11. Round-five checks
1. **Leaving.** Customers seated upstairs and downstairs all leave through the entrance's door. None go up to the open top floor.
2. **Voices.** Chatter, laughs and "mmm" now use Minecraft's villager and wandering trader voices.
3. **Day's takings.** The card opens with a page-turn sound and stays until you click the x.
4. **Dinner.** Staff sit together at the biggest table, look at whoever's talking, start talking within a few seconds of sitting, and eat slowly. Walk out of the bounds mid-conversation and it stops. `/ladle dinner off` turns the talking off.
5. **Bedtime.** Put a few beds within 24 blocks. After dinner the staff go to sleep in them, and they're up again when the shift opens.
6. **Wages.** Skip paying for a day and the hearts drop. After three unpaid days in a row they resign and leave a note on the counter.
7. **Missing dishes.** Leave part of an order on a tray. The waiter keeps a red "!" up, and right-clicking them repeats what's missing.
8. **Taking over.** Hand a customer a menu yourself while a waiter is fetching one, or serve them while a waiter is carrying their food. The waiter puts it back.
9. **Full house.** Fill every seat, the waiting line and the takeout queue. New arrivals walk up, say it's packed and leave.
10. **Takeout queue.** Customers line up on clear floor only and shuffle onto their spot without jumping.
11. **Tip jar.** The glass shows an outline now, and customers walk over to it to tip.
12. **Gramophone.** Taking the record off stops the music.
13. **Critics** only on Fridays, and **one closer** a day.
14. **Cash register book.** Regulars' heads stay in their frames when hovered, and long lists show a scroll bar.
15. **Staff tab.** Favourite cakes show a real cake, not "Air".

### 12. Round-six checks
1. **Furniture.** Customers walk round coat racks, umbrella stands, the gramophone and the water station instead of getting stuck or climbing on them. They stand in front of the water station to pour a glass.
2. **Staff traffic.** Put a bag rack, a tray and a cashier NPC close together. The waiter gets round them, nudging past if needed, and everyone ends up back at their spots.
3. **Dinner.** Staff sit down together at a table (not standing beside it), and stop eating soon after the conversation ends.
4. **Wages.** Sneak-right-click the cash register with diamonds or bills. At closing time the wages are paid from it. With an empty fund, a reminder appears in chat instead.
5. **Takeout.** Takeout orders turn up regularly again, including with carpet in front of the till.
6. **Tips.** Tips are rarer, the jar fills slowly over six levels, and the contents look like bills, diamonds or coins.
7. **Cat.** A cat visits most days.
8. **Open entrance.** Set the entrance on an open gateway with no door. Customers still come and go through it.
9. **Bubbles and bar.** Bubbles are cream with a rounded light brown outline and brown text. The patience bar is a pastel pill.
10. **Uniforms.** Staff wear the six uniforms, with women as waitresses and men as waiters, including staff hired before.
11. **Crafting.** Every item shows a recipe in EMI. See docs/10-crafting.md.

### 13. Round-seven checks
1. **Records.** Lantern Glow, Fresh Bread Morning and Snow on the Windowsill each loop on the gramophone and fade with distance.
2. **Bubbles and bar.** The bar is slimmer with a stronger green; bubble text is a softer brown.
3. **Tip jar and water.** Put the tip jar on a counter and the water station in a corner. Nobody gets stuck: they use a spot they can reach, or skip it.
4. **Cat.** It sits at the chair's seat height, naps, sits up, looks around and at you, and leaves at closing.
5. **Heads.** Seated customers and staff at dinner turn their heads to look at each other.

### 14. Round-eight checks
1. **Cash register book.** Every tab opens with a page turn. The red x and Esc both close it.
2. **Search.** Typing in the menu, calendar and regulars search bars filters the grid straight away. Clicking elsewhere stops typing there.
3. **Floor.** The map shows the wooden floor, counter, tables, chairs and furniture. Hover a chair to see the guest and their order.
4. **Calendar.** Today is red, holidays have stars and birthdays hearts. Clicking a holiday shows its dishes on the right.
5. **Staff.** Fire asks "Really fire?" first. Pay now and Withdraw change the wage fund.
6. **Day's takings.** After closing, the journal page shows stars, the cards and the dish of the day with its icon.
7. **Beds.** Staff go back to the same bed each night. Break it and they find another.
8. **Cat.** It still sits at seat height after a reload.
9. **Wage fund.** Sneak-right-click the cash register with your currency. It goes into the wage fund and the register stays put.
10. **No shadows.** Typed names and search text are plain ink with no shadow.
11. **Hints only on hover.** No instructions sit on the pages. Hover the guests bar, the average stars, a seasonal dish or the wage board to see a short hint.
12. **Live bars.** On the Floor tab, each waiting guest's bar slides down as they wait.
13. **Past days.** Days already gone in the calendar are greyed out.
14. **Favourite cakes.** Staff only love cakes you can eat, never a display stand. Anyone who loved one picks a new favourite.

### 15. Round-nine checks
1. **Late night.** During the day, run `/time set 20000`. Nobody starts a conversation, and the staff are in bed straight away.
2. **Leave mid-dinner.** Walk out during the conversation. The bubbles clear, and the staff get up one by one, look around, then go to bed.
3. **By ten.** Let a normal evening run. Everyone is asleep by 22:00.
4. **No hopping.** Watch staff passing the bag rack, counter and tables for a few minutes. Nobody ends up on top of anything, and they step round each other.
5. **Bounds.** Push a waiter out of the door, or wash them out with water. They're back inside within a few seconds. A minecart or boat won't take them.
6. **Bag racks and stands anywhere.** Hang a bag rack and put a menu stand at the far side of the room. The cashier fetches a bag, picks up the food and comes back to hand it over. Waiters walk over to the stand.
7. **Cashier attention.** Nudge the cashier a couple of blocks off their spot. They still serve someone waiting at their till.
8. **Clearing tables.** After a dine-in customer leaves, crumbs and their menu card are on the table. A waiter brushes it with a swish and a puff, and takes the card back to the stand.
9. **Leaving.** Customers go out of the entrance and keep walking until they're out of sight.
10. **Water.** A customer heading for the water station drinks once they reach it, without hovering.
11. **Tips and notes.** No more than four tips in a day, and thank-you notes are rare.
12. **Kids.** Add a beer or wine to the menu. Children never order it.
13. **New dishes.** Add a dish. Within a few customers someone orders it, and a waiter's card lists it.
14. **Nametags.** Stars under customers' names with a little glint now and then, and hearts under staff names.
15. **Corkboard.** Look at a pinned note to read it by the crosshair.
16. **Opening hours.** The arrows fit on the page. On the Menu tab, "When" and its value are the same size.
17. **Furniture.** The gramophone, computer till, coat rack and umbrella stand have their new looks.
18. **Recipes.** Dollar bills and blue ribbons have no recipe.
19. **Permissions.** On a server, a second player can open the book but can't change anything.

### 16. Round-ten checks
1. **Models.** The coat rack, umbrella stand, gramophone, cash register and water station have no see-through or missing parts from any angle. The gramophone's record is round.
2. **Cash register.** The keypad sits on a slope inside the till's edges, and the green display reads 9.89, facing the front.
3. **Takeout leaving.** A takeout customer who gets their bag walks out of the entrance and off into the distance, instead of vanishing.
4. **Dropped cards.** Drop a menu card on the floor. A waiter picks it up and takes it to a stand.
5. **Messy tables.** With every table messy, newcomers queue or are turned away. Someone heading to a table that gets messy moves seats or waits beside it.
6. **Plates and drinks.** Plates are round china with cutlery. A drink stands upright beside the plate.
7. **Water station.** The carafe is curvy glass with a brass collar and a glint.
8. **Saying hello.** Right-click someone reading the menu: a line in their own voice. Ready to order: two short lines, one in the bubble and one in the action bar. Waiting for food: the receipt opens. Holding food, a bag or sneaking still serves or checks the table as before.
9. **No middots.** No middle dots anywhere, in chat, the action bar or the cash register book.
10. **Sounds.** Each sound in docs/09 plays at its moment. The doorbell is a single chime.
11. **Ambience.** With a few customers in, the café sound is soft; with a full room, louder; with none, it fades away. Laughter is rare.

### 17. Round-eleven checks
1. **Walking out.** Dine-in customers who finish walk out of the door and off into the distance rather than vanishing.
2. **Eating.** Each dish leaves the plate as it goes into the customer's hand. Nothing is on the plate and in their hand at once.
3. **Dirty tables.** Nobody is seated at a table with crumbs, leftovers or a card on it, and a waiter clears it. Break the crumbs yourself: the card you get back still has the menu written on it, and the table is free straight away.
4. **Far tables.** Put crumbs in the middle of a long table. A waiter gets as close as they can and clears it from there.
5. **Queue outside.** Fill every seat so people wait on the line outside. Waiters clear tables before anything else.
6. **End of day.** At closing, every table is cleared before the takings card.
7. **Planner.** Click a table, then three chairs: all three link to it. Click a chair, then a different table: it moves. Sneak-click a table to unlink it. A seat with no table shows a red "!" and nobody sits there.
8. **Plates.** Plates are rounded and patterned in rose, buttercup or caramel, with no flicker. Each one faces its diner, fork on their left and knife on their right, with any drink beyond it on their right.
9. **Furniture.** The gramophone has a big horn and a tone-arm. The menu stand shows none, one, a few or a full stack of cards. The water station's glasses are open at the top with crisp outlines. The tip jar is ornate and the diamonds inside look cut and uneven.
10. **Menu cards.** Cards left on tables go back to a stand.
11. **Two waiters.** Hire two. They never fetch the same card, clear the same table or serve the same customer.
12. **Missing food.** A call-out about a missing dish repeats every 10 seconds until it turns up.
13. **Wages.** Put a big amount in the wage fund. At closing, everyone is paid. Skip a day with `/time add 24000`: the missed day is paid from the fund too.
14. **Employee card.** Right-click a member of staff. Their card shows what they're doing and why.
15. **Dinner.** Staff sit at the nearest table that fits on their own floor, look at you and at each other, and a gradient message says dinner has begun. Break a chair mid-dinner: they stand up, the talk stops, and a gentle message says dinner will keep.
16. **Couples.** Now and then two arrive together, toss a rose back and forth, and hearts float over them.
17. **Messages.** A served table shows a two-line message: gradient headline with stars, then the details.
18. **Daily report.** The rank title sits neatly in its band below the stats.
19. **Bad setups.** Place a second cash register next door, put a waiting line spot inside a wall, or link a table far away. Each gets a friendly note rather than a silent failure.
20. **Naming.** The block is called Cash Register everywhere; the staff role is still Cashier.

### 18. Round-twelve checks
1. **Your seat.** Sit on a restaurant chair. No customer is given it, and one already walking there waits beside it.
2. **Leaving.** With a two-storey restaurant, customers on both floors walk out of the ground-floor door. Nobody climbs to the floor above the entrance.
3. **Couple.** Once a day a couple arrives and sits facing each other across a table, tossing a rose back and forth.
4. **Served message.** The headline, then stars, score, order, speed, wait and who served it, each on its own line.
5. **No climbing.** Waiters walking past tables never end up standing on one; if they somehow do, they hop straight down.
6. **Cards after cleaning.** A waiter clears a table, carries the card to a stand and puts it in. It never ends up on the floor.
7. **Missing food.** Put one of two ordered dishes on a tray. The call-out repeats every 10 seconds until the second turns up.
8. **Cats.** A visiting cat purrs while it naps and meows now and then while sitting up.
9. **Cottage furniture.** The menu stand is honey oak and sage with a pink flower; the tip jar has a gingham cloth and twine.
10. **Cleaning first.** With people queueing outside, every free waiter clears tables before anything else.
11. **Cashier wages.** Skip a day with `/time add 24000` while the shift is open. The cashier and the waiters are paid from the fund.
12. **Employee card.** The wage reads "3, coin, a day" with nothing overlapping, and the birthday is shown in full.
13. **Birthday cake.** On a member of staff's birthday, give them their favourite cake. They gain a whole heart.

### 19. Round-thirteen checks
1. **Served message.** Lines read Performance (bold), Food with stars and a percentage, Speed, and Served by. No flowers.
2. **Finishes.** Right-click the menu stand and tip jar with brown dye, then white dye, then pink dye. The colours change; the shapes stay the same.
3. **Walking off.** Customers who leave walk off into the distance and are gone once they're out of view, never in plain sight.
4. **Floor plan.** The plan looks like a little pixel-art room: wall along the top, plank floor, joined tables with chairs facing them, and furniture pictures.
5. **No stacking.** Keep the cash register book open at closing time. The day's takings card waits until you close it.
6. **Dinner.** Dinner starts most evenings. Step outside for a few seconds and come back: it carries on.
7. **Mornings.** Staff wake at different times between five and half seven, step off their beds and wander the restaurant until opening. Cashiers walk at a normal pace.
8. **One cashier per till.** Try hiring a second cashier at the same register. A friendly note says to place another register.
9. **Two tills.** With two registers and both queues full, new takeout customers wait in the line outside and join whichever queue frees up first.
10. **Plates.** While someone eats, the empty plate stays. When they leave, it becomes a dirty plate with crossed cutlery and the menu card peeking out underneath.
11. **Inventory.** Every block shows a flat picture in the inventory, matching the items. Order bags, ribbons, menus and notebooks look cosier.

### 20. Round-fourteen checks
1. **Plates.** Every plate colour has the new fork and knife; the dirty plates match.
2. **Floor plan.** No legend under the map; hovering a chair still says how it's doing.
3. **Overview.** "Open" or "Closed" with no circle in front.
4. **Stars and hearts.** The book, the employee card, the receipt and the daily report use the name tag sprites. The served message in chat shows sprite stars.
5. **Fire.** The staff page says Fire, then Really fire.
6. **Skins.** Drop a few skins from The Skindex into `config/ladle_and_lantern/skins` and restart. New customers wear them.
7. **Mornings upstairs.** With a bed upstairs, the cashier wakes, steps off the bed, walks down and wanders. Nobody walks over beds.
8. **Glances.** Staff on the ground floor never look up at someone on the floor above.
9. **Wrong food.** Serve one right and one wrong dish for a two-dish order. They eat both and the Food score drops. The plates end up empty.
10. **Ambience.** Leave a busy restaurant running for twenty minutes; the background chatter keeps going. Press F3+T and it comes back on its own.
11. **Save safety.** Make a copy of a world, corrupt `data/ladle_and_lantern.dat` (cut it short), and open the copy. The world loads from the backup and a `.unreadable` copy sits beside it. Remove a food mod that's on the menu, play, put it back: those dishes are on the menu again.

### 21. Crash safety
1. During a shift, force-quit Minecraft (Cmd+Q on the launcher's game process, or kill it).
2. Reload. Menu, ranks, stars and staff should all be there. The restaurant file is `saves/<world>/data/ladle_and_lantern.dat`, with a `.bak` beside it.

## Handy commands
```
/give @s ladle_and_lantern:cashier          (the Cash Register)
/give @s ladle_and_lantern:planner
/give @s ladle_and_lantern:order_notebook
/give @s ladle_and_lantern:to_go_bag 8
/give @s ladle_and_lantern:tray 4
/give @s ladle_and_lantern:digital_clock
/give @s ladle_and_lantern:bag_rack
/give @s ladle_and_lantern:specials_board
/give @s ladle_and_lantern:menu_card 16
/give @s ladle_and_lantern:menu_stand
/give @s ladle_and_lantern:tip_jar
/give @s ladle_and_lantern:umbrella_stand
/give @s ladle_and_lantern:coat_rack
/give @s ladle_and_lantern:water_station
/give @s ladle_and_lantern:corkboard
/give @s ladle_and_lantern:cat_bed
/give @s ladle_and_lantern:gramophone
/give @s ladle_and_lantern:record_lantern_glow
/ladle currency cash | diamonds | <any item>
/ladle season | spring | summer | autumn | winter
/ladle dinner | on | off
/time set 900        shift opens
/time set 12400      shift about to close
/kill @e[type=ladle_and_lantern:customer]   clear customers
```

## Config
Per-world settings are in `saves/<world>/serverconfig/ladle_and_lantern-server.toml`: currency item, pay and tips, patience, customers per day, how many at once (`maxInside`), queue length, hiring cost, season length, and the usernames whose skins customers wear. 

## Where to look when something breaks
`logs/latest.log`. Search for `Ladle & Lantern` or `ladle_and_lantern`.
