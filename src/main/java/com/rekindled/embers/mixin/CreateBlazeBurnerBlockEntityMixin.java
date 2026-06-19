package com.rekindled.embers.mixin;

import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.api.power.IEmberCapability;
import com.rekindled.embers.api.upgrades.UpgradeContext;
import com.rekindled.embers.api.upgrades.UpgradeUtil;
import com.rekindled.embers.compat.create.CreateBlazeBurnerHelper;
import com.rekindled.embers.compat.create.EmberFueledBlazeBurner;
import com.rekindled.embers.power.DefaultEmberCapability;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlazeBurnerBlockEntity.class)
public abstract class CreateBlazeBurnerBlockEntityMixin extends SmartBlockEntity implements EmberFueledBlazeBurner {
	@Shadow protected BlazeBurnerBlockEntity.FuelType activeFuel;
	@Shadow protected int remainingBurnTime;
	@Shadow public boolean isCreative;
	@Shadow public abstract BlazeBurnerBlock.HeatLevel getHeatLevelFromBlock();
	@Shadow protected abstract void updateBlockState();
	@Shadow protected abstract void playSound();

	@Unique private static final String EMBERS_FUEL_TAG = "EmbersStoredFuel";
	@Unique private static final String EMBERS_KINDLED_TAG = "EmbersKindled";
	@Unique private static final String EMBERS_SUPERHEATED_TAG = "EmbersBellowsSuperheated";
	@Unique private static final String EMBERS_PREVIOUS_FUEL_TAG = "EmbersPreBellowsFuel";
	@Unique private static final String EMBERS_PREVIOUS_BURN_TIME_TAG = "EmbersPreBellowsBurnTime";
	@Unique private static final String EMBERS_HEARTH_COIL_HEATED_TAG = "EmbersHearthCoilHeated";
	@Unique private static final int EMBERS_REFRESH_INTERVAL = 60;
	@Unique private static final int EMBERS_SEETHING_WINDOW = 80;
	@Unique private static final int EMBERS_KINDLED_WINDOW = 185;
	@Unique private static final int EMBERS_SEETHING_REFRESH_THRESHOLD = EMBERS_SEETHING_WINDOW - EMBERS_REFRESH_INTERVAL;
	@Unique private static final int EMBERS_KINDLED_REFRESH_THRESHOLD = EMBERS_KINDLED_WINDOW - EMBERS_REFRESH_INTERVAL;

	@Unique
	private final IEmberCapability embers$storedFuel = new DefaultEmberCapability() {
		@Override
		public void onContentsChanged() {
			CreateBlazeBurnerBlockEntityMixin.this.setChanged();
		}

		@Override
		public boolean acceptsVolatile() {
			return false;
		}
	};

	@Unique private boolean embers$kindledFromStoredFuel;
	@Unique private boolean embers$superheatedFromStoredFuel;
	@Unique private boolean embers$heatedByHearthCoil;
	@Unique private boolean embers$lastWantsSuperheat;
	@Unique private boolean embers$lastHearthCoilHeated;
	@Unique private boolean embers$lastHearthCoilSuperheated;
	@Unique private BlazeBurnerBlockEntity.FuelType embers$preSuperheatFuel = BlazeBurnerBlockEntity.FuelType.NONE;
	@Unique private int embers$preSuperheatBurnTime;

