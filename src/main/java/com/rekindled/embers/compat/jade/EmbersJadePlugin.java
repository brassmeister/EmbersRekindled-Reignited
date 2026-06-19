package com.rekindled.embers.compat.jade;

import com.rekindled.embers.Embers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

public class EmbersJadePlugin implements IWailaPlugin {
	public static final ResourceLocation MACHINE_DETAILS = ResourceLocation.fromNamespaceAndPath(Embers.MODID, "machine_details");

	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(EmbersMachineProvider.INSTANCE, Block.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.addConfig(MACHINE_DETAILS, true);
		registration.registerBlockComponent(EmbersMachineProvider.INSTANCE, Block.class);
	}
}
