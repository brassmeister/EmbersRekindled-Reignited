package com.rekindled.embers.compat.jade;

import com.rekindled.embers.Embers;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IJadeProvider;
import snownee.jade.api.BlockAccessor;
import snownee.jade.impl.WailaCommonRegistration;

public final class JadeCompat {
	private JadeCompat() {
	}

	public static void register() {
		registerCommon();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			registerClient();
		}
	}

	private static void registerCommon() {
		WailaCommonRegistration registration = WailaCommonRegistration.instance();
		if (!hasProvider(registration.blockDataProviders.entries(), EmbersJadePlugin.MACHINE_DETAILS)) {
			registration.registerBlockDataProvider(EmbersMachineProvider.INSTANCE, Block.class);
			Embers.LOGGER.info("Registered Embers Jade server data provider");
		}
	}

	@SuppressWarnings("unchecked")
	private static void registerClient() {
		try {
			Class<?> registrationClass = Class.forName("snownee.jade.impl.WailaClientRegistration");
			Object registration = registrationClass.getMethod("instance").invoke(null);
			Object blockComponentProviders = registrationClass.getField("blockComponentProviders").get(registration);
			Method entries = blockComponentProviders.getClass().getMethod("entries");

			if (!hasProvider(((java.util.stream.Stream<Map.Entry<Class<?>, Collection<IComponentProvider<BlockAccessor>>>>) entries.invoke(blockComponentProviders)), EmbersJadePlugin.MACHINE_DETAILS)) {
				registrationClass.getMethod("addConfig", ResourceLocation.class, boolean.class)
						.invoke(registration, EmbersJadePlugin.MACHINE_DETAILS, true);
				registrationClass.getMethod("registerBlockComponent", IComponentProvider.class, Class.class)
						.invoke(registration, EmbersMachineProvider.INSTANCE, Block.class);
				Embers.LOGGER.info("Registered Embers Jade block component provider");
			}
		} catch (ReflectiveOperationException exception) {
			Embers.LOGGER.error("Failed to register Embers Jade client provider", exception);
		}
	}

	private static boolean hasProvider(java.util.stream.Stream<? extends Map.Entry<Class<?>, ? extends Collection<? extends IJadeProvider>>> entries, ResourceLocation uid) {
		return entries
				.flatMap(entry -> entry.getValue().stream())
				.map(IJadeProvider::getUid)
				.anyMatch(uid::equals);
	}
}
