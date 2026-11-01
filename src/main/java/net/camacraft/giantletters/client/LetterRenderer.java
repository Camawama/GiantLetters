package net.camacraft.giantletters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.giantletters.LetterBlock;
import net.camacraft.giantletters.LetterBlockEntity;
import net.camacraft.giantletters.LetterFrame;
import net.camacraft.giantletters.SquareCut;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a letter block: its part of the glyph, one pixel off the wall, in its colour; a glowing one full bright
 * with the sign's dark outline when the camera is within 16 blocks per block of its size. The letters of the
 * client's own world are drawn from {@link LetterMeshes} instead (a few static meshes a frame), so this draws only
 * what those leave out: a letter under another mod's transform (on a Valkyrien Skies ship, in a preview level).
 */
public final class LetterRenderer implements BlockEntityRenderer<LetterBlockEntity> {
    /** How far off the wall the glyph sits: on the outer face of the one-pixel plate. */
    public static final float DEPTH = 1.0F / 16.0F;

    public LetterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LetterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof LetterBlock block)) return;
        if (LetterMeshes.claims(be)) return;
        FontGlyphs.Metrics metrics = FontGlyphs.get();
        FontGlyphs.Glyph glyph = metrics.glyph(be.character());
        if (glyph == null) return;
        SquareCut.Tile tile = be.tile();
        boolean glowing = state.getValue(LetterBlock.GLOWING);
        boolean outline = glowing && outlineVisible(be, tile.size());
        GlyphQuads.drawTile(pose, buffers, metrics, glyph, tile, LetterFrame.of(state), DEPTH, be.color().getTextColor(),
                outline, glowing ? LightTexture.FULL_BRIGHT : light, be.offsetX(), be.offsetY());
    }

    /** The sign rule, scaled by the letter's size: the outline shows within 16 blocks of a one-block letter. */
    private static boolean outlineVisible(LetterBlockEntity be, int size) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return true;
        double reach = 16.0 * size;
        return camera.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < reach * reach;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
