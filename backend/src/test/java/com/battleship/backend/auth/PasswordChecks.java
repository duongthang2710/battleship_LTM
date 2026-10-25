package com.battleship.backend.auth;

public final class PasswordChecks {
    public static void main(String[] args) {
        PasswordHasher hasher = new PasswordHasher();
        String password = "PasswordCheck2026!";
        String first = hasher.hash(password), second = hasher.hash(password);
        if (first.equals(second) || first.contains(password) || !hasher.verify(password, first)
                || hasher.verify("wrong", first) || hasher.verify(password, "invalid"))
            throw new AssertionError("Password hashing regression");
        System.out.println("PASS password hashing: independent salts, correct/wrong passwords, malformed hash.");
    }
}
