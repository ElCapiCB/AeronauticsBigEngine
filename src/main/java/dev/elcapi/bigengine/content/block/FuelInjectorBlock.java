package dev.elcapi.bigengine.content.block;

import com.simibubi.create.foundation.block.IBE;
import dev.elcapi.bigengine.content.blockentity.FuelInjectorBlockEntity;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Fuel injector. Holds a small fuel tank; fill it with Create pipes or a bucket. */
public class FuelInjectorBlock extends EnginePortBlock implements IBE<FuelInjectorBlockEntity> {
    public FuelInjectorBlock(Properties properties) {
        super(properties, PortType.FUEL_INJECTOR);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return onBlockEntityUseItemOn(level, pos, be ->
                FluidUtil.interactWithFluidHandler(player, hand, be.getTank())
                        ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Class<FuelInjectorBlockEntity> getBlockEntityClass() {
        return FuelInjectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FuelInjectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.FUEL_INJECTOR.get();
    }
}
