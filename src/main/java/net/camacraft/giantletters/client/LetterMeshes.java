package net.camacraft.giantletters.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.camacraft.giantletters.GiantLetters;
import net.camacraft.giantletters.LetterBlock;
import net.camacraft.giantletters.LetterBlockEntity;
import net.camacraft.giantletters.LetterFrame;
import net.camacraft.giantletters.SquareCut;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The letters of this client's world, drawn as a few static meshes instead of block by block.
 * <p>
 * The block entity renderer drew every block of every letter again each frame: the glyph's clipped quads built anew
 * (36 vertices for a glowing block), and the text render types switched per block, which ends vanilla's shared buffer
 * and draws it (two GL draws a block). A dozen 3x3 letters, a hundred blocks, took about 5% of the render thread in
 * the owner's village (profile of 2026-10-28). Now each chunk section's letter blocks are built once into one vertex
 * buffer per text render type (the font texture, and the glow outline's polygon-offset variant), uploaded, and drawn
 * with a handful of draw calls a frame after the block entities. A section is rebuilt only when a letter in it loads,
 * unloads or changes (colour, character, shift, its place in a bigger letter), when a letter's light changes, when the
 * sign rule's outline comes or goes with the camera's distance, or when the font set changes (a resource reload).
 * <p>
 * Letters drawn under another transform than the world's are still drawn by the block entity renderer the old way
 * ({@link LetterRenderer}): those on a Valkyrien Skies ship, whose block entities sit in the shipyard millions of
 * blocks from the camera, and those in another mod's preview level. {@code -Dgiantletters.batchedLetters=false}
 * turns the meshes off and every letter is drawn block by block again.
 */
@Mod.EventBusSubscriber(modid = GiantLetters.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LetterMeshes {
    /** How far letters are drawn, as the block entity renderer's view distance. */
    private static final double VIEW_DISTANCE = 128.0;
    /** A letter block farther than this from the camera is being drawn under another mod's transform (a ship). */
    private static final double DIRECT_DISTANCE = 4096.0;
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("giantletters.batchedLetters"));

    /** The level the sections belong to; everything is dropped when the client's level changes. */
    private static ClientLevel level;
    private static final Long2ObjectOpenHashMap<Section> SECTIONS = new Long2ObjectOpenHashMap<>();
    /** The font the meshes were built with; a new one (a resource reload) rebuilds them all. */
    private static FontGlyphs.Metrics builtWith;
    private static int lastDraws, lastRebuilds, lastVisibleSections;

    private LetterMeshes() {
    }

    /** One chunk section's letters and their meshes, in the section's own space (vertices from its corner). */
    private static final class Section {
        final int x0, y0, z0;
        final Map<BlockPos, LetterBlockEntity> letters = new HashMap<>();
        /** The light each block was built with, and whether with the glow outline (the sign rule's distance). */
        final Map<BlockPos, Integer> builtLight = new HashMap<>();
        final Map<BlockPos, Boolean> builtOutline = new HashMap<>();
        final Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
        /** What the meshes cover, in the world; empty sections have none. */
        AABB box;
        boolean dirty = true;

        Section(long key) {
            this.x0 = SectionPos.sectionToBlockCoord(SectionPos.x(key));
            this.y0 = SectionPos.sectionToBlockCoord(SectionPos.y(key));
            this.z0 = SectionPos.sectionToBlockCoord(SectionPos.z(key));
        }

        void close() {
            for (VertexBuffer vb : buffers.values()) vb.close();
            buffers.clear();
            box = null;
        }
    }

    // ---- the registry (client thread) ------------------------------------------------------------------------------

    /** A letter block entity of the client's level loaded (or is first seen by the renderer). */
    public static void add(LetterBlockEntity be) {
        if (!ENABLED || !(be.getLevel() instanceof ClientLevel l) || l != Minecraft.getInstance().level) return;
        if (level != l) reset(l);
        Section section = SECTIONS.computeIfAbsent(SectionPos.asLong(be.getBlockPos()), Section::new);
        section.letters.put(be.getBlockPos().immutable(), be);
        section.dirty = true;
    }

    /** A letter block entity was removed (its block broken, or its chunk unloaded). */
    public static void remove(LetterBlockEntity be) {
        if (!ENABLED || be.getLevel() != level) return;
        Section section = SECTIONS.get(SectionPos.asLong(be.getBlockPos()));
        if (section == null || section.letters.remove(be.getBlockPos()) == null) return;
        section.dirty = true;
    }

    /** A letter block entity's colour, character, shift or tile changed: its section is rebuilt. */
    public static void changed(LetterBlockEntity be) {
        if (!ENABLED || be.getLevel() != level) return;
        Section section = SECTIONS.get(SectionPos.asLong(be.getBlockPos()));
        if (section != null) section.dirty = true;
    }

    /**
     * Whether the meshes draw this block entity, so its renderer must not: a letter of the client's level near the
     * camera (one being drawn far from it sits in a ship's shipyard, under the ship's transform). A letter the
     * registry doesn't know yet is taken in and drawn from the next frame.
     */
    public static boolean claims(LetterBlockEntity be) {
        if (!ENABLED) return false;
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null || be.getLevel() != mc.level) return false;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getPosition().distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > DIRECT_DISTANCE * DIRECT_DISTANCE) return false;
        Section section = level == be.getLevel() ? SECTIONS.get(SectionPos.asLong(be.getBlockPos())) : null;
        if (section == null || section.letters.get(be.getBlockPos()) != be) add(be);
        return true;
    }

    private static void reset(ClientLevel next) {
        for (Section section : SECTIONS.values()) section.close();
        SECTIONS.clear();
        level = next;
    }

    // ---- the pass ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (level == null) return;
        if (mc.level != level) {
            reset(mc.level);
            return;
        }
        if (SECTIONS.isEmpty()) return;
        FontGlyphs.Metrics metrics = FontGlyphs.get();
        boolean newFont = metrics != builtWith;
        builtWith = metrics;
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        double reach = VIEW_DISTANCE + 14.0;   // the section's half diagonal
        List<Section> visible = new ArrayList<>();
        LinkedHashSet<RenderType> types = new LinkedHashSet<>();
        int rebuilds = 0;
        var it = SECTIONS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Section section = it.next().getValue();
            if (section.letters.isEmpty()) {
                section.close();
                it.remove();
                continue;
            }
            if (newFont) section.dirty = true;
            double dx = section.x0 + 8 - cam.x, dy = section.y0 + 8 - cam.y, dz = section.z0 + 8 - cam.z;
            if (dx * dx + dy * dy + dz * dz > reach * reach) continue;
            if (!section.dirty) {
                if (section.box == null || !frustum.isVisible(section.box)) continue;
                pollChanges(section, cam);
            }
            if (section.dirty) {
                rebuild(section, metrics, cam);
                rebuilds++;
                if (section.box == null || !frustum.isVisible(section.box)) continue;
            }
            visible.add(section);
            types.addAll(section.buffers.keySet());
        }
        lastRebuilds = rebuilds;
        lastVisibleSections = visible.size();
        lastDraws = 0;
        if (visible.isEmpty()) return;
        Matrix4f view = event.getPoseStack().last().pose();
        Matrix4f projection = event.getProjectionMatrix();
        Matrix4f modelView = new Matrix4f();
        for (RenderType type : types) {
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                for (Section section : visible) {
                    VertexBuffer vb = section.buffers.get(type);
                    if (vb == null) continue;
                    modelView.set(view).translate((float) (section.x0 - cam.x), (float) (section.y0 - cam.y), (float) (section.z0 - cam.z));
                    vb.bind();
                    vb.drawWithShader(modelView, projection, shader);
                    lastDraws++;
                }
                VertexBuffer.unbind();
            }
            type.clearRenderState();
        }
    }

    /** Marks the section dirty when a letter's light, or whether its glow outline shows, differs from what was built. */
    private static void pollChanges(Section section, Vec3 cam) {
        for (Map.Entry<BlockPos, LetterBlockEntity> e : section.letters.entrySet()) {
            LetterBlockEntity be = e.getValue();
            if (be.isRemoved()) {
                section.dirty = true;
                return;
            }
            BlockState state = be.getBlockState();
            boolean glowing = state.getBlock() instanceof LetterBlock && state.getValue(LetterBlock.GLOWING);
            int light = glowing ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, e.getKey());
            Integer built = section.builtLight.get(e.getKey());
            if (built == null || built != light) {
                section.dirty = true;
                return;
            }
            boolean outline = glowing && outlineVisible(be, cam);
            Boolean builtOutline = section.builtOutline.get(e.getKey());
            if (builtOutline == null || builtOutline != outline) {
                section.dirty = true;
                return;
            }
        }
    }

    /** The sign rule, scaled by the letter's size: the outline shows within 16 blocks of a one-block letter. */
    static boolean outlineVisible(LetterBlockEntity be, Vec3 cam) {
        double reach = 16.0 * be.tile().size();
        return cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < reach * reach;
    }

    /** Builds the section's letters into one vertex buffer per text render type, replacing what it had. */
    private static void rebuild(Section section, FontGlyphs.Metrics metrics, Vec3 cam) {
        section.close();
        section.builtLight.clear();
        section.builtOutline.clear();
        section.dirty = false;
        Map<RenderType, Bounded> builders = new LinkedHashMap<>();
        MultiBufferSource source = type -> builders.computeIfAbsent(type, t -> {
            BufferBuilder b = new BufferBuilder(4096);
            b.begin(t.mode(), t.format());
            return new Bounded(b);
        });
        PoseStack pose = new PoseStack();
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, LetterBlockEntity> e : section.letters.entrySet()) {
            BlockPos pos = e.getKey();
            LetterBlockEntity be = e.getValue();
            BlockState state = be.getBlockState();
            if (be.isRemoved() || !(state.getBlock() instanceof LetterBlock)) {
                gone.add(pos);
                continue;
            }
            FontGlyphs.Glyph glyph = metrics.glyph(be.character());
            SquareCut.Tile tile = be.tile();
            boolean glowing = state.getValue(LetterBlock.GLOWING);
            boolean outline = glowing && outlineVisible(be, cam);
            int light = glowing ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, pos);
            section.builtLight.put(pos, light);
            section.builtOutline.put(pos, outline);
            if (glyph == null) continue;
            pose.pushPose();
            pose.translate(pos.getX() - section.x0, pos.getY() - section.y0, pos.getZ() - section.z0);
            GlyphQuads.drawTile(pose, source, metrics, glyph, tile, LetterFrame.of(state), LetterRenderer.DEPTH, be.color().getTextColor(),
                    outline, light, be.offsetX(), be.offsetY());
            pose.popPose();
        }
        for (BlockPos pos : gone) section.letters.remove(pos);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Map.Entry<RenderType, Bounded> e : builders.entrySet()) {
            Bounded b = e.getValue();
            BufferBuilder.RenderedBuffer rendered = b.builder.end();
            if (rendered.drawState().vertexCount() == 0) {
                rendered.release();
                continue;
            }
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(rendered);
            section.buffers.put(e.getKey(), vb);
            minX = Math.min(minX, b.minX);
            minY = Math.min(minY, b.minY);
            minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX);
            maxY = Math.max(maxY, b.maxY);
            maxZ = Math.max(maxZ, b.maxZ);
        }
        VertexBuffer.unbind();
        section.box = section.buffers.isEmpty() ? null
                : new AABB(section.x0 + minX - 0.05, section.y0 + minY - 0.05, section.z0 + minZ - 0.05,
                section.x0 + maxX + 0.05, section.y0 + maxY + 0.05, section.z0 + maxZ + 0.05);
    }

    /** A buffer builder that also remembers the bounds of the positions written to it (the section's box). */
    private static final class Bounded implements VertexConsumer {
        final BufferBuilder builder;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        Bounded(BufferBuilder builder) {
            this.builder = builder;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            builder.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            builder.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            builder.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            builder.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            builder.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            builder.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            builder.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            builder.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            builder.unsetDefaultColor();
        }
    }

    // ---- for the harness ---------------------------------------------------------------------------------------------

    /** Whether letters are drawn from the meshes at all. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** The draw calls of the last frame's pass. */
    public static int lastDraws() {
        return lastDraws;
    }

    /** The sections rebuilt in the last frame's pass. */
    public static int lastRebuilds() {
        return lastRebuilds;
    }

    /** The sections with letters in view in the last frame's pass. */
    public static int lastVisibleSections() {
        return lastVisibleSections;
    }

    /** The sections the registry holds for the client's level. */
    public static int sections() {
        return SECTIONS.size();
    }

    /** The letter block entities the registry holds for the client's level. */
    public static int letters() {
        int n = 0;
        for (Section s : SECTIONS.values()) n += s.letters.size();
        return n;
    }

    /** The level the meshes belong to (for the harness; null before the first letter loads). */
    public static Level level() {
        return level;
    }
}
