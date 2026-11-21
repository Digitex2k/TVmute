package ru.digit.tvmute;

/**
 * Чистая логика нажатия кнопки Mi:
 * короткое нажатие -> MUTE, удержание -> OPEN_PATCHWALL (ровно один раз).
 */
public final class PressStateMachine {

    public enum Action { NONE, MUTE, OPEN_PATCHWALL }

    public static final long LONG_PRESS_MS = 500L;

    private boolean down;
    private boolean longFired;

    public Action onDown(long eventTimeMs, boolean repeat) {
        if (repeat) {
            return Action.NONE;
        }
        down = true;
        longFired = false;
        return Action.NONE;
    }

    public Action onUp(long eventTimeMs) {
        if (!down) {
            return Action.NONE;
        }
        boolean wasLong = longFired;
        reset();
        return wasLong ? Action.NONE : Action.MUTE;
    }

    public Action onTimeout() {
        if (!down || longFired) {
            return Action.NONE;
        }
        longFired = true;
        return Action.OPEN_PATCHWALL;
    }

    public void reset() {
        down = false;
        longFired = false;
    }
}
