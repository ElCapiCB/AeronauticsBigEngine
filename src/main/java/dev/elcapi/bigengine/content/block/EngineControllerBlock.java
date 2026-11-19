package dev.elcapi.bigengine.content.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.elcapi.bigengine.client.ClientHooks;
import dev.elcapi.bigengine.content.blockentity.EngineControllerBlockEntity;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Engine Control Unit. Place it touching any block of the crank line. Right-click to open the tuning screen.
 * An analog redstone signal can drive the throttle.
 */
public class EngineControllerBlock extends HorizontalDirectionalBlock implements IBE<EngineControllerBlockEntity>, IWrenchable {
    public EngineControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(EngineControllerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) ClientHooks.openControllerScreen(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.is(state.getBlock()) && level.getBlockEntity(pos) instanceof EngineControllerBlockEntity ecu)
            ecu.onControllerRemoved();
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        if (level.isClientSide || type != ModBlockEntities.ENGINE_CONTROLLER.get()) return null;
        return (l, p, s, be) -> ((EngineControllerBlockEntity) be).serverTick();
    }

    @Override
    public Class<EngineControllerBlockEntity> getBlockEntityClass() {
        return EngineControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends EngineControllerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ENGINE_CONTROLLER.get();
    }
}
