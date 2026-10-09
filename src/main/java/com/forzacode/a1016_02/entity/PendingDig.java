package com.forzacode.a1016_02.entity;

import java.util.List;

import com.forzacode.a1016_02.core.TraceService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A goes-under shaft (D-030) that is dug but not covered, saved in {@link EntityData} from the first block he digs
 * until it is covered or put back. If he is gone before that (the server stopped, his chunk unloaded, he was cleared),
 * the shaft is put back from it later, once its chunk is loaded and the cells are out of view ({@link GoUnder#refillPending}).
 *
 * @param id        the figure dig's id ({@link TraceService.FigureDig#id})
 * @param dimension the dig's level
 * @param column    the dig's column ({@link TraceService.FigureDig#column})
 * @param cause     the dig's cause ({@link GoUnder#CAUSE})
 * @param top       the shaft's top block
 * @param depth     the shaft's planned depth
 * @param cells     every block dug so far, with the state it had, top first
 */
public record PendingDig(String id, ResourceKey<Level> dimension, BlockPos column, String cause, BlockPos top, int depth, List<Cell> cells) {
	/** One dug block of the shaft and the state it had. */
	public record Cell(BlockPos pos, BlockState state) {
		public static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Cell::pos),
				BlockState.CODEC.fieldOf("state").forGetter(Cell::state)
		).apply(i, Cell::new));
	}

	public static final Codec<PendingDig> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(PendingDig::id),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PendingDig::dimension),
			BlockPos.CODEC.fieldOf("column").forGetter(PendingDig::column),
			Codec.STRING.fieldOf("cause").forGetter(PendingDig::cause),
			BlockPos.CODEC.fieldOf("top").forGetter(PendingDig::top),
			Codec.INT.fieldOf("depth").forGetter(PendingDig::depth),
			Cell.CODEC.listOf().fieldOf("cells").forGetter(PendingDig::cells)
	).apply(i, PendingDig::new));

	public PendingDig {
		cells = List.copyOf(cells);
	}

	/** The core figure-dig session this shaft was dug in, so only its own blocks go back. */
	public TraceService.FigureDig dig() {
		return new TraceService.FigureDig(id, dimension, column, cause);
	}

	public GoUnder.Plan plan() {
		return new GoUnder.Plan(top, depth);
	}

	/** True if {@code pos} is one of the recorded dug cells. */
	public boolean recorded(BlockPos pos) {
		return cells.stream().anyMatch(c -> c.pos().equals(pos));
	}
}
