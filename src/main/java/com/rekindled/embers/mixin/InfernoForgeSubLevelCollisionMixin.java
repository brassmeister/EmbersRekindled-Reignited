package com.rekindled.embers.mixin;

import com.rekindled.embers.block.InfernoForgeBlock;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(InfernoForgeBlock.class)
public class InfernoForgeSubLevelCollisionMixin implements BlockSubLevelCollisionShape {

	@Override
	public VoxelShape getSubLevelCollisionShape(BlockGetter blockGetter, BlockState state) {
		if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return state.getValue(BlockStateProperties.OPEN) ? Shapes.empty() : InfernoForgeBlock.TOP_AABB;
		}
		return InfernoForgeBlock.BOTTOM_AABB;
	}
}
