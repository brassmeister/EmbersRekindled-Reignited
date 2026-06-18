package com.rekindled.embers.client;

import com.rekindled.embers.Embers;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class EmbersConfigScreens {

	private static final String ARCHITECTURY = "architectury";
	private static final String OMEGA_CONFIG = "omega-config";
	private static final String OMEGACONFIG = "omegaconfig";

	private EmbersConfigScreens() {
	}

	public static void register(IEventBus modEventBus, ModContainer modContainer) {
		if (usesArchitecturyConfigStack()) {
			modEventBus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(() -> registerNeoForgeConfigScreen(modContainer)));
			Embers.LOGGER.info("Delaying Embers config screen registration because Architectury or OmegaConfig is present");
			return;
		}
		registerNeoForgeConfigScreen(modContainer);
	}

	private static boolean usesArchitecturyConfigStack() {
		ModList mods = ModList.get();
		return mods.isLoaded(ARCHITECTURY) || mods.isLoaded(OMEGA_CONFIG) || mods.isLoaded(OMEGACONFIG) || isClassPresent("io.github.frqnny.omegaconfig.OmegaConfig");
	}

	private static boolean isClassPresent(String className) {
		try {
			Class.forName(className, false, EmbersConfigScreens.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException exception) {
			return false;
		}
	}

	private static void registerNeoForgeConfigScreen(ModContainer modContainer) {
		if (modContainer.getCustomExtension(IConfigScreenFactory.class).isPresent()) {
			return;
		}
		modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new ConfigurationScreen(container, parent));
	}
}
