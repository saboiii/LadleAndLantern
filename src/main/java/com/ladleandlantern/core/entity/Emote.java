package com.ladleandlantern.core.entity;

public enum Emote {
    NONE, SMILE, ANGRY, TALK, LOVE, MUSIC, ALERT, THINK, YUM, UMBRELLA, DRINK, MENU, COIN, PARTY, SLEEP, COAT;

    public int cell() {
        return ordinal() - 1;
    }

    public static Emote byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NONE;
    }
}
