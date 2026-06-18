package com.rekindled.embers.mixin;

import com.rekindled.embers.api.power.IEmberPacketReceiver;
import com.rekindled.embers.block.BeamSplitterBlock;
import com.rekindled.embers.block.EmberReceiverBlock;
import com.rekindled.embers.compat.sublevel.SubLevelCompat;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({EmberReceiverBlock.class, BeamSplitterBlock.class})
public abstract class EmberLinkTargetSubLevelAssemblyMixin implements BlockSubLevelAssemblyListener {

	@Override
	public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
		BlockEntity target = originLevel.getBlockEntity(oldPos);
		if (target instanceof IEmberPacketReceiver) {
			SubLevelCompat.rememberMovingEmberLinkTarget(target);
		}
	}

	@Override
	public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
		BlockEntity target = resultingLevel.getBlockEntity(newPos);
		if (target instanceof IEmberPacketReceiver) {
			SubLevelCompat.reconnectMovedEmberLinks(resultingLevel, oldPos, target);
		}
	}
}
