package com.rekindled.embers.entity;

import java.util.UUID;

import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.api.power.IEmberCapability;
import com.rekindled.embers.api.power.IEmberPacketReceiver;
import com.rekindled.embers.compat.sublevel.SubLevelCompat;
import com.rekindled.embers.particle.GlowParticleOptions;
import com.rekindled.embers.particle.StarParticleOptions;
import com.rekindled.embers.util.EmbersColors;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class EmberPacketEntity extends Entity {

	public BlockPos pos = new BlockPos(0,0,0);
	public BlockPos dest = new BlockPos(0,0,0);
	public BlockPos trackedTarget = null;
	public UUID trackedTargetSubLevelId = null;
	public UUID lastReceiverSubLevelId = null;
	public double value = 0;
	public static final EntityDataAccessor<Integer> lifetime = SynchedEntityData.defineId(EmberPacketEntity.class, EntityDataSerializers.INT);

	public EmberPacketEntity(EntityType<?> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.noPhysics = true;
	}

	public void initCustom(BlockPos pos, BlockPos dest, double vx, double vy, double vz, double value) {
		this.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		this.setDeltaMovement(vx, vy, vz);
		this.dest = dest;
		this.pos = pos;
		this.value = value;
	}

	public void initCustom(Vec3 pos, Vec3 dest, double vx, double vy, double vz, double value) {
		this.moveTo(pos.x, pos.y, pos.z);
		this.setDeltaMovement(vx, vy, vz);
		this.dest = BlockPos.containing(dest);
		this.pos = BlockPos.containing(pos);
		this.value = value;
	}

	public void setLifetime(int time) {
		getEntityData().set(lifetime, time);
	}

	public void setTrackedTarget(BlockPos target, UUID subLevelId) {
		trackedTarget = target;
		trackedTargetSubLevelId = subLevelId;
	}

	public boolean wasLastReceivedBy(BlockEntity blockEntity) {
		return pos.equals(blockEntity.getBlockPos())
				&& java.util.Objects.equals(lastReceiverSubLevelId, SubLevelCompat.getContainingSubLevelId(blockEntity));
	}

	public void setLastReceiver(BlockEntity blockEntity) {
		pos = blockEntity.getBlockPos().immutable();
		lastReceiverSubLevelId = SubLevelCompat.getContainingSubLevelId(blockEntity);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(lifetime, 80);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		if (nbt.contains("destX")){
			dest = new BlockPos(nbt.getInt("destX"), nbt.getInt("destY"), nbt.getInt("destZ"));
		}
		if (nbt.contains("sourceX")) {
			pos = new BlockPos(nbt.getInt("sourceX"), nbt.getInt("sourceY"), nbt.getInt("sourceZ"));
		}
		if (nbt.contains("targetX")) {
			trackedTarget = new BlockPos(nbt.getInt("targetX"), nbt.getInt("targetY"), nbt.getInt("targetZ"));
		}
		if (nbt.contains("targetSubLevel")) {
			try {
				trackedTargetSubLevelId = UUID.fromString(nbt.getString("targetSubLevel"));
			} catch (IllegalArgumentException ignored) {
				trackedTargetSubLevelId = null;
			}
		}
		if (nbt.contains("lastReceiverSubLevel")) {
			try {
				lastReceiverSubLevelId = UUID.fromString(nbt.getString("lastReceiverSubLevel"));
			} catch (IllegalArgumentException ignored) {
				lastReceiverSubLevelId = null;
			}
		}
		value = nbt.getDouble("value");
		getEntityData().set(lifetime, nbt.getInt("lifetime"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		if (dest != null){
			nbt.putInt("destX", dest.getX());
			nbt.putInt("destY", dest.getY());
			nbt.putInt("destZ", dest.getZ());
		}
		if (pos != null) {
			nbt.putInt("sourceX", pos.getX());
			nbt.putInt("sourceY", pos.getY());
			nbt.putInt("sourceZ", pos.getZ());
		}
		if (trackedTarget != null) {
			nbt.putInt("targetX", trackedTarget.getX());
			nbt.putInt("targetY", trackedTarget.getY());
			nbt.putInt("targetZ", trackedTarget.getZ());
		}
		if (trackedTargetSubLevelId != null) {
			nbt.putString("targetSubLevel", trackedTargetSubLevelId.toString());
		}
		if (lastReceiverSubLevelId != null) {
			nbt.putString("lastReceiverSubLevel", lastReceiverSubLevelId.toString());
		}
		nbt.putDouble("value", value);
		nbt.putInt("lifetime", getEntityData().get(lifetime));
	}

	public void tick() {
		//super.tick();
		int lifetime = getEntityData().get(EmberPacketEntity.lifetime);
		getEntityData().set(EmberPacketEntity.lifetime, lifetime - 1);
		lifetime --;
		if (lifetime <= 0) {
			this.remove(RemovalReason.DISCARDED);
		}
		if (!this.isRemoved()) {
			Vec3 oldPosition = new Vec3(getX(), getY(), getZ());
			Vec3 currentTarget = getCurrentTargetPosition();
			if (currentTarget != null) {
				setDeltaMovement(calculateNextMovement(position(), currentTarget, getDeltaMovement()));
			}
			move(MoverType.SELF, getDeltaMovement());

			BlockEntity trackedTargetBlockEntity = getTrackedTargetBlockEntity();
			Vec3 trackedTargetCenter = trackedTarget == null ? null : SubLevelCompat.storedPhysicalPosition(level(), trackedTarget, trackedTargetSubLevelId);
			if (trackedTargetBlockEntity != null && trackedTargetCenter != null && isInsideCenterBox(trackedTargetCenter)) {
				affectTileEntity(trackedTargetBlockEntity.getBlockState(), trackedTargetBlockEntity);
			} else {
				BlockPos pos = blockPosition();
				if (getX() > pos.getX()+0.25 && getX() < pos.getX()+0.75 && getY() > pos.getY()+0.25 && this.getY() < pos.getY()+0.75 && getZ() > pos.getZ()+0.25 && getZ() < pos.getZ()+0.75) {
					BlockEntity blockEntity = SubLevelCompat.findAtPhysicalPosition(level(), position());
					BlockState blockState = blockEntity != null ? blockEntity.getBlockState() : level().getBlockState(blockPosition());
					affectTileEntity(blockState, blockEntity);
				}
			}
			if (level().isClientSide() && lifetime != 80) {
				if (lifetime == 79) {
					for (double i = 0; i < 12; i ++) {
						level().addParticle(new StarParticleOptions(EmbersColors.EMBER_ID, 3.5f + 0.5f * random.nextFloat()), getX(), getY(), getZ(), 0.125f*(random.nextFloat()-0.5f), 0.125f*(random.nextFloat()-0.5f), 0.125f*(random.nextFloat()-0.5f));
					}
				}
				double deltaX = getX() - oldPosition.x;
				double deltaY = getY() - oldPosition.y;
				double deltaZ = getZ() - oldPosition.z;
				double dist = Math.ceil(Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 20);
				for (double i = 0; i < dist; i ++) {
					double coeff = i / dist;
					level().addParticle(GlowParticleOptions.EMBER, oldPosition.x + deltaX * coeff, oldPosition.y + deltaY * coeff, oldPosition.z + deltaZ * coeff, 0.125f*(random.nextFloat()-0.5f), 0.125f*(random.nextFloat()-0.5f), 0.125f*(random.nextFloat()-0.5f));
				}
			}
		}
	}

	public static Vec3 calculateNextMovement(Vec3 position, Vec3 target, Vec3 currentMovement) {
		Vec3 offset = target.subtract(position);
		double distance = offset.length();
		if (!Double.isFinite(distance) || distance <= 1.0E-6D) {
			return Vec3.ZERO;
		}
		if (distance <= 0.5D) {
			return offset;
		}

		Vec3 targetDirection = offset.scale(1.0D / distance);
		if (currentMovement == null || currentMovement.lengthSqr() <= 1.0E-8D) {
			return targetDirection.scale(0.5D);
		}

		Vec3 currentDirection = currentMovement.normalize();
		double alignment = currentDirection.dot(targetDirection);
		double proximityTurn = Math.max(0.0D, (4.0D - distance) / 4.0D) * 0.45D;
		double reverseTurn = Math.max(0.0D, -alignment) * 0.45D;
		double turnWeight = Math.min(0.9D, 0.18D + proximityTurn + reverseTurn);
		Vec3 direction = currentDirection.scale(1.0D - turnWeight).add(targetDirection.scale(turnWeight));
		if (direction.lengthSqr() <= 1.0E-8D) {
			direction = targetDirection;
		}
		return direction.normalize().scale(Math.min(0.5D, distance));
	}

	private Vec3 getCurrentTargetPosition() {
		if (trackedTarget != null) {
			Vec3 currentTarget = SubLevelCompat.currentTrackedPhysicalPosition(level(), trackedTarget, trackedTargetSubLevelId, null);
			dest = BlockPos.containing(currentTarget);
			return currentTarget;
		}
		if (dest.getX() == 0 && dest.getY() == 0 && dest.getZ() == 0) {
			return null;
		}
		return Vec3.atCenterOf(dest);
	}

	private BlockEntity getTrackedTargetBlockEntity() {
		if (trackedTarget == null) {
			return null;
		}
		return SubLevelCompat.findStoredPosition(level(), trackedTarget, trackedTargetSubLevelId);
	}

	private boolean isInsideCenterBox(Vec3 targetCenter) {
		return Math.abs(getX() - targetCenter.x) < 0.25D
				&& Math.abs(getY() - targetCenter.y) < 0.25D
				&& Math.abs(getZ() - targetCenter.z) < 0.25D;
	}

	public void affectTileEntity(BlockState state, BlockEntity blockEntity) {
		if (blockEntity instanceof IEmberPacketReceiver && getEntityData().get(lifetime) > 1) {
			if (((IEmberPacketReceiver) blockEntity).onReceive(this)) {
				IEmberCapability capability = com.rekindled.embers.util.CapabilityCompat.getCapability(blockEntity, EmbersCapabilities.EMBER_CAPABILITY).orElse(null);
				if (capability != null) {
					capability.addAmount(value, true);
					blockEntity.setChanged();
				}
				setDeltaMovement(0, 0, 0);
				//stay alive for one more tick for the sake of the particles reaching the receptor properly
				getEntityData().set(lifetime, 2);
			}
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		return new ClientboundAddEntityPacket(this, serverEntity);
	}
}
