package com.rekindled.embers.compat.create;

import java.util.LinkedList;
import java.util.List;

import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.api.power.IEmberCapability;
import com.rekindled.embers.api.tile.ICatalyticPlugLimitProvider;
import com.rekindled.embers.api.tile.IUpgradeable;
import com.rekindled.embers.api.upgrades.UpgradeContext;
import com.rekindled.embers.api.upgrades.UpgradeUtil;
import com.rekindled.embers.power.DefaultEmberCapability;
import com.rekindled.embers.upgrade.CatalyticPlugUpgrade;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.LazyOptional;

public class EmberKineticGeneratorBlockEntity extends GeneratingKineticBlockEntity implements IUpgradeable, ICatalyticPlugLimitProvider {
	public static final float BASE_GENERATED_RPM = 64.0f;
	public static final float MAX_GENERATED_RPM = 256.0f;
	public static final float BASE_STRESS_CAPACITY = 16.0f;
	public static final float MID_STRESS_CAPACITY = 32.0f;
	public static final float MAX_STRESS_CAPACITY = 384.0f;
	public static final double BASE_EMBER_PER_TICK = 1.0;
	public static final double MAX_EMBER_PER_TICK = 8.0;
	public static final int MAX_CATALYTIC_PLUGS = 4;
	private static final int WORK_INTERVAL = 20;

	private final IEmberCapability ember = new DefaultEmberCapability() {
		@Override
		public void onContentsChanged() {
			EmberKineticGeneratorBlockEntity.this.setChanged();
		}

		@Override
		public boolean acceptsVolatile() {
			return false;
		}
	};
	private boolean active;
	private int activeCatalyticPlugs;
	private long fundedUntilTick;
	private final List<UpgradeContext> upgrades = new LinkedList<>();

