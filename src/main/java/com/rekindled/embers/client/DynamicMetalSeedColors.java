package com.rekindled.embers.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.rekindled.embers.Embers;
import com.rekindled.embers.item.DynamicCrystalSeedBlockItem;
import com.rekindled.embers.util.DynamicMetalSeeds;
import com.rekindled.embers.util.Misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DynamicMetalSeedColors implements ResourceManagerReloadListener {

	private static final Map<String, Integer> COLORS = new HashMap<>();
	private static ResourceManager resourceManager;

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		resourceManager = manager;
		COLORS.clear();
	}

	public static int getColor(ItemStack stack) {
		return getColor(DynamicCrystalSeedBlockItem.getMetal(stack), DynamicCrystalSeedBlockItem.getColor(stack));
	}

	public static int getColor(String metal, int fallback) {
		String normalized = DynamicMetalSeeds.normalizeMetal(metal);
		Integer cached = COLORS.get(normalized);
		if (cached != null) {
			return cached;
		}
		return DynamicMetalSeeds.getVariant(normalized)
				.flatMap(DynamicMetalSeedColors::sampleIngotColor)
				.map(color -> {
					COLORS.put(normalized, color);
					return color;
				})
				.orElse(fallback);
	}

	private static Optional<Integer> sampleIngotColor(DynamicMetalSeeds.Variant variant) {
		Optional<Integer> bakedColor = sampleBakedModelColor(variant.ingotId());
		if (bakedColor.isPresent()) {
			return bakedColor;
		}
		return resourceManager == null ? Optional.empty() : sampleIngotColor(resourceManager, variant.ingotId());
	}

	private static Optional<Integer> sampleBakedModelColor(ResourceLocation ingotId) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getItemRenderer() == null) {
			return Optional.empty();
		}
		Item item = BuiltInRegistries.ITEM.get(ingotId);
		if (item == null) {
			return Optional.empty();
		}
		ItemStack stack = new ItemStack(item);
		BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
		if (model == null) {
			return Optional.empty();
		}
		long red = 0;
		long green = 0;
		long blue = 0;
		long pixels = 0;
		for (BakedQuad quad : model.getQuads(null, null, RandomSource.create(0))) {
			Optional<ColorSample> sample = sampleSprite(quad.getSprite());
			if (sample.isPresent()) {
				red += sample.get().red();
				green += sample.get().green();
				blue += sample.get().blue();
				pixels += sample.get().pixels();
			}
		}
		if (pixels == 0) {
			Optional<ColorSample> sample = sampleSprite(model.getParticleIcon());
			if (sample.isPresent()) {
				red += sample.get().red();
				green += sample.get().green();
				blue += sample.get().blue();
				pixels += sample.get().pixels();
			}
		}
		return pixels == 0 ? Optional.empty() : Optional.of(Misc.intColor((int) (red / pixels), (int) (green / pixels), (int) (blue / pixels)));
	}

	private static Optional<ColorSample> sampleSprite(TextureAtlasSprite sprite) {
		if (sprite == null || sprite.contents() == null) {
			return Optional.empty();
		}
		long red = 0;
		long green = 0;
		long blue = 0;
		long pixels = 0;
		int width = sprite.contents().width();
		int height = sprite.contents().height();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int color = sprite.getPixelRGBA(0, x, y);
				int alpha = color >>> 24 & 255;
				if (alpha < 16) {
					continue;
				}
				red += color & 255;
				green += color >> 8 & 255;
				blue += color >> 16 & 255;
				pixels++;
			}
		}
		return pixels == 0 ? Optional.empty() : Optional.of(new ColorSample(red, green, blue, pixels));
	}

	private static Optional<Integer> sampleIngotColor(ResourceManager manager, ResourceLocation ingotId) {
		Optional<Integer> modelColor = itemTextureFromModel(manager, ingotId).flatMap(texture -> sampleTexture(manager, texture));
		if (modelColor.isPresent()) {
			return modelColor;
		}
		return sampleTexture(manager, ResourceLocation.fromNamespaceAndPath(ingotId.getNamespace(), "textures/item/" + ingotId.getPath() + ".png"));
	}

	private static Optional<ResourceLocation> itemTextureFromModel(ResourceManager manager, ResourceLocation itemId) {
		ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json");
		Optional<Resource> resource = manager.getResource(modelId);
		if (resource.isEmpty()) {
			return Optional.empty();
		}
		try (var reader = resource.get().openAsReader()) {
			JsonObject json = GsonHelper.parse(reader);
			if (!json.has("textures")) {
				return Optional.empty();
			}
			JsonObject textures = GsonHelper.getAsJsonObject(json, "textures");
			if (!textures.has("layer0")) {
				return Optional.empty();
			}
			String texture = GsonHelper.getAsString(textures, "layer0");
			if (texture.startsWith("#")) {
				return Optional.empty();
			}
			ResourceLocation textureId = parseTextureLocation(texture, itemId.getNamespace());
			return Optional.of(ResourceLocation.fromNamespaceAndPath(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png"));
		} catch (IOException | RuntimeException exception) {
			Embers.LOGGER.debug("Failed to read item model {} for dynamic metal seed color", modelId, exception);
			return Optional.empty();
		}
	}

	private static ResourceLocation parseTextureLocation(String texture, String defaultNamespace) {
		return texture.contains(":") ? ResourceLocation.parse(texture) : ResourceLocation.fromNamespaceAndPath(defaultNamespace, texture);
	}

	private static Optional<Integer> sampleTexture(ResourceManager manager, ResourceLocation textureId) {
		Optional<Resource> resource = manager.getResource(textureId);
		if (resource.isEmpty()) {
			return Optional.empty();
		}
		try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
			long red = 0;
			long green = 0;
			long blue = 0;
			long pixels = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int color = image.getPixelRGBA(x, y);
					int alpha = color >>> 24 & 255;
					if (alpha < 16) {
						continue;
					}
					red += color & 255;
					green += color >> 8 & 255;
					blue += color >> 16 & 255;
					pixels++;
				}
			}
			if (pixels == 0) {
				return Optional.empty();
			}
			return Optional.of(Misc.intColor((int) (red / pixels), (int) (green / pixels), (int) (blue / pixels)));
		} catch (IOException | RuntimeException exception) {
			Embers.LOGGER.debug("Failed to sample texture {} for dynamic metal seed color", textureId, exception);
			return Optional.empty();
		}
	}

	private record ColorSample(long red, long green, long blue, long pixels) {
	}
}
