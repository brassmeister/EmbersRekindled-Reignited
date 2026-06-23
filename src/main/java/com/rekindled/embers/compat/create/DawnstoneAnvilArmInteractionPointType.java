package com.rekindled.embers.compat.create;

import com.rekindled.embers.Embers;
import com.rekindled.embers.block.DawnstoneAnvilBlock;
import com.rekindled.embers.blockentity.DawnstoneAnvilBlockEntity;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class DawnstoneAnvilArmInteractionPointType extends ArmInteractionPointType {
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Embers.MODID, "dawnstone_anvil");

	private DawnstoneAnvilArmInteractionPointType() {
	}

	public static void register(RegisterEvent event) {
		event.register(CreateRegistries.ARM_INTERACTION_POINT_TYPE, ID, DawnstoneAnvilArmInteractionPointType::new);
	}

	@Override
	public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
		return state.getBlock() instanceof DawnstoneAnvilBlock
				&& level.getBlockEntity(pos) instanceof DawnstoneAnvilBlockEntity;
	}

	@Override
	public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
		return new DawnstoneAnvilArmInteractionPoint(this, level, pos, state);
	}

	@Override
	public int getPriority() {
		return 100;
	}

	private static final class DawnstoneAnvilArmInteractionPoint extends ArmInteractionPoint {
		private DawnstoneAnvilArmInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return Vec3.atLowerCornerOf(pos).add(0.5D, 1.0D, 0.5D);
		}
	}
}
