"""Writes the per-letter JSON of Giant Letters: blockstates, item models, loot tables, stonecutting recipes, tags
and the language file. Run it from anywhere after changing the set of letters in Letters.java; the files it writes
are committed. Every letter has the same empty block model (the glyph is drawn by the block entity renderer), an
item model drawn by the mod's item renderer, drops itself, and is cut on the stonecutter from paper or from any
other letter."""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
MOD = "giantletters"

LETTERS = []
for c in "abcdefghijklmnopqrstuvwxyz":
    LETTERS.append(("capital_" + c, c.upper(), "Capital Letter " + c.upper()))
for c in "abcdefghijklmnopqrstuvwxyz":
    LETTERS.append(("small_" + c, c, "Small Letter " + c))
for d in "0123456789":
    LETTERS.append(("digit_" + d, d, "Digit " + d))

# item/generated's display settings: a flat item held and shown like paper or a sign
DISPLAY = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    lang = {
        "itemGroup.giantletters": "Giant Letters",
    }
    for name, _ch, title in LETTERS:
        lang["block.%s.%s" % (MOD, name)] = title
        write("assets/%s/blockstates/%s.json" % (MOD, name),
              {"variants": {"": {"model": "%s:block/letter" % MOD}}})
        write("assets/%s/models/item/%s.json" % (MOD, name),
              {"parent": "minecraft:builtin/entity", "gui_light": "front", "display": DISPLAY})
        write("data/%s/loot_tables/blocks/%s.json" % (MOD, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (MOD, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        others = [{"item": "minecraft:paper"}] + [{"item": "%s:%s" % (MOD, o)} for o, _c, _t in LETTERS if o != name]
        write("data/%s/recipes/cut_%s.json" % (MOD, name), {
            "type": "minecraft:stonecutting",
            "ingredient": others,
            "result": "%s:%s" % (MOD, name),
            "count": 1,
        })
    ids = ["%s:%s" % (MOD, name) for name, _c, _t in LETTERS]
    write("data/%s/tags/items/letters.json" % MOD, {"replace": False, "values": ids})
    write("data/%s/tags/blocks/letters.json" % MOD, {"replace": False, "values": ids})
    # written sorted so the file is stable between runs
    write("assets/%s/lang/en_us.json" % MOD, dict(sorted(lang.items())))
    print("%d letters written under %s" % (len(LETTERS), os.path.normpath(ROOT)))


if __name__ == "__main__":
    main()
