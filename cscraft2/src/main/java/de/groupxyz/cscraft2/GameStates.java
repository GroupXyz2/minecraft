package de.groupxyz.cscraft2;

public class GameStates {
    public static final int WAITING = 0;
    public static final int STARTING = 1;
    public static final int PLAYING = 2;
    public static final int ENDING = 3;

    public static int CURRENT_STATE = WAITING;

    GameStates() {
        // Prevent instantiation
    }
}
