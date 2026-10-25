package com.battleship.shared.auth;

import java.util.UUID;

/** Public account information. Credentials never appear in server responses. */
public record AccountProfile(UUID id, String username, String displayName) {}
