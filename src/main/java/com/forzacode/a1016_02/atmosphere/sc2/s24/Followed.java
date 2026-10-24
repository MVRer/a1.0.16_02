package com.forzacode.a1016_02.atmosphere.sc2.s24;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Who 24 follows (docs/SELF_CREATED_2.md): a player looked up again by UUID every step ({@link #player}), or the
 * gallery's staged walker. Server thread.
 */
public interface Followed {
	UUID id();

	/** Still here: a living participant in {@link #level()}. */
	boolean present();

	ServerLevel level();

	/** Where their feet are. */
	Vec3 position();

	Vec3 eye();

	/** Where they look (a unit vector). */
	Vec3 look();

	/** Sky light at their eyes {@link Herds#OUTDOORS_SKY} or more. */
	boolean outdoors();

	/** They see this box now (in their view cone, in sight). */
	boolean sees(AABB box);

	/** Their base in this level, if any. */
	Optional<BlockPos> base();

	/** Riding something (a horse, a boat, a minecart): no animal on foot keeps up (R10-10). */
	default boolean riding() {
		return false;
	}

	/** A player, looked up again by UUID each time through {@code watch} (D-062.1). */
	static Followed player(MinecraftServer server, UUID uuid, ServerLevel level, Supplier<PlayerWatch> watch) {
		return new Followed() {
			private ServerPlayer now() {
				PlayerWatch w = watch.get();
				return w.participant(server, uuid).filter(p -> w.isTargetable(p) && p.level() == level).orElse(null);
			}

			@Override
			public UUID id() {
				return uuid;
			}

			@Override
			public boolean present() {
				return now() != null;
			}

			@Override
			public ServerLevel level() {
				return level;
			}

			@Override
			public Vec3 position() {
				ServerPlayer p = now();
				return p == null ? Vec3.ZERO : p.position();
			}

			@Override
			public Vec3 eye() {
				ServerPlayer p = now();
				return p == null ? Vec3.ZERO : p.getEyePosition();
			}

			@Override
			public Vec3 look() {
				ServerPlayer p = now();
				return p == null ? new Vec3(0, 0, 1) : p.getViewVector(1.0F);
			}

			@Override
			public boolean outdoors() {
				ServerPlayer p = now();
				return p != null && Herds.outdoors(p);
			}

			@Override
			public boolean sees(AABB box) {
				ServerPlayer p = now();
				if (p == null) {
					return false;
				}
				TraceService.Viewer viewer = TraceService.Viewer.of(p, server.getPlayerList().getViewDistance());
				return TraceService.sees(level, viewer, box, ModConfig.pacing().viewNearBlocks, ModConfig.pacing().viewConeDegrees);
			}

			@Override
			public Optional<BlockPos> base() {
				ServerPlayer p = now();
				if (p == null) {
					return Optional.empty();
				}
				Optional<GlobalPos> base = watch.get().base(p);
				return base.filter(b -> b.dimension() == level.dimension()).map(GlobalPos::pos);
			}

			@Override
			public boolean riding() {
				ServerPlayer p = now();
				return p != null && (p.isPassenger() || p.isFallFlying());
			}
		};
	}
}
