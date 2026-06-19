package com.rekindled.embers.blockentity;

import com.rekindled.embers.RegistryManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import com.rekindled.embers.compat.legacy.LazyOptional;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ItemDropperBlockEntity extends BlockEntity implements IItemPipePriority {

	public ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			ItemDropperBlockEntity.this.setChanged();
		}
	};
	public LazyOptional<IItemHandler> holder = LazyOptional.of(() -> inventory);

	public ItemDropperBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.ITEM_DROPPER_ENTITY.get(), pPos, pBlockState);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		inventory.deserializeNBT(registries, nbt.getCompound("inventory"));
	}

	@Override
	public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.put("inventory", inventory.serializeNBT(registries));
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ItemDropperBlockEntity blockEntity) {
		if (!blockEntity.inventory.getStackInSlot(0).isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, pos.getX()+0.5, pos.getY()+0.25, pos.getZ()+0.5, blockEntity.inventory.extractItem(0, 1, false), 0, -0.1, 0));
		}
	}

	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (!this.isRemoved() && (side == null || side == Direction.UP) && cap == ForgeCapabilities.ITEM_HANDLER) {
			return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, holder);
		}
		return LazyOptional.empty();
	}

	public void invalidateCaps() {
		
		holder.invalidate();
	}

	@Override
	public int getPriority(Direction facing) {
		return 50;
	}
}
