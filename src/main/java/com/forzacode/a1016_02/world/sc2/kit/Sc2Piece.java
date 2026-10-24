package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import org.jspecify.annotations.Nullable;

/**
 * The one piece type of the second wave ({@code a1016_02:world/sc2p}): it saves only its kind and {@link Start} (kind
 * {@code k}, variant {@code v}, seed {@code s}, origin {@code o}, facing {@code f}, the shape's version {@code g}; with
 * vanilla's box, under 200 bytes) and rebuilds its {@link Shape} from them over the noise, once per piece object (any
 * worldgen thread; the first to ask builds it). The version is the one the start was made with (R12-2): a piece saved
 * before versions ({@code g} absent) is version 1, and a later jar builds each piece with its own version, so a place
 * half generated when a shape changed is finished as it was begun. Each chunk it touches calls {@link #postProcess} once, which fills the chunk's part through a
 * {@link Writer}; the first call offers its place to the {@link Recorder}. A piece of a kind this jar does not know
 * (a removed kind, or a dev-only one) loads and places nothing.
 */
public final class Sc2Piece extends StructurePiece {
	public static final StructurePieceType TYPE = (context, tag) -> new Sc2Piece(tag);

	private final String kind;
	private final Start start;
	private final Object shapeLock = new Object();
	private volatile @Nullable Shape shape;
	private final AtomicBoolean offered = new AtomicBoolean();

	/** A new start's piece, its shape already made by siting (the start tied to its kind's version here). */
	public Sc2Piece(String kind, Start start, Shape shape) {
		super(TYPE, 0, shape.box());
		this.kind = kind;
		this.start = Sc2Kind.resolved(kind, start);
		this.shape = shape;
	}

	/** A saved piece. */
	public Sc2Piece(CompoundTag tag) {
		super(TYPE, tag);
		this.kind = tag.getStringOr("k", "");
		BlockPos origin = BlockPos.of(tag.getLongOr("o", 0L));
		this.start = new Start(origin, Direction.from2DDataValue(tag.getIntOr("f", 2)), tag.getIntOr("v", 0), tag.getLongOr("s", 0L),
				Math.max(Start.FIRST, tag.getIntOr("g", Start.FIRST)));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("k", kind);
		tag.putInt("v", start.variant());
		tag.putLong("s", start.seed());
		tag.putLong("o", start.origin().asLong());
		tag.putInt("f", start.facing().get2DDataValue());
		tag.putInt("g", Sc2Kind.resolved(kind, start).versionOr(Start.FIRST));
	}

	public String kind() {
		return kind;
	}

	public Start start() {
		return start;
	}

	/** Its shape, made once over this terrain (null if its kind is unknown). Any thread. */
	public @Nullable Shape shape(Terrain terrain) {
		Shape made = shape;
		if (made == null) {
			Sc2Kind def = Sc2Kind.get(kind);
			if (def == null) {
				return null;
			}
			synchronized (shapeLock) {
				made = shape;
				if (made == null) {
					made = def.shape(start, terrain);
					shape = made;
				}
			}
		}
		return made;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
			ChunkPos chunkPos, BlockPos referencePos) {
		Sc2Kind def = Sc2Kind.get(kind);
		if (def == null) {
			return;
		}
		try {
			Terrain terrain = Terrains.noise(generator, level.getLevel().getChunkSource().randomState(), level);
			Shape made = shape(terrain);
			if (made == null) {
				return;
			}
			Optional<BoundingBox> clip = Draw.intersection(chunkBB, made.box());
			if (clip.isEmpty()) {
				return;
			}
			made.fill(Writer.worldgen(level, clip.get(), terrain), clip.get());
			if (offered.compareAndSet(false, true)) {
				Recorder.offer(kind, start, level.getLevel().dimension(), def.remains(), made);
			}
		} catch (RuntimeException e) {
			ScLog.error("[a1016] s2: a {} piece failed in chunk {}", kind, chunkPos, e);
		}
	}
}
