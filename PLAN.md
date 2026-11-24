# Ladle & Lantern: Plan

A cosy restaurant-management mod for NeoForge 1.21.1. You turn any build into a restaurant, customers walk in and order from your menu, and you cook everything yourself with whatever food mods you have installed. The progression loop is borrowed from the Papa's games: serve well, earn points, rank up, and get to know your regulars.

This file is the map. Each system has its own plan in `docs/`.

## Target

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.x (tested on 21.1.264) |
| Java | 21 |
| Mod id | `ladle_and_lantern` |
| Package | `com.ladleandlantern` |
| Hard dependencies | None besides NeoForge |
| Optional | EMI or JEI (recipe button), Display Delight (3D placed food is detected) |

Every food from every mod is supported through vanilla's food component and the shared `c:` tags, never through a hardcoded list.

## Guiding rules

1. **Prototype first.** Every feature works end to end in its simplest form before anything gets polished.
2. **Ponytail.** Before writing code, check whether it needs to exist at all, then whether vanilla, NeoForge or an installed mod already does it.
3. **Never cut data safety.** Restaurant progress is written atomically with a backup, and saved after every order rather than only on world save.
4. **The server is the authority.** Clients only render and send requests. Every request is validated on the server.
5. **One class, one job.** Shared code lives in `core/` (registries in `core/registry/`, game data in `core/restaurant/`, rules in `core/food/` and `core/shift/`). Rendering, screens and other-mod bridges live in `client/`.

## Systems

| # | System | Doc | Covers spec items |
|---|---|---|---|
| 1 | Restaurant setup: cash register, planner, seats, tables, counter, entrance, floors | [docs/01-restaurant-setup.md](docs/01-restaurant-setup.md) | 1, 2, 27, multiple floors |
| 2 | Customers: spawning, skins, nametags, pathing, sitting, queues | [docs/02-customers.md](docs/02-customers.md) | 9, 23, player-model skins, nametags |
| 3 | Orders, serving and scoring | [docs/03-orders-and-serving.md](docs/03-orders-and-serving.md) | 4, 6, 7, 8, 11, 12, 13, 22 |
| 4 | Progression: shifts, ranks, stars, critics, closers | [docs/04-progression.md](docs/04-progression.md) | 5, 6, 12, 14, 15, 16, 18, end of shift |
| 5 | Calendar, seasons, holidays, specials board | [docs/05-calendar-and-specials.md](docs/05-calendar-and-specials.md) | 19, 20 |
| 6 | Screens: cash register, floor plan, notebook, recipe viewer | [docs/06-screens.md](docs/06-screens.md) | 3, 10, 21 |
| 7 | Staff: trays, waiters, cashier NPCs, wages | [docs/07-staff.md](docs/07-staff.md) | 24, 25, 26 |
| 8 | Saving and multiplayer | [docs/08-saving-and-multiplayer.md](docs/08-saving-and-multiplayer.md) | 17, co-op |
| 9 | Digital clock: a placeable desk, wall or ceiling clock with a live display, and the time shown while held | this file | clock request |
| 10 | Cosy touches: tip jar, umbrella stand, coat rack, water station, corkboard and notes, cat bed, gramophone and records | [docs/09-cosy-touches.md](docs/09-cosy-touches.md) | round four |
| 11 | Crafting recipes for every item | [docs/10-crafting.md](docs/10-crafting.md) | round six |
| 12 | Build and test | [TESTING.md](TESTING.md) | |

Out of scope for now: auto-cooking (spec item 28), Fabric, Minecraft 26.x.

Next up: a redesign of the cash register screen in the inventory style of the planner.

## Order of work

No dates. Each step ends with something you can try in-game.

1. **Skeleton.** Mod loads, items and blocks exist, cash register can be placed. *Check: it shows up in the creative tab.*
2. **Setup.** Planner wand sets bounds, seats, tables, counter and entrance. Data survives a restart. *Check: set up a two-floor restaurant, quit, reload.*
3. **Customers arrive.** They spawn at the entrance with player skins and nametags, walk to a seat or the queue, and sit down. *Check: customers fill both floors.*
4. **The loop.** Right-click to take an order, cook, place or hand over food, get scored, get paid. *Check: one full dine-in and one takeout order.*
5. **Progression.** Shifts, schedule, ranks, stars, critics, closers, ribbons, end-of-shift walkouts. *Check: play a full shift and rank up.*
6. **Cash register screen.** Overview, floor plan per floor, menu, seasons and holidays, schedule, staff. *Check: set a Christmas menu and see it ordered.*
7. **Notebook.** Order list with item icons and a recipe button that opens EMI or JEI. *Check: open a Farmer's Delight recipe from an order.*
8. **Staff.** Trays, waiters, cashier NPCs, wages, upgrades. *Check: a waiter delivers a full order without you.*
9. **Polish pass.** Textures, sounds, balancing, config, manual. *Check: a co-op session with a friend.*

## Repository layout

```
ladle-and-lantern/
  PLAN.md                 this file
  TESTING.md              how to build, install and test
  docs/                   one plan per system
  build.gradle ...        standard NeoForge ModDevGradle build (`--offline` works once cached)
  scripts/make_furniture_art.py  draws the gramophone, till, coat rack and umbrella stand
  src/main/java/com/ladleandlantern/
    LadleAndLantern.java  mod entry point
    core/
      config/             server config
      registry/           *Registry classes: blocks, items, entities, block entities, menus, sounds, tab
      block/ block/entity/ item/ entity/ entity/ai/ menu/   game objects
      restaurant/         data model and the save store
      food/               food rules, order generation, scoring
      shift/              shift clock, spawning, wages
      network/            payloads
      event/              game event hooks
      util/               ClientAccess (the only door from shared code to the client) and helpers
      world/              weather
    client/
      LadleAndLanternClient.java  client setup
      renderer/ screen/ overlay/ integration/   renderers, screens, the corkboard card, EMI and JEI bridges
                          (nothing outside client/ uses client code directly, and there are no mixins)
  src/main/resources/     assets, data, mods.toml
```
