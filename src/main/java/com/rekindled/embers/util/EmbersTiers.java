package com.rekindled.embers.util;

import com.rekindled.embers.datagen.EmbersItemTags;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class EmbersTiers {

	public static final Tier LEAD = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 168, 6.0f, 2.0f, 4, () -> Ingredient.of(EmbersItemTags.LEAD_INGOT));
	public static final Tier TYRFING = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 512, 7.5f, 0.0f, 24, () -> Ingredient.of(EmbersItemTags.ASH_DUST));
	public static final Tier SILVER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 202, 7.6f, 2.0f, 20, () -> Ingredient.of(EmbersItemTags.SILVER_INGOT));
	public static final Tier DAWNSTONE = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 644, 7.5f, 2.5f, 18, () -> Ingredient.of(EmbersItemTags.DAWNSTONE_INGOT));
	public static final Tier CLOCKWORK_PICK = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031, 16.0F, 4.0F, 18, () -> Ingredient.EMPTY);
	public static final Tier CLOCKWORK_AXE = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031, 16.0F, 5.0F, 18, () -> Ingredient.EMPTY);
	public static final Tier CLOCKWORK_HAMMER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031, 6.0F, 6.0F, 18, () -> Ingredient.EMPTY);
}
