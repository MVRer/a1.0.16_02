package com.forzacode.a1016_02.ending;

import java.util.List;

import com.forzacode.a1016_02.ending.EndingBeats.B;
import com.forzacode.a1016_02.ending.EndingTestSupport.Run;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The ending's calls to other workstreams after multiplayer (STORY-1's wiring): accident keeps one trap per player, so a
 * friend's armed trap elsewhere never holds up B's escalation or B's final trap for the group, while a player's own
 * trap holds theirs back; F10 names only finished lines, in the order they were finished.
 */
public class EndingWiringTests extends EndingGroupTests {
	@GameTest
	public void aFriendsTrapElsewhereNeverHoldsTheGroupsTrapsUp(GameTestHelper helper) {
		// B's accidents close to home: the friend far away has a trap of their own armed.
		Run r = new Run(helper);
		ServerPlayer friend = r.friend();
		r.commit(EndingPath.B);
		r.data.setHouse(r.at(0, 150, 0));
		r.ports.armable.add("house_fire");
		r.player.snapTo(Vec3.atBottomCenterOf(r.at(10, 1, 10).pos()), 0, 0);
		friend.snapTo(Vec3.atBottomCenterOf(r.at(0, 1, 400).pos()), 0, 0);
		r.ports.traps.put(friend.getUUID(), new EndingState.Trap("lava_floor", r.at(0, 1, 400)));
		r.minutes(2);
		r.tick();
		helper.assertTrue(r.ports.armed.equals(List.of("house_fire")) && r.ports.traps.containsKey(r.player.getUUID()),
				"a friend's trap far away held B's home accident up: " + r.ports.armed);
		// Their own trap holds the next one back, however long they wait.
		r.minutes(180);
		r.tick();
		helper.assertTrue(r.ports.armed.size() == 1, "a second trap was armed while their own one is set");

		// B's final trap inside the copy: the friend's trap elsewhere does not hold it up.
		Run f = new Run(helper);
		ServerPlayer away = f.friend();
		f.ports.copyExists = true;
		f.ports.copySite = f.at(0, 150, 40);
		BlockPos site = f.ports.copySite.pos();
		f.ports.copyBounds = new Bounds(f.ports.copySite.dimension(), BoundingBox.fromCorners(site.offset(-4, -1, -4), site.offset(4, 4, 4)));
		f.commit(EndingPath.B);
		f.data.setHouse(f.at(0, 150, 0));
		f.data.setProgress(EndingPath.B, B.FINAL.ordinal(), f.now);
		f.ports.armable.add("dark_corner");
		GlobalPos inside = f.at(2, 151, 41);
		f.player.snapTo(Vec3.atBottomCenterOf(inside.pos()), 0, 0);
		away.snapTo(Vec3.atBottomCenterOf(f.at(0, 1, 400).pos()), 0, 0);
		f.ports.traps.put(away.getUUID(), new EndingState.Trap("lava_floor", f.at(0, 1, 400)));
		f.engine.bFinal(f.ctx());
		helper.assertTrue(f.data.finalTrap().isPresent() && f.data.finalTrap().equals(f.ports.armedTrap(f.server, f.player.getUUID())),
				"a friend's trap elsewhere held B's final trap up");
		// The friend's own trap is still theirs, and the final death is still only the final trap's.
		f.die(away, f.at(0, 1, 400));
		helper.assertTrue(f.beat(B.class, EndingPath.B) == B.FINAL, "a death outside the copy by a friend's trap was B's final one");
		f.die(f.player, inside);
		helper.assertTrue(f.beat(B.class, EndingPath.B) == B.RECORD && f.ports.f10Named.equals(List.of(f.player.getUUID())),
				"the final trap's death did not end B naming only its victim: " + f.ports.f10Named);

		// Someone in the copy with a trap of their own: no final trap for them.
		Run own = new Run(helper);
		own.ports.copyExists = true;
		own.ports.copySite = own.at(0, 150, 40);
		BlockPos ownSite = own.ports.copySite.pos();
		own.ports.copyBounds = new Bounds(own.ports.copySite.dimension(), BoundingBox.fromCorners(ownSite.offset(-4, -1, -4), ownSite.offset(4, 4, 4)));
		own.commit(EndingPath.B);
		own.data.setHouse(own.at(0, 150, 0));
		own.data.setProgress(EndingPath.B, B.FINAL.ordinal(), own.now);
		own.ports.armable.add("dark_corner");
		own.player.snapTo(Vec3.atBottomCenterOf(own.at(2, 151, 41).pos()), 0, 0);
		own.ports.traps.put(own.player.getUUID(), new EndingState.Trap("lava_floor", own.at(0, 1, 90)));
		own.engine.bFinal(own.ctx());
		helper.assertTrue(own.data.finalTrap().isEmpty() && own.ports.armed.isEmpty(), "a final trap was armed over the player's own one");
		helper.succeed();
	}

