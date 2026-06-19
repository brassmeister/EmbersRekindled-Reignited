package com.rekindled.embers.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.item.DynamicCrystalSeedBlockItem;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class DynamicMetalSeeds {

	public static final String DEFAULT_METAL = "tin";
	public static final int DEFAULT_COLOR = 0x8BB6B8;

	private static final String TAG_NAMESPACE = "c";
	private static final String INGOT_PREFIX = "ingots/";
	private static final Set<String> STATIC_ALIASES = Set.of("aluminium");
	private static final Set<String> DEFAULT_BLACKLIST = Set.of("netherite");

	private static List<Variant> variants = List.of();
	private static Set<String> blacklistedMetals = DEFAULT_BLACKLIST;
	private static boolean dirty = true;

	private DynamicMetalSeeds() {
	}

	public static synchronized void refresh() {
		dirty = true;
		getVariants();
	}

	public static synchronized List<Variant> getVariants() {
		if (!dirty) {
			return variants;
		}
		List<Variant> found = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagNames()
				.map(TagKey::location)
				.filter(id -> TAG_NAMESPACE.equals(id.getNamespace()))
				.map(ResourceLocation::getPath)
				.filter(path -> path.startsWith(INGOT_PREFIX))
				.map(path -> path.substring(INGOT_PREFIX.length()))
				.map(DynamicMetalSeeds::normalizeMetal)
				.distinct()
				.filter(DynamicMetalSeeds::hasRequiredTags)
				.filter(metal -> !isStaticSeed(metal))
				.filter(metal -> !blacklistedMetals.contains(metal))
				.sorted(Comparator.comparing(DynamicMetalSeeds::displayName))
				.forEach(metal -> representativeIngotId(metal).ifPresent(ingotId -> found.add(new Variant(metal, colorFor(metal), ingotId, modName(ingotId.getNamespace())))));
		variants = List.copyOf(found);
		dirty = false;
		return variants;
	}

	public static synchronized void setBlacklist(Set<String> metals) {
		Set<String> normalized = new HashSet<>(DEFAULT_BLACKLIST);
		for (String metal : metals) {
			normalized.add(normalizeMetal(metal));
		}
		blacklistedMetals = Set.copyOf(normalized);
		refresh();
	}

	public static List<ItemStack> getStacks() {
		return getVariants().stream()
				.map(variant -> DynamicCrystalSeedBlockItem.withMetal(variant.metal(), variant.color()))
				.toList();
	}

	public static boolean isValidMetal(String metal) {
		String normalized = normalizeMetal(metal);
		if (isStaticSeed(normalized)) {
			return false;
		}
		return getVariants().stream().anyMatch(variant -> variant.metal().equals(normalized));
	}

	public static Optional<Variant> getVariant(String metal) {
		String normalized = normalizeMetal(metal);
		return getVariants().stream().filter(variant -> variant.metal().equals(normalized)).findFirst();
	}

	public static String normalizeMetal(String metal) {
		String normalized = metal.toLowerCase(Locale.ROOT).replace('\\', '/');
		if ("aluminium".equals(normalized)) {
			return "aluminum";
		}
		return normalized;
	}

	public static String displayName(String metal) {
		String normalized = normalizeMetal(metal).replace('/', '_');
		StringBuilder result = new StringBuilder();
		for (String part : normalized.split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				result.append(part.substring(1));
			}
		}
		return result.toString();
	}

	public static int colorFor(String metal) {
		int hash = normalizeMetal(metal).hashCode();
		float hue = ((hash & 0xFF) / 255.0F + 0.08F) % 1.0F;
		float saturation = 0.35F + (((hash >> 8) & 0x3F) / 255.0F);
		float value = 0.78F + (((hash >> 16) & 0x1F) / 255.0F);
		return Mth.hsvToRgb(hue, saturation, Math.min(value, 0.92F)) & 0xFFFFFF;
	}

	private static boolean hasRequiredTags(String metal) {
		return hasItems("nuggets/" + metal) && hasItems("ingots/" + metal);
	}

	private static boolean hasItems(String path) {
		Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(ItemTags.create(ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, path)));
		return tag.isPresent() && tag.get().size() > 0;
	}

	private static Optional<ResourceLocation> representativeIngotId(String metal) {
		Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(ItemTags.create(ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "ingots/" + metal)));
		if (tag.isEmpty()) {
			return Optional.empty();
		}
		Item fallback = null;
		List<? extends String> preferences = com.rekindled.embers.ConfigManager.TAG_PREFERENCES.get();
		int preferenceIndex = Integer.MAX_VALUE;
		for (Holder<Item> holder : tag.get()) {
			Item item = holder.value();
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			for (int i = 0; i < preferences.size(); i++) {
				if (i < preferenceIndex && preferences.get(i).equals(id.getNamespace())) {
					fallback = item;
					preferenceIndex = i;
				}
			}
			if (fallback == null) {
				fallback = item;
			}
		}
		return fallback == null ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.getKey(fallback));
	}

	private static String modName(String modId) {
		return ModList.get().getModContainerById(modId)
				.map(container -> container.getModInfo().getDisplayName())
				.orElse(modId);
	}

	private static boolean isStaticSeed(String metal) {
		String normalized = normalizeMetal(metal);
		return RegistryManager.MetalCrystalSeed.seeds.containsKey(normalized)
				|| STATIC_ALIASES.contains(normalized);
	}

	public static final class Variant {
		private final String metal;
		private final int color;
		private final ResourceLocation ingotId;
		private final String modName;

		public Variant(String metal, int color, ResourceLocation ingotId, String modName) {
			this.metal = metal;
			this.color = color;
			this.ingotId = ingotId;
			this.modName = modName;
		}

		public String metal() {
			return metal;
		}

		public int color() {
			return color;
		}

		public ResourceLocation ingotId() {
			return ingotId;
		}

		public String modName() {
			return modName;
		}
	}
}
