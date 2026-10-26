package net.camacraft.giantletters;

import net.camacraft.giantletters.client.GiantLettersClient;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** A letter in the hand or the inventory: drawn by the mod's item renderer, so the icon follows the font too. */
public class LetterItem extends BlockItem {
    public LetterItem(LetterBlock block, Properties properties) {
        super(block, properties);
    }

    public LetterBlock letter() {
        return (LetterBlock) getBlock();
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
