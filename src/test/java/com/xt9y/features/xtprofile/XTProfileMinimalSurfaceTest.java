package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class XTProfileMinimalSurfaceTest {

    @Test
    void legacyCommandAndBrowserProfilerAreNotPackaged() {
        assertMissing("com.xt9y.features.xtprofile.XTProfileCommand");
        assertMissing("com.xt9y.features.xtprofile.XTProfileHttpServer");
        assertMissing("com.xt9y.features.xtprofile.XTProfileManager");
    }

    private static void assertMissing(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