	protected CreateBlazeBurnerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void embers$initStoredFuel(BlockEntityType<?> type, BlockPos pos, BlockState state, CallbackInfo ci) {
		embers$storedFuel.setEmberCapacity(MAX_STORED_EMBER);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void embers$consumeStoredEmber(CallbackInfo ci) {
		embers$refreshEmberFuelState(false);
	}

	@Inject(method = "write", at = @At("TAIL"))
	private void embers$writeStoredFuel(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
		tag.put(EMBERS_FUEL_TAG, embers$storedFuel.serializeNBT());
		tag.putBoolean(EMBERS_KINDLED_TAG, embers$kindledFromStoredFuel);
		tag.putBoolean(EMBERS_SUPERHEATED_TAG, embers$superheatedFromStoredFuel);
		tag.putBoolean(EMBERS_HEARTH_COIL_HEATED_TAG, embers$heatedByHearthCoil);
		tag.putInt(EMBERS_PREVIOUS_FUEL_TAG, embers$preSuperheatFuel.ordinal());
		tag.putInt(EMBERS_PREVIOUS_BURN_TIME_TAG, embers$preSuperheatBurnTime);
	}

	@Inject(method = "read", at = @At("TAIL"))
	private void embers$readStoredFuel(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
		if (tag.contains(EMBERS_FUEL_TAG)) {
			embers$storedFuel.deserializeNBT(tag.getCompound(EMBERS_FUEL_TAG));
		}
		embers$kindledFromStoredFuel = tag.getBoolean(EMBERS_KINDLED_TAG);
		embers$superheatedFromStoredFuel = tag.getBoolean(EMBERS_SUPERHEATED_TAG);
		embers$heatedByHearthCoil = tag.getBoolean(EMBERS_HEARTH_COIL_HEATED_TAG);
		if (tag.contains(EMBERS_PREVIOUS_FUEL_TAG)) {
			int index = tag.getInt(EMBERS_PREVIOUS_FUEL_TAG);
			BlazeBurnerBlockEntity.FuelType[] fuelTypes = BlazeBurnerBlockEntity.FuelType.values();
			if (index >= 0 && index < fuelTypes.length) {
				embers$preSuperheatFuel = fuelTypes[index];
			}
		}
		embers$preSuperheatBurnTime = tag.getInt(EMBERS_PREVIOUS_BURN_TIME_TAG);
	}

	@Override
	@Unique
	public IEmberCapability embers$getEmberFuel() {
		return embers$storedFuel;
	}

	@Override
	@Unique
	public double embers$addEmber(double amount, boolean simulate) {
		return embers$storedFuel.addAmount(amount, !simulate);
	}

	@Override
	@Unique
	public void embers$refreshEmberFuelState(boolean playSound) {
		if (level == null || level.isClientSide || isCreative) {
			return;
		}
		if (embers$kindledFromStoredFuel && activeFuel == BlazeBurnerBlockEntity.FuelType.NORMAL
				&& remainingBurnTime > EMBERS_KINDLED_WINDOW) {
			embers$kindledFromStoredFuel = false;
		}
		boolean hearthCoilHeated = CreateBlazeBurnerHelper.isHeatedByHearthCoil(level, worldPosition);

		List<UpgradeContext> upgrades = UpgradeUtil.getUpgrades(level, worldPosition, Direction.values());
		UpgradeUtil.verifyUpgrades(this, upgrades);

		double normalCost = embers$getRefreshCost(ConfigManager.CREATE_BLAZE_BURNER_EMBER_COST.get());
		double superheatCost = embers$getRefreshCost(ConfigManager.CREATE_BLAZE_BURNER_SUPERHEAT_COST.get());
		boolean hearthCoilSuperheated = hearthCoilHeated && CreateBlazeBurnerHelper.isHearthCoilSuperheating(level, worldPosition);
		BlazeBurnerBlock.HeatLevel desiredHeat = UpgradeUtil.getOtherParameter(this, "create_blaze_burner_heat",
				BlazeBurnerBlock.HeatLevel.KINDLED, upgrades);
		boolean wantsSuperheat = UpgradeUtil.getOtherParameter(this, "create_blaze_burner_superheat", false, upgrades)
				|| desiredHeat.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING);
		boolean shouldRefresh = embers$shouldRefreshEmberFuelState(playSound, hearthCoilHeated, hearthCoilSuperheated, wantsSuperheat);
		embers$lastHearthCoilHeated = hearthCoilHeated;
		embers$lastHearthCoilSuperheated = hearthCoilSuperheated;
		embers$lastWantsSuperheat = wantsSuperheat;
		if (!shouldRefresh) {
			return;
		}
		if (activeFuel == BlazeBurnerBlockEntity.FuelType.SPECIAL
				&& !embers$superheatedFromStoredFuel
				&& !embers$heatedByHearthCoil
				&& !hearthCoilHeated) {
			return;
		}
		BlazeBurnerBlock.HeatLevel previousHeat = getHeatLevelFromBlock();
		boolean changed = false;

		if (hearthCoilHeated) {
			BlazeBurnerBlockEntity.FuelType hearthFuel = hearthCoilSuperheated
					? BlazeBurnerBlockEntity.FuelType.SPECIAL
					: BlazeBurnerBlockEntity.FuelType.NORMAL;
			if (!embers$heatedByHearthCoil && !embers$superheatedFromStoredFuel) {
				embers$preSuperheatFuel = activeFuel;
				embers$preSuperheatBurnTime = remainingBurnTime;
			}
			embers$heatedByHearthCoil = true;
			embers$superheatedFromStoredFuel = false;
			embers$kindledFromStoredFuel = false;
			if (embers$setFuelWindow(hearthFuel)) {
				changed = true;
			}
		} else if (embers$heatedByHearthCoil) {
			embers$heatedByHearthCoil = false;
			if (embers$preSuperheatFuel != BlazeBurnerBlockEntity.FuelType.NONE && embers$preSuperheatBurnTime > 0) {
				activeFuel = embers$preSuperheatFuel;
				remainingBurnTime = Math.max(embers$preSuperheatBurnTime, 1);
			} else {
				activeFuel = BlazeBurnerBlockEntity.FuelType.NONE;
				remainingBurnTime = 0;
			}
			embers$preSuperheatFuel = BlazeBurnerBlockEntity.FuelType.NONE;
			embers$preSuperheatBurnTime = 0;
			changed = true;
		} else if (wantsSuperheat && embers$hasFuel(superheatCost)) {
			if (!embers$superheatedFromStoredFuel) {
				embers$preSuperheatFuel = activeFuel;
				embers$preSuperheatBurnTime = remainingBurnTime;
			}
			embers$consumeFuel(superheatCost);
			embers$superheatedFromStoredFuel = true;
			embers$kindledFromStoredFuel = false;
			if (embers$setFuelWindow(BlazeBurnerBlockEntity.FuelType.SPECIAL)) {
				changed = true;
			}
		} else if (embers$superheatedFromStoredFuel) {
			embers$superheatedFromStoredFuel = false;
			if (embers$preSuperheatFuel != BlazeBurnerBlockEntity.FuelType.NONE && embers$preSuperheatBurnTime > 0) {
				activeFuel = embers$preSuperheatFuel;
				remainingBurnTime = Math.max(embers$preSuperheatBurnTime, 1);
				changed = true;
			} else if (embers$hasFuel(normalCost)) {
				embers$consumeFuel(normalCost);
				embers$setFuelWindow(BlazeBurnerBlockEntity.FuelType.NORMAL);
				embers$kindledFromStoredFuel = true;
				changed = true;
			} else {
				activeFuel = BlazeBurnerBlockEntity.FuelType.NONE;
				remainingBurnTime = 0;
				changed = true;
			}
			embers$preSuperheatFuel = BlazeBurnerBlockEntity.FuelType.NONE;
			embers$preSuperheatBurnTime = 0;
		} else if (activeFuel == BlazeBurnerBlockEntity.FuelType.NONE) {
			if (embers$hasFuel(normalCost)) {
				embers$consumeFuel(normalCost);
				embers$setFuelWindow(BlazeBurnerBlockEntity.FuelType.NORMAL);
				embers$kindledFromStoredFuel = true;
				changed = true;
			}
		} else if (embers$kindledFromStoredFuel) {
			if (embers$hasFuel(normalCost)) {
				embers$consumeFuel(normalCost);
				if (embers$setFuelWindow(BlazeBurnerBlockEntity.FuelType.NORMAL)) {
					changed = true;
				}
			} else {
				activeFuel = BlazeBurnerBlockEntity.FuelType.NONE;
				remainingBurnTime = 0;
				embers$kindledFromStoredFuel = false;
				changed = true;
			}
		}

		if (changed) {
			updateBlockState();
			if (playSound && previousHeat != getHeatLevelFromBlock()) {
				playSound();
			}
		}
	}

