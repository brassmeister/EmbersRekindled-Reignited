package com.rekindled.embers.blockentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.api.tile.IFluidPipePriority;
import com.rekindled.embers.compat.legacy.capabilities.ForgeCapabilities;
import com.rekindled.embers.compat.sublevel.SubLevelCompat;
import com.rekindled.embers.util.CapabilityCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

public final class PipeNetworkUtil {
	private static final int MAX_VISITED_PIPES = 4096;

	private PipeNetworkUtil() {
	}

	public static ItemStack routeItem(ItemPipeBlockEntityBase origin, @Nullable Direction blockedFirstSide, ItemStack stack, boolean simulate) {
		if (origin == null || origin.getLevel() == null || stack.isEmpty()) {
			return stack;
		}
		List<ItemTarget> targets = findItemTargets(origin, blockedFirstSide);
		if (targets.isEmpty()) {
			return stack;
		}
		ItemStack remainder = stack.copy();
		for (ItemTarget target : targets) {
			ItemStack nextRemainder = insertItem(target.handler(), remainder, true);
			if (nextRemainder.getCount() == remainder.getCount()) {
				continue;
			}
			if (!simulate) {
				nextRemainder = insertItem(target.handler(), remainder, false);
				if (nextRemainder.getCount() < remainder.getCount()) {
					markPath(target.path());
				}
			}
			remainder = nextRemainder;
			if (remainder.isEmpty()) {
				break;
			}
		}
		return remainder;
	}

	public static int routeFluid(FluidPipeBlockEntityBase origin, @Nullable Direction blockedFirstSide, FluidStack stack, FluidAction action) {
		if (origin == null || origin.getLevel() == null || stack.isEmpty()) {
			return 0;
		}
		List<FluidTarget> targets = findFluidTargets(origin, blockedFirstSide);
		if (targets.isEmpty()) {
			return 0;
		}
		int filled = 0;
		for (FluidTarget target : targets) {
			int remaining = stack.getAmount() - filled;
			if (remaining <= 0) {
				break;
			}
			FluidStack offered = stack.copyWithAmount(remaining);
			int accepted = target.handler().fill(offered, FluidAction.SIMULATE);
			if (accepted <= 0) {
				continue;
			}
			if (action.execute()) {
				accepted = target.handler().fill(stack.copyWithAmount(Math.min(accepted, remaining)), FluidAction.EXECUTE);
				if (accepted > 0) {
					markPath(target.path());
				}
			}
			filled += Math.min(accepted, remaining);
		}
		return filled;
	}

	private static List<ItemTarget> findItemTargets(ItemPipeBlockEntityBase origin, @Nullable Direction blockedFirstSide) {
		ArrayList<ItemTarget> targets = new ArrayList<>();
		walk(origin, blockedFirstSide, ItemPipeBlockEntityBase.class, (pipe, direction, path) -> {
			BlockEntity neighbor = SubLevelCompat.findAdjacent(pipe, direction);
			IItemHandler handler = getAdjacentItemHandler(pipe, direction, neighbor);
			if (handler != null) {
				targets.add(new ItemTarget(handler, priority(neighbor, direction.getOpposite()), path));
			}
		});
		targets.sort(Comparator.comparingInt(ItemTarget::priority).thenComparingInt(target -> target.path().size()));
		return targets;
	}

	private static List<FluidTarget> findFluidTargets(FluidPipeBlockEntityBase origin, @Nullable Direction blockedFirstSide) {
		ArrayList<FluidTarget> targets = new ArrayList<>();
		walk(origin, blockedFirstSide, FluidPipeBlockEntityBase.class, (pipe, direction, path) -> {
			BlockEntity neighbor = SubLevelCompat.findAdjacent(pipe, direction);
			IFluidHandler handler = getAdjacentFluidHandler(pipe, direction, neighbor);
			if (handler != null) {
				targets.add(new FluidTarget(handler, priority(neighbor, direction.getOpposite()), path));
			}
		});
		targets.sort(Comparator.comparingInt(FluidTarget::priority).thenComparingInt(target -> target.path().size()));
		return targets;
	}

	private static void walk(PipeBlockEntityBase origin, @Nullable Direction blockedFirstSide, Class<? extends PipeBlockEntityBase> pipeType, EndpointVisitor visitor) {
		Set<PipeBlockEntityBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		ArrayDeque<Node> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(new Node(origin, List.of()));

		while (!queue.isEmpty() && visited.size() <= MAX_VISITED_PIPES) {
			Node node = queue.removeFirst();
			PipeBlockEntityBase pipe = node.pipe();
			for (Direction direction : Direction.values()) {
				if (pipe == origin && direction == blockedFirstSide) {
					continue;
				}
				if (!canUseAdjacentSide(pipe, direction, pipeType)) {
					continue;
				}
				BlockEntity neighbor = SubLevelCompat.findAdjacent(pipe, direction);
				ArrayList<PipeStep> path = new ArrayList<>(node.path());
				path.add(new PipeStep(pipe, direction));
				if (pipeType.isInstance(neighbor) && neighbor instanceof PipeBlockEntityBase nextPipe && canTraverse(origin, nextPipe)
						&& nextPipe.getConnection(direction.getOpposite()).transfer) {
					if (visited.add(nextPipe)) {
						queue.addLast(new Node(nextPipe, path));
					}
				} else {
					visitor.visit(pipe, direction, path);
				}
			}
		}
	}

