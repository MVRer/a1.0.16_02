package com.forzacode.a1016_02.core;

/**
 * An accident trap kind. A record rather than an enum so the accident workstream can define its own constants
 * (for example {@code new TrapType("loose_gravel")}) without changing core.
 */
public record TrapType(String id) {
}