	@Unique
	private boolean embers$shouldRefreshEmberFuelState(boolean forced, boolean hearthCoilHeated, boolean hearthCoilSuperheated, boolean wantsSuperheat) {
		if (forced || activeFuel == BlazeBurnerBlockEntity.FuelType.NONE) {
			return true;
		}
		if (hearthCoilHeated != embers$lastHearthCoilHeated
				|| hearthCoilSuperheated != embers$lastHearthCoilSuperheated
				|| wantsSuperheat != embers$lastWantsSuperheat) {
			return true;
		}
		if (embers$kindledFromStoredFuel && activeFuel != BlazeBurnerBlockEntity.FuelType.NORMAL) {
			return true;
		}
		if (embers$superheatedFromStoredFuel && activeFuel != BlazeBurnerBlockEntity.FuelType.SPECIAL) {
			return true;
		}
		if (embers$heatedByHearthCoil && remainingBurnTime > embers$getFuelWindow(activeFuel)) {
			return true;
		}
		if (embers$kindledFromStoredFuel) {
			return remainingBurnTime <= EMBERS_KINDLED_REFRESH_THRESHOLD;
		}
		if (embers$superheatedFromStoredFuel) {
			return remainingBurnTime <= EMBERS_SEETHING_REFRESH_THRESHOLD;
		}
		if (embers$heatedByHearthCoil) {
			return remainingBurnTime <= embers$getRefreshThreshold(activeFuel);
		}
		return embers$isScheduledRefreshTick();
	}

