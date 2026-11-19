package dev.elcapi.bigengine.registry;

import dev.elcapi.bigengine.BigEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

public final class ModTags {
    /** Fluids an injector accepts as fuel. */
    public static final TagKey<Fluid> ENGINE_FUEL = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(BigEngine.MOD_ID, "engine_fuel"));
    /** Fuels with reduced energy content (e.g. lava). */
    public static final TagKey<Fluid> WEAK_ENGINE_FUEL = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(BigEngine.MOD_ID, "weak_engine_fuel"));

    private ModTags() {}
}
