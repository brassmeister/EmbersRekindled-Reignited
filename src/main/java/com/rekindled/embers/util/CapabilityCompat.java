package com.rekindled.embers.util;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import com.rekindled.embers.compat.legacy.capabilities.ICapabilityProvider;
import com.rekindled.embers.compat.legacy.LazyOptional;
import com.rekindled.embers.research.capability.IResearchCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Bridges the original internal capability providers while the public API uses NeoForge capabilities.
 */
public final class CapabilityCompat {
	private static final ThreadLocal<Boolean> QUERYING_NEOFORGE = ThreadLocal.withInitial(() -> false);

	private CapabilityCompat() {
	}

	public static <T> LazyOptional<T> getCapability(@Nullable Object provider, Capability<T> capability) {
		return getCapability(provider, capability, null);
	}

	public static LazyOptional<IItemHandler> getItemHandler(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction side) {
		return getBlockCapability(level, pos, Capabilities.ItemHandler.BLOCK, side);
	}

	public static LazyOptional<IFluidHandler> getFluidHandler(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction side) {
		return getBlockCapability(level, pos, Capabilities.FluidHandler.BLOCK, side);
	}

	private static <T> LazyOptional<T> getBlockCapability(@Nullable Level level, @Nullable BlockPos pos,
			BlockCapability<T, Direction> capability, @Nullable Direction side) {
		if (level == null || pos == null || QUERYING_NEOFORGE.get()) {
			return LazyOptional.empty();
		}
		T value;
		try {
			QUERYING_NEOFORGE.set(true);
			value = level.getCapability(capability, pos, side);
		} finally {
			QUERYING_NEOFORGE.set(false);
		}
		if (value == null) {
			return LazyOptional.empty();
		}
		return LazyOptional.of(() -> value);
	}

	public static <T> LazyOptional<T> getCapability(@Nullable Object provider, Capability<T> capability, @Nullable Direction side) {
		if (provider == null) {
			return LazyOptional.empty();
		}
		if (provider instanceof Player player && capability == EmbersCapabilities.RESEARCH_CAPABILITY) {
			IResearchCapability research = player.getData(RegistryManager.RESEARCH_ATTACHMENT.get());
			return LazyOptional.of(() -> research).cast();
		}
		if (provider instanceof ICapabilityProvider capabilityProvider) {
			LazyOptional<T> optional = capabilityProvider.getCapability(capability, side);
			if (optional.isPresent()) {
				return optional;
			}
		}
		if (provider instanceof ItemStack stack) {
			try {
				Method method = stack.getItem().getClass().getMethod("initCapabilities", ItemStack.class, net.minecraft.nbt.CompoundTag.class);
				Object itemProvider = method.invoke(stack.getItem(), stack, null);
				if (itemProvider instanceof ICapabilityProvider capabilityProvider) {
					LazyOptional<T> optional = capabilityProvider.getCapability(capability, side);
					if (optional.isPresent()) {
						return optional;
					}
				}
			} catch (ReflectiveOperationException ignored) {
			}
		}
		try {
			Method method = provider.getClass().getMethod("getCapability", Capability.class, Direction.class);
			Object result = method.invoke(provider, capability, side);
			if (result instanceof LazyOptional<?> optional) {
				LazyOptional<T> cast = optional.cast();
				if (cast.isPresent()) {
					return cast;
				}
			}
		} catch (ReflectiveOperationException ignored) {
		}
		if (provider instanceof BlockEntity blockEntity
				&& capability == EmbersCapabilities.EMBER_CAPABILITY
				&& blockEntity.getLevel() != null
				&& !QUERYING_NEOFORGE.get()) {
			Object value;
			try {
				QUERYING_NEOFORGE.set(true);
				value = blockEntity.getLevel().getCapability(EmbersCapabilities.EMBER_BLOCK_CAPABILITY,
						blockEntity.getBlockPos(), side);
			} finally {
				QUERYING_NEOFORGE.set(false);
			}
			if (value != null) {
				Object captured = value;
				return LazyOptional.of(() -> (T) captured);
			}
		}
		if (provider instanceof BlockEntity blockEntity && blockEntity.getLevel() != null && !QUERYING_NEOFORGE.get()) {
			if (capability == ForgeCapabilities.FLUID_HANDLER) {
				return getFluidHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), side).cast();
			} else if (capability == ForgeCapabilities.ITEM_HANDLER) {
				return getItemHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), side).cast();
			}
		}
		return LazyOptional.empty();
	}
}
