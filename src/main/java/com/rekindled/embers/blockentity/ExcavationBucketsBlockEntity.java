package com.rekindled.embers.blockentity;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.upgrade.ExcavationBucketsUpgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.rekindled.embers.compat.legacy.capabilities.Capability;
import com.rekindled.embers.compat.legacy.LazyOptional;

public class ExcavationBucketsBlockEntity extends BlockEntity {

	public ExcavationBucketsUpgrade upgrade;
	public float angle = 0;
	public float lastAngle;

	public ExcavationBucketsBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.EXCAVATION_BUCKETS_ENTITY.get(), pPos, pBlockState);
		upgrade = new ExcavationBucketsUpgrade(this);
	}

	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (!this.isRemoved() && level.getBlockState(worldPosition).hasProperty(BlockStateProperties.FACING)) {
			if (cap == EmbersCapabilities.UPGRADE_PROVIDER_CAPABILITY && (side == null || side.getOpposite() == level.getBlockState(worldPosition).getValue(BlockStateProperties.FACING))) {
				return upgrade.getCapability(cap, side);
			}
		}
		return LazyOptional.empty();
	}
}
