package dev.elcapi.bigengine.client.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.registry.ModItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registers the Ponder scenes (hold W over an item) and the "Big Engines" Ponder tag. */
public class BigEnginePonderPlugin implements PonderPlugin {
    public static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath(BigEngine.MOD_ID, "big_engines");

    @Override
    public String getModId() {
        return BigEngine.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<DeferredItem<?>> h = helper.withKeyFunction(DeferredItem::getId);

        h.forComponents(ModItems.ENGINE_CONTROLLER, ModItems.POWER_OUTPUT, ModItems.CRANKSHAFT, ModItems.MAIN_BEARING,
                        ModItems.ENGINE_FLYWHEEL, ModItems.CRANK_THROW)
                .addStoryBoard("engine_i2", EngineScenes::overview, TAG);
        h.forComponents(ModItems.ENGINE_CONTROLLER)
                .addStoryBoard("engine_i2", EngineScenes::controller, TAG);
        h.forComponents(ModItems.CRANK_THROW)
                .addStoryBoard("engine_i2", EngineScenes::crankTiming, TAG);
        h.forComponents(ModItems.ENGINE_PISTON, ModItems.CONNECTING_ROD, ModItems.CYLINDER_WALL, ModItems.CYLINDER_HEAD)
                .addStoryBoard("engine_i2", EngineScenes::cylinder, TAG);
        h.forComponents(ModItems.ENGINE_PISTON, ModItems.CYLINDER_WALL, ModItems.ENGINE_FLYWHEEL, ModItems.AIR_INTAKE)
                .addStoryBoard("engine_big_bore", EngineScenes::bigBore, TAG);
        h.forComponents(ModItems.FUEL_INJECTOR, ModItems.SPARK_PLUG, ModItems.AIR_INTAKE, ModItems.EXHAUST_MANIFOLD, ModItems.CYLINDER_HEAD)
                .addStoryBoard("engine_i2", EngineScenes::ports, TAG);
        h.forComponents(ModItems.ENGINE_OIL, ModItems.CYLINDER_WALL)
                .addStoryBoard("engine_i2", EngineScenes::oil, TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG)
                .addToIndex()
                .item(ModItems.CRANK_THROW.get(), true, false)
                .title("Big Engines")
                .description("Block-built piston engines for Create Aeronautics")
                .register();

        PonderTagRegistrationHelper<DeferredItem<?>> h = helper.withKeyFunction(DeferredItem::getId);
        h.addToTag(TAG).add(ModItems.ENGINE_CONTROLLER).add(ModItems.POWER_OUTPUT).add(ModItems.CRANKSHAFT)
                .add(ModItems.CRANK_THROW).add(ModItems.MAIN_BEARING).add(ModItems.ENGINE_FLYWHEEL)
                .add(ModItems.CONNECTING_ROD).add(ModItems.ENGINE_PISTON).add(ModItems.CYLINDER_WALL)
                .add(ModItems.CYLINDER_HEAD).add(ModItems.FUEL_INJECTOR).add(ModItems.SPARK_PLUG)
                .add(ModItems.AIR_INTAKE).add(ModItems.EXHAUST_MANIFOLD).add(ModItems.ENGINE_OIL);
        h.addToTag(AllCreatePonderTags.KINETIC_SOURCES).add(ModItems.POWER_OUTPUT);
    }
}
