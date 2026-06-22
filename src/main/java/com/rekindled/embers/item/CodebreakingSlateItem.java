package com.rekindled.embers.item;

import com.rekindled.embers.util.ItemData;

import com.rekindled.embers.gui.SlateMenu;
import com.rekindled.embers.util.Misc;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CodebreakingSlateItem extends Item {

	public CodebreakingSlateItem(Properties pProperties) {
		super(pProperties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide) {
			ItemStack slate = player.getItemInHand(hand);
			//give players their waste back if they have a legacy slate
			ItemStackHandler inventory = new ItemStackHandler(7) {
				public void onContentsChanged(int slot) {
					ItemData.updateTag(slate, tag -> tag.put("inventory", this.serializeNBT(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))));
				}
			};
			CompoundTag nbt = ItemData.getTagElement(slate, "inventory");
			if (nbt != null && !nbt.isEmpty())
				inventory.deserializeNBT(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), nbt);
			Misc.giveItemToPlayer(inventory.getStackInSlot(5), player);
			inventory.setStackInSlot(5, ItemStack.EMPTY);
			Misc.giveItemToPlayer(inventory.getStackInSlot(6), player);
			inventory.setStackInSlot(6, ItemStack.EMPTY);

			SimpleMenuProvider provider = new SimpleMenuProvider((id, playerInventory, ignored) -> new SlateMenu(id, playerInventory, slate), getDescription());
			((ServerPlayer) player).openMenu(provider, buf -> ItemStack.STREAM_CODEC.encode(buf, slate));
		}
		return InteractionResultHolder.success(player.getItemInHand(hand));
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
	}

}
