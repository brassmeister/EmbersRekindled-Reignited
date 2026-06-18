package com.rekindled.embers.item;

import java.util.List;

import com.rekindled.embers.Embers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class AshenArmorItem extends ArmorItem {

	public static final int DURABILITY_MULTIPLIER = 33;

	public AshenArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public void setDamage(ItemStack stack, int damage) {
		int maxDamage = getMaxDamage(stack);
		super.setDamage(stack, maxDamage > 0 ? Math.min(damage, maxDamage - 1) : damage);
	}

	public boolean isBroken(ItemStack armor) {
		return armor.getDamageValue() >= armor.getMaxDamage() - 1;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag isAdvanced) {
		super.appendHoverText(stack, context, tooltip, isAdvanced);
		if (isBroken(stack))
			tooltip.add(Component.translatable(Embers.MODID + ".tooltip.broken").withStyle(ChatFormatting.GRAY));
	}

}