	static boolean canUseAdjacentSide(PipeBlockEntityBase pipe, Direction direction, Class<? extends PipeBlockEntityBase> pipeType) {
		PipeBlockEntityBase.PipeConnection connection = pipe.getConnection(direction);
		if (connection.transfer) {
			return true;
		}
		if (connection != PipeBlockEntityBase.PipeConnection.NONE || !hasLiveEndpoint(pipe, direction, pipeType)) {
			return false;
		}
		pipe.setConnection(direction, PipeBlockEntityBase.PipeConnection.END);
		return true;
	}

	static IItemHandler getAdjacentItemHandler(PipeBlockEntityBase pipe, Direction direction) {
		return getAdjacentItemHandler(pipe, direction, SubLevelCompat.findAdjacent(pipe, direction));
	}

	static IFluidHandler getAdjacentFluidHandler(PipeBlockEntityBase pipe, Direction direction) {
		return getAdjacentFluidHandler(pipe, direction, SubLevelCompat.findAdjacent(pipe, direction));
	}

	private static boolean hasLiveEndpoint(PipeBlockEntityBase pipe, Direction direction, Class<? extends PipeBlockEntityBase> pipeType) {
		BlockEntity neighbor = SubLevelCompat.findAdjacent(pipe, direction);
		if (pipeType.isInstance(neighbor)) {
			return false;
		}
		if (pipeType == ItemPipeBlockEntityBase.class) {
			return getAdjacentItemHandler(pipe, direction, neighbor) != null;
		}
		if (pipeType == FluidPipeBlockEntityBase.class) {
			return getAdjacentFluidHandler(pipe, direction, neighbor) != null;
		}
		return false;
	}

	private static IItemHandler getAdjacentItemHandler(PipeBlockEntityBase pipe, Direction direction, @Nullable BlockEntity neighbor) {
		if (neighbor instanceof ItemPipeBlockEntityBase) {
			return null;
		}
		Direction side = direction.getOpposite();
		if (neighbor != null) {
			IItemHandler handler = CapabilityCompat.getCapability(neighbor, ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
			if (handler != null) {
				return handler;
			}
		}
		Level level = pipe.getLevel();
		BlockPos targetPos = pipe.getBlockPos().relative(direction);
		if (level == null || (neighbor == null && level.isEmptyBlock(targetPos))) {
			return null;
		}
		return CapabilityCompat.getItemHandler(level, targetPos, side).orElse(null);
	}

	private static IFluidHandler getAdjacentFluidHandler(PipeBlockEntityBase pipe, Direction direction, @Nullable BlockEntity neighbor) {
		if (neighbor instanceof FluidPipeBlockEntityBase) {
			return null;
		}
		Direction side = direction.getOpposite();
		if (neighbor != null) {
			IFluidHandler handler = CapabilityCompat.getCapability(neighbor, ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
			if (handler != null) {
				return handler;
			}
		}
		Level level = pipe.getLevel();
		BlockPos targetPos = pipe.getBlockPos().relative(direction);
		if (level == null || (neighbor == null && level.isEmptyBlock(targetPos))) {
			return null;
		}
		return CapabilityCompat.getFluidHandler(level, targetPos, side).orElse(null);
	}

	private static boolean canTraverse(PipeBlockEntityBase origin, PipeBlockEntityBase nextPipe) {
		if (nextPipe == origin || nextPipe.getLevel() == null) {
			return true;
		}
		if (nextPipe instanceof ItemExtractorBlockEntity || nextPipe instanceof FluidExtractorBlockEntity) {
			return !ConfigManager.isRedstoneControlActive(nextPipe.getLevel(), nextPipe.getBlockPos());
		}
		return true;
	}

	private static int priority(@Nullable BlockEntity blockEntity, Direction side) {
		if (blockEntity instanceof IItemPipePriority itemPriority) {
			return itemPriority.getPriority(side);
		}
		if (blockEntity instanceof IFluidPipePriority fluidPriority) {
			return fluidPriority.getPriority(side);
		}
		return PipeBlockEntityBase.PRIORITY_BLOCK;
	}

	private static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
		ItemStack remainder = stack.copy();
		for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
			remainder = handler.insertItem(slot, remainder, simulate);
		}
		return remainder;
	}

	private static void markPath(List<PipeStep> path) {
		for (PipeStep step : path) {
			PipeBlockEntityBase pipe = step.pipe();
			if (!(pipe instanceof ItemExtractorBlockEntity) && !(pipe instanceof FluidExtractorBlockEntity)
					&& !(pipe instanceof ItemTransferBlockEntity) && !(pipe instanceof FluidTransferBlockEntity)) {
				continue;
			}
			if (pipe.lastTransfer != step.direction()) {
				pipe.lastTransfer = step.direction();
				pipe.syncTransfer = true;
			}
			if (pipe.clogged) {
				pipe.clogged = false;
				pipe.syncCloggedFlag = true;
			}
			pipe.setChanged();
		}
	}

	private interface EndpointVisitor {
		void visit(PipeBlockEntityBase pipe, Direction direction, List<PipeStep> path);
	}

	private record Node(PipeBlockEntityBase pipe, List<PipeStep> path) {
	}

	private record PipeStep(PipeBlockEntityBase pipe, Direction direction) {
	}

	private record ItemTarget(IItemHandler handler, int priority, List<PipeStep> path) {
	}

	private record FluidTarget(IFluidHandler handler, int priority, List<PipeStep> path) {
	}
}
