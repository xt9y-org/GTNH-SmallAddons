package com.xt9y.features.xtprofile;

final class XTProfileScroll {

    private static final int MIN_THUMB_HEIGHT = 12;

    static int maxScroll(int entryCount, int visibleRows) {
        return Math.max(0, entryCount - visibleRows);
    }

    static int clamp(int scroll, int entryCount, int visibleRows) {
        return Math.max(0, Math.min(scroll, maxScroll(entryCount, visibleRows)));
    }

    static int wheel(int scroll, int entryCount, int visibleRows, int wheelDelta) {
        if (wheelDelta == 0) return clamp(scroll, entryCount, visibleRows);
        return clamp(scroll + (wheelDelta < 0 ? 1 : -1), entryCount, visibleRows);
    }

    static int thumbHeight(int trackHeight, int entryCount, int visibleRows) {
        if (entryCount <= visibleRows || entryCount <= 0) return trackHeight;
        int proportional = (int) Math.round(trackHeight * visibleRows / (double) entryCount);
        return Math.max(MIN_THUMB_HEIGHT, Math.min(trackHeight, proportional));
    }

    static int thumbTop(int trackTop, int trackHeight, int entryCount, int visibleRows, int scroll) {
        int maxScroll = maxScroll(entryCount, visibleRows);
        if (maxScroll <= 0) return trackTop;

        int thumbHeight = thumbHeight(trackHeight, entryCount, visibleRows);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int clamped = clamp(scroll, entryCount, visibleRows);
        return trackTop + (int) Math.round(travel * clamped / (double) maxScroll);
    }

    static int scrollForThumb(int mouseY, int trackTop, int trackHeight, int entryCount, int visibleRows) {
        int maxScroll = maxScroll(entryCount, visibleRows);
        if (maxScroll <= 0) return 0;

        int thumbHeight = thumbHeight(trackHeight, entryCount, visibleRows);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbTop = mouseY - trackTop - thumbHeight / 2;
        double ratio = Math.max(0.0, Math.min(1.0, thumbTop / (double) travel));
        return clamp((int) Math.round(ratio * maxScroll), entryCount, visibleRows);
    }

    private XTProfileScroll() {}
}
