package net.camacraft.giantletters.client;

import net.camacraft.giantletters.GiantLetters;
import net.camacraft.giantletters.LetterBlock;
import net.camacraft.giantletters.LetterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A glowing letter's light in its dye's colour under Colorful Lighting: this client's letter block entities publish
 * their colour here as they load and change ({@link #publish}), and Colorful Lighting's block light colour provider,
 * registered by reflection (no compile-time dependency; nothing happens without the mod), answers from that map on
 * its own threads. A white letter defers (-1), so its light stays Colorful Lighting's plain white; a glowing letter
 * dyed again is relit through {@code CLLightView.notifyBlockChanged}, since the dye changes no block state.
 */
@Mod.EventBusSubscriber(modid = GiantLetters.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LetterLightColors {
    /** Per level: block position -> 0xRRGGBB (or -1 for white), read by Colorful Lighting's threads. */
    private static final Map<Level, ConcurrentHashMap<Long, Integer>> COLORS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean active;
    private static MethodHandle lightView, notifyChanged;

    private LetterLightColors() {
    }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("colorful_lighting")) return;
        event.enqueueWork(LetterLightColors::register);
    }

    /** Whether Colorful Lighting takes the letters' colours (for the harness). */
    public static boolean active() {
        return active;
    }

    private static void register() {
        try {
            Class<?> api = Class.forName("net.camacraft.colorfullighting.api.ColorfulLightingApi");
            Class<?> provider = Class.forName("net.camacraft.colorfullighting.api.CLBlockLightColorProvider");
            Class<?> view = Class.forName("net.camacraft.colorfullighting.api.CLLightView");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            lightView = lookup.findStatic(api, "getLightView", MethodType.methodType(view, Level.class));
            notifyChanged = lookup.findVirtual(view, "notifyBlockChanged", MethodType.methodType(void.class, BlockPos.class));
            Object instance = Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[]{provider}, (proxy, method, args) -> switch (method.getName()) {
                case "getLightColor" -> colorAt((Level) args[0], (BlockPos) args[1], (BlockState) args[2]);
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "giantletters";
                default -> null;
            });
            api.getMethod("registerBlockLightColorProvider", provider).invoke(null, instance);
            active = true;
            GiantLetters.LOG.info("[giantletters] glowing letters give light in their dye's colour (Colorful Lighting)");
        } catch (ReflectiveOperationException | RuntimeException e) {
            GiantLetters.LOG.warn("[giantletters] Colorful Lighting is loaded but its light colour API isn't usable: {}", e.toString());
        }
    }

    /** Colorful Lighting's question, on its threads: the colour of a glowing letter's light, or -1 to defer. */
    private static int colorAt(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof LetterBlock) || !state.getValue(LetterBlock.GLOWING)) return -1;
        ConcurrentHashMap<Long, Integer> colors = COLORS.get(level);
        if (colors == null) return -1;
        Integer rgb = colors.get(pos.asLong());
        return rgb == null ? -1 : rgb;
    }

    /** A client letter block entity's colour, as loaded or changed. */
    public static void publish(LetterBlockEntity be) {
        if (!active || be.getLevel() == null) return;
        Level level = be.getLevel();
        ConcurrentHashMap<Long, Integer> colors = COLORS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        int rgb = be.color() == DyeColor.WHITE ? -1 : be.color().getTextColor();
        Integer before = colors.put(be.getBlockPos().asLong(), rgb);
        if (before != null && before != rgb && be.getBlockState().getValue(LetterBlock.GLOWING)) {
            notify(level, be.getBlockPos());
        }
    }

    /** A client letter block entity going away. */
    public static void forget(LetterBlockEntity be) {
        if (!active || be.getLevel() == null) return;
        ConcurrentHashMap<Long, Integer> colors = COLORS.get(be.getLevel());
        if (colors != null) colors.remove(be.getBlockPos().asLong());
    }

    private static void notify(Level level, BlockPos pos) {
        try {
            Object view = lightView.invoke(level);
            if (view != null) notifyChanged.invoke(view, pos);
        } catch (Throwable t) {
            GiantLetters.LOG.debug("[giantletters] relight of {} refused: {}", pos, t.toString());
        }
    }
}
