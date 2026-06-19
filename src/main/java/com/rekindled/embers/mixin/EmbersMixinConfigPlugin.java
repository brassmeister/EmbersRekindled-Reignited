package com.rekindled.embers.mixin;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class EmbersMixinConfigPlugin implements IMixinConfigPlugin {

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		LoadingModList loadingModList = LoadingModList.get();
		if ("com.rekindled.embers.mixin.CTPhysicsGogglesClientUtilMixin".equals(mixinClassName)) {
			return loadingModList != null
					&& loadingModList.getModFileById("create") != null
					&& loadingModList.getModFileById("createthrusters") != null;
		}
		if ("com.rekindled.embers.mixin.JadeCommonProxyMixin".equals(mixinClassName)) {
			return loadingModList != null && loadingModList.getModFileById("jade") != null;
		}
		if ("com.rekindled.embers.mixin.CreateBlazeBurnerBlockEntityMixin".equals(mixinClassName)
				|| "com.rekindled.embers.mixin.CreateBlazeBurnerBlockInteractionMixin".equals(mixinClassName)) {
			return loadingModList != null && loadingModList.getModFileById("create") != null;
		}
		if ("com.rekindled.embers.mixin.InfernoForgeSubLevelCollisionMixin".equals(mixinClassName)
				|| "com.rekindled.embers.mixin.EmberLinkSubLevelAssemblyMixin".equals(mixinClassName)
				|| "com.rekindled.embers.mixin.EmberLinkTargetSubLevelAssemblyMixin".equals(mixinClassName)) {
			return loadingModList != null && loadingModList.getModFileById("sable") != null;
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
