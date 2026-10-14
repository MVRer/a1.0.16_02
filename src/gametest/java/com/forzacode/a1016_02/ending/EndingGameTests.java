package com.forzacode.a1016_02.ending;

/**
 * Game tests of the ending workstream, already registered in the gametest fabric.mod.json. The tests live in its
 * superclasses: {@link EndingRuleTests} (commit rules), {@link EndingBeatTests} (each path's beats, the third-death
 * rule, hardcore, the debug step), {@link EndingWorldTests} (the house, the doorway, the ledger, burning, saving) and
 * {@link EndingGroupTests} (with friends: D-058, D-063, D-064), then Ending D's {@code ending.d.EndingDGameTests} chain
 * above {@link EndingRuleTests}.
 */
public class EndingGameTests extends EndingWiringTests {
}
