package com.forzacode.a1016_02.atmosphere.client;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.CompassDriftPayload;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.client.ClientEffectsClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.level.material.FogType;

import org.jspecify.annotations.Nullable;

/**
 * Client state of the atmosphere layer and the {@link ClientEffectsClient.Handler}. Everything runs on the client
 * thread. Time is counted in client ticks that only advance while the game is not paused. {@link #reset()} on
 * disconnect; core's {@code Sync} on join restores the persistent part (music off, dusk fog).
 */
public final class ClientAtmosphere implements ClientEffectsClient.Handler {
	static final ClientAtmosphere INSTANCE = new ClientAtmosphere();

	/** Ticks over which a silence cuts the sound out. */
	private static final int SILENCE_CUT_TICKS = 12;
	private static final SoundSource[] SILENCED = {SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.MUSIC};
	/** How far into the fade each silenced category starts coming back: ambience first, then weather, then music. */
	private static final double[] SILENCE_LAG = {0.0, 0.2, 0.45};

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
		boolean hadSilence = silence != null;
		musicOff = false;
		duskLevel = 0.0F;
		surge = null;
		silence = null;
		compass = null;
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
		tickSilence();
		tickCompass(minecraft);
	}

	private void tickSilence() {
		if (silence == null) {
			return;
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
		if (changed) {
			refreshVolumes();
		}
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

	/** Multiplier for a sound category's volume (silence). */
	public static float volumeFactor(SoundSource source) {
		ClientAtmosphere self = INSTANCE;
		if (self.silence == null && self.volume[0] == 1.0F && self.volume[1] == 1.0F && self.volume[2] == 1.0F) {
			return 1.0F;
		}
		for (int i = 0; i < SILENCED.length; i++) {
			if (SILENCED[i] == source) {
				return self.volume[i];
			}
		}
		return 1.0F;
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
			dusk = self.duskLevel * Curves.duskWeight(level.getDefaultClockTime(), cfg.duskNightWeight);
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
