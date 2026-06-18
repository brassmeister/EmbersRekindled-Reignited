package com.rekindled.embers.apiimpl;

import com.rekindled.embers.util.ItemData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.augment.IAugment;
import com.rekindled.embers.api.augment.IAugmentUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class AugmentUtilImpl implements IAugmentUtil {

	@Override
	public Collection<IAugment> getAllAugments() {
		return RegistryManager.augmentRegistry.values();
	}

	@Override
	public IAugment getAugment(ResourceLocation name) {
		return RegistryManager.augmentRegistry.get(name);
	}

	@Override
	public List<IAugment> getAugments(ItemStack stack) {
		if (hasHeat(stack)) {
			ListTag tagAugments = ItemData.getOrCreateTag(stack).getCompound(IAugmentUtil.HEAT_TAG).getList("augments", Tag.TAG_COMPOUND);
			if (tagAugments.size() > 0) {
				List<IAugment> results = new ArrayList<>();
				for (int i = 0; i < tagAugments.size(); i++) {
					CompoundTag tagAugment = tagAugments.getCompound(i);
					IAugment augment = getAugment(ResourceLocation.parse(tagAugment.getString("name")));
					if (augment != null)
						results.add(augment);
				}
				return results;
			}
		}
		return Lists.newArrayList();
	}

	@Override
	public int getTotalAugmentLevel(ItemStack stack) {
		int total = 0;
		if (hasHeat(stack)) {
			ListTag list = ItemData.getOrCreateTag(stack).getCompound(HEAT_TAG).getList("augments", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i ++) {
				CompoundTag compound = list.getCompound(i);
				IAugment augment = getAugment(ResourceLocation.parse(compound.getString("name")));
				if (augment.countTowardsTotalLevel()) {
					total += compound.getInt("level");
				}
			}
		}
		return total;
	}

	@Override
	public boolean hasAugment(ItemStack stack, IAugment augment) {
		if (hasHeat(stack)) {
			ListTag list = ItemData.getOrCreateTag(stack).getCompound(HEAT_TAG).getList("augments", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i ++) {
				CompoundTag compound = list.getCompound(i);
				if (compound.contains("name")) {
					if (compound.getString("name").compareTo(augment.getName().toString()) == 0) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public void addAugment(ItemStack stack, ItemStack augmentStack, IAugment augment) {
		ItemData.updateTag(stack, tagCompound -> {
			CompoundTag heatTag = getOrCreateHeatTag(tagCompound);
			ListTag list = getOrCreateAugmentList(heatTag);
			int level = 0;
			for (int i = 0; i < list.size(); i ++) {
				CompoundTag compound = list.getCompound(i);
				if (compound.contains("name") && compound.getString("name").compareTo(augment.getName().toString()) == 0) {
					level = compound.getInt("level");
					break;
				}
			}
			if (level == 0) {
				CompoundTag augmentCompound = new CompoundTag();
				augmentCompound.putString("name", augment.getName().toString());
				ListTag items = new ListTag();
				augmentCompound.put("items", items);
				items.add(ItemData.save(augmentStack));
				augmentCompound.putInt("level", 1);
				list.add(augmentCompound);
			} else {
				for (int i = 0; i < list.size(); i ++) {
					CompoundTag augmentCompound = list.getCompound(i);
					if (augmentCompound.contains("name")) {
						if (augmentCompound.getString("name").compareTo(augment.getName().toString()) == 0) {
							ListTag items = augmentCompound.getList("items", Tag.TAG_COMPOUND);
							items.add(ItemData.save(augmentStack));
							augmentCompound.putInt("level", level + 1);
						}
					}
				}
			}
		});
		augment.onApply(stack);
	}

	@Override
	public List<ItemStack> removeAllAugments(ItemStack stack) {
		if (hasHeat(stack)) {
			CompoundTag tagCompound = ItemData.getOrCreateTag(stack);
			ListTag tagAugments = tagCompound.getCompound(IAugmentUtil.HEAT_TAG).getList("augments", Tag.TAG_COMPOUND);
			if (tagAugments.size() > 0) { //TODO: cleanup
				List<ItemStack> results = new ArrayList<>();
				ListTag remainingAugments = new ListTag();
				List<IAugment> removedAugments = new ArrayList<>();
				for (int i = 0; i < tagAugments.size(); i ++) {
					CompoundTag tagAugment = tagAugments.getCompound(i);
					IAugment augment = getAugment(ResourceLocation.parse(tagAugment.getString("name")));
					if (augment != null) {
						if (augment.canRemove()) {
							for (int j = 0; j < tagAugment.getInt("level"); j++) {
								removedAugments.add(augment);
							}
							if (tagAugment.contains("items")) {
								ListTag items = tagAugment.getList("items", Tag.TAG_COMPOUND);
								for (int j = 0; j < items.size(); j ++) {
									results.add(ItemData.parse(items.getCompound(j)));
								}
							}
						} else {
							remainingAugments.add(tagAugment);
						}
					}
				}
				ItemData.updateTagElement(stack, IAugmentUtil.HEAT_TAG, heatTag -> heatTag.put("augments", remainingAugments));
				for (IAugment augment : removedAugments) {
					augment.onRemove(stack);
				}
				return results;
			}
		}
		return Lists.newArrayList();
	}

	@Override
	public void addAugmentLevel(ItemStack stack, IAugment augment, int levels) {
		setAugmentLevel(stack, augment, getAugmentLevel(stack, augment) + levels); //This is redundant but intuitive
	}

	@Override
	public void setAugmentLevel(ItemStack stack, IAugment augment, int level) {
		ItemData.updateTag(stack, tagCompound -> {
			CompoundTag heatTag = getOrCreateHeatTag(tagCompound);
			ListTag list = getOrCreateAugmentList(heatTag);
			for (int i = 0; i < list.size(); i ++) {
				CompoundTag compound = list.getCompound(i);
				if (compound.contains("name")) {
					if (compound.getString("name").compareTo(augment.getName().toString()) == 0) {
						compound.putInt("level", level);
					}
				}
			}
		});
	}

	@Override
	public int getAugmentLevel(ItemStack stack, IAugment augment) {
		if (hasHeat(stack)) {
			ListTag list = ItemData.getOrCreateTag(stack).getCompound(IAugmentUtil.HEAT_TAG).getList("augments", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i ++) {
				CompoundTag compound = list.getCompound(i);
				if (compound.contains("name")) {
					if (compound.getString("name").compareTo(augment.getName().toString()) == 0) {
						return compound.getInt("level");
					}
				}
			}
		}
		return 0;
	}

	@Override
	public boolean hasHeat(ItemStack stack) {
		if (!stack.isEmpty()) {
			if (ItemData.hasTag(stack)) {
				if (ItemData.getTag(stack).contains(HEAT_TAG)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void addHeat(ItemStack stack, float heat) {
		ItemData.updateTag(stack, tagCompound -> {
			CompoundTag heatTag = getOrCreateHeatTag(tagCompound);
			float maxHeat = 500f + 250f * heatTag.getFloat("heat_level");
			heatTag.putFloat("heat", Math.min(maxHeat, heatTag.getFloat("heat") + heat));
		});
	}

	@Override
	public void setHeat(ItemStack stack, float heat) {
		ItemData.updateTag(stack, tagCompound -> getOrCreateHeatTag(tagCompound).putFloat("heat", heat));
	}

	@Override
	public float getHeat(ItemStack stack) {
		if (hasHeat(stack)) {
			return ItemData.getTag(stack).getCompound(HEAT_TAG).getFloat("heat");
		}
		return 0.0f;
	}

	@Override
	public float getMaxHeat(ItemStack stack) {
		if (hasHeat(stack)) {
			return 500f + 250f*ItemData.getTag(stack).getCompound(HEAT_TAG).getFloat("heat_level");
		}
		return 0.0f;
	}

	@Override
	public int getLevel(ItemStack stack) {
		if (hasHeat(stack)) {
			return ItemData.getTag(stack).getCompound(HEAT_TAG).getInt("heat_level");
		}
		return 0;
	}

	@Override
	public void setLevel(ItemStack stack, int level) {
		ItemData.updateTag(stack, tagCompound -> getOrCreateHeatTag(tagCompound).putInt("heat_level", level));
	}

	@Override
	public int getArmorAugmentLevel(LivingEntity entity, IAugment augment) {
		int maxLevel = 0;
		if (hasHeat(entity.getItemBySlot(EquipmentSlot.HEAD))) {
			int l = getAugmentLevel(entity.getItemBySlot(EquipmentSlot.HEAD), augment);
			if (l > maxLevel) {
				maxLevel = l;
			}
		}
		if (hasHeat(entity.getItemBySlot(EquipmentSlot.CHEST))) {
			int l = getAugmentLevel(entity.getItemBySlot(EquipmentSlot.CHEST), augment);
			if (l > maxLevel) {
				maxLevel = l;
			}
		}
		if (hasHeat(entity.getItemBySlot(EquipmentSlot.LEGS))) {
			int l = getAugmentLevel(entity.getItemBySlot(EquipmentSlot.LEGS), augment);
			if (l > maxLevel) {
				maxLevel = l;
			}
		}
		if (hasHeat(entity.getItemBySlot(EquipmentSlot.FEET))) {
			int l = getAugmentLevel(entity.getItemBySlot(EquipmentSlot.FEET), augment);
			if (l > maxLevel) {
				maxLevel = l;
			}
		}
		return maxLevel;
	}

	@Override
	public IAugment registerAugment(IAugment augment) {
		RegistryManager.augmentRegistry.put(augment.getName(), augment);
		return augment;
	}

	public static void checkForTag(ItemStack stack) {
		ItemData.updateTag(stack, AugmentUtilImpl::getOrCreateHeatTag);
	}

	private static CompoundTag getOrCreateHeatTag(CompoundTag tagCompound) {
		if (!tagCompound.contains(HEAT_TAG)) {
			CompoundTag heatTag = new CompoundTag();
			heatTag.putInt("heat_level", 0);
			heatTag.putFloat("heat", 0);
			heatTag.put("augments", new ListTag());
			tagCompound.put(HEAT_TAG, heatTag);
		}
		return tagCompound.getCompound(HEAT_TAG);
	}

	private static ListTag getOrCreateAugmentList(CompoundTag heatTag) {
		if (!heatTag.contains("augments"))
			heatTag.put("augments", new ListTag());
		return heatTag.getList("augments", Tag.TAG_COMPOUND);
	}
}
