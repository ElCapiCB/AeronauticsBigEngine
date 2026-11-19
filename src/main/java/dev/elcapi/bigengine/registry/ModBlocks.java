package dev.elcapi.bigengine.registry;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.content.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BigEngine.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties metalPart() {
        return metal().noOcclusion();
    }

    // ---- Crank line (rotating group) ----
    public static final DeferredBlock<CrankshaftBlock> CRANKSHAFT =
            BLOCKS.register("crankshaft", () -> new CrankshaftBlock(metalPart()));
    public static final DeferredBlock<CrankThrowBlock> CRANK_THROW =
            BLOCKS.register("crank_throw", () -> new CrankThrowBlock(metalPart()));
    public static final DeferredBlock<FlywheelBlock> ENGINE_FLYWHEEL =
            BLOCKS.register("engine_flywheel", () -> new FlywheelBlock(metalPart()));
    public static final DeferredBlock<MainBearingBlock> MAIN_BEARING =
            BLOCKS.register("main_bearing", () -> new MainBearingBlock(metalPart()));
    public static final DeferredBlock<PowerOutputBlock> POWER_OUTPUT =
            BLOCKS.register("power_output", () -> new PowerOutputBlock(metalPart()));

    // ---- Reciprocating group ----
    public static final DeferredBlock<ConnectingRodBlock> CONNECTING_ROD =
            BLOCKS.register("connecting_rod", () -> new ConnectingRodBlock(metalPart()));
    public static final DeferredBlock<EnginePistonBlock> ENGINE_PISTON =
            BLOCKS.register("engine_piston", () -> new EnginePistonBlock(metalPart()));

    // ---- Static engine block ----
    public static final DeferredBlock<Block> CYLINDER_WALL =
            BLOCKS.register("cylinder_wall", () -> new Block(metal()));
    public static final DeferredBlock<CylinderHeadBlock> CYLINDER_HEAD =
            BLOCKS.register("cylinder_head", () -> new CylinderHeadBlock(metal()));

    // ---- Ports on the cylinder head ----
    public static final DeferredBlock<FuelInjectorBlock> FUEL_INJECTOR =
            BLOCKS.register("fuel_injector", () -> new FuelInjectorBlock(metalPart()));
    public static final DeferredBlock<EnginePortBlock> SPARK_PLUG =
            BLOCKS.register("spark_plug", () -> new EnginePortBlock(metalPart(), EnginePortBlock.PortType.SPARK_PLUG));
    public static final DeferredBlock<EnginePortBlock> AIR_INTAKE =
            BLOCKS.register("air_intake", () -> new EnginePortBlock(metalPart(), EnginePortBlock.PortType.AIR_INTAKE));
    public static final DeferredBlock<EnginePortBlock> EXHAUST_MANIFOLD =
            BLOCKS.register("exhaust_manifold", () -> new EnginePortBlock(metalPart(), EnginePortBlock.PortType.EXHAUST));

    // ---- Control & lubrication ----
    public static final DeferredBlock<EngineControllerBlock> ENGINE_CONTROLLER =
            BLOCKS.register("engine_controller", () -> new EngineControllerBlock(metal()));
    public static final DeferredBlock<OilFilmBlock> OIL_FILM =
            BLOCKS.register("oil_film", () -> new OilFilmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).replaceable().noCollission().strength(0.1f)
                    .sound(SoundType.HONEY_BLOCK).noOcclusion().pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {}
}
