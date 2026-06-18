package com.rekindled.embers.blockentity;

import com.rekindled.embers.RegistryManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class InfernoForgeTopBlockEntity extends BlockEntity {

	public long lastToggle = -1;
	public boolean open = false;

	public InfernoForgeTopBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.INFERNO_FORGE_TOP_ENTITY.get(), pPos, pBlockState);
	}

	public void setOpen(boolean open, long gameTime) {
		this.open = open;
		lastToggle = gameTime;
		setChanged();
		syncOpenBlockState();
	}

	private void syncOpenBlockState() {
		if (level != null) {
			BlockState state = level.getBlockState(worldPosition);
			if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) != open) {
				level.setBlock(worldPosition, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
			} else {
				level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
			}
		}
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		lastToggle = nbt.getLong("lastToggle");
		open = nbt.getBoolean("open");
		syncOpenBlockState();
	}

	@Override
	public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putLong("lastToggle", lastToggle);
		nbt.putBoolean("open", open);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag nbt = super.getUpdateTag(registries);
		nbt.putLong("lastToggle", lastToggle);
		nbt.putBoolean("open", open);
		return nbt;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level instanceof ServerLevel)
			((ServerLevel) level).getChunkSource().blockChanged(worldPosition);
	}
}
