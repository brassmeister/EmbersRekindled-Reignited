package com.rekindled.embers.render;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.function.Function;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.Embers;
import com.rekindled.embers.EmbersClientEvents;

import net.minecraft.Util;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class EmbersRenderTypes extends RenderType {

	public static ShaderInstance additiveShader;
	public static final ShaderStateShard ADDITIVE_SHADER = new ShaderStateShard(() -> additiveShader);
	public static ShaderInstance emberParticleShader;
	public static final ShaderStateShard EMBER_PARTICLE_SHADER = new ShaderStateShard(() -> emberParticleShader);
	public static ShaderInstance emberParticleFabShader;
	public static final ShaderStateShard EMBER_PARTICLE_FAB_SHADER = new ShaderStateShard(() -> emberParticleFabShader);
	public static ShaderInstance translucentParticleShader;
	public static final ShaderStateShard TRANSLUCENT_PARTICLE_SHADER = new ShaderStateShard(() -> translucentParticleShader);
	public static ShaderInstance mithrilShader;
	public static final ShaderStateShard MITHRIL_SHADER = new ShaderStateShard(() -> mithrilShader);
	private static final ResourceLocation PARTICLE_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
	private static final Method IRIS_API_GET_INSTANCE = findMethod("net.irisshaders.iris.api.v0.IrisApi", "getInstance");
	private static final Method IRIS_API_IS_SHADER_PACK_IN_USE = findMethod("net.irisshaders.iris.api.v0.IrisApi", "isShaderPackInUse");
	private static final Method IRIS_GET_CURRENT_PACK_NAME = findMethod("net.irisshaders.iris.Iris", "getCurrentPackName");
	private static final Method IRIS_IS_PACK_IN_USE_QUICK = findMethod("net.irisshaders.iris.Iris", "isPackInUseQuick");
	private static final Method IRIS_GET_IRIS_CONFIG = findMethod("net.irisshaders.iris.Iris", "getIrisConfig");
	private static final Method IRIS_CONFIG_GET_SHADER_PACK_NAME = findMethod("net.irisshaders.iris.config.IrisConfig", "getShaderPackName");
	private static final Method LEGACY_IRIS_GET_CURRENT_PACK_NAME = findMethod("net.coderbot.iris.Iris", "getCurrentPackName");
	private static final Method LEGACY_IRIS_IS_PACK_IN_USE_QUICK = findMethod("net.coderbot.iris.Iris", "isPackInUseQuick");
	private static final Method LEGACY_IRIS_GET_IRIS_CONFIG = findMethod("net.coderbot.iris.Iris", "getIrisConfig");
	private static final Method LEGACY_IRIS_CONFIG_GET_SHADER_PACK_NAME = findMethod("net.coderbot.iris.config.IrisConfig", "getShaderPackName");
	private static final Field OPTIFINE_SHADER_PACK_LOADED = findField("net.optifine.shaders.Shaders", "shaderPackLoaded");
	private static final ParticleGroup SHADER_PARTICLE_GROUP = new ParticleGroup(2048);
	private static final ParticleGroup UNBOUND_SHADER_PARTICLE_GROUP = new ParticleGroup(96);
	private static boolean shaderPackActiveCache = false;
	private static long shaderPackActiveCacheTime = -1000L;
	private static String shaderPackNameCache = null;
	private static long shaderPackNameCacheTime = -1000L;

	public EmbersRenderTypes(String pName, VertexFormat pFormat, Mode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
		super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
	}

	public static void applyParticleUniforms(ShaderInstance shader, float offset, float fade, float alphaCutoff) {
		if (shader == null || EmbersClientEvents.depthBuffer == null) {
			return;
		}
		shader.setSampler("DepthBuffer", EmbersClientEvents.depthBuffer.getDepthTextureId());
		shader.safeGetUniform("ProjMatInv").set(new Matrix4f(RenderSystem.getProjectionMatrix()).invert());
		shader.safeGetUniform("Offset").set(offset);
		shader.safeGetUniform("Fade").set(fade);
		shader.safeGetUniform("AlphaCutoff").set(alphaCutoff);
	}

	private static boolean shouldUseSoftParticles(GraphicsStatus minimumGraphics) {
		return !ConfigManager.RENDER_FALLBACK.get()
				&& EmbersClientEvents.depthBuffer != null
				&& !isShaderPackActive()
				&& Minecraft.getInstance().options.graphicsMode().get().getId() >= minimumGraphics.getId();
	}

	public static ParticleRenderType additiveParticleSheet() {
		return shaderSafeParticleSheet(PARTICLE_SHEET_ADDITIVE);
	}

	public static ParticleRenderType emberRoughParticleSheet() {
		return shaderSafeParticleSheet(PARTICLE_SHEET_EMBER_ROUGH);
	}

	public static ParticleRenderType emberParticleSheet() {
		return shaderSafeParticleSheet(PARTICLE_SHEET_EMBER);
	}

	public static ParticleRenderType emberHardParticleSheet() {
		return shaderSafeParticleSheet(PARTICLE_SHEET_EMBER_HARD);
	}

	public static ParticleRenderType additiveXRayParticleSheet() {
		return shaderSafeParticleSheet(PARTICLE_SHEET_ADDITIVE_XRAY);
	}

	public static ParticleRenderType translucentNoDepthParticleSheet() {
		return shaderSafeParticleSheet(PARTICLE_SHEET_TRANSLUCENT_NODEPTH);
	}

	public static Optional<ParticleGroup> emberParticleGroup() {
		if (isComplementaryUnboundShaderPack()) {
			return Optional.of(UNBOUND_SHADER_PARTICLE_GROUP);
		}
		return isShaderPackActive() ? Optional.of(SHADER_PARTICLE_GROUP) : Optional.empty();
	}

	public static RenderType glowLines() {
		return isComplementaryUnboundShaderPack() ? RenderType.lines() : GLOW_LINES;
	}

	public static RenderType fieldChart() {
		return ConfigManager.RENDER_FALLBACK.get() || isShaderPackActive() ? FIELD_CHART_FALLBACK : FIELD_CHART;
	}

	private static ShaderInstance getEmberParticleShader() {
		int fancyness = Minecraft.getInstance().options.graphicsMode().get().getId();
		return fancyness >= GraphicsStatus.FABULOUS.getId() ? emberParticleFabShader : emberParticleShader;
	}

	private static boolean setupSoftParticleShader(ShaderInstance shader, float offset, float fade, float alphaCutoff) {
		if (shader == null) {
			return false;
		}
		RenderSystem.setShader(() -> shader);
		applyParticleUniforms(shader, offset, fade, alphaCutoff);
		return true;
	}

	private static ParticleRenderType shaderSafeParticleSheet(ParticleRenderType renderType) {
		return isComplementaryUnboundShaderPack() ? ParticleRenderType.NO_RENDER : renderType;
	}

	private static BufferBuilder skipParticleSheetForShaderPack() {
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		return null;
	}

	public static boolean isShaderPackActive() {
		long now = Util.getMillis();
		if (now - shaderPackActiveCacheTime < 1000L) {
			return shaderPackActiveCache;
		}
		shaderPackActiveCache = isIrisShaderPackActive() || invokeStaticBoolean(IRIS_IS_PACK_IN_USE_QUICK) || invokeStaticBoolean(LEGACY_IRIS_IS_PACK_IN_USE_QUICK) || readStaticBoolean(OPTIFINE_SHADER_PACK_LOADED);
		shaderPackActiveCacheTime = now;
		return shaderPackActiveCache;
	}

	public static boolean isComplementaryUnboundShaderPack() {
		if (!isShaderPackActive()) {
			return false;
		}
		String packName = getActiveShaderPackName();
		if (packName == null) {
			return false;
		}
		String normalizedPackName = packName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		return normalizedPackName.contains("complementaryunbound") || normalizedPackName.contains("complimentaryunbound");
	}

	public static String getActiveShaderPackName() {
		long now = Util.getMillis();
		if (now - shaderPackNameCacheTime < 1000L) {
			return shaderPackNameCache;
		}
		shaderPackNameCache = findActiveShaderPackName();
		shaderPackNameCacheTime = now;
		return shaderPackNameCache;
	}

	private static boolean isIrisShaderPackActive() {
		if (IRIS_API_GET_INSTANCE == null || IRIS_API_IS_SHADER_PACK_IN_USE == null) {
			return false;
		}
		try {
			Object irisApi = IRIS_API_GET_INSTANCE.invoke(null);
			return Boolean.TRUE.equals(IRIS_API_IS_SHADER_PACK_IN_USE.invoke(irisApi));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	private static String findActiveShaderPackName() {
		String packName = invokeStaticString(IRIS_GET_CURRENT_PACK_NAME);
		if (packName != null) {
			return packName;
		}
		packName = invokeStaticString(LEGACY_IRIS_GET_CURRENT_PACK_NAME);
		if (packName != null) {
			return packName;
		}
		packName = invokeConfigShaderPackName(IRIS_GET_IRIS_CONFIG, IRIS_CONFIG_GET_SHADER_PACK_NAME);
		if (packName != null) {
			return packName;
		}
		packName = invokeConfigShaderPackName(LEGACY_IRIS_GET_IRIS_CONFIG, LEGACY_IRIS_CONFIG_GET_SHADER_PACK_NAME);
		if (packName != null) {
			return packName;
		}
		return readConfiguredShaderPackName();
	}

	private static String invokeStaticString(Method method) {
		if (method == null) {
			return null;
		}
		try {
			return cleanShaderPackName(unwrapString(method.invoke(null)));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static String invokeConfigShaderPackName(Method configMethod, Method shaderPackNameMethod) {
		if (configMethod == null || shaderPackNameMethod == null) {
			return null;
		}
		try {
			Object config = configMethod.invoke(null);
			return config == null ? null : cleanShaderPackName(unwrapString(shaderPackNameMethod.invoke(config)));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static String unwrapString(Object value) {
		if (value instanceof Optional<?> optional) {
			return optional.map(Object::toString).orElse(null);
		}
		return value == null ? null : value.toString();
	}

	private static String cleanShaderPackName(String shaderPackName) {
		if (shaderPackName == null) {
			return null;
		}
		String trimmedName = shaderPackName.trim();
		return trimmedName.isEmpty() || trimmedName.equalsIgnoreCase("off") ? null : trimmedName;
	}

	@SuppressWarnings("resource")
	private static String readConfiguredShaderPackName() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gameDirectory == null) {
			return null;
		}
		Path configDirectory = minecraft.gameDirectory.toPath().resolve("config");
		String packName = readConfiguredShaderPackName(configDirectory.resolve("iris.properties"));
		return packName != null ? packName : readConfiguredShaderPackName(configDirectory.resolve("oculus.properties"));
	}

	private static String readConfiguredShaderPackName(Path configPath) {
		if (!Files.isRegularFile(configPath)) {
			return null;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(configPath)) {
			properties.load(reader);
		} catch (IOException | RuntimeException | LinkageError e) {
			return null;
		}
		String packName = properties.getProperty("shaderPack");
		if (packName == null) {
			packName = properties.getProperty("shaderPackName");
		}
		if (packName == null) {
			packName = properties.getProperty("currentShaderPack");
		}
		return cleanShaderPackName(packName);
	}

	private static boolean invokeStaticBoolean(Method method) {
		if (method == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(method.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	private static boolean readStaticBoolean(Field field) {
		if (field == null) {
			return false;
		}
		try {
			return field.getBoolean(null);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	private static Method findMethod(String className, String methodName) {
		try {
			Method method = Class.forName(className, false, EmbersRenderTypes.class.getClassLoader()).getMethod(methodName);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static Field findField(String className, String fieldName) {
		try {
			Field field = Class.forName(className, false, EmbersRenderTypes.class.getClassLoader()).getDeclaredField(fieldName);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	//render type used for additive particles
	public static ParticleRenderType PARTICLE_SHEET_ADDITIVE = new ParticleRenderType() {
		@SuppressWarnings("resource")
		public BufferBuilder begin(Tesselator p_107455_, TextureManager p_107456_) {
			if (isComplementaryUnboundShaderPack()) {
				return skipParticleSheetForShaderPack();
			}
			RenderSystem.enableDepthTest();
			Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
			RenderSystem.depthMask(false);
			if (shouldUseSoftParticles(GraphicsStatus.FANCY)) {
				setupSoftParticleShader(emberParticleShader, 0.0f, 3.0f / 16.0f, 0.0f);
			}
			RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			return p_107455_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);

		}

		public String toString() {
			return "PARTICLE_SHEET_ADDITIVE";
		}
	};

	//render type used for ember particles
	public static ParticleRenderType PARTICLE_SHEET_EMBER_ROUGH = new ParticleRenderType() {
		@SuppressWarnings("resource")
		public BufferBuilder begin(Tesselator p_107455_, TextureManager p_107456_) {
			if (isComplementaryUnboundShaderPack()) {
				return skipParticleSheetForShaderPack();
			}
			RenderSystem.enableDepthTest();
			Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
			RenderSystem.depthMask(false);
			if (shouldUseSoftParticles(GraphicsStatus.FANCY)) {
				if (setupSoftParticleShader(getEmberParticleShader(), 1.0f / 16.0f, 2.0f / 16.0f, 0.1f)) {
					RenderSystem.disableDepthTest();
				}
			}
			RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			return p_107455_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);

		}

		public String toString() {
			return "PARTICLE_SHEET_EMBER_ROUGH";
		}
	};

	//render type used for ember particles
	public static ParticleRenderType PARTICLE_SHEET_EMBER = new ParticleRenderType() {
		@SuppressWarnings("resource")
		public BufferBuilder begin(Tesselator p_107455_, TextureManager p_107456_) {
			if (isComplementaryUnboundShaderPack()) {
				return skipParticleSheetForShaderPack();
			}
			RenderSystem.enableDepthTest();
			Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
			RenderSystem.depthMask(false);
			if (shouldUseSoftParticles(GraphicsStatus.FANCY)) {
				if (setupSoftParticleShader(getEmberParticleShader(), 3.0f / 16.0f, 5.0f / 16.0f, 0.0f)) {
					RenderSystem.disableDepthTest();
				}
			}
			RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			return p_107455_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);

		}

		public String toString() {
			return "PARTICLE_SHEET_EMBER";
		}
	};

	//render type used for the alchemy circle
	public static ParticleRenderType PARTICLE_SHEET_EMBER_HARD = new ParticleRenderType() {
		@SuppressWarnings("resource")
		public BufferBuilder begin(Tesselator p_107455_, TextureManager p_107456_) {
			if (isComplementaryUnboundShaderPack()) {
				return skipParticleSheetForShaderPack();
			}
			RenderSystem.enableDepthTest();
			Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
			RenderSystem.depthMask(false);
			if (shouldUseSoftParticles(GraphicsStatus.FABULOUS)) {
				setupSoftParticleShader(emberParticleFabShader, 0.0f, 0.0f, 0.0f);
			}
			RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			return p_107455_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);

		}

		public String toString() {
			return "PARTICLE_SHEET_EMBER_HARD";
		}
	};

	//render type used for x ray ember particles
	public static ParticleRenderType PARTICLE_SHEET_ADDITIVE_XRAY = new ParticleRenderType() {
		@SuppressWarnings("resource")
		public BufferBuilder begin(Tesselator p_107455_, TextureManager p_107456_) {
			if (isComplementaryUnboundShaderPack()) {
				return skipParticleSheetForShaderPack();
			}
			RenderSystem.enableDepthTest();
			Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
			RenderSystem.depthMask(false);
			if (shouldUseSoftParticles(GraphicsStatus.FANCY)) {
				setupSoftParticleShader(getEmberParticleShader(), 50.0f, 40.0f, 0.0f);
			}
			RenderSystem.disableDepthTest();
			RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			return p_107455_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		public String toString() {
			return "PARTICLE_SHEET_ADDITIVE_XRAY";
		}
	};

	//render type used for smoke particles
	public static ParticleRenderType PARTICLE_SHEET_TRANSLUCENT_NODEPTH = new ParticleRenderType() {
		@SuppressWarnings("resource")
		public BufferBuilder begin(Tesselator p_107455_, TextureManager p_107456_) {
			if (isComplementaryUnboundShaderPack()) {
				return skipParticleSheetForShaderPack();
			}
			RenderSystem.enableDepthTest();
			Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
			RenderSystem.depthMask(false);
			if (shouldUseSoftParticles(GraphicsStatus.FANCY)) {
				setupSoftParticleShader(translucentParticleShader, 0.0f, 3.0f / 16.0f, 0.0f);
			}
			RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			return p_107455_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		public String toString() {
			return "PARTICLE_SHEET_TRANSLUCENT_NODEPTH";
		}
	};

	//render type used for the fluid renderer
	public static final RenderType FLUID = create(
			Embers.MODID + ":fluid_render_type",
			DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
			RenderType.CompositeState.builder()
			.setLightmapState(LIGHTMAP)
			.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
			.setTextureState(BLOCK_SHEET_MIPPED)
			.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
			.setCullState(CULL)
			.setOverlayState(OVERLAY)
			//.setOutputState(TRANSLUCENT_TARGET)
			.createCompositeState(true));

	public static final RenderStateShard.ShaderStateShard PTLC_SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
	public static final RenderStateShard.ShaderStateShard PTCN_SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
	public static final RenderStateShard.ShaderStateShard PTC_SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

	//render type used for the crystal cell
	public static final RenderType CRYSTAL = create(
			Embers.MODID + ":crystal_render_type",
			DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, true, false,
			RenderType.CompositeState.builder()
			.setShaderState(ADDITIVE_SHADER)
			.setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.parse(Embers.MODID + ":textures/block/crystal_material.png"), false, false))
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setCullState(NO_CULL)
			//.setOutputState(TRANSLUCENT_TARGET)
			.setLightmapState(NO_LIGHTMAP)
			.setOverlayState(NO_OVERLAY)
			.createCompositeState(false));
	public static final RenderType CRYSTAL_FALLBACK = create(
			Embers.MODID + ":crystal_render_type",
			DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, true, false,
			RenderType.CompositeState.builder()
			.setShaderState(PTC_SHADER)
			.setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.parse(Embers.MODID + ":textures/block/crystal_material.png"), false, false))
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setCullState(NO_CULL)
			//.setOutputState(TRANSLUCENT_TARGET)
			.setLightmapState(NO_LIGHTMAP)
			.setOverlayState(NO_OVERLAY)
			.createCompositeState(false));

	//render type used for the field chart
	public static final RenderType FIELD_CHART = create(
			Embers.MODID + ":field_chart_render_type",
			DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
			RenderType.CompositeState.builder()
			.setShaderState(ADDITIVE_SHADER)
			.setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.parse(Embers.MODID + ":textures/block/field_square.png"), false, false))
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setCullState(NO_CULL)
			//.setOutputState(TRANSLUCENT_TARGET)
			.createCompositeState(false));
	public static final RenderType FIELD_CHART_FALLBACK = create(
			Embers.MODID + ":field_chart_render_type",
			DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
			RenderType.CompositeState.builder()
			.setShaderState(PTC_SHADER)
			.setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.parse(Embers.MODID + ":textures/block/field_square.png"), false, false))
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setCullState(NO_CULL)
			//.setOutputState(TRANSLUCENT_TARGET)
			.createCompositeState(false));

	//unused render type for the crystal seeds
	public static Function<ResourceLocation, RenderType> CRYSTAL_SEED = Util.memoize(EmbersRenderTypes::getSeed);
	private static RenderType getSeed(ResourceLocation texture) {
		return create(
				Embers.MODID + ":crystal_seed_render_type",
				DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false,
				RenderType.CompositeState.builder()
				.setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
				.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
				.setOverlayState(OVERLAY)
				.setLightmapState(LIGHTMAP)
				.createCompositeState(true));
	}

	//render type used for the alchemy circle
	public static final RenderType BEAM = create(
			Embers.MODID + ":beam_render_type",
			DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
			RenderType.CompositeState.builder()
			.setShaderState(PTLC_SHADER)
			.setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.parse(Embers.MODID + ":textures/entity/alchemy_circle.png"), false, false))
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setCullState(NO_CULL)
			//.setDepthTestState(NO_DEPTH_TEST)
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setWriteMaskState(COLOR_DEPTH_WRITE)
			.setOutputState(TRANSLUCENT_TARGET)
			.createCompositeState(false));

	//render type used for highlighting the emitter being aimed
	public static final RenderType GLOW_LINES = create(
			Embers.MODID + ":glow_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
			RenderType.CompositeState.builder()
			.setShaderState(RENDERTYPE_LINES_SHADER)
			.setLightmapState(NO_LIGHTMAP)
			.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(6.0D)))
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
			.setOutputState(ITEM_ENTITY_TARGET)
			.setWriteMaskState(COLOR_DEPTH_WRITE)
			.setCullState(NO_CULL)
			.createCompositeState(false));

	//render type used for the heat bar in tooltips
	public static final RenderType GLOW_GUI = create(
			Embers.MODID + ":glow_gui",
			DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
			RenderType.CompositeState.builder()
			.setShaderState(RENDERTYPE_GUI_SHADER)
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setDepthTestState(LEQUAL_DEPTH_TEST)
			.createCompositeState(false));

	//render type used for the heat bar in tooltips
	public static final RenderType HEAT_BAR_ENDS = create(
			Embers.MODID + ":heat_bar_ends",
			DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false,
			RenderType.CompositeState.builder()
			.setShaderState(POSITION_TEX_SHADER)
			.setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(Embers.MODID, "textures/gui/heat_bar.png"), false, false))
			.setTransparencyState(NO_TRANSPARENCY)
			.setDepthTestState(LEQUAL_DEPTH_TEST)
			.createCompositeState(false));

	//render type used for additive blended text
	public static Function<RenderStateShard.EmptyTextureStateShard, RenderType> GLOW_TEXT = Util.memoize(EmbersRenderTypes::getText);
	private static RenderType getText(RenderStateShard.EmptyTextureStateShard state) {
		RenderType.CompositeState rendertype$state = RenderType.CompositeState.builder()
				.setShaderState(RENDERTYPE_TEXT_SHADER)
				.setTextureState(state)
				.setTransparencyState(LIGHTNING_TRANSPARENCY)
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false);
		return create(Embers.MODID + ":glow_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true, rendertype$state);
	}

	//render types used for alchemical note
	public static final RenderType NOTE_BACKGROUND = getNote(ResourceLocation.fromNamespaceAndPath(Embers.MODID, "textures/gui/alchemical_note.png"));
	public static final RenderType NOTE_PEDESTAL = getNote(ResourceLocation.fromNamespaceAndPath(Embers.MODID, "textures/gui/alchemical_note_pedestal.png"));
	public static RenderType getNote(ResourceLocation location) {
		RenderType.CompositeState rendertype$state = RenderType.CompositeState.builder()
				.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
				.setTextureState(new RenderStateShard.TextureStateShard(location, false, false))
				.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				.setLightmapState(LIGHTMAP)
				.setCullState(NO_CULL)
				.setOverlayState(OVERLAY)
				.createCompositeState(false);
		return create("alchemical_note", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, rendertype$state);
	}

	//render type used for mithril
	public static ResourceLocation MITHRIL_REFLECTION = ResourceLocation.parse(Embers.MODID + ":textures/misc/mithril_reflection.png");
	@SuppressWarnings("resource")
	public static RenderType MITHRIL = create(
			Embers.MODID + ":mithril_render_type",
			DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
			RenderType.CompositeState.builder()
			.setShaderState(MITHRIL_SHADER)
			.setTextureState(new RenderStateShard.EmptyTextureStateShard(() -> {
				TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
				texturemanager.getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);
				RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
				texturemanager.getTexture(MITHRIL_REFLECTION).setFilter(true, false);
				RenderSystem.setShaderTexture(3, MITHRIL_REFLECTION);
				mithrilShader.safeGetUniform("PlayerPos").set(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toVector3f());
			}, () -> {}))
			.setOverlayState(OVERLAY)
			.setLightmapState(LIGHTMAP)
			.createCompositeState(true));
}
