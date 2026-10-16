package com.forzacode.a1016_02.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.accident.sc.StoodOn;
import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.card.NameGoneCard;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.atmosphere.sc.InStep;
import com.forzacode.a1016_02.dig.sc.WayBack;
import com.forzacode.a1016_02.entity.EntityData;
import com.forzacode.a1016_02.entity.sc.HisView;
import com.forzacode.a1016_02.world.sc.Prints;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SC-7, the verifier (docs/SELF_CREATED.md, "Verifier checklist", the switch): the real {@code /a1016 sc off} on the
 * real server, every registered revert run for real (not each part's own hook), in a test environment of its own
 * ({@code a1016_02:verify/sc_switch}) because the switch is global and live. Then the same off again (idempotent), the
 * switch already off at a server start (core's start hook), and {@code /a1016 sc on}. The switch is put back on in a
 * finally, so a failure here never leaves the layer off for the batches after it.
 */
public class VerifyScSwitchTests {
	private static final String BIG = "a1016_02:world/signature";
	private static final String ENV = "a1016_02:verify/sc_switch";
	private static final long DAY = 24_000L;

	@GameTest(environment = ENV, structure = BIG, skyAccess = true, maxTicks = 60)
	public void scOffPutsEveryPartBackOnceAndAgainAtAStartWhileOff(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects effectsBefore = state.effects();
		EntityData entityData = EntityData.get(server);
		boolean doneBefore = entityData.sc03Done();
		boolean restoredBefore = entityData.sc03Restored();
		Path folder = SelfCreated.worldFolder(server);
		Path icon = folder.resolve("icon.png");
		Path before = folder.resolve("data/a1016_02/sc03_before.png");
		byte[] iconWas = Files.exists(icon) ? Files.readAllBytes(icon) : null;
		byte[] beforeWas = Files.exists(before) ? Files.readAllBytes(before) : null;
		List<String> said = new ArrayList<>();
		CommandSourceStack op = server.createCommandSourceStack().withSource(capture(said));
		UUID armed = UUID.randomUUID();
		ServerPlayer mock = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		String tag = "/verify-" + helper.absolutePos(BlockPos.ZERO).toShortString();
		try {
			helper.assertTrue(SelfCreated.enabled(), "the layer is off before the test");
			for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 10, 1, 10)) {
				helper.setBlock(rel, Blocks.GRASS_BLOCK);
			}
			// 08: a turned furnace (core keeps what the turn left).
			BlockPos furnace = new BlockPos(2, 2, 2);
			helper.setBlock(furnace, furnace(Direction.NORTH));
			helper.assertTrue(Services.traces().convert(level, helper.absolutePos(furnace), furnace(Direction.SOUTH), Turned.CAUSE), "08 refused");
			// 09 and 10: a print each, one under a flower.
			helper.setBlock(new BlockPos(6, 2, 6), Blocks.POPPY);
			print(helper, level, new BlockPos(6, 1, 6), Tracks.CAUSE + tag);
			print(helper, level, new BlockPos(8, 1, 2), WhereHeStood.CAUSE + tag);
			helper.assertBlockPresent(Blocks.DIRT, new BlockPos(6, 1, 6));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 2, 6));
			// 07: a named cow's name taken.
			Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(4, 2, 7));
			cow.setCustomName(Component.literal("Daisy"));
			helper.assertTrue(Services.mobs().unname(cow, NameGoneCard.CAUSE), "07 refused");
			helper.assertFalse(cow.hasCustomName(), "07 kept the name");
			// 10's fog place (core's).
			long now = server.overworld().getDefaultClockTime();
			Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
			helper.assertTrue(ClientEffects.addFogPlace(server, new FogPlace(Level.OVERWORLD, at, 40.0F, 0.45F, now, now + DAY, 100, 100), 4), "no fog place");
			// 04: tonight's empty night.
			helper.assertTrue(EmptyNight.setTonight(server, true).isEmpty(), "04 refused: " + EmptyNight.setTonight(server, true));
			helper.assertTrue(AtmosphereData.get(server).emptyNight().night() != EmptyNight.NEVER, "04 set no night");
			// 03: his picture over the world's own.
			helper.assertTrue(SelfCreated.writeInWorld(folder, "icon.png", "his".getBytes(StandardCharsets.UTF_8))
					&& SelfCreated.writeInWorld(folder, "data/a1016_02/sc03_before.png", "old".getBytes(StandardCharsets.UTF_8)), "03's files");
			entityData.setSc03Done(true);
			entityData.setSc03Restored(false);
			// Arms in memory: 15, 13, 06.
			StoodOn.arm(armed, Long.MAX_VALUE, true);
			WayBack.arm(mock, server.getTickCount(), null);
			InStep.LIVE.arm(mock, false, true, null);

			// --- /a1016 sc off ---
			server.getCommands().performPrefixedCommand(op, "a1016 sc off");
			String off = String.join("\n", said);
			helper.assertTrue(!SelfCreated.enabled() && off.contains("sc: off"), "sc off said: " + off);
			helper.assertTrue(helper.getBlockState(furnace).getValue(FurnaceBlock.FACING) == Direction.NORTH, "08 not turned back: " + helper.getBlockState(furnace));
			helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(6, 1, 6));
			helper.assertBlockPresent(Blocks.POPPY, new BlockPos(6, 2, 6));
			helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(8, 1, 2));
			helper.assertTrue(cow.hasCustomName() && cow.getCustomName().getString().equals("Daisy"), "07's name not back: " + cow.getCustomName());
			helper.assertTrue(state.effects().fogPlaces().isEmpty(), "the fog places stayed: " + state.effects().fogPlaces());
			helper.assertTrue(AtmosphereData.get(server).emptyNight().night() == EmptyNight.NEVER && EmptyNight.windowNow(server).isEmpty(), "04's night stayed");
			helper.assertTrue(new String(Files.readAllBytes(icon), StandardCharsets.UTF_8).equals("old") && entityData.sc03Restored(), "03's picture not put back");
			helper.assertTrue(!StoodOn.armed(armed) && !WayBack.armed(mock.getUUID()) && InStep.LIVE.armTicksLeft(server, mock.getUUID()) < 0, "an arm stayed");
			helper.assertTrue(ledger(server, tag).isEmpty() && ledgerExact(server, Turned.CAUSE, helper.absolutePos(furnace)).isEmpty(),
					"his traces stayed: " + ledger(server, tag));
			said.clear();
			server.getCommands().performPrefixedCommand(op, "a1016 sc");
			helper.assertTrue(!said.isEmpty() && said.getFirst().contains("sc: off"), "the index: " + said);

			// --- again: idempotent ---
			said.clear();
			server.getCommands().performPrefixedCommand(op, "a1016 sc off");
			helper.assertTrue(String.join("\n", said).contains("already off"), "the second off said: " + said);
			List<String> registered = SelfCreated.registered().stream().map(Map.Entry::getKey).toList();
			List<String> reran = SelfCreated.runReverts(server);
			helper.assertTrue(reran.equals(registered) && registered.size() >= 16, "a revert threw when run again: ran " + reran + " of " + registered);
			helper.assertTrue(helper.getBlockState(furnace).getValue(FurnaceBlock.FACING) == Direction.NORTH && cow.getCustomName().getString().equals("Daisy")
					&& new String(Files.readAllBytes(icon), StandardCharsets.UTF_8).equals("old"), "running the reverts again changed something");

			// --- off at a server start: what is his now goes at the start ---
			BlockPos second = new BlockPos(2, 2, 6);
			helper.setBlock(second, furnace(Direction.EAST));
			helper.assertTrue(Services.traces().convert(level, helper.absolutePos(second), furnace(Direction.WEST), Turned.CAUSE), "08 (2) refused");
			print(helper, level, new BlockPos(4, 1, 4), Tracks.CAUSE + tag);
			StoodOn.arm(armed, Long.MAX_VALUE, true);
			SelfCreated.serverStarted(server);
			helper.assertTrue(helper.getBlockState(second).getValue(FurnaceBlock.FACING) == Direction.EAST, "08 not turned back at the start");
			helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(4, 1, 4));
			helper.assertFalse(StoodOn.armed(armed), "15's arm stayed at the start");

			// --- on ---
			said.clear();
			server.getCommands().performPrefixedCommand(op, "a1016 sc on");
			helper.assertTrue(SelfCreated.enabled() && String.join("\n", said).contains("sc: on"), "sc on said: " + said);
		} finally {
			if (!SelfCreated.enabled()) {
				SelfCreated.setEnabled(server, true);
			}
			StoodOn.clear();
			WayBack.clear();
			InStep.LIVE.clear();
			EmptyNight.clearNight(server);
			ledger(server, tag).forEach(TraceLedger.get(server)::remove);
			for (BlockPos rel : List.of(new BlockPos(2, 2, 2), new BlockPos(2, 2, 6))) {
				ledgerExact(server, Turned.CAUSE, helper.absolutePos(rel)).forEach(TraceLedger.get(server)::remove);
			}
			TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(NameGoneCard.CAUSE)
					&& e.pos().pos().closerThan(helper.absolutePos(new BlockPos(4, 2, 7)), 16)).toList().forEach(TraceLedger.get(server)::remove);
			state.setEffects(effectsBefore);
			entityData.setSc03Done(doneBefore);
			entityData.setSc03Restored(restoredBefore);
			restore(icon, iconWas);
			restore(before, beforeWas);
		}
		helper.succeed();
	}

	private static BlockState furnace(Direction facing) {
		return Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facing);
	}

	private static void print(GameTestHelper helper, ServerLevel level, BlockPos ground, String cause) {
		TraceBatch batch = Services.traces().batch(level, cause);
		Prints.queue(batch, Prints.at(level, helper.absolutePos(ground)));
		helper.assertTrue(batch.commit(), "the print was refused: " + cause);
		helper.assertBlockPresent(Blocks.DIRT, ground);
	}

	private static List<TraceLedger.Entry> ledger(MinecraftServer server, String tag) {
		return TraceLedger.get(server).entries().stream().filter(e -> e.cause().contains(tag)).toList();
	}

	private static List<TraceLedger.Entry> ledgerExact(MinecraftServer server, String cause, BlockPos pos) {
		return TraceLedger.get(server).entries().stream().filter(e -> e.cause().startsWith(cause) && e.pos().pos().equals(pos)).toList();
	}

	private static void restore(Path file, byte[] was) throws java.io.IOException {
		if (was != null) {
			Files.write(file, was);
		} else {
			Files.deleteIfExists(file);
		}
	}

	private static CommandSource capture(List<String> said) {
		return new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
	}
}
