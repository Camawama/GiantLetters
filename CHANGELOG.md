# Changelog

## 0.1.4 (2026-10-27)

- Floor and ceiling letters read the way the placer was looking: the block stores that direction (as a button does),
  and 0.1.3 read it as the direction toward the placer, so every floor and ceiling letter came out turned 180 degrees.
  Letters already placed keep their block state and now read the right way.
- The Giant Letters creative tab sits among the other mods' tabs after vanilla's, not on the first page.

## 0.1.3 (2026-10-26)

- Letters can be shifted along their face to fix spacing: right-click a letter with an empty hand and it moves one
  pixel toward the side of the face you clicked (left, right, up, down, or a corner for both; the middle does
  nothing); sneak and right-click to put it back in the centre. A big letter moves as a whole by its own pixels, up to
  half of its blocks each way, so a letter can be slid into its neighbour's space (kerning).

## 0.1.2 (2026-10-26)

- A creative tab of its own, Giant Letters, after Functional Blocks: A to Z, a to z, 0 to 9, the symbols. The
  letters no longer sit in Functional Blocks (the search tab still finds them).

## 0.1.1 (2026-10-26)

- The 32 printable ASCII symbols as letters too (`! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ \` { | } ~`),
  cut on the stonecutter like the rest: 94 letters.
- Any other character the font draws: rename a letter in an anvil to exactly that one character (`é`, `ñ`, `ß`,
  `♥`, `→`, `日`...) and it shows it, in the hand and on the wall. Such letters join only letters of the same
  character, keep the character as their name when broken, and a character wider or taller than the line box is
  shrunk to fit its square.
- A glowing letter's light has its dye's colour under Colorful Lighting (its block light colour API, by reflection;
  white letters keep plain light).

## 0.1.0 (2026-10-26)

- 62 letter blocks: the capitals A to Z, the small letters a to z and the digits 0 to 9, for walls, floors and
  ceilings. A letter is a one-pixel plate against the face it hangs on, nothing collides with it, and it pops as an
  item when its wall goes.
- Drawn with the game's own font the way sign text is: a font resource pack or the Force Unicode Font option
  changes the letters too. Letters share one baseline and one scale, each centred in its square by its own width.
- A square of the same letter is one bigger letter (3x3, 9x9, up to `maxLetterSize`, 32). Two squares side by
  side stay two letters. A letter placed while sneaking never joins.
- Dye colours a whole letter (white until dyed), glow ink makes it glow with a sign's outline, an ink sac makes it
  black and takes the glow away. A glowing letter gives light (`glowLightLevel`, 7; 0 for none, as signs).
- Cut on the stonecutter from paper, or from any other letter.
