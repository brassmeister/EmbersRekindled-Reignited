package com.rekindled.embers.blockentity.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rekindled.embers.EmbersClientEvents;
import com.rekindled.embers.block.FieldChartBlock;
import com.rekindled.embers.blockentity.FieldChartBlockEntity;
import com.rekindled.embers.compat.sublevel.SubLevelCompat;
import com.rekindled.embers.render.EmbersRenderTypes;
import com.rekindled.embers.util.EmberGenUtil;
import com.rekindled.embers.util.EmbersColors;
import com.rekindled.embers.util.Misc;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FieldChartBlockEntityRenderer implements BlockEntityRenderer<FieldChartBlockEntity> {

	public static float baseHeight = 0.375f;
	public static float height = 0.55f;

	interface IChartSource {
		float get(int x, int z);
	}

	public FieldChartBlockEntityRenderer(BlockEntityRendererProvider.Context pContext) {

	}

	@Override
	public void render(FieldChartBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (blockEntity != null && blockEntity.getLevel() != null) {
			VertexConsumer buffer = bufferSource.getBuffer(EmbersRenderTypes.fieldChart());
			RenderSystem.enableDepthTest();
			RenderSystem.disableCull();

			BlockState state = SubLevelCompat.findBlockState(blockEntity, blockEntity.getBlockPos());

			if (state.hasProperty(FieldChartBlock.INVERTED) && state.getValue(FieldChartBlock.INVERTED)) {
				renderChart(blockEntity, 0, 0, 0, buffer, poseStack.last().pose(), (cx, cz) -> EmberGenUtil.getEmberStability(EmbersClientEvents.seed, cx, cz), EmbersColors.EMBER_INVERTED, Misc.multColor(EmbersColors.EMBER_INVERTED, 1.5f), Misc.multColor(EmbersColors.EMBER_INVERTED, 2.0f));
			} else {
				renderChart(blockEntity, 0, 0, 0, buffer, poseStack.last().pose(), (cx, cz) -> EmberGenUtil.getEmberDensity(EmbersClientEvents.seed, cx, cz), EmbersColors.EMBER, Misc.multColor(EmbersColors.EMBER, 1.5f), Misc.multColor(EmbersColors.EMBER, 2.0f));
			}
			RenderSystem.enableCull();
		}
	}

	public void renderChart(FieldChartBlockEntity blockEntity, float x, float y, float z, VertexConsumer buffer, Matrix4f matrix4f, IChartSource source, Vector3f color1, Vector3f color2, Vector3f color3) {
		Level level = blockEntity.getLevel();
		BlockPos pos = blockEntity.getBlockPos();
		int signal = level.getBestNeighborSignal(pos);
		float brightness = 1.0f;
		if (signal > 2) {
			brightness = Misc.getLightBrightness(15 - signal, EmbersClientEvents.ticks);
		}
		float red1 = brightness * color1.x;
		float green1 = brightness * color1.y;
		float blue1 = brightness * color1.z;

		float[][][] valueCache = new float[10][10][4];
		for (float i = -160; i < 160; i += 32) {
			for (float j = -160; j < 160; j += 32) {
				float[] values = new float[] {
						getChartValue(blockEntity, pos, source, (int) i / 2, (int) j / 2),
						getChartValue(blockEntity, pos, source, (int) i / 2 + 16, (int) j / 2),
						getChartValue(blockEntity, pos, source, (int) i / 2 + 16, (int) j / 2 + 16),
						getChartValue(blockEntity, pos, source, (int) i / 2, (int) j / 2 + 16)
				};
				valueCache[(int) (i/32f)+5][(int) (j/32f)+5] = values;

				float alphaul = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i), Math.abs(j)) / 160f));
				float alphaur = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i + 32f), Math.abs(j)) / 160f));
				float alphadr = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i + 32f), Math.abs(j + 32f)) / 160f));
				float alphadl = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i), Math.abs(j + 32f)) / 160f));
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f), y + baseHeight + values[0] * height, z + 0.5f + 1.25f * (j / 160f)).setUv(0, 0).setColor(red1 * alphaul, green1 * alphaul, blue1 * alphaul, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f) + 0.25f, y + baseHeight + values[1] * height, z + 0.5f + 1.25f * (j / 160f)).setUv(1, 0).setColor(red1 * alphaur, green1 * alphaur, blue1 * alphaur, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f) + 0.25f, y + baseHeight + values[2] * height, z + 0.5f + 1.25f * (j / 160f) + 0.25f).setUv(1, 1).setColor(red1 * alphadr, green1 * alphadr, blue1 * alphadr, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f), y + baseHeight + values[3] * height, z + 0.5f + 1.25f * (j / 160f) + 0.25f).setUv(0, 1).setColor(red1 * alphadl, green1 * alphadl, blue1 * alphadl, 1);
			}
		}
		float red2 = brightness * color2.x;
		float green2 = brightness * color2.y;
		float blue2 = brightness * color2.z;
		for (float i = -160; i < 160; i += 32) {
			for (float j = -160; j < 160; j += 32) {
				float[] values = valueCache[(int) (i/32f)+5][(int) (j/32f)+5];
				float amountul = values[0];
				float amountur = values[1];
				float amountdr = values[2];
				float amountdl = values[3];
				float alphaul = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i), Math.abs(j)) / 160f) * amountul * amountul) * 0.875f;
				float alphaur = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i + 32f), Math.abs(j)) / 160f) * amountur * amountur) * 0.875f;
				float alphadr = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i + 32f), Math.abs(j + 32f)) / 160f) * amountdr * amountdr) * 0.875f;
				float alphadl = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i), Math.abs(j + 32f)) / 160f) * amountdl * amountdl) * 0.875f;
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f), y + baseHeight + amountul * height, z + 0.5f + 1.25f * (j / 160f)).setUv(0, 0).setColor(red2 * alphaul, green2 * alphaul, blue2 * alphaul, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f) + 0.25f, y + baseHeight + amountur * height, z + 0.5f + 1.25f * (j / 160f)).setUv(1, 0).setColor(red2 * alphaur, green2 * alphaur, blue2 * alphaur, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f) + 0.25f, y + baseHeight + amountdr * height, z + 0.5f + 1.25f * (j / 160f) + 0.25f).setUv(1, 1).setColor(red2 * alphadr, green2 * alphadr, blue2 * alphadr, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f), y + baseHeight + amountdl * height, z + 0.5f + 1.25f * (j / 160f) + 0.25f).setUv(0, 1).setColor(red2 * alphadl, green2 * alphadl, blue2 * alphadl, 1);
			}
		}
		float red3 = brightness * color3.x;
		float green3 = brightness * color3.y;
		float blue3 = brightness * color3.z;
		for (float i = -160; i < 160; i += 32) {
			for (float j = -160; j < 160; j += 32) {
				float[] values = valueCache[(int) (i/32f)+5][(int) (j/32f)+5];
				float amountul = values[0];
				float amountur = values[1];
				float amountdr = values[2];
				float amountdl = values[3];
				float alphaul = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i), Math.abs(j)) / 160f) * amountul * amountul * amountul);
				float alphaur = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i + 32f), Math.abs(j)) / 160f) * amountur * amountur * amountur);
				float alphadr = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i + 32f), Math.abs(j + 32f)) / 160f) * amountdr * amountdr * amountdr);
				float alphadl = Math.min(1.0f, Math.max(0.0f, 1.0f - Math.max(Math.abs(i), Math.abs(j + 32f)) / 160f) * amountdl * amountdl * amountdl);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f), y + baseHeight + amountul * height, z + 0.5f + 1.25f * (j / 160f)).setUv(0, 0).setColor(red3 * alphaul, green3 * alphaul, blue3 * alphaul, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f) + 0.25f, y + baseHeight + amountur * height, z + 0.5f + 1.25f * (j / 160f)).setUv(1, 0).setColor(red3 * alphaur, green3 * alphaur, blue3 * alphaur, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f) + 0.25f, y + baseHeight + amountdr * height, z + 0.5f + 1.25f * (j / 160f) + 0.25f).setUv(1, 1).setColor(red3 * alphadr, green3 * alphadr, blue3 * alphadr, 1);
				buffer.addVertex(matrix4f, x + 0.5f + 1.25f * (i / 160f), y + baseHeight + amountdl * height, z + 0.5f + 1.25f * (j / 160f) + 0.25f).setUv(0, 1).setColor(red3 * alphadl, green3 * alphadl, blue3 * alphadl, 1);
			}
		}
	}

	private float getChartValue(FieldChartBlockEntity blockEntity, BlockPos chartPos, IChartSource source, int localXOffset, int localZOffset) {
		Vec3 physicalSample = SubLevelCompat.toPhysicalPosition(blockEntity, new Vec3(chartPos.getX() + localXOffset, chartPos.getY() + 0.5D, chartPos.getZ() + localZOffset));
		BlockPos samplePos = BlockPos.containing(physicalSample);
		return source.get(samplePos.getX(), samplePos.getZ());
	}
}
