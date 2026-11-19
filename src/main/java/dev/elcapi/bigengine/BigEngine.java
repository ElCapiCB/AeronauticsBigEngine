package dev.elcapi.bigengine;

import com.mojang.logging.LogUtils;
import dev.elcapi.bigengine.network.EngineControlPayload;
import dev.elcapi.bigengine.registry.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Aeronautics Big Engines: block-built piston engines for Create, whose crankshaft and pistons become
 * Sable sub-levels that really move.
 */
@Mod(BigEngine.MOD_ID)
public class BigEngine {
    public static final String MOD_ID = "aeronauticsbigengine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BigEngine(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, BigEngineConfig.SPEC);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::registerCapabilities);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(EngineControlPayload.TYPE, EngineControlPayload.STREAM_CODEC, EngineControlPayload::handle);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FUEL_INJECTOR.get(), (be, side) -> be.getTank());
    }
}
