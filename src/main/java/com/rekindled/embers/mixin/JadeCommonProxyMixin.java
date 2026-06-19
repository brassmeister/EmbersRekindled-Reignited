package com.rekindled.embers.mixin;

import com.rekindled.embers.compat.jade.JadeCompat;

import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "snownee.jade.util.CommonProxy", remap = false)
public abstract class JadeCommonProxyMixin {
	@Inject(method = "loadComplete", at = @At("HEAD"))
	private void embers$registerJadeProviders(FMLLoadCompleteEvent event, CallbackInfo callback) {
		JadeCompat.register();
	}
}