	/** F10's lines name finished lines only, never a living friend: a normal-mode friend's ordinary marked death names nobody. */
	@GameTest
	public void f10NeverNamesALivingFriend(GameTestHelper helper) {
		Run r = new Run(helper);
		ServerPlayer friend = r.friend();
		r.commit(EndingPath.B);
		r.data.setHouse(r.at(0, 150, 0));
		// A marked death that is not B's final one: nobody's line is finished, F10 names nobody.
		r.die(friend, r.at(0, 1, 300));
		helper.assertTrue(r.ports.f10Named.isEmpty() && !r.data.lineFinished(friend.getUUID()), "F10 named a living friend: " + r.ports.f10Named);
		helper.succeed();
	}

	/**
	 * MP-7, D-063 in hardcore: X dies B's final death (B's record names X); Y dies a marked death before F20 lands, so
	 * Y's line is finished too; Z plays on. When the record is done, F10 names X, then Y, and never Z.
	 */
	@GameTest
	public void hardcoreALineFinishedDuringBsRecordIsNamedOnF10Too(GameTestHelper helper) {
		Run r = new Run(helper);
		r.hardcore = true;
		ServerPlayer x = r.player;
		ServerPlayer y = r.friend();
		ServerPlayer z = r.friend();
		r.ports.copyExists = true;
		r.ports.copySite = r.at(0, 150, 40);
		r.ports.copyBounds = EndingBeatTests.copyBox(r);
		r.commit(EndingPath.B);
		r.data.setHouse(r.at(0, 150, 0));
		r.data.setProgress(EndingPath.B, B.FINAL.ordinal(), r.now);
		r.ports.armable.add("dark_corner");
		GlobalPos inside = r.at(2, 151, 41);
		x.snapTo(Vec3.atBottomCenterOf(inside.pos()), 0, 0);
		y.snapTo(Vec3.atBottomCenterOf(r.at(0, 1, 300).pos()), 0, 0);
		z.snapTo(Vec3.atBottomCenterOf(r.at(0, 1, 400).pos()), 0, 0);
		r.engine.bFinal(r.ctx());
		helper.assertTrue(r.data.finalTrap().isPresent(), "setup: the final trap was not armed");
		r.die(x, inside);
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.RECORD && r.ports.f10Named.equals(List.of(x.getUUID())) && !r.data.ended(),
				"X's final death did not start B's record naming X: " + r.ports.f10Named);
		// Y's marked death (the spent trap's grace), before F20 is down.
		r.die(y, inside);
		helper.assertTrue(r.data.lineFinished(y.getUUID()) && !r.data.ended() && r.beat(B.class, EndingPath.B) == B.RECORD, "Y's death did not finish Y's line");
		r.ports.f20Ready = true;
		r.tick();
		helper.assertTrue(r.data.ended() && r.beat(B.class, EndingPath.B) == B.DONE, "B's record did not end the story once F20 was down");
		helper.assertTrue(r.ports.f10Named.stream().distinct().toList().equals(List.of(x.getUUID(), y.getUUID())),
				"F10 did not name X, then Y: " + r.ports.f10Named);
		helper.assertFalse(r.ports.f10Named.contains(z.getUUID()), "F10 named Z, who plays on");
		helper.succeed();
	}
}
