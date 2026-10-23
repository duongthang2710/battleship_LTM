package com.battleship.frontend.network;

import com.battleship.shared.protocol.Message;

public interface MessageHandler {
    void onMessage(Message message);
    void onDisconnected(String reason);
}
