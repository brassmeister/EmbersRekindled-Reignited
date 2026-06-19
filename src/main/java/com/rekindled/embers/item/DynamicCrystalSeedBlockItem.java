package com.rekindled.embers.item;

import java.util.List;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.util.DynamicMetalSeeds;
import com.rekindled.embers.util.ItemData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class DynamicCrystalSeedBlockItem extends BlockItem {

	public static final String METAL_KEY = "metal";
	public static final String COLOR_KEY = "color";

	public DynamicCrystalSeedBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static ItemStack withMetal(String metal, int color) {
		ItemStack stack = new ItemStack(RegistryManager.DYNAMIC_CRYSTAL_SEED_ITEM.get());
		CompoundTag tag = new CompoundTag();
		tag.putString(METAL_KEY, DynamicMetalSeeds.normalizeMetal(metal));
		tag.putInt(COLOR_KEY, color & 0xFFFFFF);
		ItemData.setTag(stack, tag);
		return stack;
	}

	public static String getMetal(ItemStack stack) {
		CompoundTag tag = ItemData.getTag(stack);
		if (!hasMetal(stack)) {
			return DynamicMetalSeeds.DEFAULT_METAL;
		}
		return DynamicMetalSeeds.normalizeMetal(tag.getString(METAL_KEY));
	}

	public static boolean hasMetal(ItemStack stack) {
		CompoundTag tag = ItemData.getTag(stack);
		return tag != null && tag.contains(METAL_KEY);
	}

	public static int getColor(ItemStack stack) {
		CompoundTag tag = ItemData.getTag(stack);
		if (tag == null || !tag.contains(COLOR_KEY)) {
			return DynamicMetalSeeds.DEFAULT_COLOR;
		}
		return tag.getInt(COLOR_KEY) & 0xFFFFFF;
	}

	@Override
	public Component getName(ItemStack stack) {
		if (!hasMetal(stack)) {
			return Component.literal("Dynamic Crystal Seed");
		}
		return Component.literal(DynamicMetalSeeds.displayName(getMetal(stack)) + " Crystal Seed");
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		if (hasMetal(stack)) {
			DynamicMetalSeeds.getVariant(getMetal(stack))
					.ifPresent(variant -> tooltip.add(Component.literal("From: " + variant.modName()).withStyle(ChatFormatting.GRAY)));
		}
	}
}
