package com.forzacode.a1016_02.director;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The director's private persistence: {@code data/a1016_02/director.dat}, one {@link DirectorMemory} per world. */
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

	public static DirectorData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The live memory. Call {@link #setDirty()} after changing it. */
	public DirectorMemory memory() {
		return memory;
	}
}
