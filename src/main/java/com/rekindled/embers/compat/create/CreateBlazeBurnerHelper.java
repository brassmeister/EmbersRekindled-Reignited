package com.rekindled.embers.compat.create;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.upgrades.UpgradeContext;
import com.rekindled.embers.api.upgrades.UpgradeUtil;
import com.rekindled.embers.block.MechEdgeBlockBase;
import com.rekindled.embers.blockentity.HearthCoilBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class CreateBlazeBurnerHelper {
	private static final int MAX_GROUP_RADIUS = 2;
	private static final int MAX_GROUP_SIZE = 9;
	private static final int MAX_GROUP_SPAN = 3;
	private static final Direction[] HORIZONTAL_DIRECTIONS = {
			Direction.NORTH,
			Direction.SOUTH,
			Direction.EAST,
			Direction.WEST
	};

	private CreateBlazeBurnerHelper() {
	}

	public static List<EmberFueledBlazeBurner> getConnectedBurners(Level level, BlockPos origin) {
		BlockEntity originBlockEntity = level.getBlockEntity(origin);
		if (!(originBlockEntity instanceof EmberFueledBlazeBurner originBurner)) {
			return List.of();
		}

		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		List<BlockPos> burners = new ArrayList<>();

		queue.add(origin);
		while (!queue.isEmpty()) {
			BlockPos current = queue.removeFirst();
			if (!visited.add(current)) {
				continue;
			}
			if (current.getY() != origin.getY()
					|| Math.abs(current.getX() - origin.getX()) > MAX_GROUP_RADIUS
					|| Math.abs(current.getZ() - origin.getZ()) > MAX_GROUP_RADIUS) {
				continue;
			}

			BlockEntity currentBlockEntity = level.getBlockEntity(current);
			if (!(currentBlockEntity instanceof EmberFueledBlazeBurner)) {
				continue;
			}

			burners.add(current.immutable());

			for (Direction direction : HORIZONTAL_DIRECTIONS) {
				BlockPos next = current.relative(direction);
				if (next.getY() != origin.getY()) {
					continue;
				}
				if (Math.abs(next.getX() - origin.getX()) > MAX_GROUP_RADIUS
						|| Math.abs(next.getZ() - origin.getZ()) > MAX_GROUP_RADIUS) {
					continue;
				}
				queue.add(next);
			}
		}

		List<BlockPos> selectedBurners = selectBurnerWindow(origin, burners);
		if (selectedBurners.isEmpty()) {
			selectedBurners = new ArrayList<>(List.of(origin.immutable()));
		}

		selectedBurners.sort(Comparator
				.comparingInt((BlockPos pos) -> Math.abs(pos.getX() - origin.getX()) + Math.abs(pos.getZ() - origin.getZ()))
				.thenComparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ));

		List<EmberFueledBlazeBurner> connectedBurners = new ArrayList<>(selectedBurners.size());
		for (BlockPos burnerPos : selectedBurners) {
			BlockEntity blockEntity = level.getBlockEntity(burnerPos);
			if (blockEntity instanceof EmberFueledBlazeBurner burner) {
				connectedBurners.add(burner);
			}
		}
		return connectedBurners;
	}

	private static List<BlockPos> selectBurnerWindow(BlockPos origin, List<BlockPos> burners) {
		List<BlockPos> best = List.of();
		int bestDistance = Integer.MAX_VALUE;
		for (int minX = origin.getX() - MAX_GROUP_SPAN + 1; minX <= origin.getX(); minX++) {
			for (int minZ = origin.getZ() - MAX_GROUP_SPAN + 1; minZ <= origin.getZ(); minZ++) {
				List<BlockPos> candidate = new ArrayList<>();
				int distance = 0;
				for (BlockPos burner : burners) {
					if (burner.getX() >= minX && burner.getX() < minX + MAX_GROUP_SPAN
							&& burner.getZ() >= minZ && burner.getZ() < minZ + MAX_GROUP_SPAN) {
						candidate.add(burner);
						distance += Math.abs(burner.getX() - origin.getX()) + Math.abs(burner.getZ() - origin.getZ());
					}
				}
				if (candidate.size() > MAX_GROUP_SIZE) {
					continue;
				}
				if (candidate.size() > best.size() || candidate.size() == best.size() && distance < bestDistance) {
					best = candidate;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	public static boolean isHeatedByHearthCoil(Level level, BlockPos burnerPos) {
		HearthCoilBlockEntity hearthCoil = getSupportingHearthCoil(level, burnerPos);
		return hearthCoil != null && hearthCoil.heat > 0;
	}

	public static boolean isHearthCoilSuperheating(Level level, BlockPos burnerPos) {
		HearthCoilBlockEntity hearthCoil = getSupportingHearthCoil(level, burnerPos);
		if (hearthCoil == null || hearthCoil.heat <= 0) {
			return false;
		}
		BlockEntity burner = level.getBlockEntity(burnerPos);
		if (burner == null) {
			return false;
		}

		List<UpgradeContext> upgrades = HearthCoilBlockEntity.getHearthCoilUpgrades(level, hearthCoil.getBlockPos());
		UpgradeUtil.verifyUpgrades(burner, upgrades);
		return isHearthCoilSuperheating(burner, upgrades);
	}

	public static boolean isHearthCoilSuperheating(BlockEntity burner, List<UpgradeContext> upgrades) {
		return UpgradeUtil.getOtherParameter(burner, "create_blaze_burner_superheat", false, upgrades)
				|| UpgradeUtil.getOtherParameter(burner, "create_blaze_burner_heat",
						BlazeBurnerBlock.HeatLevel.KINDLED, upgrades).isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING);
	}

	public static int countSuperheatingBurnersOnHearthCoil(Level level, BlockPos hearthCoilPos, List<UpgradeContext> hearthCoilUpgrades) {
		int burners = 0;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				BlockEntity blockEntity = level.getBlockEntity(hearthCoilPos.offset(x, 1, z));
				if (blockEntity instanceof EmberFueledBlazeBurner) {
					List<UpgradeContext> burnerUpgrades = copyUpgrades(hearthCoilUpgrades);
					UpgradeUtil.verifyUpgrades(blockEntity, burnerUpgrades);
					if (isHearthCoilSuperheating(blockEntity, burnerUpgrades)) {
						burners++;
					}
				}
			}
		}
		return burners;
	}

	private static List<UpgradeContext> copyUpgrades(List<UpgradeContext> upgrades) {
		List<UpgradeContext> copy = new ArrayList<>(upgrades.size());
		for (UpgradeContext upgrade : upgrades) {
			copy.add(new UpgradeContext(upgrade.upgrade(), upgrade.distance(), upgrade.count()));
		}
		return copy;
	}

	public static int countBurnersOnHearthCoil(Level level, BlockPos hearthCoilPos) {
		int burners = 0;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (level.getBlockEntity(hearthCoilPos.offset(x, 1, z)) instanceof EmberFueledBlazeBurner) {
					burners++;
				}
			}
		}
		return burners;
	}

	private static HearthCoilBlockEntity getSupportingHearthCoil(Level level, BlockPos burnerPos) {
		BlockPos hearthCoilPos = resolveHearthCoilCenter(level, burnerPos.below());
		if (hearthCoilPos == null) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(hearthCoilPos);
		if (blockEntity instanceof HearthCoilBlockEntity hearthCoil) {
			return hearthCoil;
		}
		return null;
	}

	private static BlockPos resolveHearthCoilCenter(Level level, BlockPos pos) {
		BlockState blockState = level.getBlockState(pos);
		if (blockState.is(RegistryManager.HEARTH_COIL.get())) {
			return pos;
		}
		if (blockState.is(RegistryManager.HEARTH_COIL_EDGE.get()) && blockState.hasProperty(MechEdgeBlockBase.EDGE)) {
			return pos.offset(blockState.getValue(MechEdgeBlockBase.EDGE).centerPos);
		}
		return null;
	}
}
