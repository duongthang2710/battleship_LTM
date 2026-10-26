package com.battleship.shared.chat;

public final class ChatText {
    public static final int MAX_LENGTH = 300;
    private ChatText() {}

    public static String validate(String value) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) throw new IllegalArgumentException("Enter a message before sending.");
        if (value.codePointCount(0, value.length()) > MAX_LENGTH)
            throw new IllegalArgumentException("Messages are limited to 300 characters.");
        if (value.codePoints().anyMatch(ChatText::forbidden))
            throw new IllegalArgumentException("Control characters and hidden formatting are not allowed.");
        return value.strip();
    }

    public static boolean forbidden(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint) || type == Character.FORMAT || type == Character.SURROGATE
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}
