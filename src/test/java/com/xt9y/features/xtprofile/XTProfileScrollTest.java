package com.xt9y.features.xtprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XTProfileScrollTest {

    @Test
    void wheelCanMoveBeyondNineVisibleRows() {
        assertEquals(1, XTProfileScroll.wheel(0, 12, 9, -120));
        assertEquals(2, XTProfileScroll.wheel(1, 12, 9, -120));
        assertEquals(3, XTProfileScroll.wheel(2, 12, 9, -120));
        assertEquals(3, XTProfileScroll.wheel(3, 12, 9, -120));
        assertEquals(2, XTProfileScroll.wheel(3, 12, 9, 120));
    }

    @Test
    void scrollbarThumbRepresentsEntireList() {
        int thumb = XTProfileScroll.thumbHeight(135, 18, 9);
        assertTrue(thumb > 0 && thumb < 135);
        assertEquals(0, XTProfileScroll.thumbTop(0, 135, 18, 9, 0));
        assertEquals(135 - thumb, XTProfileScroll.thumbTop(0, 135, 18, 9, 9));
    }

    @Test
    void draggingThumbCanReachLastRows() {
        int scroll = XTProfileScroll.scrollForThumb(134, 0, 135, 18, 9);
        assertEquals(9, scroll);
    }
}
