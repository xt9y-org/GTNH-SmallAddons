package com.xt9y.features.xtprofile;

public final class XTProfileClientState {

    private static volatile XTProfilePanelMessage current;

    static void update(XTProfilePanelMessage message) {
        current = message;
    }

    public static XTProfilePanelMessage current() {
        return current;
    }

    public static void clear() {
        current = null;
    }

    private XTProfileClientState() {}
}
