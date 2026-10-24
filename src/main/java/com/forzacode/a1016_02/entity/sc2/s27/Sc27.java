package com.forzacode.a1016_02.entity.sc2.s27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.FogEdge;
import com.forzacode.a1016_02.entity.SightingGates;
import com.forzacode.a1016_02.entity.sc.ScGates;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 27 on the server (docs/SELF_CREATED_2.md, 27; D-098): picks the moment, the other player and the spot, and tells the
 * viewer's client alone ({@link Sc27Payload}); nothing exists on the server and nothing is spawned. The copy lives on that
 * client ({@code entity.client.sc2.s27.S27Client}) by the payload's numbers. The server ends a showing
 * ({@link Sc27EndPayload}) when the other player comes near ({@link Sc27Rules#stillAway}) or into the viewer's view, stops
 * taking part, the viewer does, the story holds it or the layer is switched off. One showing per viewer, kept in memory by
 * UUID; the caps are saved ({@link Sc27Data}). The same for a host and a guest: nothing here asks who hosts. Server thread
 * only. {@link #LIVE} is the game's; tests make their own on their own {@link Hooks}.
 */
public final class Sc27 {
	public static final String CARD_ID = "sc_27";
	/** The showings and arms are looked at this often (ticks). */
	public static final int TICK_EVERY = 10;
	/** A showing is forgotten this long past its life (its walk off can take a while). */
	static final int FORGET_AFTER_TICKS = 20 * 120;
	/** A debug arm lasts this long (ticks). */
	public static final int ARM_TICKS = 20 * 60 * 10;
	/** A debug arm looks for its moment this often (ticks). */
	static final int ARM_EVERY = 20;

	/** What the rules read and send, so tests can stand in for the live world. */
	public interface Hooks {
		/** Every participant (no spectator), the viewer among them. */
		List<ServerPlayer> participants(MinecraftServer server);

		Optional<ServerPlayer> online(MinecraftServer server, UUID player);

		/** Sends to that player's client; false if it cannot take it. */
		boolean send(ServerPlayer player, CustomPacketPayload payload);

		/** The common gates for the viewer ({@link ScGates#refusal(ServerPlayer)}). */
		Optional<String> commonRefusal(ServerPlayer viewer);

		/** The switch and the story ({@link ScGates#storyRefusal}). */
		Optional<String> storyRefusal(MinecraftServer server);

		/** Nobody, spectators included, within {@link Sc27Rules#ALONE_RADIUS} of the viewer ({@link SoundCues#alone}). */
		boolean alone(ServerPlayer viewer);

		/** The overworld's time of day. */
		long timeOfDay(MinecraftServer server);

		/** The world's fog weather counts now. */
		boolean fogCounts(MinecraftServer server);

		/** The viewer's drawn fog end (their client's report, else core's estimate). */
		double fogEnd(ServerPlayer viewer);

		/** The in-game day. */
		long day(MinecraftServer server);

		/** The caps. */
		Sc27Data data(MinecraftServer server);

		/** True if the other player is in the viewer's view (the trace service's rule, the viewer alone). */
		boolean sees(ServerPlayer viewer, ServerPlayer other);
	}

	static final Hooks LIVE_HOOKS = new Hooks() {
		@Override
		public List<ServerPlayer> participants(MinecraftServer server) {
			return Services.watch().participants(server);
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(server.getPlayerList().getPlayer(player));
		}

		@Override
		public boolean send(ServerPlayer player, CustomPacketPayload payload) {
			if (!ServerPlayNetworking.canSend(player, payload.type())) {
				return false;
			}
			ServerPlayNetworking.send(player, payload);
			return true;
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer viewer) {
			return ScGates.refusal(viewer);
		}

		@Override
		public Optional<String> storyRefusal(MinecraftServer server) {
			return ScGates.storyRefusal(server);
		}

		@Override
		public boolean alone(ServerPlayer viewer) {
			return SoundCues.alone(viewer, Sc27Rules.ALONE_RADIUS);
		}

		@Override
		public long timeOfDay(MinecraftServer server) {
			return SightingGates.timeOfDay(server);
		}

		@Override
		public boolean fogCounts(MinecraftServer server) {
			return SightingGates.fogWeather(server);
		}

		@Override
		public double fogEnd(ServerPlayer viewer) {
			return FogEdge.of(viewer).limit();
		}

		@Override
		public long day(MinecraftServer server) {
			return GameClock.day(server);
		}

		@Override
		public Sc27Data data(MinecraftServer server) {
			return Sc27Data.get(server);
		}

		@Override
		public boolean sees(ServerPlayer viewer, ServerPlayer other) {
			return inView(viewer, other);
		}
	};

	/** True if the other player is in the viewer's view by the trace service's rule (near, or in the cone with a line of sight). */
	public static boolean inView(ServerPlayer viewer, ServerPlayer other) {
		if (viewer.level() != other.level()) {
			return false;
		}
		Pacing pacing = ModConfig.pacing();
		int chunks = viewer.level().getServer().getPlayerList().getViewDistance();
		return TraceService.sees(viewer.level(), TraceService.Viewer.of(viewer, chunks), other.getBoundingBox(), pacing.viewNearBlocks,
				pacing.viewConeDegrees);
	}

	/** The game's. */
	public static final Sc27 LIVE = new Sc27(LIVE_HOOKS);

	/**
	 * One showing: its key, whose client, of whom, in which level and where, when (server tick), how long, whether a debug
	 * command made it, and whether the other player was away then (a debug fire may show someone near: then nearness does
	 * not end it).
	 */
	public record Showing(int key, UUID viewer, UUID other, ResourceKey<Level> level, Vec3 spot, int startTick, int lifeTicks, boolean forced,
			boolean awayAtStart, boolean counted) {
		/** The same showing, counted (its viewer's client saw the copy). */
		Showing counting() {
			return new Showing(key, viewer, other, level, spot, startTick, lifeTicks, forced, awayAtStart, true);
		}
	}

	/** A debug arm: until which server tick, and who asked (told when it fires or runs out). */
	public record Arm(int untilTick, @Nullable UUID askedBy) {
	}

	private final Hooks hooks;
	private final Map<UUID, Showing> showings = new LinkedHashMap<>();
	private final Map<UUID, Arm> arms = new LinkedHashMap<>();
	private int nextKey = 1;

	public Sc27(Hooks hooks) {
		this.hooks = hooks;
	}

	// --- the fit ---

	/** Where another player is for the viewer ({@link Sc27Rules.Where}). */
	public static Sc27Rules.Where where(ServerPlayer viewer, ServerPlayer other) {
		if (viewer.level() != other.level()) {
			return Sc27Rules.Where.elsewhere();
		}
		ServerLevel level = other.level();
		BlockPos eye = BlockPos.containing(other.getEyePosition());
		int sky = level.getBrightness(LightLayer.SKY, eye);
		double surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, eye.getX(), eye.getZ());
		return new Sc27Rules.Where(false, viewer.position().distanceTo(other.position()), sky, surface - other.getEyeY());
	}

	/** Every other participant who could be shown: alive, not the viewer. */
	List<ServerPlayer> others(ServerPlayer viewer) {
		List<ServerPlayer> others = new ArrayList<>();
		for (ServerPlayer player : hooks.participants(viewer.level().getServer())) {
			if (!player.getUUID().equals(viewer.getUUID()) && player.isAlive() && !player.isSpectator()) {
				others.add(player);
			}
		}
		return others;
	}

	/** The others who count as away for the viewer ({@link Sc27Rules#away}), in join order. */
	public List<ServerPlayer> awayOthers(ServerPlayer viewer) {
		Sc27Config config = Sc27Config.get();
		List<ServerPlayer> away = new ArrayList<>();
		for (ServerPlayer other : others(viewer)) {
			if (Sc27Rules.away(where(viewer, other), config)) {
				away.add(other);
			}
		}
		return away;
	}

	/**
	 * Why 27 does not fit this viewer now, or empty: the common gates ({@link ScGates}), a showing of theirs still on, the
	 * rules ({@link Sc27Rules#fit}), and (unless {@code withCaps} is false) the caps.
	 */
	public Optional<String> fit(ServerPlayer viewer, boolean withCaps) {
		Optional<String> common = hooks.commonRefusal(viewer);
		if (common.isPresent()) {
			return common;
		}
		if (!viewer.isAlive() || viewer.isSpectator() || viewer.isSleeping()) {
			return Optional.of("busy");
		}
		if (showings.containsKey(viewer.getUUID())) {
			return Optional.of("one is on");
		}
		MinecraftServer server = viewer.level().getServer();
		int sky = viewer.level().getBrightness(LightLayer.SKY, BlockPos.containing(viewer.getEyePosition()));
		List<Sc27Rules.Where> where = others(viewer).stream().map(other -> where(viewer, other)).toList();
		Optional<String> rules = Sc27Rules.fit(sky, hooks.timeOfDay(server), hooks.fogCounts(server), hooks.alone(viewer), where, Sc27Config.get(),
				EntityConfig.get());
		if (rules.isPresent() || !withCaps) {
			return rules;
		}
		Sc27Data data = hooks.data(server);
		return Sc27Rules.caps(data.lastDay(viewer.getUUID()), hooks.day(server), data.count(), Sc27Config.get());
	}

	public Optional<String> fit(ServerPlayer viewer) {
		return fit(viewer, true);
	}

	// --- the fire ---

	/**
	 * Shows the copy to this viewer now: {@link FireResult#FIRED} once its client has the payload; {@link FireResult#NO_SPOT}
	 * when no spot works now (the director keeps the card); {@link FireResult#SKIPPED} while the story holds it, with nobody
	 * to show, a showing of theirs still on, or a client that cannot take it. {@code forced} (debug): the other player may be
	 * anyone else taking part (away first), the caps are not asked but counted.
	 */
	public FireResult fire(ServerPlayer viewer, RandomSource random, boolean forced) {
		MinecraftServer server = viewer.level().getServer();
		if (hooks.storyRefusal(server).isPresent() || showings.containsKey(viewer.getUUID())) {
			return FireResult.SKIPPED;
		}
		List<ServerPlayer> away = awayOthers(viewer);
		if (away.isEmpty() && forced) {
			away = others(viewer);
		}
		if (away.isEmpty()) {
			return FireResult.SKIPPED;
		}
		ServerPlayer other = away.get(random.nextInt(away.size()));
		Sc27Config config = Sc27Config.get();
		double fogEnd = hooks.fogEnd(viewer);
		Optional<Sc27Spot.Spot> spot = Sc27Spot.find(viewer.level(), viewer.getEyePosition(), viewer.position(), viewer.getYHeadRot(),
				Sc27Rules.band(fogEnd, config), fogEnd, random, turning(viewer));
		if (spot.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		Sc27Spot.Spot s = spot.get();
		int key = nextKey++;
		Sc27Payload payload = new Sc27Payload(key, other.getUUID(), s.feet(), s.yaw(), s.walkYaw(), config.lifeTicks(), config.lookTicks(),
				(float) config.nearLimit, items(other), parts(other), other.getMainArm() == HumanoidArm.LEFT, false);
		if (!hooks.send(viewer, payload)) {
			return FireResult.SKIPPED;
		}
		// R10-8: counted once its client saw it ({@link #seen}), never when sent; it stays up to the unseen life while nobody looks.
		showings.put(viewer.getUUID(), new Showing(key, viewer.getUUID(), other.getUUID(), viewer.level().dimension(), s.feet(), server.getTickCount(),
				Math.max(config.lifeTicks(), CopyRules.UNSEEN_LIFE_TICKS), forced, Sc27Rules.away(where(viewer, other), config), false));
		A1016_02.LOGGER.info("[a1016] {}: for {} ({} out){}", CARD_ID, viewer.getName().getString(), Math.round(s.distance()), forced ? " (debug)" : "");
		return FireResult.FIRED;
	}

	/**
	 * R10-8: the viewer's client saw the copy of showing {@code key}: the showing counts now (the viewer's day, the world's
	 * count), once. A key that is not the viewer's showing now (an old one, someone else's) counts nothing.
	 */
	public void seen(ServerPlayer viewer, int key) {
		Showing showing = showings.get(viewer.getUUID());
		if (showing == null || showing.key() != key || showing.counted()) {
			return;
		}
		showings.put(viewer.getUUID(), showing.counting());
		MinecraftServer server = viewer.level().getServer();
		hooks.data(server).record(viewer.getUUID(), hooks.day(server));
	}

	/**
	 * Pure-ish (R10-8): which way the viewer is turning or walking now, as a yaw sign (+1 toward a growing yaw, -1 the other
	 * way, 0 neither): their head's turn since the last tick, else their step's side of their facing.
	 */
	static int turning(ServerPlayer viewer) {
		float turned = net.minecraft.util.Mth.wrapDegrees(viewer.getYHeadRot() - viewer.yHeadRotO);
		if (Math.abs(turned) > 0.5F) {
			return turned > 0 ? 1 : -1;
		}
		double dx = viewer.getX() - viewer.xo;
		double dz = viewer.getZ() - viewer.zo;
		if (dx * dx + dz * dz < 1.0E-4) {
			return 0;
		}
		float step = Sc27Rules.yawToward(0.0, 0.0, dx, dz);
		float side = net.minecraft.util.Mth.wrapDegrees(step - viewer.getYHeadRot());
		return Math.abs(side) < 10.0F || Math.abs(side) > 170.0F ? 0 : side > 0 ? 1 : -1;
	}

	/**
	 * The dev gallery's staging (never game code): the viewer's own profile stands in as the other player, shown at once at
	 * {@code spot} facing {@code yaw}, walking off along {@code walkYaw} once looked at {@code lookTicks}. Nothing is counted.
	 */
	public boolean stage(ServerPlayer viewer, Vec3 spot, float yaw, float walkYaw, int lifeTicks, int lookTicks, List<ItemStack> items) {
		Sc27Payload payload = new Sc27Payload(nextKey++, viewer.getUUID(), spot, yaw, walkYaw, lifeTicks, lookTicks, 4.0F, items, (byte) 0x7F, false,
				true);
		return hooks.send(viewer, payload);
	}

	/** What the other player wears, for the copy: main hand, off hand, head, chest, legs, feet. */
	static List<ItemStack> items(ServerPlayer other) {
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET)) {
			items.add(other.getItemBySlot(slot).copy());
		}
		return items;
	}

	/** Their model parts as vanilla's customisation bits (the hat layer, the jacket, the sleeves...). */
	static byte parts(ServerPlayer other) {
		int bits = 0;
		for (PlayerModelPart part : PlayerModelPart.values()) {
			if (other.isModelPartShown(part)) {
				bits |= part.getMask();
			}
		}
		return (byte) bits;
	}

	// --- the end ---

	/** Ends that viewer's showing: their client is told (the copy walks off if seen, else it is gone). */
	public void end(MinecraftServer server, UUID viewer) {
		Showing showing = showings.remove(viewer);
		if (showing != null) {
			hooks.online(server, viewer).ifPresent(p -> hooks.send(p, new Sc27EndPayload(showing.key())));
		}
	}

	/** Every showing ends (the layer switched off, the story holds it); every arm too. Idempotent. */
	public void endAll(MinecraftServer server) {
		arms.clear();
		for (UUID viewer : new ArrayList<>(showings.keySet())) {
			end(server, viewer);
		}
	}

	/** PARTICIPATION: a viewer who stops taking part is forgotten (their copy goes with their level); a shown player's showings end. */
	public void onParticipation(MinecraftServer server, UUID player, boolean participating) {
		if (participating) {
			return;
		}
		arms.remove(player);
		Showing own = showings.remove(player);
		if (own != null) {
			hooks.online(server, player).ifPresent(p -> hooks.send(p, new Sc27EndPayload(own.key())));
		}
		for (Showing showing : new ArrayList<>(showings.values())) {
			if (showing.other().equals(player)) {
				end(server, showing.viewer());
			}
		}
	}

	/** Every server tick; looks every {@link #TICK_EVERY} ticks. */
	public void tick(MinecraftServer server) {
		if ((!showings.isEmpty() || !arms.isEmpty()) && server.getTickCount() % TICK_EVERY == 0) {
			check(server);
		}
	}

	/**
	 * Looks at every showing now: all end once the story holds it; one ends when its viewer is gone, dead, spectating, asleep
	 * or in another level than it was shown in, when the other player is gone or no longer away ({@link Sc27Rules#stillAway})
	 * or in the viewer's view; one is forgotten past its life and the walk's time. Then the debug arms.
	 */
	public void check(MinecraftServer server) {
		if (hooks.storyRefusal(server).isPresent()) {
			endAll(server);
			return;
		}
		Sc27Config config = Sc27Config.get();
		int now = server.getTickCount();
		for (Showing showing : new ArrayList<>(showings.values())) {
			Optional<ServerPlayer> viewer = hooks.online(server, showing.viewer());
			if (viewer.isEmpty()) {
				showings.remove(showing.viewer());
				continue;
			}
			ServerPlayer v = viewer.get();
			if (v.level().dimension() != showing.level()) {
				// The viewer changed level: the client is told too, so a copy still waiting never stands in the new one (R12-38).
				end(server, showing.viewer());
				continue;
			}
			if (now - showing.startTick() > showing.lifeTicks() + FORGET_AFTER_TICKS) {
				showings.remove(showing.viewer()); // past its walk
				continue;
			}
			Optional<ServerPlayer> other = hooks.online(server, showing.other());
			boolean ends = !v.isAlive() || v.isSpectator() || v.isSleeping() || other.isEmpty() || other.get().isSpectator();
			if (!ends && showing.awayAtStart()) {
				ends = !Sc27Rules.stillAway(where(v, other.get()), config) || hooks.sees(v, other.get());
			}
			if (ends) {
				end(server, showing.viewer());
			}
		}
		checkArms(server, now);
	}

	// --- debug arms ---

	/** {@code sc 27 arm}: looks for the next fitting moment (the caps aside) for {@link #ARM_TICKS}. */
	public void arm(ServerPlayer viewer, @Nullable UUID askedBy) {
		arms.put(viewer.getUUID(), new Arm(viewer.level().getServer().getTickCount() + ARM_TICKS, askedBy));
	}

	public boolean isArmed(UUID viewer) {
		return arms.containsKey(viewer);
	}

	/** Server ticks left on this viewer's debug arm at server tick {@code now}, or -1 (read-only, for the debug index). */
	public long armTicksLeft(UUID viewer, int now) {
		Arm arm = arms.get(viewer);
		return arm == null ? -1 : Math.max(0, arm.untilTick() - now);
	}

	private void checkArms(MinecraftServer server, int now) {
		if (arms.isEmpty() || now % ARM_EVERY != 0) {
			return;
		}
		for (Map.Entry<UUID, Arm> entry : new ArrayList<>(arms.entrySet())) {
			UUID uuid = entry.getKey();
			Arm arm = entry.getValue();
			Optional<ServerPlayer> viewer = hooks.online(server, uuid);
			if (viewer.isEmpty()) {
				arms.remove(uuid);
				continue;
			}
			if (now >= arm.untilTick()) {
				arms.remove(uuid);
				tell(server, arm.askedBy(), "27: the arm for " + viewer.get().getName().getString() + " ran out (" + fit(viewer.get(), false).orElse("no spot")
						+ ")");
				continue;
			}
			if (fit(viewer.get(), false).isEmpty()) {
				FireResult result = Services.director().fire(server, viewer.get(), CARD_ID, false);
				if (result == FireResult.FIRED) {
					arms.remove(uuid);
					tell(server, arm.askedBy(), "27: shown to " + viewer.get().getName().getString());
				}
			}
		}
	}

	private void tell(MinecraftServer server, @Nullable UUID player, String line) {
		if (player != null) {
			hooks.online(server, player).ifPresent(p -> p.sendSystemMessage(Component.literal("[a1016] " + line)));
		}
	}

	// --- state ---

	public Optional<Showing> showing(UUID viewer) {
		return Optional.ofNullable(showings.get(viewer));
	}

	public List<Showing> showings() {
		return List.copyOf(showings.values());
	}

	/** Forgets everything without telling anyone (server stop). */
	public void clear() {
		showings.clear();
		arms.clear();
	}

	/** Lines for debug: the showings, the arms, the caps. */
	public List<String> lines(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		Sc27Data data = hooks.data(server);
		lines.add(String.format(Locale.ROOT, "27: %d of %d in this world; %d on, %d armed", data.count(), Sc27Config.get().maxPerWorld, showings.size(),
				arms.size()));
		for (Showing s : showings.values()) {
			String viewer = hooks.online(server, s.viewer()).map(p -> p.getName().getString()).orElse(s.viewer().toString());
			String other = hooks.online(server, s.other()).map(p -> p.getName().getString()).orElse(s.other().toString());
			lines.add(String.format(Locale.ROOT, "  %s sees %s at %s, %.0f s ago", viewer, other, BlockPos.containing(s.spot()).toShortString(),
					(server.getTickCount() - s.startTick()) / 20.0));
		}
		return lines;
	}
}
