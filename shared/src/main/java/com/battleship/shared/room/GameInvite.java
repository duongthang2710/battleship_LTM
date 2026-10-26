package com.battleship.shared.room;

import com.battleship.shared.auth.AccountProfile;
import java.util.UUID;

public record GameInvite(UUID inviteId, String roomCode, AccountProfile inviter, AccountProfile recipient,
                         GameInviteStatus status, long remainingMillis, String message) {}
