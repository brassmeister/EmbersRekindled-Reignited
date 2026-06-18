package com.rekindled.embers.mixin;

import com.rekindled.embers.block.BeamSplitterBlock;
import com.rekindled.embers.block.EmberEmitterBlock;
import com.rekindled.embers.block.EmberRelayBlock;
import com.rekindled.embers.block.MirrorRelayBlock;
import com.rekindled.embers.blockentity.BeamSplitterBlockEntity;
import com.rekindled.embers.blockentity.EmberEmitterBlockEntity;
import com.rekindled.embers.blockentity.EmberRelayBlockEntity;
import com.rekindled.embers.blockentity.MirrorRelayBlockEntity;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({EmberEmitterBlock.class, EmberRelayBlock.class, MirrorRelayBlock.class, BeamSplitterBlock.class})
public abstract class EmberLinkSubLevelAssemblyMixin implements BlockSubLevelAssemblyListener {

	@Override
	public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
		BlockEntity blockEntity = originLevel.getBlockEntity(oldPos);
		if (blockEntity instanceof EmberEmitterBlockEntity emitter) {
			emitter.refreshTrackedTarget();
		} else if (blockEntity instanceof EmberRelayBlockEntity relay) {
			relay.refreshTrackedTarget();
		} else if (blockEntity instanceof MirrorRelayBlockEntity relay) {
			relay.refreshTrackedTarget();
		} else if (blockEntity instanceof BeamSplitterBlockEntity splitter) {
			splitter.refreshTrackedTargets();
		}
	}

	@Override
	public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
	}
}
