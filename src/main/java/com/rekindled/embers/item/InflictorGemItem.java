package com.rekindled.embers.item;

import com.rekindled.embers.util.ItemData;

import java.util.List;

import com.rekindled.embers.Embers;
import com.rekindled.embers.api.item.IInflictorGem;
import com.rekindled.embers.datagen.EmbersDamageTypeTags;
import com.rekindled.embers.datagen.EmbersSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class InflictorGemItem extends Item implements IInflictorGem {

	public InflictorGemItem(Properties pProperties) {
		super(pProperties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide && player.isSecondaryUseActive() && ItemData.getTag(stack) != null && ItemData.getTag(stack).contains("type")) {
			ItemData.updateTag(stack, tag -> {
				tag.remove("type");
				tag.remove("damage_type");
			});
			if (player.getHealth() > 1f)
				player.setHealth(Math.max(player.getHealth() - 10.0f, 1f));
		}
		return player.isSecondaryUseActive() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag isAdvanced) {
		super.appendHoverText(stack, context, tooltip, isAdvanced);
		if (ItemData.getTag(stack) != null && ItemData.getTag(stack).contains("type")) {
			tooltip.add(Component.translatable(Embers.MODID + ".tooltip.inflictor", ItemData.getTag(stack).getString("type")).withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable(Embers.MODID + ".tooltip.inflictor.none").withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public void attuneSource(ItemStack stack, LivingEntity entity, DamageSource source) {
		if (!source.is(EmbersDamageTypeTags.INFLICTOR_GEM_BLACKLIST)) {
			ItemData.updateTag(stack, tag -> {
				tag.putString("type", source.type().msgId());
				tag.remove("damage_type");
				source.typeHolder().unwrapKey().ifPresent(key -> tag.putString("damage_type", key.location().toString()));
			});
			if (entity != null)
				entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), EmbersSounds.INFLICTOR_GEM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
		}
	}

	@Override
	public String getAttunedSource(ItemStack stack) {
		if (!stack.isEmpty() && ItemData.getTag(stack) != null && ItemData.getTag(stack).contains("type"))
			return ItemData.getTag(stack).getString("type");
		return null;
	}

	@Override
	public boolean matchesSource(ItemStack stack, DamageSource source) {
		if (stack.isEmpty() || ItemData.getTag(stack) == null) {
			return false;
		}
		String damageType = ItemData.getTag(stack).getString("damage_type");
		if (!damageType.isEmpty()) {
			return source.typeHolder().unwrapKey()
					.map(key -> key.location().toString().equals(damageType))
					.orElse(false);
		}
		return IInflictorGem.super.matchesSource(stack, source);
	}

	@Override
	public float getDamageResistance(ItemStack stack, float modifier) {
		return 0.35f;
	}
}
