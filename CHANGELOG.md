# Changelog

Every released version, newest first. The section headed with a version is what the
build sends to Modrinth, to CurseForge and to the GitHub release for it — see
[RELEASING.md](RELEASING.md) — so it is written to be read by a player rather than by
whoever wrote the commit.

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
