package dev.elcapi.bigengine.registry;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.content.EngineOilItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BigEngine.MOD_ID);

    public static final DeferredItem<BlockItem> CRANKSHAFT = ITEMS.registerSimpleBlockItem(ModBlocks.CRANKSHAFT);
    public static final DeferredItem<BlockItem> CRANK_THROW = ITEMS.registerSimpleBlockItem(ModBlocks.CRANK_THROW);
    public static final DeferredItem<BlockItem> ENGINE_FLYWHEEL = ITEMS.registerSimpleBlockItem(ModBlocks.ENGINE_FLYWHEEL);
    public static final DeferredItem<BlockItem> MAIN_BEARING = ITEMS.registerSimpleBlockItem(ModBlocks.MAIN_BEARING);
    public static final DeferredItem<BlockItem> POWER_OUTPUT = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_OUTPUT);
    public static final DeferredItem<BlockItem> CONNECTING_ROD = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTING_ROD);
    public static final DeferredItem<BlockItem> ENGINE_PISTON = ITEMS.registerSimpleBlockItem(ModBlocks.ENGINE_PISTON);
    public static final DeferredItem<BlockItem> CYLINDER_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.CYLINDER_WALL);
    public static final DeferredItem<BlockItem> CYLINDER_HEAD = ITEMS.registerSimpleBlockItem(ModBlocks.CYLINDER_HEAD);
    public static final DeferredItem<BlockItem> FUEL_INJECTOR = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_INJECTOR);
    public static final DeferredItem<BlockItem> SPARK_PLUG = ITEMS.registerSimpleBlockItem(ModBlocks.SPARK_PLUG);
    public static final DeferredItem<BlockItem> AIR_INTAKE = ITEMS.registerSimpleBlockItem(ModBlocks.AIR_INTAKE);
    public static final DeferredItem<BlockItem> EXHAUST_MANIFOLD = ITEMS.registerSimpleBlockItem(ModBlocks.EXHAUST_MANIFOLD);
    public static final DeferredItem<BlockItem> ENGINE_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.ENGINE_CONTROLLER);

    public static final DeferredItem<EngineOilItem> ENGINE_OIL = ITEMS.register("engine_oil",
            () -> new EngineOilItem(new Item.Properties().durability(48)));

    private ModItems() {}
}