	@Unique
	private boolean embers$isScheduledRefreshTick() {
		int offset = Math.floorMod(worldPosition.getX() * 31 + worldPosition.getY() * 17 + worldPosition.getZ() * 13, EMBERS_REFRESH_INTERVAL);
		return (level.getGameTime() + offset) % EMBERS_REFRESH_INTERVAL == 0;
	}

	@Unique
	private double embers$getRefreshCost(double tickCost) {
		return tickCost * EMBERS_REFRESH_INTERVAL;
	}

	@Unique
	private boolean embers$setFuelWindow(BlazeBurnerBlockEntity.FuelType fuel) {
		int window = embers$getFuelWindow(fuel);
		if (activeFuel == fuel && remainingBurnTime >= window) {
			return false;
		}
		activeFuel = fuel;
		remainingBurnTime = window;
		return true;
	}

	@Unique
	private int embers$getFuelWindow(BlazeBurnerBlockEntity.FuelType fuel) {
		return fuel == BlazeBurnerBlockEntity.FuelType.SPECIAL ? EMBERS_SEETHING_WINDOW : EMBERS_KINDLED_WINDOW;
	}

	@Unique
	private int embers$getRefreshThreshold(BlazeBurnerBlockEntity.FuelType fuel) {
		return fuel == BlazeBurnerBlockEntity.FuelType.SPECIAL ? EMBERS_SEETHING_REFRESH_THRESHOLD : EMBERS_KINDLED_REFRESH_THRESHOLD;
	}

	@Unique
	private boolean embers$hasFuel(double amount) {
		if (amount <= 0) {
			return true;
		}
		return embers$storedFuel.removeAmount(amount, false) >= amount;
	}

	@Unique
	private void embers$consumeFuel(double amount) {
		if (amount > 0) {
			embers$storedFuel.removeAmount(amount, true);
		}
	}
}
