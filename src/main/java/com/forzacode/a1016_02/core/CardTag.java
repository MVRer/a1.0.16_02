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
	PERSONAL,
	/**
	 * A card whose moment is a blind moment (D-077): it happens while the player has a menu open
	 * ({@link PlayerWatch#blindTicks} &gt; 0; dig's blind step, torch and door). A menu comes often but never lasts, so a
	 * held BLIND card waits the director's {@code blindHoldMinutes} (15) of looks instead of the 5-minute unfit give-back,
	 * then goes back into its deck parked like any card whose moment did not come ({@code unfitParkMinutes}, 30), so a
	 * player who never opens a menu, or a client without the blind signal, never freezes a tier. The director rechecks
	 * BLIND cards first, held or parked, when core calls {@link Director#recheck} during a blind moment. The card's own
	 * blind check still runs when it fires.
	 */
	BLIND
}
