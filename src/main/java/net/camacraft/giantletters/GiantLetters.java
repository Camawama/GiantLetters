package net.camacraft.giantletters;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Giant Letters: every printable keyboard character as a block for walls, floors and ceilings, drawn with the
 * game's own font the way sign text is (so a font resource pack changes them too), and any other character the
 * font draws through an anvil rename. A square of the same letter becomes one bigger letter ({@link LetterGroups});
 * dye, glow ink and an ink sac work on a whole letter as on a sign ({@link LetterBlock#use}). Letters are cut on the
 * stonecutter from paper, or from any other letter, and have a creative tab of their own.
 */
@Mod(GiantLetters.MODID)
public final class GiantLetters {
    public static final String MODID = "giantletters";
    public static final Logger LOG = LogUtils.getLogger();

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /**
     * The Giant Letters tab, among the other mods' tabs after vanilla's (no ordering of its own: ordered after
     * Functional Blocks it sat on the creative menu's first page, the owner, 2026-10-27): A to Z, a to z, 0 to 9, the
     * symbols; a Capital Letter A as its icon.
     */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("letters", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> new ItemStack(Letters.ITEMS_BY_GLYPH.get(Letters.ALL.get(0)).get()))
            .displayItems((parameters, output) -> {
                for (Letters.Glyph glyph : Letters.ALL) output.accept(Letters.ITEMS_BY_GLYPH.get(glyph).get());
            })
            .build());

    public GiantLetters() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Letters.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
