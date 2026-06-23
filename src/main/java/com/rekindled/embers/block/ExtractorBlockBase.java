package com.rekindled.embers.block;

import com.rekindled.embers.blockentity.PipeBlockEntityBase;
import com.rekindled.embers.blockentity.PipeNetworkUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class ExtractorBlockBase extends PipeBlockBase {

	public static final int WORK_TICK_INTERVAL = 8;
	public static final int IDLE_TICK_INTERVAL = 40;
	public static final VoxelShape EXTRACTOR_AABB = Block.box(5,5,5,11,11,11);
	public static final VoxelShape[] EXTRACTOR_SHAPES = new VoxelShape[729];

	static {
		makeShapes(EXTRACTOR_AABB, EXTRACTOR_SHAPES);
	}

	public ExtractorBlockBase(Properties pProperties) {
		super(pProperties);
	}

	@Override
	public VoxelShape getCenterShape() {
		return EXTRACTOR_AABB;
	}

	public static void scheduleExtractorTick(Level level, BlockPos pos, Block block, int delay) {
		if (!level.isClientSide) {
			level.scheduleTick(pos, block, Math.max(1, delay));
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		if (!state.is(oldState.getBlock())) {
			PipeNetworkUtil.invalidateCaches();
			scheduleExtractorTick(level, pos, this, 1);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		PipeNetworkUtil.invalidateCaches();
		scheduleExtractorTick(level, pos, this, 1);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		BlockEntity BE = level.getBlockEntity(pos);
		if (BE instanceof PipeBlockEntityBase pipe) {
			return EXTRACTOR_SHAPES[getShapeIndex(pipe.connections[0], pipe.connections[1], pipe.connections[2], pipe.connections[3], pipe.connections[4], pipe.connections[5])];
		}
		return CENTER_AABB;
	}

	@Override
	public boolean connected(Direction direction, BlockState state) {
		return EmberEmitterBlock.connectsToAttachable(direction, state);
	}

	@Override
	public VoxelShape getBlockSupportShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
		return Shapes.block();
	}
}
