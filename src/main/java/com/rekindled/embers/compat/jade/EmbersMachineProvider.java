package com.rekindled.embers.compat.jade;

import com.rekindled.embers.Embers;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.api.power.IEmberCapability;
import com.rekindled.embers.block.MechEdgeBlockBase;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import com.rekindled.embers.util.CapabilityCompat;
import com.rekindled.embers.util.ItemData;

import java.util.LinkedHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.ProgressStyle;

public enum EmbersMachineProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final String DATA = "EmbersMachineData";
	private static final String EMBER = "Ember";
	private static final String EMBER_AMOUNT = "Amount";
	private static final String EMBER_CAPACITY = "Capacity";
	private static final String EMBER_VOLATILE = "Volatile";
	private static final String ENERGY = "Energy";
	private static final String ENERGY_STORED = "Stored";
	private static final String ENERGY_CAPACITY = "Capacity";
	private static final String ITEMS = "Items";
	private static final String ITEM_SLOTS = "Slots";
	private static final String ITEM_FILLED = "Filled";
	private static final String ITEM_COUNT = "Count";
	private static final String ITEM_ENTRIES = "Entries";
	private static final String ITEM_SLOT = "Slot";
	private static final String ITEM_STACK = "Stack";
	private static final String FLUIDS = "Fluids";
	private static final String FLUID_ENTRIES = "Entries";
	private static final String FLUID_TANKS = "Tanks";
	private static final String FLUID_FILLED = "Filled";
	private static final String FLUID_TANK = "Tank";
	private static final String FLUID_AMOUNT = "Amount";
	private static final String FLUID_CAPACITY = "Capacity";
	private static final String FLUID_STACK = "Stack";
	private static final int EMBER_BAR_START = 0xFFFF7A00;
	private static final int EMBER_BAR_END = 0xFFD94A00;
	private static final int EMBER_BAR_BACKGROUND = 0xFF32170A;
	private static final int EMBER_BAR_BORDER = 0xFF6B3218;

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!accessor.getServerData().contains(DATA, Tag.TAG_COMPOUND)) {
			return;
		}

		CompoundTag data = accessor.getServerData().getCompound(DATA);
		appendEmber(tooltip, data);
		appendEnergy(tooltip, data);
		appendItems(tooltip, accessor, data);
		appendFluids(tooltip, accessor, data);
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		if (!isEmbersBlock(accessor.getBlock())) {
			return;
		}
		BlockEntity blockEntity = getPrimaryBlockEntity(accessor);
		if (blockEntity == null) {
			return;
		}

		CompoundTag embersData = new CompoundTag();
		appendEmberData(embersData, accessor, blockEntity);
		appendEnergyData(embersData, accessor, blockEntity);
		appendItemData(embersData, accessor, blockEntity);
		appendFluidData(embersData, accessor, blockEntity);
		if (!embersData.isEmpty()) {
			data.put(DATA, embersData);
		}
	}

	@Override
	public boolean shouldRequestData(BlockAccessor accessor) {
		return isEmbersBlock(accessor.getBlock());
	}

	@Override
	public ResourceLocation getUid() {
		return EmbersJadePlugin.MACHINE_DETAILS;
	}

	private static void appendEmber(ITooltip tooltip, CompoundTag data) {
		if (!data.contains(EMBER, Tag.TAG_COMPOUND)) {
			return;
		}
		CompoundTag ember = data.getCompound(EMBER);
		double amount = ember.getDouble(EMBER_AMOUNT);
		double capacity = ember.getDouble(EMBER_CAPACITY);
		float progress = capacity > 0 ? (float) Math.max(0, Math.min(1, amount / capacity)) : 0;
		Component label = Component.translatable(Embers.MODID + ".jade.ember", format(amount), format(capacity))
				.withStyle(ChatFormatting.WHITE);

		IElementHelper elements = IElementHelper.get();
		ProgressStyle progressStyle = elements.progressStyle()
				.color(EMBER_BAR_START, EMBER_BAR_END)
				.textColor(0xFFFFFFFF);
		BoxStyle.GradientBorder boxStyle = BoxStyle.GradientBorder.DEFAULT_NESTED_BOX.clone();
		boxStyle.bgColor = EMBER_BAR_BACKGROUND;
		boxStyle.borderColor = new int[]{EMBER_BAR_BORDER, EMBER_BAR_BORDER, EMBER_BAR_BORDER, EMBER_BAR_BORDER};
		boxStyle.roundCorner = Boolean.FALSE;
		tooltip.add(elements.progress(progress, label, progressStyle, boxStyle, true));
		if (ember.getBoolean(EMBER_VOLATILE)) {
			tooltip.add(Component.translatable(Embers.MODID + ".jade.ember.volatile").withStyle(ChatFormatting.WHITE));
		}
	}

	private static void appendEnergy(ITooltip tooltip, CompoundTag data) {
		if (!data.contains(ENERGY, Tag.TAG_COMPOUND)) {
			return;
		}
		CompoundTag energy = data.getCompound(ENERGY);
		tooltip.add(Component.translatable(Embers.MODID + ".jade.energy",
				energy.getInt(ENERGY_STORED),
				energy.getInt(ENERGY_CAPACITY)));
	}

	private static void appendItems(ITooltip tooltip, BlockAccessor accessor, CompoundTag data) {
		if (!data.contains(ITEMS, Tag.TAG_COMPOUND)) {
			return;
		}
		CompoundTag items = data.getCompound(ITEMS);
		int filledSlots = items.getInt(ITEM_FILLED);
		int slots = items.getInt(ITEM_SLOTS);
		int totalCount = items.getInt(ITEM_COUNT);
		tooltip.add(Component.translatable(Embers.MODID + ".jade.items", filledSlots, slots, totalCount));
		if (!accessor.showDetails() || !items.contains(ITEM_ENTRIES, Tag.TAG_LIST)) {
			return;
		}

		IElementHelper elements = IElementHelper.get();
		ListTag entries = items.getList(ITEM_ENTRIES, Tag.TAG_COMPOUND);
		for (int i = 0; i < entries.size(); i++) {
			CompoundTag entry = entries.getCompound(i);
			ItemStack stack = ItemData.parse(entry.getCompound(ITEM_STACK));
			if (stack.isEmpty()) {
				continue;
			}
			IElement icon = elements.smallItem(stack).message(null);
			tooltip.add(icon);
			tooltip.append(Component.translatable(Embers.MODID + ".jade.item",
					entry.getInt(ITEM_SLOT) + 1,
					stack.getHoverName(),
					stack.getCount()));
		}
	}

	private static void appendFluids(ITooltip tooltip, BlockAccessor accessor, CompoundTag data) {
		if (!data.contains(FLUIDS, Tag.TAG_COMPOUND)) {
			return;
		}
		CompoundTag fluids = data.getCompound(FLUIDS);
		if (!fluids.contains(FLUID_ENTRIES, Tag.TAG_LIST)) {
			return;
		}

		ListTag entries = fluids.getList(FLUID_ENTRIES, Tag.TAG_COMPOUND);
		tooltip.add(Component.translatable(Embers.MODID + ".jade.fluids", fluids.getInt(FLUID_FILLED), fluids.getInt(FLUID_TANKS)));
		for (int i = 0; i < entries.size(); i++) {
			CompoundTag entry = entries.getCompound(i);
			int capacity = entry.getInt(FLUID_CAPACITY);
			if (entry.contains(FLUID_STACK, Tag.TAG_COMPOUND)) {
				FluidStack stack = FluidStack.parseOptional(accessor.getLevel().registryAccess(), entry.getCompound(FLUID_STACK));
				if (!stack.isEmpty()) {
					tooltip.add(Component.translatable(Embers.MODID + ".jade.fluid",
							entry.getInt(FLUID_TANK) + 1,
							stack.getHoverName(),
							entry.getInt(FLUID_AMOUNT),
							capacity));
				}
			} else if (accessor.showDetails()) {
				tooltip.add(Component.translatable(Embers.MODID + ".jade.fluid.empty", entry.getInt(FLUID_TANK) + 1, capacity));
			}
		}
	}

	private static void appendEmberData(CompoundTag data, BlockAccessor accessor, BlockEntity blockEntity) {
		IEmberCapability ember = getEmber(accessor, blockEntity);
		if (ember == null || ember.getEmberCapacity() <= 0 && ember.getEmber() <= 0) {
			return;
		}
		CompoundTag emberData = new CompoundTag();
		emberData.putDouble(EMBER_AMOUNT, ember.getEmber());
		emberData.putDouble(EMBER_CAPACITY, ember.getEmberCapacity());
		emberData.putBoolean(EMBER_VOLATILE, ember.acceptsVolatile());
		data.put(EMBER, emberData);
	}

	private static void appendEnergyData(CompoundTag data, BlockAccessor accessor, BlockEntity blockEntity) {
		IEnergyStorage energy = getEnergy(accessor, blockEntity);
		if (energy == null || energy.getMaxEnergyStored() <= 0) {
			return;
		}
		CompoundTag energyData = new CompoundTag();
		energyData.putInt(ENERGY_STORED, energy.getEnergyStored());
		energyData.putInt(ENERGY_CAPACITY, energy.getMaxEnergyStored());
		data.put(ENERGY, energyData);
	}

	private static void appendItemData(CompoundTag data, BlockAccessor accessor, BlockEntity blockEntity) {
		IItemHandler items = getItemHandler(accessor, blockEntity);
		if (items == null || items.getSlots() <= 0) {
			return;
		}

		ListTag entries = new ListTag();
		int filledSlots = 0;
		int totalCount = 0;
		for (int slot = 0; slot < items.getSlots(); slot++) {
			ItemStack stack = items.getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			filledSlots++;
			totalCount += stack.getCount();
			CompoundTag entry = new CompoundTag();
			entry.putInt(ITEM_SLOT, slot);
			entry.put(ITEM_STACK, ItemData.save(stack));
			entries.add(entry);
		}

		CompoundTag itemData = new CompoundTag();
		itemData.putInt(ITEM_SLOTS, items.getSlots());
		itemData.putInt(ITEM_FILLED, filledSlots);
		itemData.putInt(ITEM_COUNT, totalCount);
		itemData.put(ITEM_ENTRIES, entries);
		data.put(ITEMS, itemData);
	}

	private static void appendFluidData(CompoundTag data, BlockAccessor accessor, BlockEntity blockEntity) {
		IFluidHandler fluids = getFluidHandler(accessor, blockEntity);
		if (fluids == null || fluids.getTanks() <= 0) {
			return;
		}

		ListTag entries = new ListTag();
		int filledTanks = 0;
		for (int tank = 0; tank < fluids.getTanks(); tank++) {
			FluidStack stack = fluids.getFluidInTank(tank);
			int capacity = fluids.getTankCapacity(tank);
			CompoundTag entry = new CompoundTag();
			entry.putInt(FLUID_TANK, tank);
			entry.putInt(FLUID_CAPACITY, capacity);
			if (!stack.isEmpty()) {
				filledTanks++;
				entry.putInt(FLUID_AMOUNT, stack.getAmount());
				entry.put(FLUID_STACK, stack.save(accessor.getLevel().registryAccess()));
			}
			entries.add(entry);
		}

		if (entries.isEmpty()) {
			return;
		}
		CompoundTag fluidData = new CompoundTag();
		fluidData.putInt(FLUID_TANKS, fluids.getTanks());
		fluidData.putInt(FLUID_FILLED, filledTanks);
		fluidData.put(FLUID_ENTRIES, entries);
		data.put(FLUIDS, fluidData);
	}

	private static IEmberCapability getEmber(BlockAccessor accessor, BlockEntity blockEntity) {
		for (BlockEntity candidate : getEmberProviderCandidates(accessor, blockEntity)) {
			IEmberCapability ember = getEmberCapability(candidate, accessor.getSide());
			if (ember != null) {
				return ember;
			}
		}
		return null;
	}

	private static IItemHandler getItemHandler(BlockAccessor accessor, BlockEntity blockEntity) {
		IItemHandler items = CapabilityCompat.getCapability(blockEntity, ForgeCapabilities.ITEM_HANDLER, accessor.getSide()).orElse(null);
		if (items == null) {
			items = CapabilityCompat.getCapability(blockEntity, ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
		}
		return items;
	}

	private static IFluidHandler getFluidHandler(BlockAccessor accessor, BlockEntity blockEntity) {
		IFluidHandler fluids = CapabilityCompat.getCapability(blockEntity, ForgeCapabilities.FLUID_HANDLER, accessor.getSide()).orElse(null);
		if (fluids == null) {
			fluids = CapabilityCompat.getCapability(blockEntity, ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
		}
		return fluids;
	}

	private static IEnergyStorage getEnergy(BlockAccessor accessor, BlockEntity blockEntity) {
		IEnergyStorage energy = accessor.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, blockEntity.getBlockPos(), accessor.getSide());
		if (energy == null) {
			energy = accessor.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, blockEntity.getBlockPos(), null);
		}
		return energy;
	}

	private static Iterable<BlockEntity> getEmberProviderCandidates(BlockAccessor accessor, BlockEntity blockEntity) {
		LinkedHashSet<BlockEntity> candidates = new LinkedHashSet<>();
		addCandidate(candidates, blockEntity);
		addCandidate(candidates, getDoubleTallCounterpart(blockEntity));

		BlockEntity primary = getPrimaryBlockEntity(accessor);
		addCandidate(candidates, primary);
		addCandidate(candidates, getDoubleTallCounterpart(primary));
		return candidates;
	}

	private static void addCandidate(LinkedHashSet<BlockEntity> candidates, BlockEntity blockEntity) {
		if (blockEntity != null && !blockEntity.isRemoved()) {
			candidates.add(blockEntity);
		}
	}

	private static IEmberCapability getEmberCapability(BlockEntity blockEntity, Direction preferredSide) {
		IEmberCapability ember = CapabilityCompat.getCapability(blockEntity, EmbersCapabilities.EMBER_CAPABILITY, preferredSide).orElse(null);
		if (ember != null) {
			return ember;
		}
		ember = CapabilityCompat.getCapability(blockEntity, EmbersCapabilities.EMBER_CAPABILITY, null).orElse(null);
		if (ember != null) {
			return ember;
		}
		for (Direction direction : Direction.values()) {
			ember = CapabilityCompat.getCapability(blockEntity, EmbersCapabilities.EMBER_CAPABILITY, direction).orElse(null);
			if (ember != null) {
				return ember;
			}
		}
		return null;
	}

	private static BlockEntity getPrimaryBlockEntity(BlockAccessor accessor) {
		BlockEntity blockEntity = accessor.getBlockEntity();
		if (blockEntity != null) {
			return blockEntity;
		}

		BlockState state = accessor.getBlockState();
		if (state.hasProperty(MechEdgeBlockBase.EDGE)) {
			BlockPos centerPos = accessor.getPosition().offset(state.getValue(MechEdgeBlockBase.EDGE).centerPos);
			return accessor.getLevel().getBlockEntity(centerPos);
		}
		return null;
	}

	private static BlockEntity getDoubleTallCounterpart(BlockEntity blockEntity) {
		if (blockEntity == null || blockEntity.getLevel() == null) {
			return null;
		}

		BlockState state = blockEntity.getBlockState();
		if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return null;
		}

		BlockPos otherPos = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
				? blockEntity.getBlockPos().above()
				: blockEntity.getBlockPos().below();
		BlockState otherState = blockEntity.getLevel().getBlockState(otherPos);
		if (otherState.getBlock() != state.getBlock()) {
			return null;
		}
		return blockEntity.getLevel().getBlockEntity(otherPos);
	}

	private static boolean isEmbersBlock(Block block) {
		ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
		return Embers.MODID.equals(key.getNamespace());
	}

	private static String format(double value) {
		if (!Double.isFinite(value)) {
			return "0";
		}
		if (value == Math.rint(value)) {
			return Long.toString(Math.round(value));
		}
		String formatted = String.format(java.util.Locale.ROOT, "%.2f", value);
		while (formatted.endsWith("0")) {
			formatted = formatted.substring(0, formatted.length() - 1);
		}
		if (formatted.endsWith(".")) {
			formatted = formatted.substring(0, formatted.length() - 1);
		}
		return formatted;
	}
}
