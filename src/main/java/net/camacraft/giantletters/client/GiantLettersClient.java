package net.camacraft.giantletters.client;

import net.camacraft.giantletters.GiantLetters;
import net.camacraft.giantletters.Letters;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The client's half: the block entity renderer for the letters and the item renderer for their icons. */
@Mod.EventBusSubscriber(modid = GiantLetters.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GiantLettersClient {
    private static LetterItemRenderer itemRenderer;

    private GiantLettersClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Letters.BLOCK_ENTITY.get(), LetterRenderer::new);
    }

    /** The one item renderer, made when the first letter item is drawn (it needs the running client). */
    public static LetterItemRenderer itemRenderer() {
        if (itemRenderer == null) itemRenderer = new LetterItemRenderer(Minecraft.getInstance());
        return itemRenderer;
    }
}
