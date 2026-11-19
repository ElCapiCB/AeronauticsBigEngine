package dev.elcapi.bigengine.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BigEngine.MOD_ID, value = Dist.CLIENT)
public final class BigEngineClient {
    private BigEngineClient() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Rotating shaft on the power output, rendered by Flywheel like any Create shaft.
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.POWER_OUTPUT.get())
                    .factory(SingleAxisRotatingVisual::shaft)
                    .skipVanillaRender(be -> false)
                    .apply();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.POWER_OUTPUT.get(), KineticBlockEntityRenderer::new);
    }
}
