package com.rekindled.embers.util;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemData {

	private static final RegistryAccess.Frozen BUILTIN_REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

	private ItemData() {
	}

	public static boolean hasTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && !data.isEmpty();
	}

	@Nullable
	@SuppressWarnings("deprecation")
	public static CompoundTag getTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.getUnsafe();
	}

	@SuppressWarnings("deprecation")
	public static CompoundTag getOrCreateTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
			data = stack.get(DataComponents.CUSTOM_DATA);
		}
		return data.getUnsafe();
	}

	@Nullable
	public static CompoundTag getTagElement(ItemStack stack, String key) {
		CompoundTag tag = getTag(stack);
		return tag != null && tag.contains(key) ? tag.getCompound(key) : null;
	}

	public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
		CompoundTag tag = getOrCreateTag(stack);
		if (!tag.contains(key))
			tag.put(key, new CompoundTag());
		return tag.getCompound(key);
	}

	public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
		if (tag == null) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	@SuppressWarnings("deprecation")
	public static void updateTag(ItemStack stack, Consumer<CompoundTag> updater) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.getUnsafe().copy();
		updater.accept(tag);
		setTag(stack, tag.isEmpty() ? null : tag);
	}

	public static void updateTagElement(ItemStack stack, String key, Consumer<CompoundTag> updater) {
		updateTag(stack, tag -> {
			if (!tag.contains(key))
				tag.put(key, new CompoundTag());
			updater.accept(tag.getCompound(key));
		});
	}

	public static CompoundTag save(ItemStack stack) {
		return (CompoundTag) stack.save(BUILTIN_REGISTRIES);
	}

	public static ItemStack parse(CompoundTag tag) {
		return ItemStack.parseOptional(BUILTIN_REGISTRIES, tag);
	}
}
