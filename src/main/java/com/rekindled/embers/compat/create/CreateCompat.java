package com.rekindled.embers.compat.create;

import com.rekindled.embers.Embers;
import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.EmbersAPI;
import com.rekindled.embers.compat.createthrusters.ThrustersCompat;
import com.rekindled.embers.item.AshenArmorItem;
import com.rekindled.embers.item.MixedGogglesItem;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;

public final class CreateCompat {
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Embers.MODID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Embers.MODID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Embers.MODID);

	public static final DeferredBlock<EmberKineticGeneratorBlock> EMBER_KINETIC_GENERATOR = BLOCKS.register(
			"ember_kinetic_generator",
			() -> new EmberKineticGeneratorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_YELLOW)
					.strength(1.6f)
					.requiresCorrectToolForDrops()
					.noOcclusion()));
	public static final DeferredItem<BlockItem> EMBER_KINETIC_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem(EMBER_KINETIC_GENERATOR);
	public static final DeferredItem<MixedGogglesItem> ENGINEERS_ASHEN_GOGGLES = ITEMS.register("engineers_ashen_goggles",
			() -> new MixedGogglesItem(new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(AshenArmorItem.DURABILITY_MULTIPLIER)), ConfigManager.ASHEN_GOGGLES_SLOTS));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EmberKineticGeneratorBlockEntity>> EMBER_KINETIC_GENERATOR_ENTITY =
			BLOCK_ENTITIES.register("ember_kinetic_generator",
					() -> BlockEntityType.Builder.of(EmberKineticGeneratorBlockEntity::new, EMBER_KINETIC_GENERATOR.get()).build(null));

	private CreateCompat() {
	}

	public static void init(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		BLOCK_ENTITIES.register(modEventBus);
		modEventBus.addListener(CreateCompat::commonSetup);
		modEventBus.addListener(CreateCompat::addCreativeTabItem);
		modEventBus.addListener(CreateCompat::registerCapabilities);
		if (FMLEnvironment.dist.isClient()) {
			CreateCompatClient.init(modEventBus);
		}
	}

	private static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			GogglesItem.addIsWearingPredicate(player -> {
				if (player.getItemBySlot(EquipmentSlot.HEAD).is(ENGINEERS_ASHEN_GOGGLES.get())) {
					return true;
				}
				return ModList.get().isLoaded("createthrusters")
						&& ThrustersCompat.isPhysicsAshenGoggles(player.getItemBySlot(EquipmentSlot.HEAD));
			});
			EmbersAPI.registerWearableLens(Ingredient.of(ENGINEERS_ASHEN_GOGGLES.get()));
			EmbersAPI.registerEmberResonance(Ingredient.of(ENGINEERS_ASHEN_GOGGLES.get()), 2.0);
		});
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(EmbersCapabilities.EMBER_BLOCK_CAPABILITY, EMBER_KINETIC_GENERATOR_ENTITY.get(),
				(blockEntity, side) -> blockEntity.getEmberCapability());
		BuiltInRegistries.BLOCK_ENTITY_TYPE
				.getOptional(ResourceLocation.fromNamespaceAndPath("create", "blaze_heater"))
				.ifPresent(type -> event.registerBlockEntity(EmbersCapabilities.EMBER_BLOCK_CAPABILITY, type,
						(blockEntity, side) -> blockEntity instanceof EmberFueledBlazeBurner emberBurner
								? emberBurner.embers$getEmberFuel()
								: null));
	}

	private static void addCreativeTabItem(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(RegistryManager.EMBERS_TAB.getKey())) {
			event.accept(EMBER_KINETIC_GENERATOR_ITEM.get());
			event.accept(ENGINEERS_ASHEN_GOGGLES.get());
		}
	}
}
