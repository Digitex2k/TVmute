package ru.digit.tvmute;

public class PressStateMachineTest {

    private static int total = 0;
    private static int bad = 0;

    private static void scenario(String name, boolean ok) {
        total++;
        if (ok) {
            System.out.println("ok: " + name);
        } else {
            bad++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        PressStateMachine m = new PressStateMachine();
        boolean s1 = m.onDown(1000, false) == PressStateMachine.Action.NONE
                && m.onUp(1150) == PressStateMachine.Action.MUTE;
        scenario("short tap -> MUTE", s1);

        m = new PressStateMachine();
        boolean downNone = m.onDown(0, false) == PressStateMachine.Action.NONE;
        boolean timeout = m.onTimeout() == PressStateMachine.Action.OPEN_PATCHWALL;
        boolean upAfterLong = m.onUp(700) == PressStateMachine.Action.NONE;
        boolean timeoutTwice = m.onTimeout() == PressStateMachine.Action.NONE;
        scenario("hold -> OPEN_PATCHWALL once, UP no mute",
                downNone && timeout && upAfterLong && timeoutTwice);

        m = new PressStateMachine();
        boolean t1 = m.onDown(0, false) == PressStateMachine.Action.NONE
                && m.onUp(150) == PressStateMachine.Action.MUTE;
        boolean t2 = m.onDown(160, false) == PressStateMachine.Action.NONE
                && m.onUp(300) == PressStateMachine.Action.MUTE;
        scenario("two quick taps -> two MUTE", t1 && t2);

        m = new PressStateMachine();
        boolean repeatNone = m.onDown(0, false) == PressStateMachine.Action.NONE;
        boolean repeatIgnored = m.onDown(200, true) == PressStateMachine.Action.NONE;
        boolean longStillWorks = m.onTimeout() == PressStateMachine.Action.OPEN_PATCHWALL;
        scenario("repeat DOWN ignored, long still fires", repeatNone && repeatIgnored && longStillWorks);

        m = new PressStateMachine();
        m.onDown(0, false);
        m.reset();
        boolean noTimeout = m.onTimeout() == PressStateMachine.Action.NONE;
        boolean noMute = m.onUp(100) == PressStateMachine.Action.NONE;
        scenario("reset cancels pending press", noTimeout && noMute);

        m = new PressStateMachine();
        scenario("timeout without DOWN -> NONE", m.onTimeout() == PressStateMachine.Action.NONE);

        System.out.println("PressStateMachineTest: " + (total - bad) + "/" + total + " OK");
        System.exit(bad == 0 ? 0 : 1);
    }
}
