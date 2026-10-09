package com.forzacode.a1016_02.atmosphere.card;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatSoundVariant;
import net.minecraft.world.phys.Vec3;

/**
 * Cats hissing at a corner: the player's cat stops, turns to an empty indoor corner and hisses at it. Vanilla cats
 * hiss at creepers and phantoms, so the player checks. Nothing is there.
 */
public final class CatHissesCornerCard extends AtmosphereCard {
	public static final String ID = "cat_hisses_corner";
	private static final int CORNER_RADIUS = 6;

	public CatHissesCornerCard() {
		super(ID, Tier.AMBIENT, Stage.PROXIMITY, Set.of(Habit.VISITOR), Set.of(CardTag.MOB, CardTag.SOUND), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return !cats(world, player).isEmpty();
	}

	private static List<Cat> cats(ServerLevel level, ServerPlayer player) {
		return untampered(level, Cat.class, player.position(), cfg().catRadius,
				cat -> cat.isTame() && cat.isOwnedBy(player) && !cat.isPassenger() && WorldScan.covered(level, cat.blockPosition(), 6));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		for (Cat cat : cats(level, player).stream().sorted(Comparator.comparingDouble(c -> c.distanceToSqr(player))).toList()) {
			BlockPos corner = WorldScan.corner(level, cat.blockPosition(), CORNER_RADIUS, player.position());
			if (corner == null) {
				continue;
			}
			int ticks = AtmosphereConfig.ticks(cfg().catHissSeconds);
			Vec3 target = Vec3.atCenterOf(corner);
			if (!mobs().freeze(cat, ticks) || !mobs().face(cat, target, ticks)) {
				mobs().release(cat);
				continue;
			}
			UUID uuid = player.getUUID();
			MinecraftServer server = level.getServer();
			for (int delay : new int[] {12, 12 + Math.max(20, ticks / 3)}) {
				if (delay < ticks) {
					Tasks.later(delay, () -> hiss(server, uuid, cat));
				}
			}
			return FireResult.FIRED;
		}
		return FireResult.NO_SPOT;
	}

	private static void hiss(MinecraftServer server, UUID player, Cat cat) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		Holder<CatSoundVariant> variant = cat.get(DataComponents.CAT_SOUND_VARIANT);
		if (online == null || variant == null || !cat.isAlive() || online.level() != cat.level()) {
			return;
		}
		Holder<SoundEvent> hiss = cat.isBaby() ? variant.value().babySounds().hissSound() : variant.value().adultSounds().hissSound();
		SoundCues.playTo(online, hiss, SoundSource.NEUTRAL, cat.position(), 1.0F, 0.9F + cat.getRandom().nextFloat() * 0.2F);
	}
}
