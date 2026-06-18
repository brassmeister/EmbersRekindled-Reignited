package com.rekindled.embers.blockentity;

import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.upgrade.ClockworkAttenuatorUpgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.LazyOptional;

public class ClockworkAttenuatorBlockEntity extends BlockEntity {

	public ClockworkAttenuatorUpgrade upgrade;
	public double activeSpeed = 0, inactiveSpeed = 1;

	public double[] validSpeeds = new double[]{0.0, 0.0625, 0.125, 0.25, 0.5, 1.0};

	public ClockworkAttenuatorBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.CLOCKWORK_ATTENUATOR_ENTITY.get(), pPos, pBlockState);
		upgrade = new ClockworkAttenuatorUpgrade(this);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		activeSpeed = nbt.getDouble("active_speed");
		inactiveSpeed = nbt.getDouble("inactive_speed");
	}

	@Override
	public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putDouble("active_speed", activeSpeed);
		nbt.putDouble("inactive_speed", inactiveSpeed);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag nbt = super.getUpdateTag(registries);
		nbt.putDouble("active_speed", activeSpeed);
		nbt.putDouble("inactive_speed", inactiveSpeed);
		return nbt;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (!this.isRemoved() && cap == EmbersCapabilities.UPGRADE_PROVIDER_CAPABILITY && (side == null || level.getBlockState(worldPosition).getValue(BlockStateProperties.FACING).getOpposite() == side)) {
			return upgrade.getCapability(cap, side);
		}
		return LazyOptional.empty();
	}

	public double getSpeed() {
		return ConfigManager.isRedstoneControlActive(level, worldPosition) ? activeSpeed : inactiveSpeed;
	}

	public double getNext(double current) {
		for (int i = 0; i < validSpeeds.length - 1; i++) {
			double a = validSpeeds[i];
			double b = validSpeeds[i + 1];

			if (b > current && a <= current)
				return b;
		}
		return current;
	}

	public double getPrevious(double current) {
		for (int i = 0; i < validSpeeds.length - 1; i++) {
			double a = validSpeeds[i];
			double b = validSpeeds[i + 1];

			if (b >= current && a < current)
				return a;
		}
		return current;
	}

	public void invalidateCaps() {
		
		upgrade.invalidate();
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level instanceof ServerLevel)
			((ServerLevel) level).getChunkSource().blockChanged(worldPosition);
	}
}
