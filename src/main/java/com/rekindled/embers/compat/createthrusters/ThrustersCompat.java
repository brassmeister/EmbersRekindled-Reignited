package com.rekindled.embers.compat.createthrusters;

import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.Embers;
import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.EmbersAPI;
import com.rekindled.embers.item.AshenArmorItem;
import com.rekindled.embers.item.MixedGogglesItem;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ThrustersCompat {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Embers.MODID);

	public static final DeferredItem<MixedGogglesItem> PHYSICS_ASHEN_GOGGLES = ITEMS.register("physics_ashen_goggles",
			() -> new MixedGogglesItem(new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(AshenArmorItem.DURABILITY_MULTIPLIER)), ConfigManager.ASHEN_GOGGLES_SLOTS));

	private ThrustersCompat() {
	}

	public static void init(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
		modEventBus.addListener(ThrustersCompat::commonSetup);
		modEventBus.addListener(ThrustersCompat::addCreativeTabItem);
	}

	public static boolean isPhysicsAshenGoggles(ItemStack stack) {
		return !stack.isEmpty() && stack.is(PHYSICS_ASHEN_GOGGLES.get());
	}

	public static boolean isWearingPhysicsAshenGoggles(net.minecraft.world.entity.player.Player player) {
		return isPhysicsAshenGoggles(player.getItemBySlot(EquipmentSlot.HEAD));
	}

	private static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			EmbersAPI.registerWearableLens(Ingredient.of(PHYSICS_ASHEN_GOGGLES.get()));
			EmbersAPI.registerEmberResonance(Ingredient.of(PHYSICS_ASHEN_GOGGLES.get()), 2.0);
		});
	}

	private static void addCreativeTabItem(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(RegistryManager.EMBERS_TAB.getKey())) {
			event.accept(PHYSICS_ASHEN_GOGGLES.get());
		}
	}
}
