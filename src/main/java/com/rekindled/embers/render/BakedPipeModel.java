package com.rekindled.embers.render;

import java.util.ArrayList;
import java.util.List;

import com.rekindled.embers.blockentity.PipeBlockEntityBase;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

public class BakedPipeModel extends BakedModelWrapper<BakedModel> {

	private final BakedModel centerModel;
	private BakedModel[] connectionModel;
	private BakedModel[] endModel;
	public static final List<BakedQuad> EMPTY = new ArrayList<BakedQuad>();

	@SuppressWarnings("unchecked")
	public final List<BakedQuad>[] QUAD_CACHE = new List[729];

	public BakedPipeModel(BakedModel centerModel, BakedModel[] connectionModel, BakedModel[] endModel) {
		super(centerModel);
		this.centerModel = centerModel;
		this.connectionModel = connectionModel;
		this.endModel = endModel;
	}

	public static int getCacheIndex(int[] data) {
		return (((((data[0] * 3 + data[1]) * 3 + data[2]) * 3 + data[3]) * 3 + data[4]) * 3) + data[5];
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof PipeBlockEntityBase pipe) {
			return pipe.getModelData();
		}
		return super.getModelData(level, pos, state, modelData);
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
		if (side != null)
			return EMPTY;
		int[] sides = data.get(PipeBlockEntityBase.DATA_TYPE);

		if (sides != null) {
			List<BakedQuad> quads = QUAD_CACHE[getCacheIndex(sides)];
			if (quads != null)
				return quads;

			quads = new ArrayList<BakedQuad>();
			quads.addAll(centerModel.getQuads(state, side, rand, data, renderType));
			if (quads.isEmpty())
				return quads;
			for (int i = 0; i < sides.length; i++) {
				if (sides[i] == 1)
					quads.addAll(connectionModel[i].getQuads(state, side, rand, data, renderType));
				else if (sides[i] == 2)
					quads.addAll(endModel[i].getQuads(state, side, rand, data, renderType));
			}
			if (!quads.isEmpty())
				QUAD_CACHE[getCacheIndex(sides)] = new ArrayList<BakedQuad>(quads);
			return quads;
		}
		return centerModel.getQuads(state, side, rand, data, renderType);
	}

	@Override
	public boolean useAmbientOcclusion() {
		return centerModel.useAmbientOcclusion();
	}

	@Override
	public boolean isGui3d() {
		return centerModel.isGui3d();
	}

	@Override
	public boolean usesBlockLight() {
		return centerModel.usesBlockLight();
	}

	@Override
	public boolean isCustomRenderer() {
		return centerModel.isCustomRenderer();
	}

	@Override
	public ItemOverrides getOverrides() {
		return ItemOverrides.EMPTY;
	}
}
