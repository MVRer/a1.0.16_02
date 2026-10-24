package com.forzacode.a1016_02.entity.sc2.s27;

import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the viewer's client only (27, {@code a1016_02:entity/sc27}): show a copy of the other player {@code other} (their
 * skin and name from this client's own player list) standing at {@code spot} facing {@code yaw}, for {@code lifeTicks};
 * looked at {@code lookTicks} near the middle of the screen it turns and walks off along {@code walkYaw}; the viewer within
 * {@code near} makes it go. It wears what they wear ({@code items}: main hand, off hand, head, chest, legs, feet), with
 * their model parts ({@code parts}, vanilla's customisation bits) and main arm. {@code key} names this showing for
 * {@link Sc27EndPayload}. {@code staged}: the dev gallery's stand-in (the viewer's own profile as "Friend", shown at
 * once); a client outside a development environment ignores such a payload. The client's side is
 * {@code entity.client.sc2.s27.S27Client}.
 */
public record Sc27Payload(int key, UUID other, Vec3 spot, float yaw, float walkYaw, int lifeTicks, int lookTicks, float near, List<ItemStack> items,
		byte parts, boolean leftHanded, boolean staged) implements CustomPacketPayload {
	public static final Type<Sc27Payload> TYPE = new Type<>(A1016_02.id("entity/sc27"));
	/** Main hand, off hand, head, chest, legs, feet. */
	public static final int ITEMS = 6;
	public static final StreamCodec<RegistryFriendlyByteBuf, Sc27Payload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.key);
		UUIDUtil.STREAM_CODEC.encode(buf, p.other);
		buf.writeDouble(p.spot.x);
		buf.writeDouble(p.spot.y);
		buf.writeDouble(p.spot.z);
		buf.writeFloat(p.yaw);
		buf.writeFloat(p.walkYaw);
		buf.writeVarInt(p.lifeTicks);
		buf.writeVarInt(p.lookTicks);
		buf.writeFloat(p.near);
		for (int i = 0; i < ITEMS; i++) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, i < p.items.size() ? p.items.get(i) : ItemStack.EMPTY);
		}
		buf.writeByte(p.parts);
		buf.writeBoolean(p.leftHanded);
		buf.writeBoolean(p.staged);
	}, buf -> {
		int key = buf.readVarInt();
		UUID other = UUIDUtil.STREAM_CODEC.decode(buf);
		Vec3 spot = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		float yaw = buf.readFloat();
		float walkYaw = buf.readFloat();
		int life = buf.readVarInt();
		int look = buf.readVarInt();
		float near = buf.readFloat();
		ItemStack[] items = new ItemStack[ITEMS];
		for (int i = 0; i < ITEMS; i++) {
			items[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
		}
		return new Sc27Payload(key, other, spot, yaw, walkYaw, life, look, near, List.of(items), buf.readByte(), buf.readBoolean(), buf.readBoolean());
	});

	public Sc27Payload {
		lifeTicks = Math.max(1, lifeTicks);
		lookTicks = Math.max(1, lookTicks);
		items = List.copyOf(items);
	}

	@Override
	public Type<Sc27Payload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
