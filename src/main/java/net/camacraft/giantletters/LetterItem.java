package net.camacraft.giantletters;

import net.camacraft.giantletters.client.GiantLettersClient;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * A letter in the hand or the inventory: drawn by the mod's item renderer, so the icon follows the font too. A
 * letter renamed in an anvil to exactly one character stands for that character ({@link #customCharacter}): the
 * icon shows it and the placed block takes it, so any character the font draws can be put on a wall.
 */
public class LetterItem extends BlockItem {
    public LetterItem(LetterBlock block, Properties properties) {
        super(block, properties);
    }

    public LetterBlock letter() {
        return (LetterBlock) getBlock();
    }

    /**
     * The character a renamed letter stands for: the stack's custom name when it is exactly one printable character
     * (a code point), else 0 for the block's own.
     */
    public static int customCharacter(ItemStack stack) {
        if (!stack.hasCustomHoverName()) return 0;
        String name = stack.getHoverName().getString();
        if (name.isEmpty() || name.codePointCount(0, name.length()) != 1) return 0;
        int codePoint = name.codePointAt(0);
        return Character.isWhitespace(codePoint) || Character.isISOControl(codePoint) ? 0 : codePoint;
    }

    /** The character this stack shows: its custom one or the block's own. */
    public static int characterOf(ItemStack stack) {
        int custom = customCharacter(stack);
        if (custom != 0) return custom;
        return stack.getItem() instanceof LetterItem item ? item.letter().glyph().ch() : '?';
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GiantLettersClient.itemRenderer();
            }
        });
    }
}
