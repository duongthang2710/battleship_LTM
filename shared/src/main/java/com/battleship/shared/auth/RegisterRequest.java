package com.battleship.shared.auth;

public record RegisterRequest(String username, String password, String confirmPassword) {
    @Override public String toString() { return "RegisterRequest[credentials redacted]"; }
}
