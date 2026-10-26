package net.camacraft.giantletters;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Giant Letters: the 26 letters, their small forms and the ten digits as blocks for walls, floors and ceilings,
 * drawn with the game's own font the way sign text is (so a font resource pack changes them too). A square of the
 * same letter becomes one bigger letter ({@link LetterGroups}); dye, glow ink and an ink sac work on a whole letter
 * as on a sign ({@link LetterBlock#use}). Letters are cut on the stonecutter from paper, or from any other letter.
 */
@Mod(GiantLetters.MODID)
public final class GiantLetters {
    public static final String MODID = "giantletters";
    public static final Logger LOG = LogUtils.getLogger();

    public GiantLetters() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Letters.register(bus);
        bus.addListener(this::creativeTabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    /** Functional Blocks, right after the signs: A to Z, a to z, 0 to 9. */
    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) return;
        var entries = event.getEntries();
        ItemStack previous = new ItemStack(Items.WARPED_HANGING_SIGN);
        boolean afterSigns = entries.contains(previous);
        for (Letters.Glyph glyph : Letters.ALL) {
            ItemStack stack = new ItemStack(Letters.ITEMS_BY_GLYPH.get(glyph).get());
            if (afterSigns) {
                entries.putAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } else {
                event.accept(stack);
            }
            previous = stack;
        }
    }
}
