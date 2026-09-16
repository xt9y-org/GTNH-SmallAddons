package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class XTProfileLoadingScreenCompatTest {

    @Test
    void suppressesOnlyBetterLoadingScreenBackgroundRefresh() {
        assertTrue(shouldSuppress("BLS Splash renderer"));
        assertFalse(shouldSuppress("Client thread"));
        assertFalse(shouldSuppress("Server thread"));
    }

    private static boolean shouldSuppress(String threadName) {
        try {
            Class<?> compat = Class.forName("com.xt9y.features.xtprofile.XTProfileLoadingScreenCompat");
            Method method = compat.getDeclaredMethod("shouldSuppressBackgroundResourceRefresh", String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, threadName);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
