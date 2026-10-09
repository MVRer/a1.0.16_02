package com.forzacode.a1016_02.atmosphere.card;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Footstep that stops late: the next time the player walks and stops, their own step sound plays once more a moment
 * after. Firing arms a watcher (the director's cadence can't hit the moment itself); it expires unheard after
 * {@code footstepArmSeconds}. At most once per session.
 */
public final class FootstepLateCard extends AtmosphereCard {
	public static final String ID = "footstep_late";

	private static final class Watch {
		final long deadline;
		final int delay;
		Vec3 lastPos;
		int walkTicks;
		int stoppedTicks;

		Watch(long deadline, int delay, Vec3 pos) {
			this.deadline = deadline;
			this.delay = delay;
			this.lastPos = pos;
		}
	}

	private static final Set<UUID> USED_THIS_SESSION = new HashSet<>();
	private static final Map<UUID, Watch> ARMED = new HashMap<>();

	public FootstepLateCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.WATCHER), Set.of(CardTag.SOUND), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return Gates.footstep(usedThisSession(player), ARMED.containsKey(player.getUUID()), player.onGround(), player.isPassenger());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		if (!ctx.forced() && (usedThisSession(player) || ARMED.containsKey(player.getUUID()))) {
			return FireResult.SKIPPED;
		}
		AtmosphereConfig cfg = cfg();
		int delay = cfg.footstepDelayMinTicks + ctx.random().nextInt(Math.max(1, cfg.footstepDelayMaxTicks - cfg.footstepDelayMinTicks + 1));
		long deadline = ctx.level().getServer().getTickCount() + AtmosphereConfig.ticks(cfg.footstepArmSeconds);
		ARMED.put(player.getUUID(), new Watch(deadline, delay, player.position()));
		return FireResult.FIRED;
	}

	public static boolean usedThisSession(ServerPlayer player) {
		return USED_THIS_SESSION.contains(player.getUUID());
	}

	public static boolean armed(ServerPlayer player) {
		return ARMED.containsKey(player.getUUID());
	}

	/** Every server tick: watch armed players for a walk, then a stop. */
	public static void tick(MinecraftServer server) {
		if (ARMED.isEmpty()) {
			return;
		}
		int minWalk = AtmosphereConfig.get().footstepMinWalkTicks;
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Watch>> it = ARMED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Watch> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Watch watch = entry.getValue();
			if (player == null || now > watch.deadline) {
				it.remove();
				continue;
			}
			Vec3 pos = player.position();
			double dx = pos.x - watch.lastPos.x;
			double dz = pos.z - watch.lastPos.z;
			boolean moved = dx * dx + dz * dz > 1.0E-4;
			watch.lastPos = pos;
			boolean onFoot = player.onGround() && !player.isPassenger() && !player.isSwimming() && !player.isFallFlying() && !player.getAbilities().flying;
			if (moved) {
				watch.stoppedTicks = 0;
				if (onFoot) {
					watch.walkTicks++;
				}
				continue;
			}
			if (watch.walkTicks < minWalk || !onFoot) {
				watch.walkTicks = 0;
				continue;
			}
			if (++watch.stoppedTicks >= watch.delay) {
				it.remove();
				if (playStep(player)) {
					USED_THIS_SESSION.add(player.getUUID());
				}
			}
		}
	}

	private static boolean playStep(ServerPlayer player) {
		BlockState state = player.level().getBlockState(player.getOnPosLegacy());
		if (state.isAir()) {
			return false;
		}
		SoundType sound = state.getSoundType();
		SoundCues.playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.getStepSound()), SoundSource.PLAYERS, player.position(),
				sound.getVolume() * 0.15F, sound.getPitch());
		return true;
	}

	public static void onLeave(ServerPlayer player) {
		USED_THIS_SESSION.remove(player.getUUID());
		ARMED.remove(player.getUUID());
	}

	public static void clear() {
		USED_THIS_SESSION.clear();
		ARMED.clear();
	}
}
