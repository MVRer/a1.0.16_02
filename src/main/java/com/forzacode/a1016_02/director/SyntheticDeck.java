package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

/**
 * A stand-in deck shaped like DESIGN.md's event catalog, for dry runs before the other workstreams register their
 * cards ({@code /a1016 director sim <hours> synthetic}) and for the game tests. Never registered, never fired.
 */
public final class SyntheticDeck {
	private SyntheticDeck() {
	}

	public static List<CardInfo> cards() {
		List<CardInfo> cards = new ArrayList<>();
		// Ambient (often)
		cards.add(card("sim_fog_drift", Tier.AMBIENT, Stage.ALONE, true, Set.of(), CardTag.FOG));
		cards.add(card("sim_far_cow", Tier.AMBIENT, Stage.ALONE, true, Set.of(Habit.WATCHER), CardTag.SIGHTING, CardTag.MOB));
		cards.add(card("sim_cave_sound", Tier.AMBIENT, Stage.ALONE, true, Set.of(), CardTag.SOUND));
		cards.add(card("sim_animals_face_fog", Tier.AMBIENT, Stage.TRACES, true, Set.of(Habit.WATCHER), CardTag.MOB, CardTag.FOG));
		cards.add(card("sim_music_off", Tier.AMBIENT, Stage.TRACES, false, Set.of(), CardTag.SOUND));
		cards.add(card("sim_silence", Tier.AMBIENT, Stage.TRACES, false, Set.of(Habit.VISITOR), CardTag.SOUND));
		cards.add(card("sim_mob_frozen", Tier.AMBIENT, Stage.PROXIMITY, true, Set.of(), CardTag.MOB));
		cards.add(card("sim_torch_flicker", Tier.AMBIENT, Stage.PROXIMITY, false, Set.of(Habit.MOURNER), CardTag.LIGHT));
		// Minor (sometimes)
		cards.add(card("sim_new_scar", Tier.MINOR, Stage.TRACES, false, Set.of(Habit.CARVER, Habit.STRIPPER), CardTag.SCAR));
		cards.add(card("sim_far_sighting", Tier.MINOR, Stage.TRACES, true, Set.of(Habit.WATCHER), CardTag.SIGHTING));
		cards.add(card("sim_chest_opens", Tier.MINOR, Stage.PROXIMITY, true, Set.of(Habit.COLLECTOR, Habit.VISITOR), CardTag.ITEM));
		cards.add(card("sim_mining_in_dark", Tier.MINOR, Stage.PROXIMITY, true, Set.of(Habit.CARVER), CardTag.SOUND, CardTag.DIG));
		cards.add(card("sim_torch_gone", Tier.MINOR, Stage.PROXIMITY, false, Set.of(Habit.MOURNER), CardTag.LIGHT));
		cards.add(card("sim_leaves_gone", Tier.MINOR, Stage.PROXIMITY, false, Set.of(Habit.STRIPPER), CardTag.SCAR));
		cards.add(card("sim_moved_mob", Tier.MINOR, Stage.PROXIMITY, true, Set.of(), CardTag.MOB, CardTag.ACCIDENT));
		cards.add(card("sim_blank_sign", Tier.MINOR, Stage.TELLING, false, Set.of(), CardTag.TEXT));
		// Major (rare)
		cards.add(card("sim_close_sighting", Tier.MAJOR, Stage.PROXIMITY, true, Set.of(Habit.WATCHER), CardTag.SIGHTING));
		cards.add(card("sim_tunnel_into_mine", Tier.MAJOR, Stage.PROXIMITY, false, Set.of(Habit.CARVER), CardTag.DIG, CardTag.SCAR));
		cards.add(card("sim_emptied_house", Tier.MAJOR, Stage.PROXIMITY, false, Set.of(Habit.COLLECTOR), CardTag.ITEM));
		cards.add(card("sim_lone_light", Tier.MAJOR, Stage.PROXIMITY, false, Set.of(Habit.MOURNER), CardTag.LIGHT));
		cards.add(card("sim_armed_fall", Tier.MAJOR, Stage.PROXIMITY, false, Set.of(), CardTag.ACCIDENT));
		// Signature (once)
		cards.add(card(CardInfo.SIGNATURE_STILL_BURNING, Tier.SIGNATURE, Stage.PROXIMITY, false, Set.of(), CardTag.LIGHT));
		cards.add(card(CardInfo.SIGNATURE_HOUSE_ELSEWHERE, Tier.SIGNATURE, Stage.PROXIMITY, false, Set.of(), CardTag.SCAR));
		cards.add(card(CardInfo.SIGNATURE_CROSS_ROW, Tier.SIGNATURE, Stage.PROXIMITY, false, Set.of(Habit.MOURNER), CardTag.SCAR));
		cards.add(card("sim_signature_other", Tier.SIGNATURE, Stage.TRACES, false, Set.of(), CardTag.SCAR));
		return cards;
	}

	private static CardInfo card(String id, Tier tier, Stage stage, boolean fake, Set<Habit> habits, CardTag... tags) {
		Set<CardTag> tagSet = tags.length == 0 ? Set.of() : EnumSet.copyOf(List.of(tags));
		return new CardInfo(id, tier, stage, habits, tagSet, fake);
	}
}
