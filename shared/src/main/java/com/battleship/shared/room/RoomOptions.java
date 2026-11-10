package com.battleship.shared.room;

import com.google.gson.annotations.SerializedName;

public record RoomOptions(
        @SerializedName(value = "hideSunkInfo", alternate = {"hideFleetReport"}) boolean hideSunkInfo) {
    public static final RoomOptions DEFAULT = new RoomOptions(false);
}
