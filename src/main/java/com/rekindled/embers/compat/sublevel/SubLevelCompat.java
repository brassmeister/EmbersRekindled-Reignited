package com.rekindled.embers.compat.sublevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SubLevelCompat {
	private static Object helper;
	private static Method getContaining;
	private static Method runIncludingSubLevels;
	private static Method logicalPose;
	private static Method transformPosition;
	private static Method transformPositionInverse;
	private static Method transformNormal;
	private static Method transformNormalInverse;
	private static Class<?> subLevelContainerClass;
	private static boolean initialized;

	private SubLevelCompat() {
	}

	private static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		try {
			Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
			Field helperField = sable.getField("HELPER");
			helper = helperField.get(null);
			getContaining = helper.getClass().getMethod("getContaining", BlockEntity.class);
			Class<?> access = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
			runIncludingSubLevels = helper.getClass().getMethod("runIncludingSubLevels", Level.class,
					net.minecraft.core.Position.class, boolean.class, access, BiFunction.class);
			subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
			logicalPose = access.getMethod("logicalPose");
			transformPosition = findMethod(logicalPose.getReturnType(), "transformPosition", "transformPoint", "transform");
			transformPositionInverse = findMethod(logicalPose.getReturnType(), "transformPositionInverse", "inverseTransformPosition", "transformInverse");
			transformNormal = findMethod(logicalPose.getReturnType(), "transformNormal", "transformDirection", "rotateNormal", "rotateDirection");
			transformNormalInverse = findMethod(logicalPose.getReturnType(), "transformNormalInverse", "transformDirectionInverse", "inverseTransformNormal", "inverseTransformDirection");
		} catch (ReflectiveOperationException ignored) {
			helper = null;
		}
	}

	public static @Nullable UUID getContainingSubLevelId(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return null;
		}
		init();
		if (helper == null || getContaining == null) {
			return null;
		}
		try {
			return getSubLevelId(getContaining.invoke(helper, blockEntity));
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	public static @Nullable BlockEntity findLinkedTarget(BlockEntity origin, @Nullable BlockPos targetPosition, @Nullable UUID targetSubLevelId) {
		if (origin == null || targetPosition == null || origin.getLevel() == null) {
			return null;
		}
		if (targetSubLevelId != null) {
			Object subLevel = getSubLevel(origin.getLevel(), targetSubLevelId);
			if (subLevel != null) {
				return findBlockEntityInAccess(origin.getLevel(), subLevel, targetPosition);
			}
			return null;
		}
		BlockEntity target = findAtPhysicalPosition(origin.getLevel(), Vec3.atCenterOf(targetPosition));
		return target != null ? target : findAtPhysicalPosition(origin, targetPosition);
	}

	public static @Nullable BlockEntity findReachableLinkedTarget(BlockEntity origin, @Nullable BlockPos targetPosition, @Nullable UUID targetSubLevelId) {
		BlockEntity target = findLinkedTarget(origin, targetPosition, targetSubLevelId);
		if (target == null || origin == null || origin.getLevel() == null || targetPosition == null) {
			return null;
		}
		if (!targetPosition.equals(target.getBlockPos()) || !Objects.equals(targetSubLevelId, getContainingSubLevelId(target))) {
			return null;
		}
		Vec3 physicalPosition = linkedTargetPhysicalPosition(origin, targetPosition, targetSubLevelId);
		BlockEntity reachableTarget = findAtPhysicalPosition(origin.getLevel(), physicalPosition);
		if (reachableTarget == null) {
			return null;
		}
		return targetPosition.equals(reachableTarget.getBlockPos()) && Objects.equals(targetSubLevelId, getContainingSubLevelId(reachableTarget))
				? target
				: null;
	}

	public static @Nullable BlockEntity findStoredPosition(Level level, @Nullable BlockPos position, @Nullable UUID subLevelId) {
		if (level == null || position == null) {
			return null;
		}
		if (subLevelId != null) {
			Object subLevel = getSubLevel(level, subLevelId);
			return subLevel == null ? null : findBlockEntityInAccess(level, subLevel, position);
		}
		return findAtPosition(level, position);
	}

	public static Vec3 linkedTargetPhysicalPosition(BlockEntity origin, @Nullable BlockPos targetPosition, @Nullable UUID targetSubLevelId) {
		if (targetPosition == null) {
			return origin == null ? Vec3.ZERO : Vec3.atCenterOf(origin.getBlockPos());
		}
		if (origin == null || origin.getLevel() == null || targetSubLevelId == null) {
			return Vec3.atCenterOf(targetPosition);
		}
		Object subLevel = getSubLevel(origin.getLevel(), targetSubLevelId);
		if (subLevel == null) {
			return Vec3.atCenterOf(targetPosition);
		}
		return transformSubLevelPosition(subLevel, Vec3.atCenterOf(targetPosition));
	}

	public static Vec3 storedPhysicalPosition(Level level, @Nullable BlockPos position, @Nullable UUID subLevelId) {
		if (position == null) {
			return Vec3.ZERO;
		}
		if (level == null || subLevelId == null) {
			return Vec3.atCenterOf(position);
		}
		Object subLevel = getSubLevel(level, subLevelId);
		return subLevel == null ? Vec3.atCenterOf(position) : transformSubLevelPosition(subLevel, Vec3.atCenterOf(position));
	}

	public static <T extends Entity> List<T> getEntitiesInPhysicalBounds(BlockEntity origin, Class<T> entityClass, AABB localBounds) {
		if (origin == null || origin.getLevel() == null || entityClass == null || localBounds == null) {
			return List.of();
		}
		AABB physicalBounds = toPhysicalBounds(origin, localBounds);
		List<T> found = origin.getLevel().getEntitiesOfClass(entityClass, physicalBounds);
		if (found.size() < 2) {
			return found;
		}
		List<T> unique = new ArrayList<>(found.size());
		Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (T entity : found) {
			if (entity != null && !entity.isRemoved() && seen.add(entity)) {
				unique.add(entity);
			}
		}
		return unique;
	}

	public static AABB toPhysicalBounds(BlockEntity origin, AABB localBounds) {
		if (origin == null || localBounds == null) {
			return localBounds;
		}
		Vec3[] corners = new Vec3[] {
				new Vec3(localBounds.minX, localBounds.minY, localBounds.minZ),
				new Vec3(localBounds.minX, localBounds.minY, localBounds.maxZ),
				new Vec3(localBounds.minX, localBounds.maxY, localBounds.minZ),
				new Vec3(localBounds.minX, localBounds.maxY, localBounds.maxZ),
				new Vec3(localBounds.maxX, localBounds.minY, localBounds.minZ),
				new Vec3(localBounds.maxX, localBounds.minY, localBounds.maxZ),
				new Vec3(localBounds.maxX, localBounds.maxY, localBounds.minZ),
				new Vec3(localBounds.maxX, localBounds.maxY, localBounds.maxZ)
		};
		Vec3 first = toPhysicalPosition(origin, corners[0]);
		double minX = first.x;
		double minY = first.y;
		double minZ = first.z;
		double maxX = first.x;
		double maxY = first.y;
		double maxZ = first.z;
		for (int i = 1; i < corners.length; i++) {
			Vec3 transformed = toPhysicalPosition(origin, corners[i]);
			minX = Math.min(minX, transformed.x);
			minY = Math.min(minY, transformed.y);
			minZ = Math.min(minZ, transformed.z);
			maxX = Math.max(maxX, transformed.x);
			maxY = Math.max(maxY, transformed.y);
			maxZ = Math.max(maxZ, transformed.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static @Nullable Method findMethod(Class<?> owner, String... names) {
		for (String name : names) {
			try {
				return owner.getMethod(name, Vec3.class);
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return null;
	}

	public static @Nullable BlockEntity findAdjacent(BlockEntity origin, Direction localDirection) {
		Level level = origin.getLevel();
		if (level == null) {
			return null;
		}
		BlockPos directPos = origin.getBlockPos().relative(localDirection);

		init();
		if (helper == null || getContaining == null || runIncludingSubLevels == null) {
			return level.getBlockEntity(directPos);
		}
		try {
			Object containing = getContaining.invoke(helper, origin);
			if (containing == null) {
				return level.getBlockEntity(directPos);
			}
			Object result = runIncludingSubLevels.invoke(helper, level, Vec3.atCenterOf(directPos), true, containing,
					(BiFunction<Object, BlockPos, BlockEntity>) (subLevel, internalPos) -> {
						BlockEntity candidate = findBlockEntityInAccess(level, subLevel, internalPos);
						return candidate == origin ? null : candidate;
					});
			return result instanceof BlockEntity blockEntity ? blockEntity : null;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	public static @Nullable BlockEntity findAtPosition(Level level, BlockPos position) {
		if (level == null || position == null) {
			return null;
		}
		return findAtPhysicalPosition(level, Vec3.atCenterOf(position));
	}

	public static BlockState findBlockState(Level level, BlockPos position) {
		if (level == null || position == null) {
			return null;
		}
		return findBlockState(level, Vec3.atCenterOf(position));
	}

	public static @Nullable BlockEntity findAtPhysicalPosition(BlockEntity origin, BlockPos localPosition) {
		Level level = origin.getLevel();
		if (level == null) {
			return null;
		}
		init();
		if (helper == null || getContaining == null || runIncludingSubLevels == null) {
			return level.getBlockEntity(localPosition);
		}
		try {
			Object containing = getContaining.invoke(helper, origin);
			if (containing == null) {
				return findAtPhysicalPosition(level, Vec3.atCenterOf(localPosition));
			}
			Object result = runIncludingSubLevels.invoke(helper, level, Vec3.atCenterOf(localPosition), true, containing,
					(BiFunction<Object, BlockPos, BlockEntity>) (subLevel, internalPos) -> findBlockEntityInAccess(level, subLevel, internalPos));
			return result instanceof BlockEntity blockEntity ? blockEntity : null;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	public static BlockState findBlockState(BlockEntity origin, BlockPos localPosition) {
		Level level = origin.getLevel();
		if (level == null) {
			return null;
		}
		init();
		if (helper == null || getContaining == null || runIncludingSubLevels == null) {
			return level.getBlockState(localPosition);
		}
		try {
			Object containing = getContaining.invoke(helper, origin);
			if (containing == null) {
				return findBlockState(level, Vec3.atCenterOf(localPosition));
			}
			Object result = runIncludingSubLevels.invoke(helper, level, Vec3.atCenterOf(localPosition), true, containing,
					(BiFunction<Object, BlockPos, BlockState>) (subLevel, internalPos) -> level.getBlockState(internalPos));
			return result instanceof BlockState state ? state : level.getBlockState(localPosition);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return level.getBlockState(localPosition);
		}
	}

	public static boolean isInSubLevel(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return false;
		}
		init();
		if (helper == null || getContaining == null) {
			return false;
		}
		try {
			return getContaining.invoke(helper, blockEntity) != null;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return false;
		}
	}

	public static @Nullable BlockEntity findAtPhysicalPosition(Level level, Vec3 physicalPosition) {
		if (level == null || physicalPosition == null) {
			return null;
		}
		BlockPos directPos = BlockPos.containing(physicalPosition);
		BlockEntity direct = level.getBlockEntity(directPos);
		if (direct != null) {
			return direct;
		}
		init();
		if (helper == null || runIncludingSubLevels == null) {
			return null;
		}
		try {
			Object result = runIncludingSubLevels.invoke(helper, level, physicalPosition, true, null,
					(BiFunction<Object, BlockPos, BlockEntity>) (subLevel, internalPos) -> findBlockEntityInAccess(level, subLevel, internalPos));
			return result instanceof BlockEntity blockEntity ? blockEntity : null;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	public static BlockState findBlockState(Level level, Vec3 physicalPosition) {
		if (level == null || physicalPosition == null) {
			return null;
		}
		BlockPos directPos = BlockPos.containing(physicalPosition);
		BlockState direct = level.getBlockState(directPos);
		if (!direct.isAir()) {
			return direct;
		}
		init();
		if (helper == null || runIncludingSubLevels == null) {
			return direct;
		}
		try {
			Object result = runIncludingSubLevels.invoke(helper, level, physicalPosition, true, null,
					(BiFunction<Object, BlockPos, BlockState>) (subLevel, internalPos) -> level.getBlockState(internalPos));
			return result instanceof BlockState state ? state : direct;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return direct;
		}
	}

	public static Vec3 toPhysicalPosition(BlockEntity origin, BlockPos localPosition) {
		return toPhysicalPosition(origin, Vec3.atCenterOf(localPosition));
	}

	public static Vec3 toPhysicalPosition(BlockEntity origin, Vec3 localPosition) {
		if (origin == null || localPosition == null) {
			return localPosition;
		}
		init();
		if (helper == null || getContaining == null || logicalPose == null || transformPosition == null) {
			return localPosition;
		}
		try {
			Object containing = getContaining.invoke(helper, origin);
			if (containing == null) {
				return localPosition;
			}
			Object pose = logicalPose.invoke(containing);
			Object transformed = transformPosition.invoke(pose, localPosition);
			return transformed instanceof Vec3 vec ? vec : localPosition;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return localPosition;
		}
	}

	private static Vec3 transformSubLevelPosition(Object subLevel, Vec3 localPosition) {
		if (subLevel == null || localPosition == null) {
			return localPosition;
		}
		init();
		if (logicalPose == null || transformPosition == null) {
			return localPosition;
		}
		try {
			Object pose = logicalPose.invoke(subLevel);
			Object transformed = transformPosition.invoke(pose, localPosition);
			return transformed instanceof Vec3 vec ? vec : localPosition;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return localPosition;
		}
	}

	public static Vec3 toLocalPosition(BlockEntity origin, Vec3 physicalPosition) {
		if (origin == null || physicalPosition == null) {
			return physicalPosition;
		}
		init();
		if (helper == null || getContaining == null || logicalPose == null || transformPositionInverse == null) {
			return physicalPosition;
		}
		try {
			Object containing = getContaining.invoke(helper, origin);
			if (containing == null) {
				return physicalPosition;
			}
			Object pose = logicalPose.invoke(containing);
			Object transformed = transformPositionInverse.invoke(pose, physicalPosition);
			return transformed instanceof Vec3 vec ? vec : physicalPosition;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return physicalPosition;
		}
	}

	public static Vec3 toPhysicalDirection(BlockEntity origin, Vec3 localDirection) {
		return transformDirection(origin, localDirection, transformNormal);
	}

	public static Vec3 toLocalDirection(BlockEntity origin, Vec3 physicalDirection) {
		return transformDirection(origin, physicalDirection, transformNormalInverse);
	}

	private static Vec3 transformDirection(BlockEntity origin, Vec3 direction, Method transformMethod) {
		if (origin == null || direction == null || direction.lengthSqr() < 1.0E-8D) {
			return direction;
		}
		init();
		if (helper == null || getContaining == null || logicalPose == null || transformMethod == null) {
			return direction;
		}
		try {
			Object containing = getContaining.invoke(helper, origin);
			if (containing == null) {
				return direction;
			}
			Object pose = logicalPose.invoke(containing);
			Object transformed = transformMethod.invoke(pose, direction);
			if (transformed instanceof Vec3 vec) {
				return vec;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return direction;
	}

	private static @Nullable BlockEntity findBlockEntityInAccess(Level level, @Nullable Object subLevel, BlockPos internalPos) {
		if (subLevel == null) {
			return level.getBlockEntity(internalPos);
		}
		for (BlockEntity blockEntity : getBlockEntities(subLevel)) {
			if (blockEntity != null && !blockEntity.isRemoved() && internalPos.equals(blockEntity.getBlockPos())) {
				return blockEntity;
			}
		}
		return null;
	}

	private static @Nullable Object getSubLevel(Level level, UUID id) {
		if (level == null || id == null) {
			return null;
		}
		init();
		if (subLevelContainerClass == null) {
			return null;
		}
		try {
			Object container = getContainer(level);
			if (container == null) {
				return null;
			}
			return container.getClass().getMethod("getSubLevel", UUID.class).invoke(container, id);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static @Nullable Object getContainer(Level level) throws ReflectiveOperationException {
		if (subLevelContainerClass == null || level == null) {
			return null;
		}
		for (Method method : subLevelContainerClass.getMethods()) {
			if (!method.getName().equals("getContainer") || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> parameter = method.getParameterTypes()[0];
			if (parameter.isInstance(level)) {
				return method.invoke(null, level);
			}
		}
		return null;
	}

	private static @Nullable UUID getSubLevelId(@Nullable Object subLevel) {
		if (subLevel == null) {
			return null;
		}
		try {
			Object id = subLevel.getClass().getMethod("getUniqueId").invoke(subLevel);
			return id instanceof UUID uuid ? uuid : null;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static List<BlockEntity> getBlockEntities(Object subLevel) {
		Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
		if (subLevel == null) {
			return List.of();
		}
		try {
			Object plot = subLevel.getClass().getMethod("getPlot").invoke(subLevel);
			collectActorBlockEntities(plot, blockEntities);
			Object loadedChunks = plot.getClass().getMethod("getLoadedChunks").invoke(plot);
			if (!(loadedChunks instanceof Collection<?> chunks)) {
				return new ArrayList<>(blockEntities.values());
			}
			for (Object chunkHolder : chunks) {
				Object chunk = chunkHolder.getClass().getMethod("getChunk").invoke(chunkHolder);
				Object blockEntityMap = chunk.getClass().getMethod("getBlockEntities").invoke(chunk);
				if (!(blockEntityMap instanceof Map<?, ?> entries)) {
					continue;
				}
				for (Object value : entries.values()) {
					if (value instanceof BlockEntity blockEntity) {
						blockEntities.putIfAbsent(blockEntity.getBlockPos().immutable(), blockEntity);
					}
				}
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return new ArrayList<>(blockEntities.values());
	}

	private static void collectActorBlockEntities(Object plot, Map<BlockPos, BlockEntity> blockEntities) throws ReflectiveOperationException {
		Object actors = plot.getClass().getMethod("getBlockEntityActors").invoke(plot);
		if (!(actors instanceof Iterable<?> actorIterable)) {
			return;
		}
		for (Object actor : actorIterable) {
			if (actor instanceof BlockEntity blockEntity) {
				blockEntities.putIfAbsent(blockEntity.getBlockPos().immutable(), blockEntity);
			}
		}
	}
}
