package com.rekindled.embers.block;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.blockentity.ItemExtractorBlockEntity;
import com.rekindled.embers.blockentity.ItemPipeBlockEntityBase;
import com.rekindled.embers.datagen.EmbersBlockTags;
import com.rekindled.embers.util.Misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class ItemExtractorBlock extends ExtractorBlockBase {

	public ItemExtractorBlock(Properties pProperties) {
		super(pProperties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
		return RegistryManager.ITEM_EXTRACTOR_ENTITY.get().create(pPos, pState);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
		return pLevel.isClientSide ? createTickerHelper(pBlockEntityType, RegistryManager.ITEM_EXTRACTOR_ENTITY.get(), ItemExtractorBlockEntity::clientTick) : null;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof ItemExtractorBlockEntity extractor) {
			int nextDelay = ItemExtractorBlockEntity.scheduledServerTick(level, pos, state, extractor);
			if (nextDelay > 0) {
				scheduleExtractorTick(level, pos, this, nextDelay);
			}
		}
	}

	@Override
	public TagKey<Block> getConnectionTag() {
		return EmbersBlockTags.ITEM_PIPE_CONNECTION;
	}

	@Override
	public TagKey<Block> getToggleConnectionTag() {
		return EmbersBlockTags.ITEM_PIPE_CONNECTION_TOGGLEABLE;
	}

	@Override
	public boolean connectToTile(BlockEntity blockEntity, Direction direction) {
		return blockEntity != null && com.rekindled.embers.util.CapabilityCompat.getCapability(blockEntity, ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).isPresent();
	}

	@Override
	public boolean connectToTile(LevelAccessor level, BlockPos pos, BlockState state, BlockEntity blockEntity, Direction direction) {
		if (level instanceof Level actualLevel && com.rekindled.embers.util.CapabilityCompat.getItemHandler(actualLevel, pos, direction.getOpposite()).isPresent()) {
			return true;
		}
		return connectToTile(blockEntity, direction);
	}

	@Override
	public boolean unclog(BlockEntity blockEntity, Level level, BlockPos pos) {
		if (blockEntity instanceof ItemPipeBlockEntityBase pipeEntity && pipeEntity.clogged) {
			IItemHandler handler = com.rekindled.embers.util.CapabilityCompat.getCapability(blockEntity, ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
			if (handler instanceof IItemHandlerModifiable) {
				Misc.spawnInventoryInWorld(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, handler);
				level.updateNeighbourForOutputSignal(pos, this);
				((IItemHandlerModifiable) handler).setStackInSlot(0, ItemStack.EMPTY);
				return true;
			}
		}
		return false;
	}
}