	public EmberKineticGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(CreateCompat.EMBER_KINETIC_GENERATOR_ENTITY.get(), pos, state);
		ember.setEmberCapacity(4000);
	}

	@Override
	public void initialize() {
		super.initialize();
		updateGeneratedRotation();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide) {
			return;
		}
		long gameTime = level.getGameTime();
		boolean expired = active && gameTime >= fundedUntilTick;
		if (!expired && !isScheduledWorkTick(gameTime)) {
			return;
		}
		refreshUpgrades();
		int nextActiveCatalyticPlugs = getActiveCatalyticPlugCount();
		double emberPerTick = getEmberBurnRate(nextActiveCatalyticPlugs);
		int fundedTicks = fundWorkTicks(emberPerTick);
		boolean nextActive = fundedTicks > 0;
		if (nextActive) {
			fundedUntilTick = gameTime + fundedTicks;
			doBatchedCatalystWork(fundedTicks);
		}
		if (active != nextActive || activeCatalyticPlugs != nextActiveCatalyticPlugs) {
			active = nextActive;
			activeCatalyticPlugs = nextActiveCatalyticPlugs;
			updateGeneratedRotation();
			sendData();
			setChanged();
		}
	}

	@Override
	public float getGeneratedSpeed() {
		if (!active) {
			return 0;
		}
		return convertToDirection(getGeneratedRpm(activeCatalyticPlugs), getBlockState().getValue(EmberKineticGeneratorBlock.FACING));
	}

	@Override
	public float calculateAddedStressCapacity() {
		lastCapacityProvided = active ? getStressCapacity(activeCatalyticPlugs) : 0;
		return lastCapacityProvided;
	}

	public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
		if (!isRemoved() && capability == EmbersCapabilities.EMBER_CAPABILITY && ember instanceof DefaultEmberCapability defaultEmber) {
			return defaultEmber.getCapability(capability, side);
		}
		return LazyOptional.empty();
	}

	public IEmberCapability getEmberCapability() {
		return ember;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		ember.writeToNBT(tag);
		tag.putBoolean("Active", active);
		tag.putInt("ActiveCatalyticPlugs", activeCatalyticPlugs);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		ember.deserializeNBT(tag);
		active = tag.getBoolean("Active");
		activeCatalyticPlugs = tag.getInt("ActiveCatalyticPlugs");
	}

	@Override
	public boolean isSideUpgradeSlot(Direction face) {
		return face.getAxis() != getBlockState().getValue(EmberKineticGeneratorBlock.FACING).getAxis();
	}

	@Override
	public int getCatalyticPlugLimit() {
		return MAX_CATALYTIC_PLUGS;
	}

	private void refreshUpgrades() {
		upgrades.clear();
		UpgradeUtil.getUpgrades(level, worldPosition, Direction.values(), upgrades);
		upgrades.removeIf(upgrade -> !(upgrade.upgrade() instanceof CatalyticPlugUpgrade));
		UpgradeUtil.verifyUpgrades(this, upgrades);
	}

	private boolean isScheduledWorkTick(long gameTime) {
		int offset = Math.floorMod(worldPosition.getX() * 31 + worldPosition.getY() * 17 + worldPosition.getZ() * 13, WORK_INTERVAL);
		return (gameTime + offset) % WORK_INTERVAL == 0;
	}

	private int fundWorkTicks(double emberPerTick) {
		if (emberPerTick <= 0) {
			return WORK_INTERVAL;
		}
		double fullCost = emberPerTick * WORK_INTERVAL;
		if (ember.removeAmount(fullCost, false) >= fullCost) {
			ember.removeAmount(fullCost, true);
			return WORK_INTERVAL;
		}
		int affordableTicks = Math.min(WORK_INTERVAL, (int) Math.floor(ember.getEmber() / emberPerTick));
		if (affordableTicks <= 0) {
			return 0;
		}
		ember.removeAmount(emberPerTick * affordableTicks, true);
		return affordableTicks;
	}

	private void doBatchedCatalystWork(int ticks) {
		for (UpgradeContext upgrade : upgrades) {
			if (upgrade.upgrade() instanceof CatalyticPlugUpgrade plug) {
				plug.doBatchedWork(this, upgrades, upgrade.distance(), upgrade.count(), ticks);
			}
		}
	}

	private int getActiveCatalyticPlugCount() {
		int activePlugCount = 0;
		for (UpgradeContext upgrade : upgrades) {
			if (upgrade.upgrade() instanceof CatalyticPlugUpgrade plug
					&& plug.getSpeed(this, 1.0, upgrade.distance(), upgrade.count()) > 1.0) {
				activePlugCount++;
			}
		}
		return Math.min(MAX_CATALYTIC_PLUGS, activePlugCount);
	}

	private static float getGeneratedRpm(int activeCatalyticPlugs) {
		return switch (clampCatalyticPlugs(activeCatalyticPlugs)) {
		case 0 -> BASE_GENERATED_RPM;
		case 1, 2 -> 128.0f;
		default -> MAX_GENERATED_RPM;
		};
	}

	private static float getStressCapacity(int activeCatalyticPlugs) {
		return switch (clampCatalyticPlugs(activeCatalyticPlugs)) {
		case 0, 1 -> BASE_STRESS_CAPACITY;
		case 2, 3 -> MID_STRESS_CAPACITY;
		default -> MAX_STRESS_CAPACITY;
		};
	}

	private static double getEmberBurnRate(int activeCatalyticPlugs) {
		int plugs = clampCatalyticPlugs(activeCatalyticPlugs);
		if (plugs <= 0) {
			return BASE_EMBER_PER_TICK;
		}
		double burnRatio = MAX_EMBER_PER_TICK / BASE_EMBER_PER_TICK;
		return BASE_EMBER_PER_TICK * Math.pow(burnRatio, (double) plugs / MAX_CATALYTIC_PLUGS);
	}

	private static int clampCatalyticPlugs(int activeCatalyticPlugs) {
		return Math.max(0, Math.min(MAX_CATALYTIC_PLUGS, activeCatalyticPlugs));
	}
}
