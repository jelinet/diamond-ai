package com.showengine.enums;

import lombok.Getter;

@Getter
public enum PlayerEnum {

    PITCHER("PITCHER","claude"),
    CATCHER("CATCHER","gemini"),
    FIELDER("FIELDER","codex"),
    ;

    private String player;

    private String aiName;


    PlayerEnum(String player, String aiName) {
        this.player = player;
        this.aiName = aiName;
    }

    /**
     * Finds a PlayerEnum by player name.
     */
    public static PlayerEnum fromPlayer(String player) {
        for (PlayerEnum value : values()) {
            if (value.player.equals(player)) {
                return value;
            }
        }
        return PITCHER;
    }

    /**
     * Finds a PlayerEnum by AI name.
     */
    public static PlayerEnum fromAiName(String aiName) {
        for (PlayerEnum value : values()) {
            if (value.aiName.equals(aiName)) {
                return value;
            }
        }
        return PITCHER;
    }

}
