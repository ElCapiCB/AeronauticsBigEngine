package dev.elcapi.bigengine.content.block;

import com.simibubi.create.foundation.block.IBE;
import dev.elcapi.bigengine.content.blockentity.EngineLinkBlockEntity;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Engine piston. FACING points towards the cylinder head. Several pistons side by side (up to 3x3) form one
 * large-bore piston: more moving mass, more torque.
 */
public class EnginePistonBlock extends EngineDirectionalBlock implements IBE<EngineLinkBlockEntity> {
    public EnginePistonBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<EngineLinkBlockEntity> getBlockEntityClass() {
        return EngineLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends EngineLinkBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ENGINE_LINK.get();
    }
}
