package com.xt9y.features.xtprofile;

public final class XTProfileLoadingScreenCompat {

    private static final String BLS_SPLASH_THREAD = "BLS Splash renderer";

    private XTProfileLoadingScreenCompat() {}

    public static boolean shouldSuppressBackgroundResourceRefresh(String threadName) {
        return threadName != null && threadName.startsWith(BLS_SPLASH_THREAD);
    }
}
