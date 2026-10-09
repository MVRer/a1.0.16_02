package com.forzacode.a1016_02.atmosphere.client;

import java.util.List;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.CompassDriftPayload;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.atmosphere.DeadMountains;
import com.forzacode.a1016_02.atmosphere.DeadMountainsPayload;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.client.ClientEffectsClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;

import org.jspecify.annotations.Nullable;

/**
 * Client state of the atmosphere layer and the {@link ClientEffectsClient.Handler}. Everything runs on the client
 * thread. Time is counted in client ticks that only advance while the game is not paused. {@link #reset()} on
 * disconnect; core's {@code Sync} on join restores the persistent part (music off, dusk fog).
 *
 * <p>Dead mountains: while the player stands inside one of the areas the server sent, ambience (birds, wind loops,
 * cave mood, ambient additions, every {@code AMBIENT} sound) and music fade out, and no new music track starts; they
 * come back gradually after leaving. Footsteps, blocks, mobs and weather are untouched.
 */
public final class ClientAtmosphere implements ClientEffectsClient.Handler {
	static final ClientAtmosphere INSTANCE = new ClientAtmosphere();

	/** Ticks over which a silence cuts the sound out. */
	private static final int SILENCE_CUT_TICKS = 12;
	private static final SoundSource[] SILENCED = {SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.MUSIC};
	/** How far into the fade each silenced category starts coming back: ambience first, then weather, then music. */
	private static final double[] SILENCE_LAG = {0.0, 0.2, 0.45};
	/**
	 * The quietest a category gets. Not zero: the sound engine skips starting a sound at volume 0, and a biome loop
	 * skipped that way would not come back until the biome changes. At -80 dB nothing is heard.
	 */
	private static final float MIN_VOLUME = 1.0E-4F;

	private long ticks;

	private boolean musicOff;
	private float duskLevel;

	private ClientEffects.@Nullable FogSurge surge;
	private long surgeStart;

	private ClientEffects.@Nullable Silence silence;
	private long silenceStart;
	private final float[] volume = {1.0F, 1.0F, 1.0F};

	private @Nullable CompassDriftPayload compass;
	private long compassStart;

	private @Nullable ResourceKey<Level> deadDimension;
	private List<DeadMountains.Area> deadAreas = List.of();
	/** 0 = normal, 1 = as quiet as a dead mountain gets. */
	private double deadQuiet;
	private float deadVolume = 1.0F;

	private ClientAtmosphere() {
	}

	// --- handler (client thread) ---

	@Override
	public void fogSurge(ClientEffects.FogSurge effect) {
		surge = effect;
		surgeStart = ticks;
	}

	@Override
	public void silence(ClientEffects.Silence effect) {
		silence = effect;
		silenceStart = ticks;
	}

	@Override
	public void musicOff(ClientEffects.MusicOff effect) {
		setMusicOff(effect.off());
	}

	@Override
	public void duskFog(ClientEffects.DuskFog effect) {
		duskLevel = effect.level();
	}

	@Override
	public void sync(ClientEffects.Sync effect) {
		setMusicOff(effect.musicOff());
		duskLevel = effect.duskFogLevel();
	}

	void deadMountains(DeadMountainsPayload payload) {
		deadDimension = payload.dimension();
		deadAreas = List.copyOf(payload.areas());
	}

	void compassDrift(CompassDriftPayload payload) {
		compass = payload.ticks() > 0 ? payload : null;
		compassStart = ticks;
	}

	private void setMusicOff(boolean off) {
		musicOff = off;
		if (off) {
			Minecraft.getInstance().getMusicManager().stopPlaying();
		}
	}

	/** Back to vanilla: on disconnect. */
	void reset() {
		boolean hadSilence = silence != null || deadVolume != 1.0F;
		musicOff = false;
		duskLevel = 0.0F;
		surge = null;
		silence = null;
		compass = null;
		deadDimension = null;
		deadAreas = List.of();
		deadQuiet = 0.0;
		deadVolume = 1.0F;
		for (int i = 0; i < volume.length; i++) {
			volume[i] = 1.0F;
		}
		if (hadSilence) {
			refreshVolumes();
		}
	}

	// --- tick ---

