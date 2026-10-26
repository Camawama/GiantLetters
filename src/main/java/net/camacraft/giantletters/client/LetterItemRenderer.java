package net.camacraft.giantletters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.giantletters.LetterFrame;
import net.camacraft.giantletters.LetterItem;
import net.camacraft.giantletters.SquareCut;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A letter item: the glyph in white with the dark outline (so it reads on the inventory's grey), as a flat item
 * like paper, the back mirrored as a generated item's is.
 */
public final class LetterItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final LetterFrame FRONT = new LetterFrame(Direction.EAST, Direction.UP, Direction.SOUTH);
    private static final LetterFrame BACK = new LetterFrame(Direction.WEST, Direction.UP, Direction.NORTH);

    LetterItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof LetterItem item)) return;
        FontGlyphs.Metrics metrics = FontGlyphs.get();
        FontGlyphs.Glyph glyph = metrics.glyph(item.letter().glyph().ch());
        if (glyph == null) return;
        int white = DyeColor.WHITE.getTextColor();
        GlyphQuads.drawTile(pose, buffers, metrics, glyph, SquareCut.Tile.SINGLE, FRONT, 0.5F, white, true, light);
        GlyphQuads.drawTile(pose, buffers, metrics, glyph, SquareCut.Tile.SINGLE, BACK, 0.5F, white, true, light);
    }
}
