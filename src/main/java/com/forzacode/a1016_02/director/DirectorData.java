package com.forzacode.a1016_02.director;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The director's private persistence: {@code data/a1016_02/director.dat}, one {@link DirectorMemory} per world (the
 * world part plus each player's pacing). A v1 file loads too; see {@link DirectorMemory}.
 */
public final class DirectorData extends SavedData {
	public static final Codec<DirectorData> CODEC = CompoundTag.CODEC.xmap(tag -> new DirectorData(DirectorMemory.fromTag(tag)),
			data -> data.memory.toTag());
	public static final SavedDataType<DirectorData> TYPE = new SavedDataType<>(A1016_02.id("director"), DirectorData::new, CODEC, null);

	private final DirectorMemory memory;

	public DirectorData() {
		this(new DirectorMemory());
	}

	private DirectorData(DirectorMemory memory) {
		this.memory = memory;
	}

	/** The world's director memory. A v1 subject's pacing goes to the first known player as soon as there is one. */
	public static DirectorData get(MinecraftServer server) {
		DirectorData data = server.getDataStorage().computeIfAbsent(TYPE);
		if (data.memory.unowned() != null) {
			data.adoptUnowned(HerobrineState.get(server));
		}
		return data;
	}

	/** Hands a v1 subject's pacing to the first known player (v1.0's subject, D-062.8). Nothing if nobody is known. */
	void adoptUnowned(HerobrineState state) {
		state.players().stream().findFirst().ifPresent(first -> {
			if (memory.adoptUnowned(first.uuid())) {
				A1016_02.LOGGER.info("[a1016] director: the old single memory is now {}'s pacing", first.name());
				setDirty();
			}
		});
	}

	/** The live memory. Call {@link #setDirty()} after changing it. */
	public DirectorMemory memory() {
		return memory;
	}
}
