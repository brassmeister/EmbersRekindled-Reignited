package com.rekindled.embers.recipe;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rekindled.embers.ConfigManager;
import com.rekindled.embers.RegistryManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class AlchemyRecipeForBabies extends AlchemyRecipeBase {

	public static final Serializer SERIALIZER = new Serializer();

	public AlchemyRecipeForBabies(ResourceLocation id, Ingredient tablet, ArrayList<Ingredient> aspects, ArrayList<Ingredient> inputs, ItemStack output, ItemStack failure) {
		super(id, tablet, aspects, inputs, output, failure);
	}

	public Long cachedSeed = null;
	public ArrayList<Ingredient> code = null;

	@Override
	public ArrayList<Ingredient> getCode(long seed) {
		if (!ConfigManager.randomizeAlchemyAspectus()) {
			return super.getCode(seed);
		}
		if (cachedSeed == null || cachedSeed != seed) {
			int incr = 0;
			boolean incorrectCode = true;
			while (incorrectCode) {
				code = super.getCode(seed + incr);
				incorrectCode = false;
				for (Ingredient ingredient : aspects) {
					//only return this recipe if it contains all possible aspecti
					if (!code.contains(ingredient)) {
						incorrectCode = true;
						break;
					}
				}
				incr++;
			}
			cachedSeed = seed;
		}
		return code;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	public static class Serializer extends LegacyRecipeSerializer<AlchemyRecipeForBabies> {

		@Override
		public AlchemyRecipeForBabies fromJson(ResourceLocation recipeId, JsonObject json) {
			Ingredient tablet = RecipeSerialization.readIngredient(json.get("tablet"));

			ArrayList<Ingredient> inputs = new ArrayList<>();
			JsonArray inputJson = GsonHelper.getAsJsonArray(json, "inputs", null);
			if (inputJson != null) {
				for (JsonElement element : inputJson) {
					inputs.add(RecipeSerialization.readIngredient(element));
				}
			}
			ArrayList<Ingredient> aspects = new ArrayList<>();
			JsonArray aspectJson = GsonHelper.getAsJsonArray(json, "aspects", null);
			if (aspectJson != null) {
				for (JsonElement element : aspectJson) {
					aspects.add(RecipeSerialization.readIngredient(element));
				}
			}
			ItemStack output = RecipeSerialization.readItem(GsonHelper.getAsJsonObject(json, "output"));
			ItemStack failure;
			if (json.has("failure")) {
				failure = RecipeSerialization.readItem(GsonHelper.getAsJsonObject(json, "failure"));
			} else {
				failure = new ItemStack(RegistryManager.ALCHEMICAL_WASTE.get());
			}
			return new AlchemyRecipeForBabies(recipeId, tablet, aspects, inputs, output, failure);
		}

		@Override
		public @Nullable AlchemyRecipeForBabies fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
			Ingredient tablet = RecipeSerialization.readIngredient(buffer);
			ArrayList<Ingredient> aspects = buffer.readCollection((i) -> new ArrayList<>(), (buf) -> RecipeSerialization.readIngredient(buf));
			ArrayList<Ingredient> inputs = buffer.readCollection((i) -> new ArrayList<>(), (buf) -> RecipeSerialization.readIngredient(buf));
			ItemStack output = RecipeSerialization.readItem(buffer);
			ItemStack failure = RecipeSerialization.readItem(buffer);

			return new AlchemyRecipeForBabies(recipeId, tablet, aspects, inputs, output, failure);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buffer, AlchemyRecipeForBabies recipe) {
			RecipeSerialization.writeIngredient(buffer, recipe.tablet);
			buffer.writeCollection(recipe.aspects, (buf, input) -> RecipeSerialization.writeIngredient(buf, input));
			buffer.writeCollection(recipe.inputs, (buf, input) -> RecipeSerialization.writeIngredient(buf, input));
			RecipeSerialization.writeItem(buffer, recipe.output);
			RecipeSerialization.writeItem(buffer, recipe.failure);
		}
	}
}
