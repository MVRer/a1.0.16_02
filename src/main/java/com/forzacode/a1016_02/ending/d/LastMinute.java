package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.Variant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The last minute, a scripted sequence once the last plank is on his cross (DESIGN.md "The last minute"):
 * <ol>
 * <li>every ambient sound stops (the torches keep burning);</li>
 * <li>the missing stair blocks come back one at a time, climbing like footsteps (the exact ledgered blocks, through
 * {@code restoreBlock}; each one's step sound for the player);</li>
 * <li>a torch they lost is back on the stairwell wall (a ledgered torch, through {@code restoreBlock});</li>
 * <li>they come out at dawn and the fog pulls back to full render distance (dusk fog 0, for good);</li>
 * <li>on the far shore, at the edge of render distance, he stands facing them, and once seen turns and walks into the
 * bare grove behind him ({@code FigureApi}, walking);</li>
 * <li>the leaves fill back in after him in one red and orange wave (his own taken leaves from the ledger, crowns on
 * the old bare trunks), each block out of view; where he stood, nothing;</li>
 * <li>music: back on, and one of the game's calm tracks starts.</li>
 * </ol>
 * Then the afterward. Server thread only; the phase is saved, the rest starts over after a restart.
 */
public final class LastMinute {
	public enum Phase { START, FOOTSTEPS, TORCH, CLIMB, FIGURE, LEAVES, MUSIC, DONE }

	/** One item of the leaf wave: a taken leaf, or a crown. */
	private record WaveItem(BlockPos pos, TraceLedger.@Nullable Entry entry, Regrow.@Nullable Crown crown) {
	}

	private static Phase phase = Phase.START;
	private static long phaseStart;
	private static long nextFootstep;
	private static int tries;
	private static List<TraceLedger.Entry> footsteps = new ArrayList<>();
	private static List<BlockPos> previewSteps = new ArrayList<>();
	private static @Nullable HimEntity figure;
	private static boolean walkedAway;
	private static @Nullable Vec3 lastSeenAt;
	private static SiteRegistry.@Nullable Site grove;
	private static List<WaveItem> wave = new ArrayList<>();
	private static boolean waveBuilt;
	private static @Nullable BlockPos origin;
	private static boolean running;

	private LastMinute() {
	}

	public static Phase phase() {
		return phase;
	}

	public static boolean running() {
		return running;
	}

	/**
	 * Starts the sequence. {@code preview} (debug) plays it at the player's spot without completing the ending: no
	 * flags, no clock change.
	 */
	public static void start(MinecraftServer server, EndingDState data, boolean preview, BlockPos at) {
		reset();
		running = true;
		origin = at.immutable();
		data.set(EndingDState.PREVIEW, preview);
		data.setLastMinutePhase(Phase.START.ordinal());
		if (!preview) {
			data.setStep(Step.LAST_MINUTE);
		}
		phase = Phase.START;
		phaseStart = server.getTickCount();
	}

	/** After a restart in the middle of it: carry on from the saved phase (the climb at the earliest). */
	static void resume(MinecraftServer server, EndingDState data) {
		if (running || data.step() != Step.LAST_MINUTE) {
			return;
		}
		reset();
		running = true;
		Phase saved = Phase.values()[Mth.clamp(data.lastMinutePhase(), 0, Phase.values().length - 1)];
		phase = saved.ordinal() <= Phase.CLIMB.ordinal() ? Phase.START : saved;
		origin = data.stair().map(StairPlan::twin).orElse(null);
		phaseStart = server.getTickCount();
	}

	static void reset() {
		running = false;
		phase = Phase.START;
		footsteps = new ArrayList<>();
		previewSteps = new ArrayList<>();
		figure = null;
		walkedAway = false;
		lastSeenAt = null;
		grove = null;
		wave = new ArrayList<>();
		waveBuilt = false;
		origin = null;
		tries = 0;
	}

	private static void enter(MinecraftServer server, EndingDState data, Phase next) {
		phase = next;
		phaseStart = server.getTickCount();
		tries = 0;
		data.setLastMinutePhase(next.ordinal());
		A1016_02.LOGGER.info("[a1016] ending d: last minute, {}", next.name().toLowerCase(java.util.Locale.ROOT));
	}

	static void tick(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		if (!running) {
			return;
		}
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return;
		}
		ServerPlayer player = subject.get();
		boolean preview = data.has(EndingDState.PREVIEW);
		long now = server.getTickCount();
		long inPhase = now - phaseStart;
		switch (phase) {
			case START -> {
				ClientEffects.silence(player, (int) EndingDConfig.ticks(cfg.silenceSeconds), cfg.silenceFadeTicks);
				if (!preview) {
					complete(server, data, cfg);
				}
				footsteps = footstepEntries(server, data);
				if (footsteps.isEmpty() && preview) {
					for (int i = 1; i <= 10; i++) {
						previewSteps.add(player.blockPosition().above(i * 2).relative(player.getDirection(), i));
					}
				}
				nextFootstep = now + EndingDConfig.ticks(1.5);
				enter(server, data, Phase.FOOTSTEPS);
			}
			case FOOTSTEPS -> {
				if (now < nextFootstep) {
					return;
				}
				nextFootstep = now + Math.max(1, cfg.footstepTicks);
				if (!footsteps.isEmpty()) {
					TraceLedger.Entry entry = footsteps.getFirst();
					ServerLevel level = server.getLevel(entry.pos().dimension());
					BlockState state = entry.state().orElseThrow();
					boolean back = level != null && TraceLedger.get(server).entries().contains(entry) && Services.traces().restoreBlock(level, entry, entry.pos().pos());
					if (back) {
						footstep(player, entry.pos().pos(), state);
					}
					if (back || ++tries * cfg.footstepTicks > EndingDConfig.ticks(cfg.footstepGiveUpSeconds)
							|| !TraceLedger.get(server).entries().contains(entry)) {
						footsteps.removeFirst();
						tries = 0;
					}
				} else if (!previewSteps.isEmpty()) {
					BlockPos pos = previewSteps.removeFirst();
					footstep(player, pos, player.level().getBlockState(player.blockPosition().below()));
				} else {
					enter(server, data, Phase.TORCH);
				}
			}
			case TORCH -> {
				if (inPhase < EndingDConfig.ticks(cfg.torchDelaySeconds)) {
					return;
				}
				Optional<TraceLedger.Entry> torch = lostTorch(server, data);
				if (torch.isEmpty()) {
					enter(server, data, Phase.CLIMB);
					return;
				}
				ServerLevel level = server.getLevel(torch.get().pos().dimension());
				if (level != null && Services.traces().restoreBlock(level, torch.get(), torch.get().pos().pos())
						|| inPhase > EndingDConfig.ticks(cfg.torchDelaySeconds + cfg.footstepGiveUpSeconds)) {
					enter(server, data, Phase.CLIMB);
				}
			}
			case CLIMB -> {
				boolean out = cameOut(player, data, preview);
				if (figure == null && (out || nearTop(player, data))) {
					trySpawn(player, data, cfg);
				}
				if (out || inPhase > EndingDConfig.ticks(cfg.climbTimeoutSeconds)) {
					ClientEffects.setDuskFog(server, 0.0F);
					enter(server, data, Phase.FIGURE);
				}
			}
			case FIGURE -> {
				if (figure == null) {
					if (now % 20 == 0) {
						trySpawn(player, data, cfg);
					}
				} else if (figure.isRemoved()) {
					enter(server, data, Phase.LEAVES);
					return;
				} else {
					lastSeenAt = figure.position();
					if (!walkedAway && figure.everSeen() && figure.seenFor() >= EndingDConfig.ticks(cfg.figureStareSeconds)) {
						FigureApi.walkAway(figure);
						walkedAway = true;
					}
				}
				if (inPhase > EndingDConfig.ticks(cfg.figureTimeoutSeconds)) {
					if (figure != null && !figure.isRemoved()) {
						FigureApi.walkAway(figure);
					}
					enter(server, data, Phase.LEAVES);
				}
			}
			case LEAVES -> {
				ServerLevel level = player.level();
				if (!waveBuilt) {
					buildWave(level, player);
				}
				int budget = Math.max(1, cfg.leafWavePerTick);
				for (int i = 0; i < wave.size() && budget > 0; ) {
					WaveItem item = wave.get(i);
					budget--;
					boolean done;
					if (item.entry() != null) {
						done = !TraceLedger.get(server).entries().contains(item.entry())
								|| Services.traces().restoreBlock(level, item.entry(), item.entry().pos().pos());
					} else {
						done = Regrow.grow(level, item.crown(), Services.traces());
					}
					if (done) {
						wave.remove(i);
					} else {
						i++;
					}
				}
				if (wave.isEmpty() || inPhase > EndingDConfig.ticks(cfg.leafWaveTimeoutSeconds)) {
					enter(server, data, Phase.MUSIC);
				}
			}
			case MUSIC -> {
				if (inPhase < EndingDConfig.ticks(cfg.musicDelaySeconds)) {
					return;
				}
				ClientEffects.setMusicOff(server, false);
				ClientEffects.silence(player, 1, cfg.silenceFadeTicks);
				CalmMusicPayload.send(player);
				enter(server, data, Phase.DONE);
				running = false;
				if (!preview) {
					data.setStep(Step.AFTERWARD);
				}
				data.set(EndingDState.PREVIEW, false);
			}
			case DONE -> running = false;
		}
	}

	/**
	 * D is complete: the flags (the director's silence for good, the sting), the day, the dawn. Also the path to D
	 * in the ending workstream's API once it exists.
	 */
	static void complete(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		HerobrineState state = HerobrineState.get(server);
		state.setFlag(EndingDInit.COMPLETE_FLAG, true);
		state.setFlag(EndingDInit.SILENCE_FOREVER_FLAG, true);
		// TODO(ending): set the path to D through ending.EndingApi once it is on main.
		data.setCompleteDay(GameClock.day(server));
		Sting.refresh(server);
		if (cfg.forceDawn) {
			toDawn(server, cfg);
		}
		A1016_02.LOGGER.info("[a1016] ending d: complete (no longer with us)");
	}

	/** Moves the clock forward (never back) so dawn breaks about {@code dawnLeadSeconds} from now. */
	static void toDawn(MinecraftServer server, EndingDConfig cfg) {
		long lead = Math.min(6000, Math.max(0, Math.round(cfg.dawnLeadSeconds * 20.0)));
		long target = 24000 - lead;
		long time = Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
		if (time >= target || time < 1000) {
			return;
		}
		Optional<Holder.Reference<WorldClock>> clock = server.registryAccess().get(WorldClocks.OVERWORLD);
		clock.ifPresent(holder -> server.clockManager().addTicks(holder, (int) (target - time)));
	}

	/** The step sound of a block coming back, for the player only (blocks, not ambient: the silence does not mute it). */
	private static void footstep(ServerPlayer player, BlockPos pos, BlockState state) {
		SoundCues.playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(state.getSoundType().getStepSound()), SoundSource.BLOCKS,
				Vec3.atCenterOf(pos), 0.9F, 0.9F);
	}

	/** The stair blocks he took, lowest first: they come back climbing upward. */
	static List<TraceLedger.Entry> footstepEntries(MinecraftServer server, EndingDState data) {
		List<TraceLedger.Entry> steps = new ArrayList<>();
		for (TraceLedger.Entry entry : TraceLedger.get(server).entries()) {
			if (entry.kind() == TraceLedger.Kind.REMOVE && entry.cause().equals(Stair.LOSS_CAUSE)
					&& entry.state().map(s -> s.getBlock() instanceof StairBlock).orElse(false)) {
				steps.add(entry);
			}
		}
		steps.sort(Comparator.comparingInt(e -> e.pos().pos().getY()));
		return steps;
	}

	/**
	 * A torch they lost, back on the stairwell wall: the one the stair took, else the oldest torch he took anywhere in
	 * the stairwell. Only ledgered torches, put back exactly where they were.
	 */
	static Optional<TraceLedger.Entry> lostTorch(MinecraftServer server, EndingDState data) {
		Optional<StairPlan> plan = data.stair();
		TraceLedger.Entry fallback = null;
		for (TraceLedger.Entry entry : TraceLedger.get(server).entries()) {
			if (entry.kind() != TraceLedger.Kind.REMOVE || !entry.state().map(s -> s.getBlock() instanceof BaseTorchBlock).orElse(false)
					|| Undo.skipReason(server, entry).isPresent()) {
				continue;
			}
			if (entry.cause().equals(Stair.LOSS_CAUSE)) {
				return Optional.of(entry);
			}
			if (fallback == null && plan.isPresent() && entry.pos().dimension().equals(plan.get().dimension())) {
				BlockPos p = entry.pos().pos();
				StairPlan s = plan.get();
				if (Math.abs(p.getX() - s.axisX()) <= 2 && Math.abs(p.getZ() - s.axisZ()) <= 2 && p.getY() >= s.chamberCeil() && p.getY() <= s.yTop()) {
					fallback = entry;
				}
			}
		}
		return Optional.ofNullable(fallback);
	}

	/** Out of the pyramid: above the stair's top with the sky over their head (out of the water). */
	static boolean cameOut(ServerPlayer player, EndingDState data, boolean preview) {
		ServerLevel level = player.level();
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		boolean sky = level.canSeeSky(eye) && level.getFluidState(eye).isEmpty();
		if (preview) {
			return sky;
		}
		return sky && data.stair().map(s -> player.getY() > s.yTop() + 1).orElse(true);
	}

	private static boolean nearTop(ServerPlayer player, EndingDState data) {
		return data.stair().map(s -> player.getY() >= s.yTop() - 24).orElse(false);
	}

	// --- him ---

	/** Where he stands: on the far shore at the edge of render distance, facing the pyramid, a bare grove behind him. */
	public record Spot(Vec3 feet, float yaw, SiteRegistry.@Nullable Site grove) {
	}

	private static void trySpawn(ServerPlayer player, EndingDState data, EndingDConfig cfg) {
		ServerLevel level = player.level();
		BlockPos from = data.has(EndingDState.PREVIEW) || data.stair().isEmpty() ? player.blockPosition()
				: new BlockPos(data.stair().get().axisX(), level.getSeaLevel(), data.stair().get().axisZ());
		Optional<Spot> spot = farShore(level, player, from, cfg);
		if (spot.isEmpty()) {
			return;
		}
		Optional<HimEntity> him = FigureApi.spawnAt(level, Variant.ACROSS_WATER, spot.get().feet(), spot.get().yaw());
		if (him.isPresent()) {
			figure = him.get();
			grove = spot.get().grove();
			lastSeenAt = figure.position();
		}
	}

	/**
	 * The far shore: along the line from {@code from} toward the nearest bare grove (else ahead of the player), the
	 * farthest dry ground with water before it, at most {@code figureDistanceFraction} of the full render distance and
	 * inside the ticking range. Turned a little left and right if that line finds nothing.
	 */
	public static Optional<Spot> farShore(ServerLevel level, ServerPlayer player, BlockPos from, EndingDConfig cfg) {
		MinecraftServer server = level.getServer();
		double render = FogLimits.of(player).renderLimit();
		double ticking = Math.max(32, server.getPlayerList().getSimulationDistance() * 16 - 24);
		double far = Math.min(render * cfg.figureDistanceFraction, ticking);
		List<SiteRegistry.Site> groves = Regrow.groves(level, from);
		double base = groves.isEmpty() ? Math.toRadians(player.getYRot() + 90.0F)
				: Math.atan2(groves.getFirst().pos().getZ() - from.getZ(), groves.getFirst().pos().getX() - from.getX());
		for (int turn = 0; turn <= 18; turn++) {
			double angle = base + Math.toRadians((turn % 2 == 0 ? 1 : -1) * ((turn + 1) / 2) * 10.0);
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			for (double d = far; d >= far * 0.5; d -= 2.0) {
				int x = Mth.floor(from.getX() + dx * d);
				int z = Mth.floor(from.getZ() + dz * d);
				if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos ground = new BlockPos(x, y - 1, z);
				if (!level.getFluidState(ground).isEmpty() || !level.getBlockState(ground).isSolidRender() || !waterBefore(level, x, z, -dx, -dz)) {
					continue;
				}
				Vec3 feet = new Vec3(x + 0.5, y, z + 0.5);
				if (!HimEntity.tickingAround(level, feet, HimEntity.SPAWN_TICK_MARGIN)) {
					continue;
				}
				float yaw = HimEntity.yawToward(feet, Vec3.atCenterOf(from));
				return Optional.of(new Spot(feet, yaw, groveBehind(groves, from, feet)));
			}
		}
		return Optional.empty();
	}

	/** True if there is open water within a few blocks of this column, toward the pyramid. */
	private static boolean waterBefore(ServerLevel level, int x, int z, double dx, double dz) {
		for (int k = 1; k <= 8; k++) {
			int wx = Mth.floor(x + 0.5 + dx * k);
			int wz = Mth.floor(z + 0.5 + dz * k);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
			if (level.getFluidState(new BlockPos(wx, y, wz)).is(Fluids.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** The bare grove behind him (farther from the pyramid than he is, close by), else the nearest one to him. */
	private static SiteRegistry.@Nullable Site groveBehind(List<SiteRegistry.Site> groves, BlockPos from, Vec3 feet) {
		SiteRegistry.Site best = null;
		double bestD = Double.MAX_VALUE;
		Vec3 out = feet.subtract(Vec3.atCenterOf(from)).horizontal();
		for (SiteRegistry.Site site : groves) {
			Vec3 to = Vec3.atCenterOf(site.pos()).subtract(feet).horizontal();
			double d = to.lengthSqr();
			boolean behind = to.dot(out) > 0;
			double score = behind ? d : d * 4;
			if (d <= 128 * 128 && score < bestD) {
				best = site;
				bestD = score;
			}
		}
		return best;
	}

	// --- the leaves ---

	private static void buildWave(ServerLevel level, ServerPlayer player) {
		waveBuilt = true;
		wave = new ArrayList<>();
		Vec3 from = lastSeenAt != null ? lastSeenAt : player.position();
		SiteRegistry.Site target = grove;
		if (target == null) {
			List<SiteRegistry.Site> near = Regrow.groves(level, BlockPos.containing(from));
			target = near.isEmpty() ? null : near.getFirst();
		}
		if (target == null || !target.dimension().equals(level.dimension())) {
			return;
		}
		int radius = Math.max(8, target.size() + 4);
		for (TraceLedger.Entry entry : Regrow.ledgerLeaves(level, target.pos(), radius)) {
			wave.add(new WaveItem(entry.pos().pos(), entry, null));
		}
		for (Regrow.Crown crown : Regrow.crowns(level, target.pos(), radius)) {
			wave.add(new WaveItem(crown.top(), null, crown));
		}
		wave.sort(Comparator.comparingDouble(item -> Vec3.atCenterOf(item.pos()).distanceToSqr(from)));
	}

	/** Status line. */
	static String describe() {
		if (!running) {
			return "not running";
		}
		return phase.name().toLowerCase(java.util.Locale.ROOT) + (phase == Phase.FOOTSTEPS ? " (" + footsteps.size() + " to come back)" : "")
				+ (phase == Phase.LEAVES ? " (" + wave.size() + " left)" : "") + (figure != null ? ", he is out" : "");
	}
}
