package com.rekindled.embers.item;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.util.EmberInventoryUtil;
import com.rekindled.embers.util.ItemData;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DawnstoneToolUtil {

	private DawnstoneToolUtil() {
	}

	public static <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
		return usesDurability() ? amount : 0;
	}

	public static boolean hasEmber(ItemStack stack) {
		return usesDurability() || ItemData.hasTag(stack) && ItemData.getTag(stack).getBoolean("poweredOn");
	}

	public static void markUsed(ItemStack stack) {
		if (usesDurability()) {
			return;
		}
		ItemData.updateTag(stack, tag -> tag.putBoolean("didUse", true));
	}

	public static boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		if (usesDurability()) {
			return slotChanged || newStack.getItem() != oldStack.getItem();
		}
		if (ItemData.hasTag(oldStack) && ItemData.hasTag(newStack)) {
			return slotChanged
					|| ItemData.getTag(oldStack).getBoolean("poweredOn") != ItemData.getTag(newStack).getBoolean("poweredOn")
					|| newStack.getItem() != oldStack.getItem();
		}
		return slotChanged || newStack.getItem() != oldStack.getItem();
	}

	public static boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
		if (usesDurability()) {
			return false;
		}
		if (ItemData.hasTag(oldStack) && ItemData.hasTag(newStack)) {
			return ItemData.getTag(oldStack).getBoolean("poweredOn") != ItemData.getTag(newStack).getBoolean("poweredOn");
		}
		return false;
	}

	public static void inventoryTick(ItemStack stack, Level world, Entity entity, boolean selected) {
		if (usesDurability() || !selected || world.isClientSide()) {
			return;
		}
		if (!ItemData.hasTag(stack)) {
			ItemData.updateTag(stack, tag -> {
				tag.putBoolean("poweredOn", false);
				tag.putBoolean("didUse", false);
			});
			return;
		}
		if (!(entity instanceof Player player)) {
			return;
		}
		if (world.getGameTime() % 5 == 0) {
			boolean poweredOn = EmberInventoryUtil.getEmberTotal(player) > 5.0;
			if (ItemData.getTag(stack).getBoolean("poweredOn") != poweredOn) {
				ItemData.updateTag(stack, tag -> tag.putBoolean("poweredOn", poweredOn));
			}
		}
		if (ItemData.getTag(stack).getBoolean("didUse")) {
			EmberInventoryUtil.removeEmber(player, 5.0);
			boolean poweredOn = EmberInventoryUtil.getEmberTotal(player) >= 5.0;
			ItemData.updateTag(stack, tag -> {
				tag.putBoolean("didUse", false);
				if (!poweredOn) {
					tag.putBoolean("poweredOn", false);
				}
			});
		}
	}

	private static boolean usesDurability() {
		return ConfigManager.DAWNSTONE_TOOLS_USE_DURABILITY != null && ConfigManager.DAWNSTONE_TOOLS_USE_DURABILITY.get();
	}
}
