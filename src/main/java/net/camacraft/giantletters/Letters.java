package net.camacraft.giantletters;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 94 glyphs (every printable ASCII character but the space) and their blocks, items and the one block entity
 * type. Registry names are {@code capital_a}, {@code small_a}, {@code digit_0} and {@code symbol_exclamation},
 * which also sorts the stonecutter's list A to Z, 0 to 9, a to z, then the symbols. Any other character the font
 * draws comes from a letter renamed to that one character in an anvil ({@link LetterItem#customCharacter}).
 * {@code tools/generate_assets.py} writes the JSON for this list; keep its table in step.
 */
public final class Letters {
    /** One glyph: its registry name, the character the font draws, and its kind ({@code capital}, {@code small}, {@code digit}, {@code symbol}). */
    public record Glyph(String id, char ch, String kind) {
    }

    /** The printable ASCII symbols, in code order, with their registry names. */
    private static final String SYMBOL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    private static final String[] SYMBOL_IDS = {
            "exclamation", "quote", "hash", "dollar", "percent", "ampersand", "apostrophe", "paren_left", "paren_right",
            "asterisk", "plus", "comma", "hyphen", "period", "slash", "colon", "semicolon", "less", "equals", "greater",
            "question", "at", "bracket_left", "backslash", "bracket_right", "caret", "underscore", "grave", "brace_left",
            "bar", "brace_right", "tilde"};

    public static final List<Glyph> ALL;

    static {
        List<Glyph> all = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) all.add(new Glyph("capital_" + c, Character.toUpperCase(c), "capital"));
        for (char c = 'a'; c <= 'z'; c++) all.add(new Glyph("small_" + c, c, "small"));
        for (char c = '0'; c <= '9'; c++) all.add(new Glyph("digit_" + c, c, "digit"));
        for (int i = 0; i < SYMBOL_CHARS.length(); i++) all.add(new Glyph("symbol_" + SYMBOL_IDS[i], SYMBOL_CHARS.charAt(i), "symbol"));
        ALL = Collections.unmodifiableList(all);
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GiantLetters.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GiantLetters.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GiantLetters.MODID);

    public static final Map<Glyph, RegistryObject<LetterBlock>> BLOCKS_BY_GLYPH = new LinkedHashMap<>();
    public static final Map<Glyph, RegistryObject<Item>> ITEMS_BY_GLYPH = new LinkedHashMap<>();

    static {
        for (Glyph glyph : ALL) {
            RegistryObject<LetterBlock> block = BLOCKS.register(glyph.id(), () -> new LetterBlock(glyph, properties()));
            BLOCKS_BY_GLYPH.put(glyph, block);
            ITEMS_BY_GLYPH.put(glyph, ITEMS.register(glyph.id(), () -> new LetterItem(block.get(), new Item.Properties())));
        }
    }

    /** One block entity type for every letter: it holds the colour and a custom character, and on the client the letter's place in its square. */
    public static final RegistryObject<BlockEntityType<LetterBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("letter",
            () -> BlockEntityType.Builder.of(LetterBlockEntity::new,
                    BLOCKS_BY_GLYPH.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private Letters() {
    }

    /**
     * A paper cut-out stuck to a wall: nothing to walk into, nothing on a map, breaks when pushed, soft to the
     * touch, taken down as quickly as a sign.
     */
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noCollission()
                .noOcclusion()
                .strength(1.0F)
                .sound(SoundType.WOOL)
                .pushReaction(PushReaction.DESTROY);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
