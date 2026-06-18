package com.rekindled.embers.blockentity;

import java.util.List;

import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.Embers;
import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.tile.IExtraCapabilityInformation;
import com.rekindled.embers.util.Misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import com.rekindled.embers.compat.legacy.LazyOptional;
import com.rekindled.embers.compat.sublevel.SubLevelCompat;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ItemVacuumBlockEntity extends BlockEntity implements IExtraCapabilityInformation {
	private static final double SUCTION_RANGE = 7.0D;
	private static final double MAX_PULL_SPEED = 0.45D;

	public ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			ItemVacuumBlockEntity.this.setChanged();
		}
	};
	private final IItemHandler outputSide = Misc.makeRestrictedItemHandler(inventory, false, true);
	public LazyOptional<IItemHandler> holder = LazyOptional.of(() -> outputSide);

	public ItemVacuumBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.ITEM_VACUUM_ENTITY.get(), pPos, pBlockState);
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

	public static void tick(Level level, BlockPos pos, BlockState state, ItemVacuumBlockEntity blockEntity) {
		if (level.isClientSide || !ConfigManager.isRedstoneControlActive(level, pos)) {
			return;
		}

		Direction facing = state.getValue(BlockStateProperties.FACING);
		BlockEntity tile = SubLevelCompat.findAdjacent(blockEntity, facing.getOpposite());
		IItemHandler attachedInventory = tile == null ? null : com.rekindled.embers.util.CapabilityCompat.getCapability(tile, ForgeCapabilities.ITEM_HANDLER, facing).orElse(null);

		if (!blockEntity.inventory.getStackInSlot(0).isEmpty() && attachedInventory != null) {
			blockEntity.inventory.setStackInSlot(0, insertIntoHandler(blockEntity.inventory.getStackInSlot(0), attachedInventory));
		}

		Vec3 vacuumCenter = SubLevelCompat.toPhysicalPosition(blockEntity, Vec3.atCenterOf(pos));
		Vec3 suckDirection = SubLevelCompat.toPhysicalDirection(blockEntity,
				new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
		if (suckDirection == null || suckDirection.lengthSqr() < 1.0E-8D) {
			suckDirection = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
		}
		suckDirection = suckDirection.normalize();

		Vec3 intakeCenter = vacuumCenter.add(suckDirection.scale(0.85D));
		AABB suckBB = centeredBox(vacuumCenter, SUCTION_RANGE * 2.0D, SUCTION_RANGE * 2.0D, SUCTION_RANGE * 2.0D);
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, suckBB, entity -> !entity.isRemoved() && canAcceptCollectedItem(entity.getItem(), blockEntity.inventory, attachedInventory));
		for (ItemEntity item : items) {
			Vec3 toIntake = intakeCenter.subtract(item.position());
			double distance = toIntake.length();
			if (distance > 1.0E-4D) {
				Vec3 pull = toIntake.scale(Math.min(MAX_PULL_SPEED, 0.12D + 0.9D / (distance + 0.5D)) / distance);
				Vec3 dampedMotion = item.getDeltaMovement().scale(0.35D).add(pull);
				item.setDeltaMovement(dampedMotion);
			}
			item.hasImpulse = true;

			if (item.getBoundingBox().inflate(0.1D).intersects(centeredBox(intakeCenter, 1.25D, 1.25D, 1.25D))) {
				ItemStack remainder = storeCollectedItem(item.getItem(), blockEntity.inventory, attachedInventory);
				item.setItem(remainder);
				if (item.getItem().isEmpty()) {
					item.discard();
				} else {
					item.setDeltaMovement(Vec3.ZERO);
				}
			}
		}

		if (!blockEntity.inventory.getStackInSlot(0).isEmpty() && attachedInventory != null) {
			blockEntity.inventory.setStackInSlot(0, insertIntoHandler(blockEntity.inventory.getStackInSlot(0), attachedInventory));
		}
	}

	private static AABB centeredBox(Vec3 center, double xSize, double ySize, double zSize) {
		double halfX = xSize * 0.5D;
		double halfY = ySize * 0.5D;
		double halfZ = zSize * 0.5D;
		return new AABB(center.x - halfX, center.y - halfY, center.z - halfZ, center.x + halfX, center.y + halfY, center.z + halfZ);
	}

	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		Direction facing = level.getBlockState(worldPosition).getValue(BlockStateProperties.FACING);
		if (!this.isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER && side == facing.getOpposite()) {
			return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, holder);
		}
		return LazyOptional.empty();
	}

	public void invalidateCaps() {
		
		holder.invalidate();
	}

	private static boolean canAcceptIntoVacuum(ItemStack stack, ItemStackHandler inventory) {
		return getVacuumInsertedRemainder(stack, inventory).getCount() < stack.getCount();
	}

	private static boolean canAcceptCollectedItem(ItemStack stack, ItemStackHandler inventory, IItemHandler attachedInventory) {
		if (stack.isEmpty()) {
			return false;
		}
		ItemStack remainder = stack;
		if (attachedInventory != null) {
			remainder = insertIntoHandler(remainder, attachedInventory, true);
		}
		if (remainder.isEmpty()) {
			return true;
		}
		return canAcceptIntoVacuum(remainder, inventory);
	}

	private static ItemStack insertIntoVacuum(ItemStack stack, ItemStackHandler inventory) {
		return getVacuumInsertedRemainder(stack, inventory, false);
	}

	private static ItemStack getVacuumInsertedRemainder(ItemStack stack, ItemStackHandler inventory) {
		return getVacuumInsertedRemainder(stack, inventory, true);
	}

	private static ItemStack getVacuumInsertedRemainder(ItemStack stack, ItemStackHandler inventory, boolean simulate) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack existing = inventory.getStackInSlot(0);
		if (existing.isEmpty()) {
			int inserted = Math.min(stack.getCount(), inventory.getSlotLimit(0));
			if (!simulate) {
				ItemStack stored = stack.copyWithCount(inserted);
				inventory.setStackInSlot(0, stored);
			}
			return stack.copyWithCount(stack.getCount() - inserted);
		}
		if (!ItemStack.isSameItemSameComponents(existing, stack)) {
			return stack;
		}
		int room = Math.min(inventory.getSlotLimit(0), existing.getMaxStackSize()) - existing.getCount();
		if (room <= 0) {
			return stack;
		}
		int inserted = Math.min(stack.getCount(), room);
		if (!simulate) {
			ItemStack stored = existing.copyWithCount(existing.getCount() + inserted);
			inventory.setStackInSlot(0, stored);
		}
		return stack.copyWithCount(stack.getCount() - inserted);
	}

	private static ItemStack insertIntoHandler(ItemStack stack, IItemHandler inventory) {
		return insertIntoHandler(stack, inventory, false);
	}

	private static ItemStack insertIntoHandler(ItemStack stack, IItemHandler inventory, boolean simulate) {
		ItemStack remainder = stack;
		for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
			remainder = inventory.insertItem(slot, remainder, simulate);
		}
		return remainder;
	}

	private static ItemStack storeCollectedItem(ItemStack stack, ItemStackHandler inventory, IItemHandler attachedInventory) {
		ItemStack remainder = stack;
		if (attachedInventory != null) {
			remainder = insertIntoHandler(remainder, attachedInventory);
		}
		if (!remainder.isEmpty()) {
			remainder = insertIntoVacuum(remainder, inventory);
		}
		return remainder;
	}

	@Override
	public void addOtherDescription(List<Component> strings, Direction facing) {
		strings.add(Component.translatable(Embers.MODID + ".tooltip.goggles.redstone_signal"));
	}
}
