package com.rekindled.embers.network.message;

import java.util.HashMap;
import java.util.Map;

import com.rekindled.embers.research.ResearchManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.PacketFlow;

public class MessageResearchData {
	public static final int NAME_MAX_LENGTH = 64;
	private final Map<ResourceLocation, Boolean> ticks;

	public MessageResearchData() {
		this.ticks = new HashMap<>();
	}

	public MessageResearchData(Map<ResourceLocation, Boolean> ticks) {
		this.ticks = new HashMap<>(ticks);
	}

	public static void encode(MessageResearchData msg, FriendlyByteBuf buf) {
		buf.writeInt(msg.ticks.size());
		for (Map.Entry<ResourceLocation, Boolean> entry : msg.ticks.entrySet()) {
			buf.writeResourceLocation(entry.getKey());
			buf.writeBoolean(entry.getValue());
		}
	}

	public static MessageResearchData decode(FriendlyByteBuf buf) {
		Map<ResourceLocation, Boolean> ticks = new HashMap<>();
		int entries = buf.readInt();
		for(int i = 0; i < entries; i++) {
			ResourceLocation key = buf.readResourceLocation();
			boolean value = buf.readBoolean();
			ticks.put(key, value);
		}
		return new MessageResearchData(ticks);
	}

	public static void handle(MessageResearchData msg, IPayloadContext ctx) {
		if (ctx.flow() == PacketFlow.CLIENTBOUND) {
			ctx.enqueueWork(() -> {
				ResearchManager.receiveResearchData(msg.ticks);
			});
		}
	}
}
