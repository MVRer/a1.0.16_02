package com.forzacode.a1016_02.core;

/** What a card does, used by gates such as "no other sound event in the last 10 minutes". */
public enum CardTag {
	SOUND,
	SIGHTING,
	SCAR,
	FOG,
	MOB,
	ACCIDENT,
	ITEM,
	LIGHT,
	TEXT,
	DIG,
	/**
	 * A card for one player alone (D-074; the tunnel ladder, D-072): it only works while nobody can confirm it. The
	 * director never draws it while the player has sharers ({@link PlayerWatch#sharingWith} lists anyone besides them),
	 * and a held PERSONAL card goes back into its deck as soon as a sharer comes close, so it never blocks its tier while
	 * friends stand together. The card's own alone check still runs when it fires.
	 */
	PERSONAL
}
