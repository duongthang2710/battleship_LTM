package com.battleship.shared.auth;

public record LoginRequest(String username, String password) {
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
