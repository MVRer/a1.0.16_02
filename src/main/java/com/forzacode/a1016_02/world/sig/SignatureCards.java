package com.forzacode.a1016_02.world.sig;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.world.SignatureData;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The world's signature cards (the director maps the profile's signature to these ids, and fires each at most
 * once) and the lone redstone torch. Each card only starts its moment; the world side keeps the once-per-world
 * rule whatever fires it (the debug command included).
 */
public final class SignatureCards {
	private SignatureCards() {
	}

	/** "Still burning" (SIGNATURE, Proximity), D-004. */
	public static final class StillBurningCard implements EventCard {
		@Override
		public String id() {
			return StillBurning.ID;
		}

		@Override
		public Tier tier() {
			return Tier.SIGNATURE;
		}

		@Override
		public Stage earliestStage() {
			return Stage.PROXIMITY;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of();
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.LIGHT);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			SignatureData data = WorldData.get(server).signatures();
			HerobrineState state = HerobrineState.get(server);
			return StillBurning.wanted(state.profile()) && !data.stillBurningPending()
					&& StillBurning.refusal(data, state.hasFlag(StillBurning.LORE_FLAG)) == null;
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return StillBurning.start(ctx.level().getServer(), ctx.forced(), null).result();
		}
	}

	/** "Your house, elsewhere" (SIGNATURE, Proximity, from day {@code houseCopyMinDay}), D-005. */
	public static final class HouseElsewhereCard implements EventCard {
		@Override
		public String id() {
			return HouseCopier.ID;
		}

		@Override
		public Tier tier() {
			return Tier.SIGNATURE;
		}

		@Override
		public Stage earliestStage() {
			return Stage.PROXIMITY;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of();
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.SCAR);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			return HerobrineState.get(server).profile().hasHouseCopy() && GameClock.day(server) >= ModConfig.pacing().houseCopyMinDay
					&& WorldData.get(server).signatures().houseCopy().isEmpty() && !HouseCopier.anchors(server, server.overworld()).isEmpty();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return HouseCopier.start(ctx.level().getServer(), ctx.forced()).result();
		}
	}

	/** "A row of crosses with a fresh one" (SIGNATURE, Proximity). */
	public static final class CrossRowCard implements EventCard {
		@Override
		public String id() {
			return CrossRow.ID;
		}

		@Override
		public Tier tier() {
			return Tier.SIGNATURE;
		}

		@Override
		public Stage earliestStage() {
			return Stage.PROXIMITY;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of(Habit.MOURNER);
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.SCAR);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			return world.dimension() == Level.OVERWORLD && HerobrineState.get(server).profile().signature() == Signature.CROSS_ROW
					&& CrossRow.refusal(WorldData.get(server).signatures()) == null;
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return CrossRow.start(ctx.level().getServer(), ctx.forced()).result();
		}
	}

	/** The lone redstone torch (MINOR, Traces), D-033. */
	public static final class LoneRedstoneTorchCard implements EventCard {
		@Override
		public String id() {
			return LoneTorch.ID;
		}

		@Override
		public Tier tier() {
			return Tier.MINOR;
		}

		@Override
		public Stage earliestStage() {
			return Stage.TRACES;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of(Habit.MOURNER, Habit.WATCHER);
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.LIGHT);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			MinecraftServer server = world.getServer();
			SignatureData data = WorldData.get(server).signatures();
			return world.dimension() == Level.OVERWORLD && LoneTorch.capRefusal(data.torches(), GameClock.day(server), WorldConfig.get()) == null
					&& (!data.caveSpots().isEmpty() || !Services.sites().find(SiteType.TUNNEL_END, GlobalPos.of(world.dimension(), player.blockPosition()),
							512).isEmpty());
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return LoneTorch.place(ctx.player(), ctx.random()).isPresent() ? FireResult.FIRED : FireResult.NO_SPOT;
		}
	}
}
