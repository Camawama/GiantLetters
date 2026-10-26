package net.camacraft.giantletters.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.camacraft.giantletters.GiantLetters;
import net.camacraft.giantletters.Letters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.network.chat.Style;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's font as the letters see it. The glyphs come from the live default font set, the one sign text is
 * drawn with ({@code Font.getFontSet(Style.DEFAULT_FONT)}), so a font resource pack or the Force Unicode Font
 * option changes the letters as it changes sign text. Measured once per font set (a new one comes with every
 * resource reload): each glyph's ink on the font texture, read back from the texture, and the line box of the
 * whole set, from the highest ink (the capitals' tops) to the lowest (the descenders of g, j, p, q, y). Letters are
 * laid out at one scale on that box, so a word reads as typeset text: common baseline, each letter centred in its
 * own square by its own width.
 */
public final class FontGlyphs {
    /** One glyph: the baked glyph (its texture and coordinates) and its ink bounds, all in text units (a glyph pixel of the default font). */
    public static final class Glyph {
        public final BakedGlyph baked;
        public final float u0, u1, v0, v1, left, right, up, down;
        public float inkLeft, inkRight, inkTop, inkBottom;
        public boolean hasInk;

        Glyph(BakedGlyph baked) {
            this.baked = baked;
            this.u0 = baked.u0;
            this.u1 = baked.u1;
            this.v0 = baked.v0;
            this.v1 = baked.v1;
            this.left = baked.left;
            this.right = baked.right;
            this.up = baked.up;
            this.down = baked.down;
            this.inkLeft = left;
            this.inkRight = right;
            this.inkTop = up;
            this.inkBottom = down;
        }

        /** Finds the ink in this glyph's rectangle of the texture {@code image} ({@code w} x {@code h} texels). */
        void measure(NativeImage image, int w, int h) {
            int x0 = Math.round(u0 * w), x1 = Math.round(u1 * w), y0 = Math.round(v0 * h), y1 = Math.round(v1 * h);
            int l = Integer.MAX_VALUE, r = -1, t = Integer.MAX_VALUE, b = -1;
            for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
                for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
                    if ((image.getLuminanceOrAlpha(x, y) & 0xFF) == 0) continue;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
            }
            if (r < 0 || x1 <= x0 || y1 <= y0) return;
            float perTexelX = (right - left) / (x1 - x0), perTexelY = (down - up) / (y1 - y0);
            inkLeft = left + (l - x0) * perTexelX;
            inkRight = left + (r + 1 - x0) * perTexelX;
            inkTop = up + (t - y0) * perTexelY;
            inkBottom = up + (b + 1 - y0) * perTexelY;
            hasInk = true;
        }
    }

    /** The measured font: its glyphs by character and the line box they share, in text units. */
    public static final class Metrics {
        final FontSet set;
        private final Map<Integer, Glyph> glyphs;
        public final float boxTop, boxBottom;

        Metrics(FontSet set, Map<Integer, Glyph> glyphs, float boxTop, float boxBottom) {
            this.set = set;
            this.glyphs = glyphs;
            this.boxTop = boxTop;
            this.boxBottom = boxBottom;
        }

        public float boxHeight() {
            return boxBottom - boxTop;
        }

        /** The glyph for a character, or null when the font draws nothing for it. */
        public Glyph glyph(char c) {
            return glyphs.get((int) c);
        }
    }

    private static Metrics metrics;

    private FontGlyphs() {
    }

    /** The current font's metrics, measured now if the font set changed (render thread only). */
    public static Metrics get() {
        FontSet set = Minecraft.getInstance().font.getFontSet(Style.DEFAULT_FONT);
        Metrics m = metrics;
        if (m == null || m.set != set) {
            m = measure(set);
            metrics = m;
        }
        return m;
    }

    /** The line box's height in text units (8 for the default font), for the harness. */
    public static float boxHeight() {
        return get().boxHeight();
    }

    private static Metrics measure(FontSet set) {
        RenderSystem.assertOnRenderThread();
        Map<Integer, Glyph> glyphs = new HashMap<>();
        for (Letters.Glyph g : Letters.ALL) {
            BakedGlyph baked = set.getGlyph(g.ch());
            if (baked instanceof EmptyGlyph) continue;
            glyphs.put((int) g.ch(), new Glyph(baked));
        }
        for (FontTexture texture : new ArrayList<>(set.textures)) {
            measure(texture, glyphs.values());
        }
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        int inked = 0;
        for (Glyph g : glyphs.values()) {
            if (!g.hasInk) continue;
            inked++;
            top = Math.min(top, g.inkTop);
            bottom = Math.max(bottom, g.inkBottom);
        }
        if (inked == 0 || bottom <= top) {
            // a font that drew nothing we could read: vanilla's line, 8 rows from 3 under the line's top
            top = 3.0F;
            bottom = 11.0F;
        }
        GiantLetters.LOG.info("[giantletters] font measured: {} of {} glyphs with ink, line box {} to {} text units", inked, glyphs.size(), top, bottom);
        return new Metrics(set, glyphs, top, bottom);
    }

    /** Reads one font texture back and measures the glyphs that live on it. */
    private static void measure(FontTexture texture, Collection<Glyph> glyphs) {
        List<Glyph> mine = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.baked.renderTypes.normal() == texture.renderTypes.normal()) mine.add(g);
        }
        if (mine.isEmpty()) return;
        RenderSystem.bindTexture(texture.getId());
        int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0) return;
        NativeImage.Format format = texture.colored ? NativeImage.Format.RGBA : NativeImage.Format.LUMINANCE;
        try (NativeImage image = new NativeImage(format, w, h, false)) {
            image.downloadTexture(0, false);
            for (Glyph g : mine) g.measure(image, w, h);
        }
    }
}
