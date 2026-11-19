package com.debate.game.validation;

import com.debate.game.exception.InvalidRoomSettingsException;

/**
 * R14: single source of default values and limits for backend room settings.
 * The frontend currently defines bounds for only maxItems, itemSeconds, cardsPerItem.
 * Speech/rebuttal upper bounds are temporary server-side safety limits (see docs/R14.md).
 */
public final class RoomSettingsPolicy {
    private RoomSettingsPolicy() {}

    public static final int DEFAULT_CARDS_PER_ITEM = 3;
    public static final int MIN_CARDS_PER_ITEM = 1;
    public static final int MAX_CARDS_PER_ITEM = 5;

    public static final int DEFAULT_MAX_ITEMS = 3;
    public static final int MIN_MAX_ITEMS = 1;
    public static final int MAX_MAX_ITEMS = 5;

    public static final int DEFAULT_ITEM_SECONDS = 300;
    public static final int MIN_ITEM_SECONDS = 180;
    public static final int MAX_ITEM_SECONDS = 1200;
    // The existing DB turn_time_limit column stores minutes; preserve exact persistence.
    public static final int ITEM_SECONDS_STEP = 60;

    public static final int DEFAULT_SPEECH_SECONDS = 120;
    public static final int DEFAULT_REBUTTAL_SECONDS = 60;
    // Provisional backend-only bounds. No frontend min/max yet. Adjust after team agreement.
    public static final int MIN_SPEECH_SECONDS = 1;
    public static final int MAX_SPEECH_SECONDS = 3600;
    public static final int MIN_REBUTTAL_SECONDS = 1;
    public static final int MAX_REBUTTAL_SECONDS = 3600;

    public static int cardsPerItem(Integer value) {
        return inRange(value, "cardsPerItem", MIN_CARDS_PER_ITEM, MAX_CARDS_PER_ITEM);
    }

    public static int maxItems(Integer value) {
        return inRange(value, "maxItems", MIN_MAX_ITEMS, MAX_MAX_ITEMS);
    }

    public static int speechSeconds(Integer value) {
        return inRange(value, "speechSeconds", MIN_SPEECH_SECONDS, MAX_SPEECH_SECONDS);
    }

    public static int rebuttalSeconds(Integer value) {
        return inRange(value, "rebuttalSeconds", MIN_REBUTTAL_SECONDS, MAX_REBUTTAL_SECONDS);
    }

    public static int itemSeconds(Integer value) {
        int seconds = inRange(value, "itemSeconds", MIN_ITEM_SECONDS, MAX_ITEM_SECONDS);
        if (seconds % ITEM_SECONDS_STEP != 0) {
            throw new InvalidRoomSettingsException("itemSeconds는 60초 단위여야 합니다.");
        }
        return seconds;
    }

    /** Optional legacy POST /api/rooms fields, before the game settings UI is connected. */
    public static int initialCardsPerItem(Integer value) {
        return cardsPerItem(value == null ? DEFAULT_CARDS_PER_ITEM : value);
    }

    public static int initialItemMinutes(Integer minutes) {
        int actual = minutes == null ? DEFAULT_ITEM_SECONDS / ITEM_SECONDS_STEP : minutes;
        // Check BEFORE multiplying, to avoid integer overflow on malicious input.
        int minMinutes = MIN_ITEM_SECONDS / ITEM_SECONDS_STEP;
        int maxMinutes = MAX_ITEM_SECONDS / ITEM_SECONDS_STEP;
        if (actual < minMinutes || actual > maxMinutes) {
            throw new InvalidRoomSettingsException(
                    "turnTimeLimit는 " + minMinutes + "~" + maxMinutes + "분이어야 합니다.");
        }
        return actual;
    }

    private static int inRange(Integer value, String name, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new InvalidRoomSettingsException(
                    name + "는 " + min + "~" + max + " 범위여야 합니다.");
        }
        return value;
    }
}