	void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		ticks++;
		if (surge != null && ticks - surgeStart > Curves.surgeLength(surge.rampTicks(), surge.holdTicks(), surge.fadeTicks())) {
			surge = null;
		}
		boolean changed = tickSilence();
		changed |= tickDeadMountain(minecraft);
		if (changed) {
			refreshVolumes();
		}
		tickCompass(minecraft);
	}

	/** @return whether a volume changed */
	private boolean tickDeadMountain(Minecraft minecraft) {
		boolean inside = insideDeadMountain(minecraft);
		if (!inside && deadQuiet == 0.0) {
			return false;
		}
		AtmosphereConfig cfg = AtmosphereConfig.get();
		deadQuiet = Curves.quietStep(deadQuiet, inside, cfg.deadMountainFadeOutTicks, cfg.deadMountainRestoreTicks);
		float v = (float) Curves.quietVolume(deadQuiet);
		if (v != deadVolume) {
			deadVolume = v;
			return true;
		}
		return false;
	}

	private boolean insideDeadMountain(Minecraft minecraft) {
		if (deadAreas.isEmpty() || minecraft.player == null || minecraft.level == null || !minecraft.level.dimension().equals(deadDimension)) {
			return false;
		}
		double x = minecraft.player.getX();
		double y = minecraft.player.getY();
		double z = minecraft.player.getZ();
		for (DeadMountains.Area area : deadAreas) {
			if (area.contains(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a volume changed */
	private boolean tickSilence() {
		if (silence == null) {
			return false;
		}
		long t = ticks - silenceStart;
		boolean over = t > Curves.silenceLength(silence.ticks(), silence.fadeTicks());
		boolean changed = false;
		for (int i = 0; i < SILENCED.length; i++) {
			float v = over ? 1.0F : (float) Curves.silenceVolume(t, Math.min(SILENCE_CUT_TICKS, Math.max(1, silence.ticks())), silence.ticks(),
					silence.fadeTicks(), SILENCE_LAG[i]);
			if (Math.abs(v - volume[i]) > 1.0E-3F || over && volume[i] != 1.0F) {
				volume[i] = v;
				changed = true;
			}
		}
		if (over) {
			silence = null;
		}
		return changed;
	}

	private static void refreshVolumes() {
		for (SoundSource source : SILENCED) {
			Minecraft.getInstance().getSoundManager().refreshCategoryVolume(source);
		}
	}

	private void tickCompass(Minecraft minecraft) {
		if (compass == null) {
			return;
		}
		boolean expired = ticks - compassStart >= compass.ticks();
		boolean arrived = false;
		if (minecraft.player != null) {
			double dx = minecraft.player.getX() - (compass.x() + 0.5);
			double dz = minecraft.player.getZ() - (compass.z() + 0.5);
			arrived = dx * dx + dz * dz <= (double) compass.settleBlocks() * compass.settleBlocks();
		}
		if (expired || arrived) {
			compass = null;
		}
	}

	// --- hooks for the mixins ---

	public static boolean musicOff() {
		return INSTANCE.musicOff;
	}

	/** While on (or just leaving) a dead mountain no new music track starts; the schedule picks up again after. */
	public static boolean holdMusic() {
		return INSTANCE.deadQuiet > 0.0;
	}

	/** Multiplier for a sound category's volume (silence, dead mountain quiet). Never below {@link #MIN_VOLUME}. */
	public static float volumeFactor(SoundSource source) {
		ClientAtmosphere self = INSTANCE;
		if (self.silence == null && self.deadVolume == 1.0F && self.volume[0] == 1.0F && self.volume[1] == 1.0F && self.volume[2] == 1.0F) {
			return 1.0F;
		}
		float factor = 1.0F;
		for (int i = 0; i < SILENCED.length; i++) {
			if (SILENCED[i] == source) {
				factor = self.volume[i];
				break;
			}
		}
		if (source == SoundSource.AMBIENT || source == SoundSource.MUSIC) {
			factor *= self.deadVolume;
		}
		return factor >= 1.0F ? 1.0F : Math.max(MIN_VOLUME, factor);
	}

	/** Pulls the world fog in for the dusk level and any fog surge. Atmospheric fog only (not water, lava, snow). */
	public static void applyFog(FogData fog, Camera camera, @Nullable ClientLevel level, float partialTick) {
		if (level == null || camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		ClientAtmosphere self = INSTANCE;
		AtmosphereConfig cfg = AtmosphereConfig.get();
		double dusk = 0.0;
		if (self.duskLevel > 0.0F && !level.dimensionType().hasFixedTime()) {
			// The same amount and curve core's FogLimits uses on the server (game test: serverFogMatchesClientFog).
			dusk = Curves.duskAmount(self.duskLevel, level.getDefaultClockTime(), cfg.duskNightWeight);
		}
		double surgeAmount = 0.0;
		ClientEffects.FogSurge s = self.surge;
		if (s != null) {
			double t = self.ticks - self.surgeStart + partialTick;
			surgeAmount = s.strength() * Curves.surgeEnvelope(t, s.rampTicks(), s.holdTicks(), s.fadeTicks());
		}
		if (dusk < 1.0E-3 && surgeAmount < 1.0E-3) {
			return;
		}
		boolean renderLimited = fog.renderDistanceEnd <= fog.environmentalEnd;
		double base = renderLimited ? fog.renderDistanceEnd : fog.environmentalEnd;
		double baseStart = renderLimited ? fog.renderDistanceStart : fog.environmentalStart;
		if (base <= 1.0) {
			return;
		}
		double end = Curves.fogEnd(Curves.fogEnd(base, cfg.duskMinFogBlocks, dusk), cfg.surgeMinFogBlocks, surgeAmount);
		double heavy = Curves.combine(dusk, surgeAmount);
		double startFraction = Curves.lerp(heavy, Curves.clamp01(baseStart / base), Curves.clamp01(cfg.heavyFogStartFraction));
		float e = (float) end;
		float st = (float) (end * startFraction);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, e);
		fog.environmentalStart = Math.min(fog.environmentalStart, st);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, e);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, st);
		fog.skyEnd = Math.min(fog.skyEnd, e);
		fog.cloudEnd = Math.min(fog.cloudEnd, e);
	}

	/** Where the local player's spawn compass points: the drift target while a drift runs. */
	public static @Nullable GlobalPos compassTarget(ClientLevel level, @Nullable ItemOwner owner, @Nullable GlobalPos original) {
		CompassDriftPayload drift = INSTANCE.compass;
		Minecraft minecraft = Minecraft.getInstance();
		if (drift == null || owner == null || original == null || minecraft.player == null || owner.asLivingEntity() != minecraft.player
				|| original.dimension() != level.dimension()) {
			return original;
		}
		return GlobalPos.of(level.dimension(), new BlockPos(drift.x(), minecraft.player.getBlockY(), drift.z()));
	}
}
