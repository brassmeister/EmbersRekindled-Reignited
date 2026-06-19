package com.rekindled.embers.blockentity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.block.MechEdgeBlockBase;
import com.rekindled.embers.datagen.EmbersBlockTags;
import com.rekindled.embers.util.CapabilityCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import com.rekindled.embers.compat.legacy.LazyOptional;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CaminiteValveBlockEntity extends BlockEntity {

	int ticksExisted = 0;
	ReservoirBlockEntity reservoir;
	IFluidHandler fluidHandler;
	private final LazyOptional<IFluidHandler> holder = LazyOptional.of(() -> fluidHandler);

	public CaminiteValveBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.CAMINITE_VALVE_ENTITY.get(), pPos, pBlockState);
		fluidHandler = new IFluidHandler() {

			@Override
			public int getTanks() {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.getTanks();
				return 0;
			}

			@Override
			public @NotNull FluidStack getFluidInTank(int tank) {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.getFluidInTank(tank);
				return FluidStack.EMPTY;
			}

			@Override
			public int getTankCapacity(int tank) {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.getTankCapacity(tank);
				return 0;
			}

			@Override
			public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.isFluidValid(tank, stack);
				return false;
			}

			@Override
			public int fill(FluidStack resource, FluidAction action) {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.fill(resource, action);
				return 0;
			}

			@Override
			public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.drain(resource, action);
				return FluidStack.EMPTY;
			}

			@Override
			public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
				IFluidHandler handler = getReservoirHandler();
				if (handler != null)
					return handler.drain(maxDrain, action);
				return FluidStack.EMPTY;
			}
		};
	}

	public ReservoirBlockEntity getReservoir() {
		return reservoir;
	}

	private @Nullable IFluidHandler getReservoirHandler() {
		if (reservoir == null || reservoir.isRemoved())
			return null;
		return CapabilityCompat.getCapability(reservoir, ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
	}

	public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction facing) {
		if (!this.isRemoved() && capability == ForgeCapabilities.FLUID_HANDLER && (facing == null || facing.getAxis() != Direction.Axis.Y))
			return holder.cast();
		return LazyOptional.empty();
	}

	public void updateTank() {
		if (isRemoved() || !level.getBlockState(worldPosition).hasProperty(MechEdgeBlockBase.EDGE))
			return;
		reservoir = null;
		BlockPos basePos = worldPosition.offset(level.getBlockState(worldPosition).getValue(MechEdgeBlockBase.EDGE).centerPos);
		for (int i = 1; i < 64; i++) {
			BlockPos pos = basePos.below(i);
			if (!level.getBlockState(pos).is(EmbersBlockTags.RESERVOIR_EXPANSION)) {
				BlockEntity tile = level.getBlockEntity(pos);
				if (tile instanceof ReservoirBlockEntity) {
					reservoir = (ReservoirBlockEntity) tile;
				}
				break;
			}
		}
	}

	public static void commonTick(Level level, BlockPos pos, BlockState state, CaminiteValveBlockEntity blockEntity) {
		blockEntity.ticksExisted++;
		if (blockEntity.reservoir != null && blockEntity.reservoir.isRemoved())
			blockEntity.reservoir = null;
		if (blockEntity.ticksExisted % 20 == 0)
			blockEntity.updateTank();
	}
}
