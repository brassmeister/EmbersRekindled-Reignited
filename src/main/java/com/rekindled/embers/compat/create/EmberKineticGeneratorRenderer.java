package com.rekindled.embers.compat.create;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class EmberKineticGeneratorRenderer extends KineticBlockEntityRenderer<EmberKineticGeneratorBlockEntity> {
	private static final float SHAFT_OUTSET = 1.0f / 16.0f;

	public EmberKineticGeneratorRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected BlockState getRenderedBlockState(EmberKineticGeneratorBlockEntity blockEntity) {
		return shaft(getRotationAxisOf(blockEntity));
	}

	@Override
	protected SuperByteBuffer getRotatedModel(EmberKineticGeneratorBlockEntity blockEntity, BlockState state) {
		Direction facing = blockEntity.getBlockState().getValue(EmberKineticGeneratorBlock.FACING);
		return CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF, blockEntity.getBlockState(), facing)
				.translate(facing.getStepX() * SHAFT_OUTSET, facing.getStepY() * SHAFT_OUTSET, facing.getStepZ() * SHAFT_OUTSET);
	}
}
