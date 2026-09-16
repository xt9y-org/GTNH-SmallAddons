package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class XTProfileLoadingScreenCompatTest {

    @Test
    void skipsOnlyBetterLoadingScreenSplashThreadReload() {
        assertTrue(shouldSkip("BLS Splash renderer"));
        assertFalse(shouldSkip("Client thread"));
        assertFalse(shouldSkip("Server thread"));
    }

    private static boolean shouldSkip(String threadName) {
        try {
            Class<?> compat = Class.forName("com.xt9y.features.xtprofile.XTProfileLoadingScreenCompat");
            Method method = compat.getDeclaredMethod("shouldSkipFontResourceReload", String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, threadName);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
