# Changelog

Every released version, newest first. The section headed with a version is what the
build sends to Modrinth, to CurseForge and to the GitHub release for it — see
[RELEASING.md](RELEASING.md) — so it is written to be read by a player rather than by
whoever wrote the commit.

## 0.5.4

The first build of this mod for **Minecraft 1.12.2**. Everything the 1.7.10 version does
is here, drawn the same way and configured by the same file — what follows is what is
new since 0.5.3 and what is particular to this version.

**Shader packs** (needs Angelica)

- Choosing a shader pack has a screen of its own here now, in this interface rather than
  Iris's: the packs in a list, the switch above them, the folder they live in, and the
  loaded pack's own settings.
- The pack's settings are the pack author's — their rows, their order, their pages, their
  descriptions, read out of `shaders.properties` and their language files. Descriptions
  are wrapped rather than cut off mid-sentence, which is where a pack says what an option
  costs.
- Nothing is applied until Apply: a reload rebuilds the whole pipeline, so reading five
  pack names is not five recompiles. What is waiting is said in the corner of the panel.
- Drag a pack onto the window to add it.
- The backdrop is the sky with the black hole left out — a pack is judged by how light
  behaves, and the brightest thing on screen should not be something the pack has no say
  over. Over a world, the world stays visible behind the panel.

**Waila**

- A list of blocks Waila says nothing about at all, in `uky.cfg` under
  `mods.wailaHiddenBlocks`, with a switch on the Other tab. Vanilla terrain out of the
  box: the tooltip earns its place over a machine and earns nothing over the ground.

**Xaero's Minimap**

- A frame style of this mod's own, added to the ones Xaero ships: the gold rail down the
  map's left edge and the corners picked out as brackets. It is picked in their own menu,
  in Frame Style, next to Default, Colored and Colored Thin — their styles are not
  replaced and not touched, and picking one of theirs draws nothing of ours.
- Only the frame. The map, the entities, the waypoints and the coordinates under it are
  Xaero's and are untouched, and nothing is written to their config.
- It sits on the map's own edge, taken from the map: their renderer draws the square map
  as a single quad, and the frame is drawn on the same four numbers. The pass that draws
  the map into their frame buffer is skipped, and the scale it is all drawn under is read
  off the matrix and carried across, so this holds at any minimap and GUI scale.
- `mods.restyleXaeroFrame` off takes the entry out of their menu again. Somebody who had
  the frame set to Off will find Off one place along, because that is where ours went in.
- A minimap set to round keeps Xaero's own frame: that one is an ellipse drawn elsewhere.

**Mod settings**

- Config lists can be edited in the game — ours and any other mod's. A list used to be a
  row that said "list" and did nothing when clicked. Click a row to edit it, the arrows
  to move it, the bin to take it out, Add for a new one, Restore defaults to start over.

**Advancements**

- The chat line for an earned advancement is shortened and made clickable, and it now
  says the right thing. It used to be rewritten for some advancements and left alone for
  others, because it looked the advancement up by its title and the lookup could miss.
  The name is read from the announcement itself now, and the link carries that name, so
  every one of them is rewritten and every one of them opens the list.
- The panel plays this mod's own sound again. The file it names was declared without its
  namespace, so the game looked for it under `minecraft:` and found nothing.
- Vanilla's own toasts — a recipe unlocked, the tutorial, "world backed up" — are no
  longer swallowed. This mod's panel cancels the toast pass while it is on screen, and it
  was cancelling it when there was nothing of ours to show.

**Menus**

- Switching a settings tab, unfolding one of the renderer's sections, or changing a tab
  or filter on the advancements list no longer replays every row's arrival, which read as
  the text flickering rather than as the panel animating.
- Tooltips, the toasts and the chat are drawn through the game's own state manager. 1.7.10
  had none and this mod's drawing matched what vanilla did; 1.12 caches every GL state and
  a raw call leaves that cache asserting the opposite of the truth — after which something
  unrelated renders wrong several frames later. That is what took the item tooltips out,
  and what drew the settings tabs as fragments of the black hole's own texture.
- The font is the game's own again. This mod corrected a glyph-width bug of 1.7.10's that
  1.12 does not have, using a table read once and a list of characters copied from the
  older version — so a few characters were measured by one rule and drawn by another.
- Chat hovers and clicks land on the line under the pointer. With this mod drawing the
  chat, the game's own hit test was answering for a layout it was not drawing, and a
  tooltip could belong to a line one or two above the one it appeared for.
- Tab completion moves on to the next argument. It asked the server to complete the word
  already typed, because the word boundary it used skips whitespace and walked back over
  the space after a command.

**The backdrop**

- A comet crosses it every forty seconds or so, and one star, high on the left, twinkles
  on its own: put the pointer on it and it flares, click it and a comet leaves from that
  point. Both are off together under `effects.comets`.

**The mod's own icon**

- The artwork is in the jar now, so the mod list and the launchers show it instead of a
  generic cube.

## 0.5.3

**Quest book** (needs BetterQuesting)

- Tooltips are drawn as one of this mod's panels and made to fit. The quest book keeps
  its own copy of vanilla's tooltip code, which wraps to a width but never checks the
  height — so a quest listing forty entries ran off the top of the screen and off the
  bottom at the same time, and the half you wanted to read was the half that was not
  there.
- Finishing a quest shows the same panel an achievement does, with the quest's own icon
  in its frame, instead of a title across the middle of the screen. Completing a quest
  and earning an achievement together no longer puts two announcements of the same kind
  of thing on screen in two different shapes. Off in `uky.cfg` under
  `mods.restyleQuestToast`, which leaves BetterQuesting's own notice and its own
  notification settings.
