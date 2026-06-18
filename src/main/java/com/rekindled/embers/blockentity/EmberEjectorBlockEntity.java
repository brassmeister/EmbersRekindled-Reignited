package com.rekindled.embers.blockentity;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.api.power.IEmberCapability;
import com.rekindled.embers.api.power.IEmberPacketReceiver;
import com.rekindled.embers.datagen.EmbersSounds;
import com.rekindled.embers.entity.EmberPacketEntity;
import com.rekindled.embers.compat.sublevel.SubLevelCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class EmberEjectorBlockEntity extends EmberEmitterBlockEntity {

	public static final double TRANSFER_RATE = 400.0;
	public static final double PULL_RATE = 100.0;

	public EmberEjectorBlockEntity(BlockPos pPos, BlockState pBlockState) {
		super(RegistryManager.EMBER_EJECTOR_ENTITY.get(), pPos, pBlockState);
		capability.setEmberCapacity(2000);
		capability.setEmber(0);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, EmberEjectorBlockEntity blockEntity) {
		blockEntity.ticksExisted ++;
		Direction facing = state.getValue(BlockStateProperties.FACING);
		BlockEntity attachedTile = SubLevelCompat.findAdjacent(blockEntity, facing.getOpposite());
		if (blockEntity.ticksExisted % 5 == 0 && attachedTile != null) {
			IEmberCapability cap = com.rekindled.embers.util.CapabilityCompat.getCapability(attachedTile, EmbersCapabilities.EMBER_CAPABILITY, facing).orElse(null);
			if (cap != null) {
				if (cap.getEmber() > 0 && blockEntity.capability.getEmber() < blockEntity.capability.getEmberCapacity()){
					double removed = cap.removeAmount(PULL_RATE, true);
					blockEntity.capability.addAmount(removed, true);
				}
			}
		}
		if ((blockEntity.ticksExisted + blockEntity.offset) % 20 == 0 && blockEntity.canSendBurst() && blockEntity.capability.getEmber() > PULL_RATE) {
			BlockEntity targetTile = SubLevelCompat.findReachableLinkedTarget(blockEntity, blockEntity.target, blockEntity.targetSubLevelId, blockEntity.targetPhysicalPosition);
			if (targetTile instanceof IEmberPacketReceiver) {
				if (((IEmberPacketReceiver) targetTile).hasRoomFor(TRANSFER_RATE)) {
					EmberPacketEntity packet = RegistryManager.EMBER_PACKET.get().create(blockEntity.level);
					Vec3 velocity = SubLevelCompat.toPhysicalDirection(blockEntity, getBurstVelocity(facing));
					Vec3 start = SubLevelCompat.toPhysicalPosition(blockEntity, Vec3.atCenterOf(pos));
					Vec3 destination = SubLevelCompat.currentTrackedPhysicalPosition(blockEntity, blockEntity.target, blockEntity.targetSubLevelId, blockEntity.targetPhysicalPosition);
					packet.initCustom(start, destination, velocity.x, velocity.y, velocity.z, Math.min(TRANSFER_RATE, blockEntity.capability.getEmber()));
					packet.pos = blockEntity.getBlockPos().immutable();
					packet.setTrackedTarget(blockEntity.target, blockEntity.targetSubLevelId);
					blockEntity.capability.removeAmount(Math.min(TRANSFER_RATE, blockEntity.capability.getEmber()), true);
					blockEntity.level.addFreshEntity(packet);
					level.playSound(null, pos, EmbersSounds.EMBER_EMIT_BIG.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
				}
			}
		}
	}
}
