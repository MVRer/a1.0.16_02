package com.forzacode.a1016_02.ending.d.client;

import com.forzacode.a1016_02.ending.d.CalmMusicPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Musics;

/** Client side of Ending D: the last minute's calm track. Called by the ending client init. */
public final class EndingDClientInit {
	private EndingDClientInit() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CalmMusicPayload.TYPE, (payload, context) -> {
			Minecraft minecraft = context.client();
			// The game's own calm music (the same pool the music manager plays from), one track, now.
			minecraft.getMusicManager().stopPlaying();
			minecraft.getMusicManager().startPlaying(Musics.GAME);
		});
	}
}
