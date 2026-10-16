package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.dig.mixin.FenceGateBlockAccessor;
import com.forzacode.a1016_02.dig.mixin.TrapDoorBlockAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Blind moments (D-077): while a player has a menu open ({@link PlayerWatch#blindTicks}: the inventory, a container, a
 * book or sign being edited, the advancements, the pause menu on a server), they are not watching the world, and that
 * is when it happens. Three cards ({@link BlindCards}), each for one player alone ({@code PERSONAL}) and only in a
 * blind moment ({@code BLIND}):
 * <ul>
 * <li>{@code blind_step}: one or two footsteps 3 to 5 blocks behind them, against their facing, the floor block's own
 * step sound (the ladder's recipe); fake: their own step, late.</li>
 * <li>{@code blind_torch}: a torch or lantern a player placed, 6 to 20 blocks away and out of everyone's view, goes
 * ({@link TraceService#remove}, cause {@value #CAUSE_TORCH}): only while another light source is within 8 blocks of
 * it, never the last light within 6 blocks of a bed; its support stays.</li>
 * <li>{@code blind_door}: a closed door, trapdoor or fence gate 4 to 12 blocks away and out of view plays its own open
 * then close sound; nothing moves.</li>
 * </ul>
 * The gates, every card: blind at least {@code blindMinSeconds} (1.5 s), no combat for {@code blindNoCombatSeconds}
 * (30), and alone by the ladder's rule ({@link Ladder#alone}: no sharer and no other player at all, spectators and the
 * dead included, within the shared moment radius, 48). A forced fire keeps every gate (debug: {@code /a1016 dig blind
 * arm}). The sounds go out through {@link SoundCues#playAt}; a second step and a door's close play only while the
 * player is still blind and alone ({@link BlindCues}).
 */
final class Blind {
	static final String CAUSE_TORCH = "dig:blind_torch";

	/** The three cards. */
	enum Kind {
		STEP("blind_step"),
		TORCH("blind_torch"),
		DOOR("blind_door");

		final String id;

		Kind(String id) {
			this.id = id;
		}
	}

	/** Why a blind card does not fit right now. */
	enum Why {
		NOT_BLIND("not blind long enough (no menu open, or for less than blindMinSeconds)"),
		COMBAT("combat too recently"),
		NOT_ALONE("someone else (a spectator too) within the shared moment radius"),
		NO_FLOOR("no floor behind them to step on"),
		NO_TORCH("no player's torch or lantern that may go"),
		NO_DOOR("no closed door, trapdoor or gate out of view");

		final String text;

		Why(String text) {
			this.text = text;
		}
	}

	/** Whether blocks are out of everyone's view: {@link TraceService#isOutOfView} live, a test's own viewers in tests. */
	interface View {
		boolean outOfView(ServerLevel level, Collection<BlockPos> positions);
	}

	/**
	 * What the blind cards read and use: whose moment it is and since when (the watch), the numbers, the sound out and
	 * who it reaches ({@link Ladder.Sounds}, the alone rule's listeners too), the view, the trace service, the bases (for
	 * the beds) and the ticks since combat. {@link #live} for the game; tests make their own (the live watch ignores mock
	 * players: tests mark their own players blind on their own watch, {@link PlayerWatch#markBlind}).
	 */
	record Env(PlayerWatch watch, DigConfig config, Ladder.Sounds sounds, View view, TraceService traces, Bases bases,
			ToLongFunction<ServerPlayer> sinceCombat) {
		static Env live(MinecraftServer server) {
			PlayerWatch watch = Services.watch();
			return new Env(watch, DigConfig.get(), (level, sound, source, pos, volume, pitch) -> SoundCues.playAt(level, sound, source, pos, volume, pitch),
					(level, positions) -> Services.traces().isOutOfView(level, positions), Services.traces(), Bases.live(server), watch::ticksSinceCombat);
		}
	}

	private Blind() {
	}

	// --- the gates ---

	/** The blind wait in ticks ({@code blindMinSeconds}, at least 1). */
	static long minBlindTicks(DigConfig config) {
		return Math.max(1, ModConfig.realTicks(config.blindMinSeconds));
	}

	/** The gates every blind card shares (null: they pass): blind long enough, no combat lately, alone. */
	static @Nullable Why gates(ServerPlayer player, Env env) {
		DigConfig config = env.config();
		if (env.watch().blindTicks(player) < minBlindTicks(config)) {
			return Why.NOT_BLIND;
		}
		if (env.sinceCombat().applyAsLong(player) <= ModConfig.realTicks(config.blindNoCombatSeconds)) {
			return Why.COMBAT;
		}
		if (!Ladder.alone(player, env.watch(), env.sounds())) {
			return Why.NOT_ALONE;
		}
		return null;
	}

	/** Whether {@code kind} fits {@code player} now (null) or why not: the gates, then a spot to do it. Cheap enough to ask often. */
	static @Nullable Why check(Kind kind, ServerPlayer player, Env env) {
		Why why = gates(player, env);
		if (why != null) {
			return why;
		}
		return switch (kind) {
			case STEP -> floorBehind(player, env.config()) ? null : Why.NO_FLOOR;
			case TORCH -> torch(player, env, null) != null ? null : Why.NO_TORCH;
			case DOOR -> door(player, env, null) != null ? null : Why.NO_DOOR;
		};
	}

	/**
	 * Fires {@code kind} for the context's player: the gates again ({@code SKIPPED} if one fails; a forced fire keeps
	 * them), then the spot ({@code NO_SPOT} without one: the director keeps the card), then the deed.
	 */
	static FireResult fire(Kind kind, FireContext ctx, Env env) {
		ServerPlayer player = ctx.player();
		if (gates(player, env) != null) {
			return FireResult.SKIPPED;
		}
		return switch (kind) {
			case STEP -> ctx.fake() ? ownStepLate(player, env) : steps(player, env, ctx.random());
			case TORCH -> takeTorch(player, env);
			case DOOR -> doorSounds(player, env, ctx.random());
		};
	}

	// --- blind_step ---

	/** The player's facing, flat: where their look points, ignoring the pitch (a unit vector). */
	static Vec3 facing(ServerPlayer player) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/**
	 * Where a step {@code back} blocks behind the player (against their facing) lands: on the floor of the cell there, at
	 * the player's level or one up or down, where someone could stand (two cells without collision or fluid, a floor
	 * under them); null if none.
	 */
	static @Nullable Vec3 behind(ServerPlayer player, double back) {
		ServerLevel level = player.level();
		Vec3 point = player.position().subtract(facing(player).scale(back));
		int y = player.blockPosition().getY();
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos cell = BlockPos.containing(point.x, y + dy, point.z);
			if (standable(level, cell)) {
				return new Vec3(point.x, cell.getY(), point.z);
			}
		}
		return null;
	}

	/** True if someone could stand in {@code cell}: it and the cell above free of collision and fluid, a floor under it. */
	static boolean standable(ServerLevel level, BlockPos cell) {
		if (!level.isLoaded(cell)) {
			return false;
		}
		BlockPos floor = cell.below();
		return free(level, cell) && free(level, cell.above()) && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
	}

	private static boolean free(ServerLevel level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		return state.getCollisionShape(level, cell).isEmpty() && state.getFluidState().isEmpty();
	}

	/** True if a step could land somewhere {@code blindStepBackMin} to {@code blindStepBackMax} blocks behind the player. */
	static boolean floorBehind(ServerPlayer player, DigConfig config) {
		int min = Math.max(1, config.blindStepBackMin);
		for (int back = min; back <= Math.max(min, config.blindStepBackMax); back++) {
			if (behind(player, back) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The steps: one, or two (the second a block closer, {@code blindStepTicks} later), 3 to 5 blocks behind the player
	 * against their facing, as the ladder's rung 1 plans them; a planned step with no floor is left out, and if none has
	 * one, a single step goes on the first floor 3 to 5 back. Each the floor block's own step sound, PLAYERS, at
	 * {@code blindStepVolume} (at most 1: vanilla's 16 blocks, inside the alone radius) and the sound type's pitch.
	 */
	static List<BlindCues.Cue> planSteps(ServerPlayer player, DigConfig config, RandomSource random) {
		int min = Math.max(1, config.blindStepBackMin);
		int max = Math.max(min, config.blindStepBackMax);
		List<Integer> backs;
		if (max > min && random.nextDouble() < config.blindStepTwoStepsChance) {
			int first = min + 1 + random.nextInt(max - min);
			backs = List.of(first, first - 1);
		} else {
			backs = List.of(min + random.nextInt(max - min + 1));
		}
		ServerLevel level = player.level();
		float volume = Mth.clamp(config.blindStepVolume, 0.0F, 1.0F);
		int cadence = Math.max(1, config.blindStepTicks);
		List<BlindCues.Cue> steps = new ArrayList<>();
		for (int back : backs) {
			Vec3 pos = behind(player, back);
			if (pos != null) {
				steps.add(step(level, steps.size() * (long) cadence, pos, volume, false));
			}
		}
		for (int back = min; steps.isEmpty() && back <= max; back++) {
			Vec3 pos = behind(player, back);
			if (pos != null) {
				steps.add(step(level, 0, pos, volume, false));
			}
		}
		return steps;
	}

	/**
	 * A step at {@code pos}: the floor's own step sound at the sound type's pitch, PLAYERS (the ladder's recipe); the
	 * player's own step ({@code own}) at vanilla's step volume, 0.15 of the type's.
	 */
	static BlindCues.Cue step(ServerLevel level, long at, Vec3 pos, float volume, boolean own) {
		SoundType type = floorSound(level, BlockPos.containing(pos));
		float loud = own ? Mth.clamp(type.getVolume() * 0.15F, 0.0F, 1.0F) : volume;
		return new BlindCues.Cue(at, holder(type.getStepSound()), SoundSource.PLAYERS, pos, loud, type.getPitch());
	}

	/**
	 * The sounds of a step in {@code cell}: the floor it is on (a slab or carpet in the cell itself, else the block under
	 * it), or what covers that floor where vanilla's step plays the cover ({@link SoundCards#stepSound}: one snow layer,
	 * leaf litter, pink petals ... over the grass); stone with no floor. The ladder's {@link SoundCards#soundAt} takes stone
	 * for anything not a full block, right for a tunnel's floor; a base's floor is often slabs, stairs or carpet, and a
	 * step there sounds like them.
	 */
	static SoundType floorSound(ServerLevel level, BlockPos cell) {
		for (BlockPos floor : new BlockPos[] {cell, cell.below()}) {
			BlockState state = level.getBlockState(floor);
			if (!state.getCollisionShape(level, floor).isEmpty()) {
				return SoundCards.stepSound(level, floor, state.getSoundType());
			}
		}
		return SoundType.STONE;
	}

	private static FireResult steps(ServerPlayer player, Env env, RandomSource random) {
		List<BlindCues.Cue> steps = planSteps(player, env.config(), random);
		if (steps.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		DigTicker.INSTANCE.blind().start(player.level().getServer(), new BlindCues.Sequence(Kind.STEP.id, player, env, steps));
		return FireResult.FIRED;
	}

	/** The fake: just the player's own step, at their feet on their floor at vanilla's step volume, {@code blindStepTicks} late. */
	private static FireResult ownStepLate(ServerPlayer player, Env env) {
		BlindCues.Cue own = step(player.level(), Math.max(1, env.config().blindStepTicks), player.position(), 0.0F, true);
		DigTicker.INSTANCE.blind().start(player.level().getServer(), new BlindCues.Sequence(Kind.STEP.id + " (fake)", player, env, List.of(own)));
		return FireResult.FIRED;
	}

	// --- blind_torch ---

	/**
	 * A light {@code blind_torch} may take: a torch or wall torch (soul and copper ones too, not redstone torches,
	 * {@link Tunnels#isTorch}) or a lantern (soul and copper ones too).
	 */
	static boolean isBlindLight(BlockState state) {
		return Tunnels.isTorch(state) || state.getBlock() instanceof LanternBlock;
	}

	/**
	 * The torch {@code blind_torch} takes now, or null: the nearest light ({@link #isBlindLight}) a player placed
	 * ({@link PlayerWatch#wasPlacedByPlayer}) {@code blindTorchMinDistance} to {@code blindTorchMaxDistance} (6 to 20)
	 * blocks from the player that may go ({@link #torchMayGo}) and is out of everyone's view. {@code skip}, if given,
	 * says which to pass over (a torch the trace service just refused).
	 */
	static @Nullable BlockPos torch(ServerPlayer player, Env env, @Nullable Predicate<BlockPos> skip) {
		ServerLevel level = player.level();
		DigConfig config = env.config();
		int max = Math.max(0, config.blindTorchMaxDistance);
		BlockPos feet = player.blockPosition();
		if (!Tunnels.chunksLoaded(level, feet, max)) {
			return null;
		}
		Vec3 at = player.position();
		double minSqr = (double) config.blindTorchMinDistance * config.blindTorchMinDistance;
		double maxSqr = (double) max * max;
		List<BlockPos> lights = new ArrayList<>();
		for (BlockPos pos : env.watch().placedNear(level, feet, max, Blind::isBlindLight)) {
			double dist = pos.distToCenterSqr(at);
			if (dist >= minSqr && dist <= maxSqr) {
				lights.add(pos);
			}
		}
		if (lights.isEmpty()) {
			return null;
		}
		lights.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(at)));
		List<BlockPos> beds = env.bases().all(level.dimension());
		for (BlockPos light : lights) {
			if ((skip == null || !skip.test(light)) && torchMayGo(level, light, beds, config) && env.view().outOfView(level, List.of(light))) {
				return light;
			}
		}
		return null;
	}

	/**
	 * The light rules (D-077), the view aside: not in a protected area, another light source within
	 * {@code blindTorchOtherLightRadius} (8) of it (the cheap rule for "never the only light of a spawn-proof room"), and
	 * never the last light within {@code blindTorchBedRadius} (6) of a bed: any base dig knows in this level ({@link
	 * Bases#all}: each known player's respawn point, else the last base dig saw, else their first block). A light
	 * source is a block giving at least {@code blindTorchOtherLightMin} (7) light.
	 */
	static boolean torchMayGo(ServerLevel level, BlockPos torch, List<BlockPos> beds, DigConfig config) {
		if (Services.protectedAreas().isProtected(level.dimension(), torch)) {
			return false;
		}
		int min = config.blindTorchOtherLightMin;
		if (!lightNear(level, torch, config.blindTorchOtherLightRadius, torch, min)) {
			return false;
		}
		long bedRadius = config.blindTorchBedRadius;
		for (BlockPos bed : beds) {
			if (torch.distSqr(bed) <= bedRadius * bedRadius && !lightNear(level, bed, config.blindTorchBedRadius, torch, min)) {
				return false;
			}
		}
		return true;
	}

	/** True if a block within {@code radius} of {@code center}, other than {@code except}, gives at least {@code min} light (loaded chunks only). */
	static boolean lightNear(ServerLevel level, BlockPos center, int radius, BlockPos except, int min) {
		Predicate<BlockState> light = state -> state.getLightEmission() >= Math.max(1, min);
		return scan(level, center, radius, light, (pos, state) -> !pos.equals(except));
	}

	private static FireResult takeTorch(ServerPlayer player, Env env) {
		ServerLevel level = player.level();
		List<BlockPos> refused = new ArrayList<>();
		for (int tries = 0; tries < 8; tries++) {
			BlockPos torch = torch(player, env, refused::contains);
			if (torch == null) {
				return FireResult.NO_SPOT;
			}
			if (env.traces().remove(level, torch, CAUSE_TORCH)) {
				return FireResult.FIRED;
			}
			refused.add(torch.immutable());
		}
		return FireResult.NO_SPOT;
	}

	// --- blind_door ---

	/** A door, trapdoor or fence gate (the palette check of the door scan). */
	static boolean doorLike(BlockState state) {
		Block block = state.getBlock();
		return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
	}

	/**
	 * A door {@code blind_door} may sound: closed, opened by hand (not iron: someone came through), a door at its lower
	 * half; any trapdoor a hand opens; any fence gate.
	 */
	static boolean isBlindDoor(BlockState state) {
		Block block = state.getBlock();
		if (!doorLike(state) || state.getValue(BlockStateProperties.OPEN)) {
			return false;
		}
		if (block instanceof DoorBlock door) {
			return door.type().canOpenByHand() && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
		}
		if (block instanceof TrapDoorBlock trapdoor) {
			return ((TrapDoorBlockAccessor) trapdoor).a1016_02$type().canOpenByHand();
		}
		return true;
	}

	/** Its own open and close sounds: a door's and a trapdoor's from its {@link BlockSetType}, a gate's from its wood type. */
	record DoorSounds(SoundEvent open, SoundEvent close) {
		static @Nullable DoorSounds of(BlockState state) {
			Block block = state.getBlock();
			if (block instanceof DoorBlock door) {
				return new DoorSounds(door.type().doorOpen(), door.type().doorClose());
			}
			if (block instanceof TrapDoorBlock trapdoor) {
				BlockSetType type = ((TrapDoorBlockAccessor) trapdoor).a1016_02$type();
				return new DoorSounds(type.trapdoorOpen(), type.trapdoorClose());
			}
			if (block instanceof FenceGateBlock gate) {
				WoodType type = ((FenceGateBlockAccessor) gate).a1016_02$type();
				return new DoorSounds(type.fenceGateOpen(), type.fenceGateClose());
			}
			return null;
		}
	}

	/** The cells a player would see of a door at {@code pos} (both halves of a door). */
	static List<BlockPos> doorCells(BlockState state, BlockPos pos) {
		return state.getBlock() instanceof DoorBlock ? List.of(pos, pos.above()) : List.of(pos);
	}

	/**
	 * The door {@code blind_door} sounds now, or null: a {@link #isBlindDoor} block {@code blindDoorMinDistance} to
	 * {@code blindDoorMaxDistance} (4 to 12) blocks from the player and out of everyone's view. With {@code random}, one
	 * of those at random; without, the first found (the context check).
	 */
	static @Nullable BlockPos door(ServerPlayer player, Env env, @Nullable RandomSource random) {
		ServerLevel level = player.level();
		DigConfig config = env.config();
		int max = Math.max(0, config.blindDoorMaxDistance);
		Vec3 at = player.position();
		double minSqr = (double) config.blindDoorMinDistance * config.blindDoorMinDistance;
		double maxSqr = (double) max * max;
		List<BlockPos> doors = new ArrayList<>();
		BlockPos[] first = {null};
		scan(level, player.blockPosition(), max + 1, Blind::doorLike, (pos, state) -> {
			double dist = pos.distToCenterSqr(at);
			if (dist < minSqr || dist > maxSqr || !isBlindDoor(state)) {
				return false;
			}
			BlockPos door = pos.immutable();
			if (random != null) {
				doors.add(door);
				return false;
			}
			if (env.view().outOfView(level, doorCells(state, door))) {
				first[0] = door;
				return true;
			}
			return false;
		});
		if (random == null) {
			return first[0];
		}
		NetworkGrower.shuffle(doors, random);
		for (BlockPos door : doors) {
			if (env.view().outOfView(level, doorCells(level.getBlockState(door), door))) {
				return door;
			}
		}
		return null;
	}

	/**
	 * The door's open sound now and its close {@code blindDoorGapTicksMin} to {@code blindDoorGapTicksMax} later, at its
	 * centre, BLOCKS, at {@code blindDoorVolume} and vanilla's pitch (0.9 to 1). The close only while the player is still
	 * blind and alone ({@link BlindCues}). The block never changes.
	 */
	private static FireResult doorSounds(ServerPlayer player, Env env, RandomSource random) {
		BlockPos door = door(player, env, random);
		if (door == null) {
			return FireResult.NO_SPOT;
		}
		DoorSounds sounds = DoorSounds.of(player.level().getBlockState(door));
		if (sounds == null) {
			return FireResult.NO_SPOT;
		}
		DigConfig config = env.config();
		int gapMin = Math.max(1, config.blindDoorGapTicksMin);
		int gap = gapMin + random.nextInt(Math.max(1, config.blindDoorGapTicksMax - gapMin + 1));
		Vec3 pos = Vec3.atCenterOf(door);
		float volume = Math.max(0.0F, config.blindDoorVolume);
		List<BlindCues.Cue> cues = List.of(
				new BlindCues.Cue(0, holder(sounds.open()), SoundSource.BLOCKS, pos, volume, 0.9F + random.nextFloat() * 0.1F),
				new BlindCues.Cue(gap, holder(sounds.close()), SoundSource.BLOCKS, pos, volume, 0.9F + random.nextFloat() * 0.1F));
		DigTicker.INSTANCE.blind().start(player.level().getServer(), new BlindCues.Sequence(Kind.DOOR.id, player, env, cues));
		return FireResult.FIRED;
	}

	// --- scanning ---

	/**
	 * Visits every block within {@code radius} of {@code center} (a sphere of block positions) in loaded chunks, skipping
	 * whole chunk sections whose palette holds no state {@code palette} accepts (most hold no door and no light at all),
	 * and only the blocks {@code palette} accepts; stops at the first {@code visit} that answers true, and says whether
	 * one did. The position handed to {@code visit} is mutable: keep {@code pos.immutable()}. Never loads a chunk.
	 */
	static boolean scan(ServerLevel level, BlockPos center, int radius, Predicate<BlockState> palette, BiPredicate<BlockPos, BlockState> visit) {
		int minX = center.getX() - radius;
		int maxX = center.getX() + radius;
		int minY = Math.max(level.getMinY(), center.getY() - radius);
		int maxY = Math.min(level.getMaxY(), center.getY() + radius);
		int minZ = center.getZ() - radius;
		int maxZ = center.getZ() + radius;
		long radiusSqr = (long) radius * radius;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSectionsCount()) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(palette)) {
						continue;
					}
					for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++) {
						for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++) {
							for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
								long dx = x - center.getX();
								long dy = y - center.getY();
								long dz = z - center.getZ();
								if (dx * dx + dy * dy + dz * dz > radiusSqr) {
									continue;
								}
								BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
								if (palette.test(state) && visit.test(pos.set(x, y, z), state)) {
									return true;
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	static Holder<SoundEvent> holder(SoundEvent sound) {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
	}

	// --- debug ---

	/** {@code /a1016 dig blind}: how long the player has been blind, each card's answer now, the sounds still to come and an armed fire. */
	static List<String> debugLines(ServerPlayer player, Env env) {
		List<String> lines = new ArrayList<>();
		long blind = env.watch().blindTicks(player);
		lines.add(String.format(Locale.ROOT, "[a1016] dig: %s's blind moment: %s (the cards wait for %.1f s)", player.getName().getString(),
				blind > 0 ? String.format(Locale.ROOT, "blind for %.1f s", blind / 20.0) : "not blind", minBlindTicks(env.config()) / 20.0));
		for (Kind kind : Kind.values()) {
			Why why = check(kind, player, env);
			lines.add(kind.id + ": " + (why == null ? "fits now" : "no (" + why.text + ")"));
		}
		BlindCues cues = DigTicker.INSTANCE.blind();
		lines.add("sounds now: " + cues.sequence(player.getUUID()).map(BlindCues.Sequence::describe).orElse("none"));
		lines.add("armed: " + cues.armed(player.getUUID()).map(BlindCues.Armed::describe).orElse("nothing"));
		return lines;
	}
}
