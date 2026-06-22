package com.rekindled.embers.api.item;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface IInflictorGem {
	void attuneSource(ItemStack stack, @Nullable LivingEntity entity, DamageSource source);

	@Nullable
	String getAttunedSource(ItemStack stack);

	default boolean matchesSource(ItemStack stack, DamageSource source) {
		return Objects.equals(getAttunedSource(stack), source.type().msgId());
	}

	float getDamageResistance(ItemStack stack, float modifier);
}
