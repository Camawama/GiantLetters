# Giant Letters

Letters, digits and symbols for your builds, on Forge 1.20.1. Ninety-four blocks: the capitals A to Z, the small
letters a to z, the digits 0 to 9 and the 32 symbols of a keyboard (`! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ]
^ _ \` { | } ~`), and any other character the font draws through an anvil. Put one on a wall, a floor or a ceiling
and it reads the way you placed it; put the same letter in a square and the square becomes one bigger letter. They
are drawn with the game's own font, so they look like sign text and follow a font resource pack the way sign text
does.

## In the game

- **Getting letters.** Put paper in a stonecutter: every letter is there. Any letter goes back in and becomes any
  other letter, so nothing is wasted.
- **Any other character.** Rename a letter in an anvil to exactly one character (`é`, `ñ`, `ß`, `♥`, `→`, `日`,
  anything a sign can show) and it becomes that character: the icon shows it and so does the placed block. Such a
  letter joins only letters of the same character, and keeps the character as its name when broken or picked. A
  character wider or taller than the font's line (some East Asian or symbol glyphs) is shrunk to fit its square.
- **Placing.** A letter is a one-pixel plate against the block face you click: a wall (it stands upright facing
  you), a floor (its top points away from you, you read it looking down) or a ceiling (its top points toward you).
  Nothing collides with it, it can be waterlogged, and it pops off as an item when the block behind it goes.
- **Bigger letters.** Letters of the same kind that touch along their surface join, and the group is cut into
  squares: a 3x3 of `A` is one big A, a 9x9 a huge one, up to 32 across. Two squares side by side stay two letters
  (the two L's of a 3x3 HELLO). A letter placed while sneaking never joins, like a sneak-placed chest.
- **Colour and glow.** A letter is white until dyed. Right-click with a dye and the whole letter takes the colour,
  one dye however big it is. Glow ink makes it glow (full bright, with the dark outline sign text gets) and give
  light; an ink sac makes it black and takes the glow away. Nothing is used up when the letter already is as asked.
  Colour and glow are lost when a letter is broken, as a sign's are. With Colorful Lighting, a glowing letter's
  light has its dye's colour (a white letter's stays plain).
- **Spacing.** Letters sit centred in their block, so a word is set like a monospace font. To close a gap or nudge a
  letter, right-click it with an empty hand: it moves one pixel toward the side of the face you clicked (the face is
  a 3x3 of zones like a chiseled bookshelf's slots: left, right, top, bottom, a corner for both). Sneak and right-click
  to put it back in the centre. A big letter moves as a whole, by its own pixels, up to half of its blocks each way, and
  may reach into its neighbour's block.
- **Where they are.** Their own creative tab, Giant Letters, after Functional Blocks.

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
Colorful Lighting is optional: its block light colour provider is registered by reflection
(`client/LetterLightColors`), answering from a map the client's letter block entities keep up to date, so nothing
is compiled against it. A renamed letter's character is measured from the font the first time it is drawn.

## Building

`./gradlew build` (Java 17). `tools/generate_assets.py` writes the per-letter JSON (blockstates, item models, loot
tables, stonecutter recipes, tags, lang) from the list in `Letters.java`; run it after changing that list.
