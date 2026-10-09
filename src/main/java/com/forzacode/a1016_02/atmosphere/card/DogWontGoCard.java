package com.forzacode.a1016_02.atmosphere.card;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;

/**
 * The dog won't go: the player's tamed wolf stops at one spot, whimpers and refuses to follow, watching them leave.
 * After a while it comes as usual (vanilla follow and teleport take over again).
 */
public final class DogWontGoCard extends AtmosphereCard {
	public static final String ID = "dog_wont_go";
	private static final int[] WHINES = {4, 50, 115, 190};

	public DogWontGoCard() {
		super(ID, Tier.AMBIENT, Stage.TRACES, Set.of(Habit.WATCHER), Set.of(CardTag.MOB, CardTag.SOUND), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return Services.watch().stillTicks(player) == 0 && !wolves(world, player).isEmpty();
	}

	private static List<Wolf> wolves(ServerLevel level, ServerPlayer player) {
		return untampered(level, Wolf.class, player.position(), cfg().dogRadius,
				wolf -> wolf.isTame() && wolf.isOwnedBy(player) && !wolf.isOrderedToSit() && !wolf.isPassenger() && !wolf.isLeashed());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		Wolf wolf = wolves(ctx.level(), player).stream().min(Comparator.comparingDouble(w -> w.distanceToSqr(player))).orElse(null);
		if (wolf == null) {
			return FireResult.SKIPPED;
		}
		int ticks = AtmosphereConfig.ticks(cfg().dogStaySeconds);
		if (!mobs().freeze(wolf, ticks)) {
			return FireResult.SKIPPED;
		}
		mobs().face(wolf, player.getEyePosition(), ticks);
		Tasks.start(new Stay(player.getUUID(), wolf, ticks));
		return FireResult.FIRED;
	}

	static Holder<SoundEvent> whine(Wolf wolf) {
		Holder<WolfSoundVariant> variant = wolf.get(DataComponents.WOLF_SOUND_VARIANT);
		if (variant == null) {
			return null;
		}
		return wolf.isBaby() ? variant.value().babySounds().whineSound() : variant.value().adultSounds().whineSound();
	}

	/** Keeps the wolf watching the player and whimpering now and then. */
	private static final class Stay implements Tasks.Episode {
		private final UUID player;
		private final Wolf wolf;
		private final int length;
		private int age;

		Stay(UUID player, Wolf wolf, int length) {
			this.player = player;
			this.wolf = wolf;
			this.length = length;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			age++;
			ServerPlayer subject = server.getPlayerList().getPlayer(player);
			if (age > length || !wolf.isAlive() || subject == null || subject.level() != wolf.level() || !MobTamperImpl.INSTANCE.isFrozen(wolf)) {
				mobs().release(wolf);
				return false;
			}
			if (age % 20 == 0) {
				mobs().face(wolf, subject.getEyePosition(), length - age + 1);
			}
			for (int at : WHINES) {
				if (age == at) {
					Holder<SoundEvent> sound = whine(wolf);
					if (sound != null) {
						SoundCues.playTo(subject, sound, SoundSource.NEUTRAL, wolf.position(), 1.0F, 0.9F + wolf.getRandom().nextFloat() * 0.2F);
					}
				}
			}
			return true;
		}

		@Override
		public void stop(MinecraftServer server) {
			mobs().release(wolf);
		}
	}
}
