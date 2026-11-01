package com.battleship.shared.room;

/** Shared room preferences; the authoritative ship and turn rules stay in GameRules. */
public record RoomOptions(boolean shuffleFleet, boolean hideFleetReport) {
    public static final RoomOptions DEFAULT=new RoomOptions(false,false);
}
