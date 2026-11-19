package dev.elcapi.bigengine.registry;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.content.blockentity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BigEngine.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineControllerBlockEntity>> ENGINE_CONTROLLER =
            BLOCK_ENTITIES.register("engine_controller", () -> BlockEntityType.Builder
                    .of(EngineControllerBlockEntity::new, ModBlocks.ENGINE_CONTROLLER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerOutputBlockEntity>> POWER_OUTPUT =
            BLOCK_ENTITIES.register("power_output", () -> BlockEntityType.Builder
                    .of(PowerOutputBlockEntity::new, ModBlocks.POWER_OUTPUT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelInjectorBlockEntity>> FUEL_INJECTOR =
            BLOCK_ENTITIES.register("fuel_injector", () -> BlockEntityType.Builder
                    .of(FuelInjectorBlockEntity::new, ModBlocks.FUEL_INJECTOR.get()).build(null));

    /** Shared by crank throws and pistons: links a block living inside a Sable sub-level back to its controller. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineLinkBlockEntity>> ENGINE_LINK =
            BLOCK_ENTITIES.register("engine_link", () -> BlockEntityType.Builder
                    .of(EngineLinkBlockEntity::new, ModBlocks.CRANK_THROW.get(), ModBlocks.ENGINE_PISTON.get()).build(null));

    private ModBlockEntities() {}
}
