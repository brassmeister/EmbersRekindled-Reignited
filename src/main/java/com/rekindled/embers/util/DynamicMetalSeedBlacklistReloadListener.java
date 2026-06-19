package com.rekindled.embers.util;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rekindled.embers.Embers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public class DynamicMetalSeedBlacklistReloadListener implements ResourceManagerReloadListener {

	private static final String FILE_NAME = "metal-seed-blacklist.json";

	public static void onAddReloadListeners(AddReloadListenerEvent event) {
		event.addListener(new DynamicMetalSeedBlacklistReloadListener());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Set<String> blacklist = new HashSet<>();
		for (String namespace : manager.getNamespaces()) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, FILE_NAME);
			for (Resource resource : manager.getResourceStack(id)) {
				try (Reader reader = resource.openAsReader()) {
				JsonObject json = GsonHelper.parse(reader);
				if (!json.has("metals")) {
					continue;
				}
				for (JsonElement element : GsonHelper.getAsJsonArray(json, "metals")) {
					blacklist.add(GsonHelper.convertToString(element, "metal"));
				}
				} catch (IOException | RuntimeException exception) {
					Embers.LOGGER.warn("Failed to load dynamic metal seed blacklist {}", id, exception);
				}
			}
		}
		DynamicMetalSeeds.setBlacklist(blacklist);
	}
}
