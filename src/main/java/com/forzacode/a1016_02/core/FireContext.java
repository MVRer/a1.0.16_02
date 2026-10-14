package com.forzacode.a1016_02.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * What a card gets when it fires.
 *
 * @param player the participant the card fires for (D-059); look them up again by UUID if anything runs later
 * @param level  that player's level
 * @param fake   fire the false-positive version (an ordinary cow, a real cave sound)
 * @param forced fired by a debug command: gates and pacing were skipped, the out-of-view rule never is
 * @param random use this for every roll
 */
public record FireContext(ServerPlayer player, ServerLevel level, boolean fake, boolean forced, RandomSource random) {
}
