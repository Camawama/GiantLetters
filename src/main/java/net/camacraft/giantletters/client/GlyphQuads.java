package net.camacraft.giantletters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.camacraft.giantletters.LetterFrame;
import net.camacraft.giantletters.SquareCut;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.DyeColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a glyph, or the part of it that falls on one block of a bigger letter, as sign text is drawn: quads on the
 * font's own texture with the text render types. The letter's square is {@code size} blocks across; the font's
 * line box fills it top to bottom, and the glyph's ink is centred in it. Each block draws only what lies on its own
 * face, so a 9x9 letter is 81 clipped pieces of one glyph, each lit by its own block.
 */
public final class GlyphQuads {
    private GlyphQuads() {
    }

    /**
     * @param frame   which way the letter reads, in the block's own space
     * @param depth   how far off the wall the glyph sits, in blocks
     * @param rgb     the glyph colour (a dye's text colour)
     * @param outline the glow ink's one-pixel outline in a darker colour behind the glyph (signs show it within 16 blocks)
     * @param light   the packed light the glyph is drawn with (full bright for a glowing letter)
     */
    public static void drawTile(PoseStack pose, MultiBufferSource buffers, FontGlyphs.Metrics metrics, FontGlyphs.Glyph glyph,
                                SquareCut.Tile tile, LetterFrame frame, float depth, int rgb, boolean outline, int light) {
        int n = tile.size();
        float s = n / metrics.boxHeight();   // blocks per text unit
        float inkW = glyph.inkRight - glyph.inkLeft, inkH = glyph.inkBottom - glyph.inkTop;
        float centre = (glyph.inkLeft + glyph.inkRight) * 0.5F;
        // the whole glyph quad in the square's space: x right, y up, in blocks from the square's bottom-left corner
        float gy0, gy1;
        if (inkW * s > n || inkH * s > n) {
            // a character outside the measured set that doesn't fit the line box (a wide or tall glyph): shrunk to fit and centred
            s = n / Math.max(inkW, inkH);
            float middle = (glyph.inkTop + glyph.inkBottom) * 0.5F;
            gy1 = n * 0.5F + (middle - glyph.up) * s;
            gy0 = n * 0.5F + (middle - glyph.down) * s;
        } else {
            gy1 = (metrics.boxBottom - glyph.up) * s;
            gy0 = (metrics.boxBottom - glyph.down) * s;
        }
        float gx0 = n * 0.5F + (glyph.left - centre) * s;
        float gx1 = n * 0.5F + (glyph.right - centre) * s;
        Matrix4f matrix = pose.last().pose();
        // the tile's face, in the square's space; the glow outline may reach one glyph pixel past the square's own
        // edges (a capital's top sits on the line box's top, so its outline has no room inside the square)
        float cx0 = tile.column(), cx1 = tile.column() + 1, cy0 = tile.row(), cy1 = tile.row() + 1;
        if (outline) {
            float ox0 = tile.column() == 0 ? cx0 - s : cx0, ox1 = tile.column() == n - 1 ? cx1 + s : cx1;
            float oy0 = tile.row() == 0 ? cy0 - s : cy0, oy1 = tile.row() == n - 1 ? cy1 + s : cy1;
            int dark = darkColor(rgb);
            VertexConsumer vc = buffers.getBuffer(glyph.baked.renderType(Font.DisplayMode.NORMAL));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    quad(vc, matrix, frame, depth, gx0 + dx * s, gy0 - dy * s, gx1 + dx * s, gy1 - dy * s, glyph, tile, ox0, oy0, ox1, oy1, dark, LightTexture.FULL_BRIGHT);
                }
            }
            VertexConsumer front = buffers.getBuffer(glyph.baked.renderType(Font.DisplayMode.POLYGON_OFFSET));
            quad(front, matrix, frame, depth, gx0, gy0, gx1, gy1, glyph, tile, cx0, cy0, cx1, cy1, rgb, light);
        } else {
            VertexConsumer vc = buffers.getBuffer(glyph.baked.renderType(Font.DisplayMode.NORMAL));
            quad(vc, matrix, frame, depth, gx0, gy0, gx1, gy1, glyph, tile, cx0, cy0, cx1, cy1, rgb, light);
        }
    }

    /** The glow outline's colour, as a sign's: 40% of the text colour (an RGB int), or a light grey behind black. */
    public static int darkColor(int rgb) {
        if (rgb == DyeColor.BLACK.getTextColor()) return 0xF0EBCC;
        int r = (int) (((rgb >> 16) & 0xFF) * 0.4), g = (int) (((rgb >> 8) & 0xFF) * 0.4), b = (int) ((rgb & 0xFF) * 0.4);
        return r << 16 | g << 8 | b;
    }

    /**
     * The part of the glyph quad ({@code gx0..gx1}, {@code gy0..gy1} in the square's space) that lies inside the
     * tile's clip rectangle ({@code cx0..cx1}, {@code cy0..cy1}, its face or a little more for the outline), with
     * the texture coordinates of that part, as one quad facing the reader.
     */
    private static void quad(VertexConsumer vc, Matrix4f matrix, LetterFrame frame, float depth, float gx0, float gy0, float gx1, float gy1,
                             FontGlyphs.Glyph glyph, SquareCut.Tile tile, float cx0, float cy0, float cx1, float cy1, int rgb, int light) {
        float x0 = Math.max(gx0, cx0), x1 = Math.min(gx1, cx1);
        float y0 = Math.max(gy0, cy0), y1 = Math.min(gy1, cy1);
        if (x0 >= x1 || y0 >= y1 || gx1 <= gx0 || gy1 <= gy0) return;
        float u0 = glyph.u0 + (x0 - gx0) / (gx1 - gx0) * (glyph.u1 - glyph.u0);
        float u1 = glyph.u0 + (x1 - gx0) / (gx1 - gx0) * (glyph.u1 - glyph.u0);
        // v0 is the glyph's top; y grows upward
        float v0 = glyph.v0 + (gy1 - y1) / (gy1 - gy0) * (glyph.v1 - glyph.v0);
        float v1 = glyph.v0 + (gy1 - y0) / (gy1 - gy0) * (glyph.v1 - glyph.v0);
        float lx0 = x0 - tile.column(), lx1 = x1 - tile.column(), ly0 = y0 - tile.row(), ly1 = y1 - tile.row();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        vertex(vc, matrix, frame.point(lx0, ly0, depth), r, g, b, u0, v1, light);
        vertex(vc, matrix, frame.point(lx1, ly0, depth), r, g, b, u1, v1, light);
        vertex(vc, matrix, frame.point(lx1, ly1, depth), r, g, b, u1, v0, light);
        vertex(vc, matrix, frame.point(lx0, ly1, depth), r, g, b, u0, v0, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Vector3f p, int r, int g, int b, float u, float v, int light) {
        vc.vertex(matrix, p.x, p.y, p.z).color(r, g, b, 255).uv(u, v).uv2(light).endVertex();
    }
}