- The theme is handed to the book once rather than re-selected every time it opens.
  Another theme picked in the book's own Themes screen used to last exactly until the
  book was next opened; now it stays picked.

**Tooltips in packs with NEI**

- NEI draws item tooltips itself and never touches the vanilla path this mod hooks, so
  with it installed every tooltip in the game came out in vanilla's purple frame no
  matter what the tooltip setting said. They are restyled now too. What is inside them
  is untouched, including NEI's own paging and the bars and item grids that packs put
  inside a tooltip.

**Worlds and servers**

- Holding the bin cracks the card: a point of impact, and cracks spreading out of it,
  deeper the longer the button is held. Letting go closes them again.
- The card then comes apart along exactly those cracks — long shards thrown from the
  impact, rather than the grid of rectangles it used to break into. Nothing breaks into
  rectangles, which is why the old one read as a tile sliding apart rather than
  shattering.
- Shift-click the bin to delete at once, on both lists. The hold is there because a
  world is months of work; clearing out six test worlds is not a misclick.
- Servers are deleted by that same gesture. It used to be a vanilla confirmation screen
  dropped into the middle of a dark one, asking a question the pointer had already
  answered by being on the bin.
- A server added or edited on that screen is pinged straight away. It used to sit on
  "Asking the server..." until the whole list was refreshed by hand.
- A world created after another was deleted no longer opens on a photograph of the
  deleted one. Minecraft hands folder names back out, and the picture was still cached
  under the name the new world had just been given.

**Entering and leaving a world**

- Leaving is one movement now: the pause menu animates out, the world darkens to
  exactly the colour the saving screen comes up on, and the menu climbs back out of
  black on the far side. It used to cut three times in a row.
- Creating a world dives into it the way opening one from the list already did.

**Chat**

- The command list opens as soon as `/` is typed, and follows the command along as it
  is written, a word at a time. It waited for Tab before, which meant it only ever
  appeared to somebody who already knew it was there. Ordinary messages are still
  Tab-only — that completion is player names, and it is not worth a packet for every
  word of every sentence.
- Right arrow at the end of the line takes the completion shown greyed ahead of the
  cursor.

**Interface**

- Text in this mod's screens has a shadow under it, the way the rest of the game does.
  These screens were flat while every vanilla screen, every other mod and the chat
  around them were not, which is what made the font look wrong when nothing was wrong
  with it. `effects.textShadow`.
- Controls at the right-hand end of a row are no longer underneath the scrollbar.
  Aiming at the control caught the bar and aiming at the bar caught the control, on
  every list in the mod at once.
- The open-to-LAN screen wraps each game mode's description into its card instead of
  cutting it off after three words.
- Settings this build no longer has are dropped from the config file, rather than kept
  there and listed in the in-game editor with nothing behind them.

**Loading screen**

- Paced to twenty frames a second rather than sixty. Every frame here costs far more
  than it appears to — the captions go through vanilla's font renderer, which emits a
  `glBegin`/`glEnd` pair per character — and this is the one screen shown during work it
  must not compete with.
- No longer logs an exception and a stack trace on every launch looking for a logo image
  that has never shipped.

**Fixes**

- The item in the achievement panel is lit correctly while it spins. The light was being
  positioned inside the entrance animation, so it turned with the item and lit it from
  somewhere new on every frame.
- The menu no longer starts tracing black hole fallback tables when the driver has
  simply not been asked yet. During mod loading the GL context belongs to the loading
  screen and the question has no answer rather than the answer "no" — and taking that
  for a no spent the whole session on the slow path, on hardware that runs the shader
  perfectly well.

## 0.5.2

**Achievements**

- The popup is this mod's own: a slanted panel that cuts in from the right on a line
  of light, the item landing in its frame with a shockwave behind it, the name typing
  itself out, and a hairline along the bottom counting down what is left of it. Earning
  several at once queues them rather than replacing them, which vanilla did not.
- It has a sound, off the master slider and switchable on its own.
- The chat line an achievement produces is cut down to the achievement, keeping the
  player's name only when somebody else earned it, and clicking it opens the
  achievements list scrolled to that entry with it picked out.
- The achievements list shows the chain: each one indented under the achievement it
  needs, with the connector drawn gold once that one is earned, a count of how many
  further achievements each opens up, and the name of what is still missing spelled out
  on any entry that is locked.
- Filters — all, earned, still to do — beside the search box, and a completion bar
  along the rule under the tabs.

**Chat** (off by default: Settings → Sound & Chat → Redesign chat)

- Messages on this mod's own panels, arriving from the left, with the line under the
  pointer picked out, the time at the right edge, and a single glass field behind the
  whole thing while the chat is open.
- Commands are coloured as they are typed — the command in the accent, each argument in
  its own colour, with numbers, quoted strings and `@` selectors called out.
- Completions are a list you pick from with the arrow keys, above the cursor, with the
  rest of the highlighted one shown greyed ahead of what you have typed. Vanilla wrote
  them into the chat log as a comma-separated message.

**Tooltips** (Settings → Other → Restyle tooltips)

- Drawn as one of this mod's panels, and made to fit: a line too long for the screen is
  wrapped, a box too tall for the window is scaled to it, and one that is still too tall
  after that is cut and counted rather than drawn off the edge. What the lines say is
  untouched, including everything other mods put in them.

**Fixes**

- Scrollbars can be grabbed. They were an indicator: a click landed on the list behind
  them, so dragging one scrolled the wrong way and selected whatever row was underneath.
