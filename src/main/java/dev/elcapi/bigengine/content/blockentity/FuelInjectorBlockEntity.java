package dev.elcapi.bigengine.content.blockentity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import dev.elcapi.bigengine.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/** Small fuel tank inside every injector. Exposed as a fluid handler so Create pipes can feed it. */
public class FuelInjectorBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int CAPACITY = 1000;

    private final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.is(ModTags.ENGINE_FUEL)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide && level.getGameTime() % 10 == 0)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    };

    public FuelInjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_INJECTOR.get(), pos, state);
    }

    public FluidTank getTank() {
        return tank;
    }

    /** Burns up to {@code mb} millibuckets (fractional amounts accumulate). Returns the energy-weighted amount burnt. */
    private double fractional;

    public double burn(double mb) {
        if (tank.isEmpty() || mb <= 0) return 0;
        FluidStack fs = tank.getFluid();
        double energy = fs.is(ModTags.WEAK_ENGINE_FUEL) ? 0.5 : 1.0;
        fractional += mb;
        int whole = (int) fractional;
        if (whole > 0) {
            FluidStack drained = tank.drain(whole, IFluidHandler.FluidAction.EXECUTE);
            fractional -= whole;
            if (drained.getAmount() < whole) {
                double got = drained.getAmount() + 0.0;
                return got / whole * mb * energy;
            }
        }
        return mb * energy;
    }

    /** Energy multiplier of the stored fuel (0 if empty). */
    public double fuelEnergy() {
        if (tank.isEmpty()) return 0;
        return tank.getFluid().is(ModTags.WEAK_ENGINE_FUEL) ? 0.5 : 1.0;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("Frac", fractional);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        fractional = tag.getDouble("Frac");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) loadAdditional(pkt.getTag(), registries);
    }
}
