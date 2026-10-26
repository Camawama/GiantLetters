# Giant Letters

Letters and digits for your builds, on Forge 1.20.1. Sixty-two blocks: the capitals A to Z, the small letters a to
z and the digits 0 to 9. Put one on a wall, a floor or a ceiling and it reads the way you placed it; put the same
letter in a square and the square becomes one bigger letter. They are drawn with the game's own font, so they look
like sign text and follow a font resource pack the way sign text does.

## In the game

- **Getting letters.** Put paper in a stonecutter: every letter is there. Any letter goes back in and becomes any
  other letter, so nothing is wasted.
- **Placing.** A letter is a one-pixel plate against the block face you click: a wall (it stands upright facing
  you), a floor (its top points away from you, you read it looking down) or a ceiling (its top points toward you).
  Nothing collides with it, it can be waterlogged, and it pops off as an item when the block behind it goes.
- **Bigger letters.** Letters of the same kind that touch along their surface join, and the group is cut into
  squares: a 3x3 of `A` is one big A, a 9x9 a huge one, up to 32 across. Two squares side by side stay two letters
  (the two L's of a 3x3 HELLO). A letter placed while sneaking never joins, like a sneak-placed chest.
- **Colour and glow.** A letter is white until dyed. Right-click with a dye and the whole letter takes the colour,
  one dye however big it is. Glow ink makes it glow (full bright, with the dark outline sign text gets) and give
  light; an ink sac makes it black and takes the glow away. Nothing is used up when the letter already is as asked.
  Colour and glow are lost when a letter is broken, as a sign's are.
- **Where they are.** Functional Blocks, after the signs.

## Config

`serverconfig/giantletters-server.toml` in the world (synced to clients):

| Key | Default | What |
|---|---|---|
| `letters.glowLightLevel` | 7 | The light a glow-inked letter gives, 0 to 15. 0 gives none, as a glowing sign gives none. |
| `letters.maxLetterSize` | 32 | The biggest square that becomes one letter, in blocks across. |

## How it draws

The letter block has no texture of its own. Its block entity renderer takes the glyph from the live default font
set, the one the sign renderer uses, and draws quads on the font's glyph texture with the text render types. Once
per font set (every resource reload makes a new one) the mod reads the glyph texture back and measures each
glyph's ink and the whole set's line box (the capitals' tops to the descenders' bottoms). Every letter is drawn at
one scale, the line box filling the square top to bottom, on a common baseline, centred by its own ink width. A
block of a big letter draws only the part of the glyph on its own face, with its own light, so nothing special is
needed for culling or chunk borders.

Which blocks form one letter is computed where it is needed and cached in the block entities on the client: when a
letter block appears or disappears, the connected region of that letter is walked once and cut into squares
(`SquareCut`, unit-tested). The server runs the same cut when a dye is applied, to colour the whole letter.

No mod dependency. Four fields and one method of the client's font classes are opened with an access transformer.

## Building

`./gradlew build` (Java 17). `tools/generate_assets.py` writes the per-letter JSON (blockstates, item models, loot
tables, stonecutter recipes, tags, lang) from the list in `Letters.java`; run it after changing that list.
