package com.forzacode.a1016_02.entity.client.sc2.s27;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

import org.jspecify.annotations.Nullable;

/**
 * 27's copy (docs/SELF_CREATED_2.md, 27): a remote player that exists on this client only, under an id of its own (negative,
 * never the server's) and a UUID of its own (never the other player's, whose real entity this client may track too). It
 * looks like them because it takes their entry from this client's own player list ({@link #getPlayerInfo}): their skin, cape
 * and model; its name is theirs, so their team's colours and vanilla's name tag are theirs too. It always looks as a player
 * at play looks (never a spectator's floating head). Nothing can touch it: not pickable, pushable or hittable; it pushes
 * nothing. Its {@link Showing} moves it every tick before vanilla's own tick (the walk animation, the body's turn, the
 * cape follow from that).
 */
final class FriendCopy extends RemotePlayer {
	private static int nextId = -2_700_000;

	private final UUID other;
	private final boolean standIn;
	private final Showing showing;

	FriendCopy(ClientLevel level, GameProfile profile, UUID other, boolean standIn, Showing showing) {
		super(level, profile);
		this.other = other;
		this.standIn = standIn;
		this.showing = showing;
		setId(nextId--);
		if (nextId < -2_799_999) {
			nextId = -2_700_000;
		}
	}

	/** The other player's entry in this client's player list (null once they left); the stand-in: this client's own. */
	@Override
	protected @Nullable PlayerInfo getPlayerInfo() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return null;
		}
		if (standIn && Minecraft.getInstance().player != null) {
			return connection.getPlayerInfo(Minecraft.getInstance().player.getUUID());
		}
		return connection.getPlayerInfo(other);
	}

	/** Never drawn once the other player's entry is gone (they left): not a frame with a stranger's skin before it goes. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return getPlayerInfo() != null && super.shouldRenderAtSqrDistance(distance);
	}

	@Override
	public @Nullable GameType gameMode() {
		return GameType.SURVIVAL;
	}

	@Override
	public void tick() {
		showing.step(this);
		if (!isRemoved()) {
			super.tick();
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	/** The dev gallery's stand-in (this client's own profile as "Friend"). */
	boolean standIn() {
		return standIn;
	}

	/** Its customisation bits (the hat layer, jacket, sleeves, trousers, cape), as the other player has them. */
	void parts(byte bits) {
		getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, bits);
	}
}
